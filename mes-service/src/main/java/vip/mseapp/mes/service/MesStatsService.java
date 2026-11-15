package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.entity.MesHalt;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.entity.MesQcException;
import vip.mseapp.mes.entity.MesQcOrder;
import vip.mseapp.mes.entity.MesReport;
import vip.mseapp.mes.entity.MesRepair;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.mapper.MesHaltMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.mapper.MesQcExceptionMapper;
import vip.mseapp.mes.mapper.MesQcOrderMapper;
import vip.mseapp.mes.mapper.MesReportMapper;
import vip.mseapp.mes.mapper.MesRepairMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板与统计：总览指标（今日产量/合格率/在制/设备/异常）、产量与质量趋势、设备统计、CSV 导出。
 * 统计口径在内存聚合（一期数据量级足够；三期接数采后可下沉 SQL 聚合）。
 */
@Service
public class MesStatsService {

    private static final int DB_IN_BATCH_SIZE = 500;

    private final MesReportMapper reportMapper;
    private final MesOrderMapper orderMapper;
    private final MesQcOrderMapper qcOrderMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final MesRepairMapper repairMapper;
    private final MesHaltMapper haltMapper;
    private final MesQcExceptionMapper exceptionMapper;

    public MesStatsService(MesReportMapper reportMapper, MesOrderMapper orderMapper, MesQcOrderMapper qcOrderMapper,
                           MesEquipmentMapper equipmentMapper, MesRepairMapper repairMapper,
                           MesHaltMapper haltMapper, MesQcExceptionMapper exceptionMapper) {
        this.reportMapper = reportMapper;
        this.orderMapper = orderMapper;
        this.qcOrderMapper = qcOrderMapper;
        this.equipmentMapper = equipmentMapper;
        this.repairMapper = repairMapper;
        this.haltMapper = haltMapper;
        this.exceptionMapper = exceptionMapper;
    }

