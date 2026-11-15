package vip.mseapp.mes.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mes.entity.MesDefect;
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.entity.MesModel;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.entity.MesPlan;
import vip.mseapp.mes.entity.MesQcException;
import vip.mseapp.mes.entity.MesRepair;
import vip.mseapp.mes.mapper.MesDefectMapper;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.mapper.MesModelMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.mapper.MesPlanMapper;
import vip.mseapp.mes.mapper.MesQcExceptionMapper;
import vip.mseapp.mes.mapper.MesRepairMapper;
import vip.mseapp.mes.service.MesOrderService;
import vip.mseapp.mes.service.MesStatsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MES AI 服务：生产日报生成、质量异常诊断、设备故障诊断、智能问数、排产建议、工艺/SOP 问答（二期）
 * + 安灯异常摘要、SPC 判读（三期）。AI 只产出草稿/建议（页面统一标注「AI 生成，仅供参考」），
 * 一切写操作由人工确认后经既有接口执行。
 *
 * <p>模型通道由 {@link MesAiChatPort} 屏蔽：AI 服务模式（mse-ai 智能体，可挂 mes_query 反向工具受控取数）
 * 与直连模式（starter-ai mseChatClient，无 mse-ai 部署形态）管线一致；开关与模式由系统参数 mes.ai.mode
 * 控制（{@link MesAiModeResolver}）。AI 服务模式使用的智能体按场景经「AI 智能化设置」页映射
 * （{@link MesAiAgentMapService}），yaml 仅兜底默认。
 *
 * <p>智能问数依赖服务侧挂载的 mes_query 反向工具受控供数（绝不让模型直连业务库拼 SQL），
 * 故仅 AI 服务通道可用；其余场景上下文由本服务装配为文本后一次性生成。
 */
@Service
public class MesAiService {

    private static final Logger log = LoggerFactory.getLogger(MesAiService.class);

    /** 上下文长度上限（字符），防超长数据撑爆提示词 */
    private static final int CONTEXT_MAX = 4000;

    /** Jackson 2：AI 提示词上下文单点序列化（仅 Map/数值/字符串，无 java.time），版本由 Boot 4 的 jackson-2-bom 托管；
     *  与全站 Jackson 3 基线并存的知悉项，如切 tools.jackson 需确认提示词输出逐字节不变 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MesAiChatPort chatPort;
    private final MesAiModeResolver modeResolver;
    private final MesAiAgentMapService agentMapService;
    private final MesStatsService statsService;
    private final MesOrderMapper orderMapper;
    private final MesModelMapper modelMapper;
    private final MesPlanMapper planMapper;
    private final MesQcExceptionMapper exceptionMapper;
    private final MesDefectMapper defectMapper;
    private final MesRepairMapper repairMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final vip.mseapp.mes.andon.MesAndonService andonService;
    private final vip.mseapp.mes.spc.MesSpcService spcService;

    public MesAiService(MesAiChatPort chatPort, MesAiModeResolver modeResolver,
                        MesAiAgentMapService agentMapService, MesStatsService statsService,
                        MesOrderMapper orderMapper, MesModelMapper modelMapper, MesPlanMapper planMapper,
                        MesQcExceptionMapper exceptionMapper, MesDefectMapper defectMapper,
                        MesRepairMapper repairMapper, MesEquipmentMapper equipmentMapper,
                        vip.mseapp.mes.andon.MesAndonService andonService,
                        vip.mseapp.mes.spc.MesSpcService spcService) {
        this.chatPort = chatPort;
        this.modeResolver = modeResolver;
        this.agentMapService = agentMapService;
        this.statsService = statsService;
        this.orderMapper = orderMapper;
        this.modelMapper = modelMapper;
        this.planMapper = planMapper;
        this.exceptionMapper = exceptionMapper;
        this.defectMapper = defectMapper;
        this.repairMapper = repairMapper;
        this.equipmentMapper = equipmentMapper;
        this.andonService = andonService;
        this.spcService = spcService;
    }

    // ==================== 场景 1：AI 生产日报/周报生成（挂 509，mes_report_writer） ====================

    /** 汇总近 N 天产量/质量/设备上下文 → 日报草稿（人工确认后发布归档，本服务不落库） */
    public String dailyReport(int days) {
        int span = Math.min(Math.max(days, 1), 30);
        StringBuilder context = new StringBuilder();
        context.append("【总览】").append(jsonOrNull(statsService.overview())).append('\n');
        context.append("【产量趋势（近 ").append(span).append(" 天）】").append(jsonOrNull(statsService.production(span))).append('\n');
        context.append("【不良 TOP5】").append(jsonOrNull(statsService.quality(span, 5))).append('\n');
        context.append("【设备维修统计】").append(jsonOrNull(statsService.equipment(span))).append('\n');
        return chat(MesAiAgentMap.SCENE_REPORT, MesAiPrompts.REPORT,
                "请根据以下 MES 统计数据生成生产日报草稿：\n" + context);
    }

