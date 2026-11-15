package vip.mseapp.mes.andon;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.core.context.TenantContext;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.schedule.MseScheduled;
import vip.mseapp.mes.entity.MesAndonCall;
import vip.mseapp.mes.mapper.MesAndonCallMapper;
import vip.mseapp.mes.service.MesCodeGenerator;
import vip.mseapp.mybatis.datasource.TenantDsRegistry;
import vip.mseapp.mybatis.schema.MasterSchemaAutoMigration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安灯管理（三期）：工位呼叫（设备/质量/物料）→ 响应处理 → 关闭；待响应超时逐级升级。
 *
 * <p>升级策略：待响应超过 {@link #ESCALATE_MINUTES} 分钟升级一级（{@code escalate_level} 0→2 上限），
 * 由统一调度中心周期扫描触发（延迟启动 + 多实例防重 + 平台端「任务调度」可管理，
 * 对齐集成重试模式）；
 * 看板按升级级别高亮（0 绿 / 1 黄 / 2 红），「逐级通知」以看板高亮 + 升级统计呈现。
 */
@Service
public class MesAndonService {

    private static final Logger log = LoggerFactory.getLogger(MesAndonService.class);

    /** 待响应升级阈值（分钟/级） */
    public static final int ESCALATE_MINUTES = 15;
    /** 升级级别上限 */
    public static final int MAX_LEVEL = 2;

    private final MesAndonCallMapper callMapper;
    private final MesCodeGenerator codeGenerator;
    private final ObjectProvider<TenantDsRegistry> registryProvider;

    /** 平台库数据源 key（primary=平台库，循环跳过；mes 无平台表，首扫按缺表 DEBUG 跳过） */
    private final String platformDsKey;

    public MesAndonService(MesAndonCallMapper callMapper, MesCodeGenerator codeGenerator,
                           ObjectProvider<TenantDsRegistry> registryProvider,
                           @org.springframework.beans.factory.annotation.Value(
                                   "${mse.tenant.schema.platform-ds:platform}") String platformDsKey) {
        this.callMapper = callMapper;
        this.codeGenerator = codeGenerator;
        this.registryProvider = registryProvider;
        this.platformDsKey = platformDsKey == null || platformDsKey.isBlank() ? "platform" : platformDsKey.trim();
    }

    // ==================== 呼叫/响应/关闭 ====================

    /** 发起呼叫（操作工） */
    @Transactional(rollbackFor = Exception.class)
    public MesAndonCall call(Integer type, Long lineId, Long stationId, String description) {
        if (type == null || type < MesAndonCall.TYPE_EQUIPMENT || type > MesAndonCall.TYPE_MATERIAL) {
            throw new ServiceException(400, "呼叫类型非法（1 设备 2 质量 3 物料）");
        }
        MesAndonCall call = new MesAndonCall();
        call.setCode(codeGenerator.next("AL"));
        call.setType(type);
        call.setLineId(lineId);
        call.setStationId(stationId);
        call.setDescription(description);
        call.setStatus(MesAndonCall.ST_WAITING);
        call.setEscalateLevel(0);
        call.setCallBy(UserContext.getUserId());
        call.setCallTime(LocalDateTime.now());
        callMapper.insert(call);
        return call;
    }

    /** 响应（调度员受理：待响应 → 处理中） */
    @Transactional(rollbackFor = Exception.class)
    public void respond(Long id) {
        MesAndonCall call = requireStatus(id, MesAndonCall.ST_WAITING, "响应");
        call.setStatus(MesAndonCall.ST_HANDLING);
        call.setRespondBy(UserContext.getUserId());
        call.setRespondTime(LocalDateTime.now());
        callMapper.updateById(call);
    }

    /** 关闭（处理完成：待响应/处理中 → 已关闭；记录处理结果） */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String handleResult) {
        MesAndonCall call = callMapper.selectById(id);
        if (call == null || call.getStatus() == MesAndonCall.ST_CLOSED) {
            throw new ServiceException(1409, "呼叫单不存在或已关闭");
        }
        if (call.getStatus() == MesAndonCall.ST_WAITING) {
            // 未响应直接关闭视为自处理，补记响应时间保持响应时长口径完整
            call.setRespondBy(UserContext.getUserId());
            call.setRespondTime(LocalDateTime.now());
        }
        call.setStatus(MesAndonCall.ST_CLOSED);
        call.setCloseBy(UserContext.getUserId());
        call.setCloseTime(LocalDateTime.now());
        call.setHandleResult(handleResult);
        callMapper.updateById(call);
    }

    // ==================== 查询 ====================

    /** 活跃呼叫（待响应/处理中，按升级级别倒序、呼叫时间正序——看板主列表） */
    public List<MesAndonCall> active() {
        return callMapper.selectList(new LambdaQueryWrapper<MesAndonCall>()
                .in(MesAndonCall::getStatus, MesAndonCall.ST_WAITING, MesAndonCall.ST_HANDLING)
                .orderByDesc(MesAndonCall::getEscalateLevel)
                .orderByAsc(MesAndonCall::getCallTime));
    }

    /** 呼叫记录分页（类型/状态过滤） */
    public PageResult<MesAndonCall> page(long pageNum, long pageSize, Integer type, Integer status) {
        LambdaQueryWrapper<MesAndonCall> query = new LambdaQueryWrapper<MesAndonCall>()
                .eq(type != null, MesAndonCall::getType, type)
                .eq(status != null, MesAndonCall::getStatus, status)
                .orderByDesc(MesAndonCall::getId);
        Page<MesAndonCall> page = callMapper.selectPage(Page.of(pageNum, pageSize), query);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    /** 安灯统计（近 N 天：呼叫次数/类型分布/平均响应分钟/升级次数/待响应数） */
    public Map<String, Object> stats(int days) {
        int span = Math.min(Math.max(days, 1), 90);
        LocalDateTime start = LocalDateTime.now().minusDays(span);
        List<MesAndonCall> calls = callMapper.selectList(new LambdaQueryWrapper<MesAndonCall>()
                .ge(MesAndonCall::getCallTime, start));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("total", calls.size());
        Map<Integer, Long> byType = new LinkedHashMap<>();
        long escalated = 0;
        long responded = 0;
        double respondMinutes = 0;
        for (MesAndonCall call : calls) {
            byType.merge(call.getType(), 1L, Long::sum);
            if (call.getEscalateLevel() != null && call.getEscalateLevel() > 0) {
                escalated++;
            }
            if (call.getRespondTime() != null) {
                responded++;
                respondMinutes += Math.max(0, Duration.between(call.getCallTime(), call.getRespondTime()).toSeconds()) / 60.0;
            }
        }
        result.put("byType", byType);
        result.put("escalated", escalated);
        result.put("avgRespondMinutes", responded == 0 ? null
                : BigDecimal.valueOf(respondMinutes / responded).setScale(1, RoundingMode.HALF_UP));
        result.put("waiting", callMapper.selectCount(new LambdaQueryWrapper<MesAndonCall>()
                .eq(MesAndonCall::getStatus, MesAndonCall.ST_WAITING)));
        // 高频工位 TOP5
        Map<Long, Long> byStation = new LinkedHashMap<>();
        for (MesAndonCall call : calls) {
            if (call.getStationId() != null) {
                byStation.merge(call.getStationId(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> topStations = new ArrayList<>();
        byStation.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(5)
                .forEach(e -> topStations.add(Map.of("stationId", e.getKey(), "calls", e.getValue())));
        result.put("topStations", topStations);
        return result;
    }

    // ==================== 超时升级 ====================

    /**
     * 待响应超时逐级升级（后台扫描入口，也可手动触发验证）：跨世界扫描，逐行回放租户上下文。
     * 不加 @Transactional：调度线程无上下文开事务会把连接钉死在主库，事务内 runAs 切不动绑定连接——
     * 独立库租户的呼叫行写不达。扫描逐数据源遍历（0.1.0：首扫 primary=平台库——mes 无平台表按缺表
     * DEBUG 跳过，保留以兼容离线单库形态；租户世界全在已注册动态池：缺省共享库 shared-main +
     * 各独立库，runIgnore 免行级条件，对齐 mse-monitor 的 scanAllDataSources 范式），
     * 升级写按行内 tenant_id 语句级路由回本世界。
     */
    public int escalateDue() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ESCALATE_MINUTES);
        List<MesAndonCall> due = new ArrayList<>();
        // 首扫 primary（在线=平台库缺表跳过；离线=本地单库即全部世界）
        due.addAll(scanDueInCurrentDs(threshold, "primary"));
        // 各注册租户库（缺省共享库 + 独立租户世界）
        TenantDsRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            for (String dsKey : registry.registeredKeys()) {
                if (dsKey.equals(platformDsKey)) {
                    continue; // 平台库即 primary：首扫已覆盖
                }
                if (registry.isMainDbAlias(dsKey)) {
                    continue; // 与缺省共享库同库：共享池轮已覆盖
                }
                DynamicDataSourceContextHolder.push(dsKey);
                try {
                    due.addAll(scanDueInCurrentDs(threshold, dsKey));
                } finally {
                    DynamicDataSourceContextHolder.poll();
                }
            }
        }
        int count = 0;
        for (MesAndonCall call : due) {
            Integer updated = TenantContext.runAs(call.getTenantId(), () -> {
                // 距上次升级不足一个周期的跳过（避免长待响应被一次扫描连升）
                if (call.getLastEscalateTime() != null
                        && call.getLastEscalateTime().isAfter(LocalDateTime.now().minusMinutes(ESCALATE_MINUTES))) {
                    return 0;
                }
                MesAndonCall update = new MesAndonCall();
                update.setId(call.getId());
                update.setEscalateLevel(call.getEscalateLevel() + 1);
                update.setLastEscalateTime(LocalDateTime.now());
                callMapper.updateById(update);
                return 1;
            });
            count += updated;
        }
        return count;
    }

    /** 当前数据源内扫待升级行（runIgnore 免行级条件，行内 tenant_id 即回放锚点）；缺表等数据源级异常降级跳过 */
    private List<MesAndonCall> scanDueInCurrentDs(LocalDateTime threshold, String dsKey) {
        try {
            return TenantContext.runIgnore(() -> callMapper.selectList(
                    new LambdaQueryWrapper<MesAndonCall>()
                            .eq(MesAndonCall::getStatus, MesAndonCall.ST_WAITING)
                            .lt(MesAndonCall::getEscalateLevel, MAX_LEVEL)
                            .le(MesAndonCall::getCallTime, threshold)));
        } catch (DataAccessException e) {
            // 缺库/缺表 = 独立库注册可见早于初始化直建、平台库无 mes 表的预期中间态，DEBUG 免周期噪声
            log.debug("数据源 {} 安灯升级扫描跳过（表未就绪）", dsKey);
            return List.of();
        } catch (Exception e) {
            log.warn("数据源 {} 安灯升级扫描失败（本轮跳过）", dsKey, e);
            return List.of();
        }
    }

    @MseScheduled(taskId = "mes-andon-escalate", name = "安灯待响应升级扫描",
            description = "待响应超 15 分钟逐级升级（跨主库与独立租户库扫描 mes_andon_call）",
            fixedDelay = 30_000, initialDelay = 30_000)
    private void escalateDueQuietly() {
        // 双保险门禁：统一延迟启动已晚于结构迁移，此处兜底防极端场景（表尚未直建跳过本轮，下周期自愈）
        if (!MasterSchemaAutoMigration.isSchemaReady()) {
            log.debug("结构迁移未完成（首次启动建表中），本轮安灯升级扫描跳过");
            return;
        }
        try {
            escalateDue();
        } catch (Exception e) {
            log.warn("安灯超时升级扫描失败", e);
        }
    }

    private MesAndonCall requireStatus(Long id, int expectedStatus, String action) {
        MesAndonCall call = callMapper.selectById(id);
        if (call == null) {
            throw new ServiceException(1404, "呼叫单不存在");
        }
        if (call.getStatus() != expectedStatus) {
            throw new ServiceException(1409, "呼叫单当前状态不允许" + action);
        }
        return call;
    }
}
