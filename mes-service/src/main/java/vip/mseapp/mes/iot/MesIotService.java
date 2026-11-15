package vip.mseapp.mes.iot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.entity.MesIotCollect;
import vip.mseapp.mes.entity.MesIotConfig;
import vip.mseapp.mes.entity.MesReport;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.mapper.MesIotCollectMapper;
import vip.mseapp.mes.mapper.MesIotConfigMapper;
import vip.mseapp.mes.mapper.MesReportMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备数采（三期）：标准化上报接入（设备状态 + 累计产量计数）→ 设备状态实时化 +
 * OEE 完整口径统计（可用率 × 性能 × 质量）。
 *
 * <p>实时源规则：设备在 {@link #REALTIME_WINDOW_MINUTES} 分钟内有数采即视为在线，
 * 看板与 mes_query 工具的设备状态以最近一条数采为准（否则回退台账状态——一期手工/点检驱动）。
 *
 * <p>OEE 口径（按日 × 设备，基于数采状态时长折算）：
 * 可用率 A = (总时长 - 维修/停机时长) / 总时长；
 * 性能 P = 产量增量 / (额定小时产能 × 运行+待机时长)，未配置额定产能时按 1 计；
 * 质量指数 Q = 报工合格数 / (合格数 + 不良数)，无报工按 1 计。OEE = A × P × Q。
 */
@Service
public class MesIotService {

    /** 实时窗口：最近 N 分钟内有数采视为在线（窗口外回退台账状态） */
    public static final int REALTIME_WINDOW_MINUTES = 5;

    private final MesIotCollectMapper collectMapper;
    private final MesIotConfigMapper configMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final MesReportMapper reportMapper;

    public MesIotService(MesIotCollectMapper collectMapper, MesIotConfigMapper configMapper,
                         MesEquipmentMapper equipmentMapper, MesReportMapper reportMapper) {
        this.collectMapper = collectMapper;
        this.configMapper = configMapper;
        this.equipmentMapper = equipmentMapper;
        this.reportMapper = reportMapper;
    }

    // ==================== 上报 ====================

    /**
     * 数采上报（IoT 网关单条推送；也可以「设备数采监控」页模拟上报用于演示/验证）。
     * 写明细 + 折算产量增量 + 设备状态实时化（台账 status 同步刷成数采状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> collect(String equipmentCode, Integer status, BigDecimal outputQty,
                                       LocalDateTime collectTime) {
        if (equipmentCode == null || equipmentCode.isBlank()) {
            throw new ServiceException(400, "设备编码不能为空");
        }
        if (status == null || status < 1 || status > 4) {
            throw new ServiceException(400, "状态非法（1 运行 2 待机 3 维修 4 停机）");
        }
        MesEquipment equipment = equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getCode, equipmentCode).last("LIMIT 1"));
        if (equipment == null) {
            throw new ServiceException(1404, "设备不存在: " + equipmentCode);
        }
        MesIotCollect latest = latest(equipment.getId());
        BigDecimal delta = BigDecimal.ZERO;
        if (outputQty != null) {
            if (latest != null && latest.getOutputQty() != null && outputQty.compareTo(latest.getOutputQty()) >= 0) {
                delta = outputQty.subtract(latest.getOutputQty());
            } else if (latest == null || latest.getOutputQty() == null) {
                delta = BigDecimal.ZERO;
            }
        }
        MesIotCollect row = new MesIotCollect();
        row.setEquipmentId(equipment.getId());
        row.setEquipmentCode(equipment.getCode());
        row.setStatus(status);
        row.setOutputQty(outputQty);
        row.setOutputDelta(delta);
        row.setCollectTime(collectTime == null ? LocalDateTime.now() : collectTime);
        collectMapper.insert(row);
        if (equipment.getStatus() == null || !equipment.getStatus().equals(status)) {
            equipment.setStatus(status);
            equipmentMapper.updateById(equipment);
        }
        return Map.of("equipmentCode", equipment.getCode(), "outputDelta", delta,
                "collectTime", String.valueOf(row.getCollectTime()));
    }

    // ==================== 实时监控 ====================

    /** 实时状态看板：全部设备 + 最近数采 + 在线标记（窗口内）+ 今日产量增量 */
    public List<Map<String, Object>> realtime() {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(REALTIME_WINDOW_MINUTES);
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MesEquipment equipment : equipmentMapper.selectList(new LambdaQueryWrapper<MesEquipment>()
                .orderByAsc(MesEquipment::getCode))) {
            MesIotCollect latest = latest(equipment.getId());
            MesIotConfig config = configOf(equipment.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentId", equipment.getId());
            row.put("equipmentCode", equipment.getCode());
            row.put("equipmentName", equipment.getName());
            // 实时状态：窗口内有数采用数采状态，否则回退台账状态（一期手工/点检驱动）
            boolean online = latest != null && latest.getCollectTime().isAfter(windowStart);
            row.put("online", online);
            row.put("status", online ? latest.getStatus() : equipment.getStatus());
            row.put("source", online ? "iot" : "manual");
            row.put("lastCollectTime", latest == null ? null : String.valueOf(latest.getCollectTime()));
            if (latest != null) {
                // 今日产量增量 = 最近累计计数 - 今日起点前最后一条累计计数（起点前无数采按最近值全计）
                BigDecimal todayOutput = todayOutput(equipment.getId(), dayStart, latest);
                row.put("todayOutput", todayOutput);
            } else {
                row.put("todayOutput", null);
            }
            row.put("ratedCapacity", config == null ? null : config.getRatedCapacity());
            rows.add(row);
        }
        return rows;
    }

    /** 今日产量增量：最近计数减去今日起点前的最后一条计数 */
    private BigDecimal todayOutput(Long equipmentId, LocalDateTime dayStart, MesIotCollect latest) {
        if (latest.getOutputQty() == null) {
            return null;
        }
        MesIotCollect firstOfDay = collectMapper.selectOne(new LambdaQueryWrapper<MesIotCollect>()
                .eq(MesIotCollect::getEquipmentId, equipmentId)
                .ge(MesIotCollect::getCollectTime, dayStart)
                .orderByAsc(MesIotCollect::getCollectTime)
                .last("LIMIT 1"));
        if (firstOfDay != null) {
            BigDecimal base = before(dayStart, equipmentId);
            return latest.getOutputQty().subtract(base == null ? BigDecimal.ZERO : base);
        }
        return latest.getOutputQty();
    }

    /** 今日起点前最后一条的累计计数（计数器基线） */
    private BigDecimal before(LocalDateTime dayStart, Long equipmentId) {
        MesIotCollect last = collectMapper.selectOne(new LambdaQueryWrapper<MesIotCollect>()
                .eq(MesIotCollect::getEquipmentId, equipmentId)
                .lt(MesIotCollect::getCollectTime, dayStart)
                .orderByDesc(MesIotCollect::getCollectTime)
                .last("LIMIT 1"));
        return last == null ? null : last.getOutputQty();
    }

    /** 设备最近一条数采 */
    public MesIotCollect latest(Long equipmentId) {
        return collectMapper.selectOne(new LambdaQueryWrapper<MesIotCollect>()
                .eq(MesIotCollect::getEquipmentId, equipmentId)
                .orderByDesc(MesIotCollect::getCollectTime)
                .last("LIMIT 1"));
    }

    // ==================== OEE ====================

    /** OEE 统计（近 N 天 × 全部设备；数采状态时长折算 + 报工合格率） */
    public List<Map<String, Object>> oee(int days) {
        int span = Math.min(Math.max(days, 1), 30);
        LocalDateTime start = LocalDate.now().minusDays(span - 1).atStartOfDay();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MesEquipment equipment : equipmentMapper.selectList(new LambdaQueryWrapper<MesEquipment>()
                .orderByAsc(MesEquipment::getCode))) {
            List<MesIotCollect> points = collectMapper.selectList(new LambdaQueryWrapper<MesIotCollect>()
                    .eq(MesIotCollect::getEquipmentId, equipment.getId())
                    .ge(MesIotCollect::getCollectTime, start)
                    .orderByAsc(MesIotCollect::getCollectTime));
            MesIotConfig config = configOf(equipment.getId());
            rows.add(oeeRow(equipment, points, config, span));
        }
        return rows;
    }

    private Map<String, Object> oeeRow(MesEquipment equipment, List<MesIotCollect> points,
                                       MesIotConfig config, int span) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("equipmentCode", equipment.getCode());
        row.put("equipmentName", equipment.getName());
        row.put("days", span);
        // 状态时长折算：相邻数采点间隔计入前点状态；尾点计到现在（上限总时长）
        long totalMinutes = Duration.ofDays(span).toMinutes();
        double downMinutes = 0;
        BigDecimal output = BigDecimal.ZERO;
        for (int i = 0; i < points.size(); i++) {
            MesIotCollect point = points.get(i);
            output = output.add(point.getOutputDelta() == null ? BigDecimal.ZERO : point.getOutputDelta());
            if (i + 1 < points.size()) {
                long gap = Duration.between(point.getCollectTime(), points.get(i + 1).getCollectTime()).toMinutes();
                if (gap > 0 && isDown(point.getStatus())) {
                    downMinutes += Math.min(gap, totalMinutes);
                }
            }
        }
        double availability = totalMinutes == 0 ? 0 : (totalMinutes - Math.min(downMinutes, totalMinutes)) / (double) totalMinutes;
        BigDecimal rated = config == null ? null : config.getRatedCapacity();
        double performance = 1;
        String performanceNote = "未配置额定产能，性能按 1 计";
        if (rated != null && rated.signum() > 0) {
            double runtimeHours = Math.max(0, (totalMinutes - downMinutes)) / 60.0;
            double denominator = rated.doubleValue() * runtimeHours;
            if (denominator > 0) {
                performance = Math.min(1, output.doubleValue() / denominator);
                performanceNote = null;
            }
        }
        BigDecimal[] q = reportQty(equipment.getId(), span);
        double quality = q[1].signum() > 0 ? q[0].divide(q[1], 4, RoundingMode.HALF_UP).doubleValue() : 1;
        double oee = availability * performance * quality;
        row.put("availability", round4(availability));
        row.put("performance", round4(performance));
        row.put("performanceNote", performanceNote);
        row.put("quality", round4(quality));
        row.put("oee", round4(oee));
        row.put("outputQty", output);
        row.put("downMinutes", (long) downMinutes);
        row.put("samplePoints", points.size());
        return row;
    }

    private boolean isDown(Integer status) {
        return status != null && (status == 3 || status == 4);
    }

    /** 近 N 天报工 [合格数, 合格+不良] */
    private BigDecimal[] reportQty(Long equipmentId, int span) {
        List<MesReport> reports = reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                .ge(MesReport::getReportTime, LocalDate.now().minusDays(span - 1).atStartOfDay()));
        // 一期报工不落设备维度，此处按全厂口径折算质量指数（设备级质量待报工关联工位后细化）
        BigDecimal ok = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (MesReport report : reports) {
            ok = ok.add(report.getOkQty() == null ? BigDecimal.ZERO : report.getOkQty());
            total = total.add(report.getOkQty() == null ? BigDecimal.ZERO : report.getOkQty())
                    .add(report.getNgQty() == null ? BigDecimal.ZERO : report.getNgQty());
        }
        return new BigDecimal[]{ok, total};
    }

    // ==================== 数采明细与配置 ====================

    /** 数采明细分页（设备/时间段过滤） */
    public PageResult<MesIotCollect> pageCollects(long pageNum, long pageSize, Long equipmentId,
                                                  LocalDateTime begin, LocalDateTime end) {
        LambdaQueryWrapper<MesIotCollect> query = new LambdaQueryWrapper<MesIotCollect>()
                .eq(equipmentId != null, MesIotCollect::getEquipmentId, equipmentId)
                .ge(begin != null, MesIotCollect::getCollectTime, begin)
                .le(end != null, MesIotCollect::getCollectTime, end)
                .orderByDesc(MesIotCollect::getCollectTime);
        Page<MesIotCollect> page = collectMapper.selectPage(Page.of(pageNum, pageSize), query);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    /** 设备数采配置清单（未配置的设备补默认行展示） */
    public List<Map<String, Object>> configList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MesEquipment equipment : equipmentMapper.selectList(new LambdaQueryWrapper<MesEquipment>()
                .orderByAsc(MesEquipment::getCode))) {
            MesIotConfig config = configOf(equipment.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentId", equipment.getId());
            row.put("equipmentCode", equipment.getCode());
            row.put("equipmentName", equipment.getName());
            row.put("ratedCapacity", config == null ? null : config.getRatedCapacity());
            row.put("enabled", config == null || config.getEnabled() == null ? 0 : config.getEnabled());
            rows.add(row);
        }
        return rows;
    }

    /** 保存设备数采配置（额定产能/启用开关，upsert） */
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(Long equipmentId, BigDecimal ratedCapacity, Integer enabled) {
        MesIotConfig config = configOf(equipmentId);
        if (config == null) {
            config = new MesIotConfig();
            config.setEquipmentId(equipmentId);
        }
        config.setRatedCapacity(ratedCapacity);
        config.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
    }

    private MesIotConfig configOf(Long equipmentId) {
        return configMapper.selectOne(new LambdaQueryWrapper<MesIotConfig>()
                .eq(MesIotConfig::getEquipmentId, equipmentId).last("LIMIT 1"));
    }

    private BigDecimal round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