    // ==================== 场景 2：AI 质量异常诊断（挂 505，mes_quality_diag） ====================

    /** 装配质量异常单 + 同工单不良明细 + 近期不良 TOP → 可能原因排序与处置建议 */
    public String qualityDiag(Long exceptionId) {
        MesQcException exception = exceptionMapper.selectById(exceptionId);
        if (exception == null) {
            throw new ServiceException(404, "质量异常单不存在: " + exceptionId);
        }
        StringBuilder context = new StringBuilder();
        context.append("【异常单】").append(exception.getCode()).append(' ').append(exception.getTitle())
                .append("（状态 ").append(exception.getStatus()).append("，级别 ").append(exception.getLevel()).append("）\n");
        context.append("【描述】").append(nvl(exception.getDescription())).append('\n');
        if (exception.getOrderId() != null) {
            MesOrder order = orderMapper.selectById(exception.getOrderId());
            if (order != null) {
                context.append("【关联工单】").append(order.getCode())
                        .append(" 计划 ").append(order.getPlanQty()).append(" 完工 ").append(order.getFinishedQty())
                        .append(" 合格 ").append(order.getOkQty()).append(" 不良 ").append(order.getNgQty()).append('\n');
            }
            List<MesDefect> defects = defectMapper.selectList(new LambdaQueryWrapper<MesDefect>()
                    .eq(MesDefect::getOrderId, exception.getOrderId())
                    .orderByDesc(MesDefect::getQty).last("LIMIT 20"));
            context.append("【该工单不良明细】\n");
            for (MesDefect d : defects) {
                context.append("- 数量 ").append(d.getQty()).append(" 原因 ").append(nvl(d.getReason()))
                        .append(" 处置 ").append(disposeName(d.getDispose())).append('\n');
            }
        }
        context.append("【近 14 天全厂不良 TOP5】").append(jsonOrNull(statsService.quality(14, 5))).append('\n');
        if (exception.getCauseAnalysis() != null && !exception.getCauseAnalysis().isBlank()) {
            context.append("【人工已填原因分析】").append(exception.getCauseAnalysis()).append('\n');
        }
        return chat(MesAiAgentMap.SCENE_QUALITY, MesAiPrompts.QUALITY,
                "请分析以下质量异常：\n" + context);
    }

    // ==================== 场景 3：AI 设备故障诊断（挂 506，mes_equip_diag） ====================

    /** 装配维修单 + 设备档案 + 同设备历史维修 → 故障假设排序与维修建议 */
    public String equipDiag(Long repairId) {
        MesRepair repair = repairMapper.selectById(repairId);
        if (repair == null) {
            throw new ServiceException(404, "维修工单不存在: " + repairId);
        }
        StringBuilder context = new StringBuilder();
        context.append("【维修单】").append(repair.getCode()).append("（状态 ").append(repair.getStatus()).append("）\n");
        context.append("【故障描述】").append(nvl(repair.getFaultDesc())).append('\n');
        if (repair.getEquipmentId() != null) {
            MesEquipment equipment = equipmentMapper.selectById(repair.getEquipmentId());
            if (equipment != null) {
                context.append("【设备】").append(equipment.getCode()).append(' ').append(equipment.getName())
                        .append(" 型号 ").append(nvl(equipment.getModel())).append("（状态 ").append(equipment.getStatus()).append("）\n");
            }
            LocalDateTime since = LocalDate.now().minusDays(90).atStartOfDay();
            List<MesRepair> history = repairMapper.selectList(new LambdaQueryWrapper<MesRepair>()
                    .eq(MesRepair::getEquipmentId, repair.getEquipmentId())
                    .ge(MesRepair::getReportTime, since)
                    .orderByDesc(MesRepair::getReportTime).last("LIMIT 10"));
            context.append("【该设备近 90 天维修记录】\n");
            for (MesRepair r : history) {
                if (r.getId().equals(repair.getId())) {
                    continue;
                }
                context.append("- ").append(r.getReportTime()).append(" 故障：").append(nvl(r.getFaultDesc()))
                        .append(" 处理：").append(nvl(r.getHandleDesc()))
                        .append(" 停机 ").append(r.getDowntimeMinutes() == null ? 0 : r.getDowntimeMinutes()).append(" 分钟\n");
            }
        }
        return chat(MesAiAgentMap.SCENE_EQUIP, MesAiPrompts.EQUIP,
                "请分析以下设备故障：\n" + context);
    }