    /** 总览看板指标 */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        List<MesReport> todayReports = reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                .select(MesReport::getOkQty, MesReport::getNgQty)
                .ge(MesReport::getReportTime, dayStart)
                .lt(MesReport::getReportTime, today.plusDays(1).atStartOfDay()));
        BigDecimal ok = todayReports.stream().map(MesReport::getOkQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ng = todayReports.stream().map(MesReport::getNgQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long producing = orderMapper.selectCount(new LambdaQueryWrapper<MesOrder>()
                .in(MesOrder::getStatus, MesOrderService.ST_RELEASED, MesOrderService.ST_PRODUCING));
        Long pendingQc = qcOrderMapper.selectCount(new LambdaQueryWrapper<MesQcOrder>().eq(MesQcOrder::getStatus, 0));
        Long halting = haltMapper.selectCount(new LambdaQueryWrapper<MesHalt>().isNull(MesHalt::getEndTime));
        Long openExceptions = exceptionMapper.selectCount(new LambdaQueryWrapper<MesQcException>()
                .in(MesQcException::getStatus, 0, 1));
        Map<Integer, Long> equipment = new HashMap<>();
        for (Map<String, Object> row : equipmentMapper.selectMaps(new QueryWrapper<MesEquipment>()
                .select("status", "COUNT(1) AS status_count")
                .groupBy("status"))) {
            Object rawStatus = mapValueIgnoreCase(row, "status");
            Object rawCount = mapValueIgnoreCase(row, "status_count");
            Integer status = rawStatus == null ? null : Integer.valueOf(rawStatus.toString());
            Long count = rawCount instanceof Number number ? number.longValue()
                    : Long.valueOf(String.valueOf(rawCount));
            equipment.put(status, count);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("todayOkQty", ok);
        result.put("todayNgQty", ng);
        result.put("todayRate", rate(ok, ok.add(ng)));
        result.put("producingOrders", producing);
        result.put("pendingQc", pendingQc);
        result.put("halting", halting);
        result.put("openExceptions", openExceptions);
        result.put("equipmentStatus", equipment);
        return result;
    }

    /** 产量趋势（近 N 天，按日汇总合格/不良） */
    public List<Map<String, Object>> production(int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        List<MesReport> reports = reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                .select(MesReport::getReportTime, MesReport::getOkQty, MesReport::getNgQty)
                .ge(MesReport::getReportTime, start)
                .lt(MesReport::getReportTime, today.plusDays(1).atStartOfDay()));
        Map<LocalDate, BigDecimal> okMap = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> ngMap = new LinkedHashMap<>();
        for (MesReport r : reports) {
            LocalDate day = r.getReportTime().toLocalDate();
            okMap.merge(day, r.getOkQty(), BigDecimal::add);
            ngMap.merge(day, r.getNgQty(), BigDecimal::add);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            BigDecimal ok = okMap.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal ng = ngMap.getOrDefault(day, BigDecimal.ZERO);
            Map<String, Object> row = new HashMap<>();
            row.put("date", day.toString());
            row.put("okQty", ok);
            row.put("ngQty", ng);
            row.put("rate", rate(ok, ok.add(ng)));
            rows.add(row);
        }
        return rows;
    }

    /** 质量统计：不良原因 TOP（近 N 天） */
    public List<Map<String, Object>> quality(int days, int top) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        Map<String, BigDecimal> byReason = new LinkedHashMap<>();
        for (MesReport r : reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                .select(MesReport::getDefectReason, MesReport::getNgQty)
                .ge(MesReport::getReportTime, start)
                .lt(MesReport::getReportTime, today.plusDays(1).atStartOfDay())
                .gt(MesReport::getNgQty, BigDecimal.ZERO))) {
            String reason = r.getDefectReason() == null || r.getDefectReason().isBlank() ? "未分类" : r.getDefectReason();
            byReason.merge(reason, r.getNgQty(), BigDecimal::add);
        }
        return byReason.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(top)
                .map(e -> Map.<String, Object>of("reason", e.getKey(), "qty", e.getValue()))
                .toList();
    }

    /** 设备统计：近 N 天维修单与停机时长（按设备） */
    public List<Map<String, Object>> equipment(int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        Map<Long, long[]> byEquip = new LinkedHashMap<>();
        for (MesRepair r : repairMapper.selectList(new LambdaQueryWrapper<MesRepair>()
                .select(MesRepair::getEquipmentId, MesRepair::getDowntimeMinutes)
                .ge(MesRepair::getReportTime, start)
                .lt(MesRepair::getReportTime, today.plusDays(1).atStartOfDay()))) {
            long[] stat = byEquip.computeIfAbsent(r.getEquipmentId(), k -> new long[2]);
            stat[0]++;
            stat[1] += r.getDowntimeMinutes() == null ? 0 : r.getDowntimeMinutes();
        }
        List<Long> equipmentIds = byEquip.keySet().stream()
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, MesEquipment> equipmentById = new HashMap<>();
        for (int from = 0; from < equipmentIds.size(); from += DB_IN_BATCH_SIZE) {
            List<Long> batch = equipmentIds.subList(from, Math.min(from + DB_IN_BATCH_SIZE, equipmentIds.size()));
            for (MesEquipment equipment : equipmentMapper.selectBatchIds(batch)) {
                equipmentById.put(equipment.getId(), equipment);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : byEquip.entrySet()) {
            MesEquipment equipment = e.getKey() == null ? null : equipmentById.get(e.getKey());
            Map<String, Object> row = new HashMap<>();
            row.put("equipmentId", e.getKey());
            row.put("equipmentName", equipment == null ? "-" : equipment.getName());
            row.put("repairCount", e.getValue()[0]);
            row.put("downtimeMinutes", e.getValue()[1]);
            row.put("status", equipment == null ? null : equipment.getStatus());
            rows.add(row);
        }
        return rows;
    }

    /** 统计导出（CSV 文本，前端落盘为文件） */
    public String exportCsv(String type, int days) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        if ("production".equals(type)) {
            csv.append("日期,合格数,不良数,合格率\n");
            for (Map<String, Object> row : production(days)) {
                csv.append(row.get("date")).append(',').append(row.get("okQty")).append(',')
                        .append(row.get("ngQty")).append(',').append(row.get("rate")).append('\n');
            }
        } else if ("quality".equals(type)) {
            csv.append("不良原因,数量\n");
            for (Map<String, Object> row : quality(days, 50)) {
                csv.append(row.get("reason")).append(',').append(row.get("qty")).append('\n');
            }
        } else {
            csv.append("设备,维修次数,停机分钟,状态\n");
            for (Map<String, Object> row : equipment(days)) {
                csv.append(row.get("equipmentName")).append(',').append(row.get("repairCount")).append(',')
                        .append(row.get("downtimeMinutes")).append(',').append(row.get("status")).append('\n');
            }
        }
        return csv.toString();
    }

    private BigDecimal rate(BigDecimal ok, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return ok.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private Object mapValueIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