    // ==================== 场景 5：AI 排产建议（挂 502，mes_schedule_advisor） ====================

    /**
     * 装配已下达/生产中未完工工单 + 产线 + 未来 7 天既有排产 → 排产草稿（产线×日期×工单 JSON 数组）。
     * 只返回草稿：前端展示可编辑表格，人工确认后逐条走既有排产保存接口（mes:plan:save）生效。
     */
    public String scheduleAdvise() {
        List<MesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MesOrder>()
                .in(MesOrder::getStatus, MesOrderService.ST_RELEASED, MesOrderService.ST_PRODUCING)
                .orderByAsc(MesOrder::getPlanEnd).last("LIMIT 30"));
        StringBuilder context = new StringBuilder();
        context.append("【待排产工单（已下达/生产中，按交期升序，至多 30 条）】\n");
        for (MesOrder order : orders) {
            BigDecimal remaining = order.getPlanQty().subtract(order.getFinishedQty());
            context.append("- 工单 id ").append(order.getId()).append(" 工单号 ").append(order.getCode())
                    .append(" 产品物料 id ").append(order.getProductId())
                    .append(" 工艺路线 id ").append(nvl(order.getRouteId()))
                    .append(" 剩余数量 ").append(remaining)
                    .append(" 交期 ").append(order.getPlanEnd()).append('\n');
        }
        context.append("【可用产线】\n");
        for (MesModel line : modelMapper.selectList(new LambdaQueryWrapper<MesModel>().eq(MesModel::getType, 3))) {
            context.append("- 产线 id ").append(line.getId()).append(' ').append(line.getName()).append('\n');
        }
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(7);
        List<MesPlan> plans = planMapper.selectList(new LambdaQueryWrapper<MesPlan>()
                .between(MesPlan::getWorkDate, from, to));
        context.append("【未来 7 天既有排产（避免冲突）】\n");
        for (MesPlan plan : plans) {
            context.append("- 工单 id ").append(plan.getOrderId()).append(" 产线 id ").append(plan.getLineId())
                    .append(" 日期 ").append(plan.getWorkDate()).append(" 班次 ").append(plan.getShiftCode())
                    .append(" 计划数 ").append(plan.getPlanQty()).append('\n');
        }
        return chat(MesAiAgentMap.SCENE_SCHEDULE, MesAiPrompts.SCHEDULE,
                "请生成排产草稿：\n" + context);
    }

    // ==================== 场景 4：AI 智能问数（挂 509，data_analyst + mes_query 反向工具） ====================

    /**
     * 自然语言查生产数据：AI 服务侧智能体按需调用 mes_query 反向工具受控取数（工具内 SQL 语义写死在 Mapper）。
     * 依赖 AI 服务通道（mes_query 挂在服务侧智能体上），直连模式无受控供数通道，明确提示不可用。
     */
    public String dataQa(String question) {
        modeResolver.ensureEnabled();
        if (modeResolver.resolve() == MesAiMode.DIRECT) {
            throw new ServiceException("智能问数需经 AI 服务（mes_query 工具受控供数）："
                    + "请部署 mse-ai 并将参数 " + MesAiMode.CONFIG_KEY + " 设为 platform/auto");
        }
        String answer = chatPort.chatViaPlatform(agentMapService.resolveAgentCode(MesAiAgentMap.SCENE_DATA_QA), question);
        if (answer == null || answer.isBlank()) {
            throw new ServiceException("AI 未返回内容，请重试");
        }
        return answer;
    }

    // ==================== 场景 6：AI 工艺/SOP 知识问答（挂 504，mes_kb_qa） ====================

    /**
     * 工艺/SOP 问答：AI 服务模式走 mes_kb_qa 智能体（工艺文档/SOP 入 mse-ai 知识库，RAG 检索）；
     * 直连模式无知识库，退化为通用工艺常识回答（页面提示「未接入知识库」由能力接口告知）。
     */
    public String kbQa(String question) {
        return chat(MesAiAgentMap.SCENE_KB, MesAiPrompts.KB, question);
    }

    // ==================== 场景 7：AI 安灯异常摘要（三期，挂 513，mes_andon_summary） ====================

    /** 装配近 N 天安灯呼叫统计（类型分布/平均响应/升级/高频工位）→ 异常聚类摘要与根因线索 */
    public String andonSummary(int days) {
        int span = Math.min(Math.max(days, 1), 90);
        return chat(MesAiAgentMap.SCENE_ANDON, MesAiPrompts.ANDON,
                "请汇总分析近 " + span + " 天的安灯呼叫数据：\n"
                        + jsonOrNull(andonService.stats(span)));
    }

    // ==================== 场景 8：AI SPC 判读（三期，挂 514，mes_spc_interpret） ====================

    /** 装配监控项控制图统计量（控制限/超限/能力指数/近期子组）→ 异常模式判读与工艺调整建议 */
    public String spcInterpret(Long itemId, int days) {
        int span = Math.min(Math.max(days, 1), 90);
        Map<String, Object> chart = spcService.chart(itemId, span);
        StringBuilder context = new StringBuilder();
        context.append("【监控项】").append(jsonOrNull(chart.get("item"))).append('\n');
        context.append("【子组大小】").append(chart.get("sampleSize"))
                .append("，样本总数 ").append(chart.get("totalSamples")).append('\n');
        context.append("【控制限】").append(jsonOrNull(chart.get("limits"))).append('\n');
        context.append("【过程能力】").append(jsonOrNull(chart.get("capability"))).append('\n');
        context.append("【超控制限子组数】").append(chart.get("outOfControl"))
                .append("，超规格样本数 ").append(chart.get("outOfSpec")).append('\n');
        // 近 15 个子组的均值/极差序列（供判读趋势/游程等异常模式）
        Object groups = chart.get("groups");
        context.append("【近期子组序列（最多 15 组）】").append(truncateJson(groups, 15)).append('\n');
        return chat(MesAiAgentMap.SCENE_SPC, MesAiPrompts.SPC,
                "请判读以下 SPC 控制图数据：\n" + context);
    }

    /** 子组序列截取（最多 tail 组），防超长上下文 */
    private String truncateJson(Object data, int tail) {
        try {
            String json = jsonOrNull(data);
            if (json.length() <= CONTEXT_MAX) {
                return json;
            }
            // 取尾部子组：数组场景简单截断并补右括号，仅供模型阅读不参与解析
            String cut = json.substring(json.length() - CONTEXT_MAX);
            return "…（仅显示尾部）" + cut;
        } catch (Exception e) {
            return "[]";
        }
    }

    // ==================== 能力状态 ====================

    /** AI 能力状态（前端按钮置灰与提示） */
    public AiCapability capability() {
        MesAiMode mode = modeResolver.resolve();
        boolean platform = chatPort.platformAvailable();
        boolean direct = chatPort.directAvailable();
        boolean available = switch (mode) {
            case OFF -> false;
            case PLATFORM -> platform;
            case DIRECT -> direct;
            case AUTO -> platform || direct;
        };
        String message = null;
        if (!available) {
            message = mode == MesAiMode.OFF
                    ? "AI 能力未开启：请管理员在「参数设置」配置参数 " + MesAiMode.CONFIG_KEY
                    : (platform ? "" : "未检测到 AI 服务；") + (direct ? "" : "未配置直连模型（spring.ai.*）");
        }
        return new AiCapability(mode.value(), platform, direct, available, message);
    }

    // ==================== 内部工具 ====================

    /** 统一入口：能力开关守卫 → 按场景解析智能体 → 通道分派生成 */
    private String chat(String scene, String directSystemPrompt, String userMessage) {
        modeResolver.ensureEnabled();
        String agentCode = agentMapService.resolveAgentCode(scene);
        return chatPort.chat(modeResolver.resolve(), agentCode, directSystemPrompt, userMessage);
    }

    private String jsonOrNull(Object data) {
        try {
            return JSON.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("AI 上下文序列化失败", e);
            return "[]";
        }
    }

    private String disposeName(Integer dispose) {
        if (dispose == null) {
            return "待处置";
        }
        return switch (dispose) {
            case 1 -> "返工";
            case 2 -> "报废";
            case 3 -> "让步接收";
            default -> "待处置";
        };
    }

    private String nvl(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /** AI 能力状态（capability 端点响应） */
    public record AiCapability(String mode, boolean platformAvailable, boolean directAvailable,
                               boolean available, String message) {
    }
}
