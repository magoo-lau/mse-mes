package vip.mseapp.mes.aitool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.ai.api.RemoteAiTool;
import vip.mseapp.ai.api.dto.AiToolDescriptor;
import vip.mseapp.ai.api.dto.AiToolResult;
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.iot.MesIotService;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.service.MesExecService;
import vip.mseapp.mes.service.MesOrderService;
import vip.mseapp.mes.service.MesStatsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 生产数据工具门面（mse-ai 平台反向集成：一服务一工具，action 分发，对齐 {@code oa_flow} 模式）。
 *
 * <p>供 AI 智能体（智能问数/日报/诊断类场景）按需受控取数：工具内 SQL 语义写死在既有
 * Service/Mapper（聚合口径不暴露给模型，无注入面），数据经本模块 Mapper 查询天然带租户隔离；
 * 模型只见结构化结果，绝不直连业务库拼 SQL。
 *
 * <p>三期：equipment_status 已接数采实时源——设备在 {@code MesIotService.REALTIME_WINDOW_MINUTES}
 * 分钟内有数采即标记 online 并用实时状态，否则回退台账状态（一期手工/点检驱动）。
 *
 * <p>单体形态：mse-ai LocalToolAutoRegistrar 自动收集容器内本 Bean 幂等登记 ai_tool；
 * 微服务形态：mes-boot 以 group=mse-mes Dubbo 导出（{@code MesAiToolExportConfiguration}），
 * ai_tool 登记 mse-ai 工具中心后按 group 泛化调用。
 */
@Component
public class MesQueryTools implements RemoteAiTool {

    private static final String TOOL_CODE = "mes_query";

    private final JsonMapper objectMapper;
    private final MesOrderMapper orderMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final MesStatsService statsService;
    private final MesExecService execService;
    private final MesIotService iotService;

    public MesQueryTools(JsonMapper objectMapper, MesOrderMapper orderMapper,
                         MesEquipmentMapper equipmentMapper, MesStatsService statsService,
                         MesExecService execService, MesIotService iotService) {
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
        this.equipmentMapper = equipmentMapper;
        this.statsService = statsService;
        this.execService = execService;
        this.iotService = iotService;
    }

    @Override
    public AiToolDescriptor describe() {
        AiToolDescriptor descriptor = new AiToolDescriptor();
        descriptor.setToolCode(TOOL_CODE);
        descriptor.setName("MES 生产数据查询");
        descriptor.setDescription("查询 MES 制造执行数据：工单进度（状态/完工数/合格数/交期）、产量统计（按日）、"
                + "质量统计（合格率趋势与不良 TOP）、设备状态与维修统计、在制品（WIP）分布。只读查询。");
        descriptor.setParametersJsonSchema("""
                {"type":"object","properties":{
                  "action":{"type":"string","enum":["order_progress","production_stats","quality_stats","equipment_status","wip_query"],
                    "description":"order_progress=工单进度列表；production_stats=按日产量统计；quality_stats=质量统计(合格率趋势+不良TOP)；equipment_status=设备状态与维修统计；wip_query=在制品分布"},
                  "status":{"type":"integer","description":"order_progress 按状态过滤：2 已下达、3 生产中、4 完工；缺省查在制(2/3)"},
                  "days":{"type":"integer","description":"统计天数，默认 7，最大 30"},
                  "top":{"type":"integer","description":"quality_stats 不良 TOP 条数，默认 5，最大 10"},
                  "lineId":{"type":"integer","description":"wip_query 按产线过滤，可空"}
                },"required":["action"]}""");
        return descriptor;
    }

    @Override
    public AiToolResult invoke(String argumentsJson) {
        try {
            Map<String, Object> args = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
                    });
            String action = String.valueOf(args.getOrDefault("action", ""));
            return switch (action) {
                case "order_progress" -> AiToolResult.ok(toJson(orderProgress(args)));
                case "production_stats" -> AiToolResult.ok(toJson(Map.of("days", intArg(args, "days", 7),
                        "rows", statsService.production(intArg(args, "days", 7)))));
                case "quality_stats" -> {
                    int days = intArg(args, "days", 7);
                    yield AiToolResult.ok(toJson(Map.of("days", days,
                            "trend", statsService.production(days),
                            "topDefects", statsService.quality(days, intArg(args, "top", 5)))));
                }
                case "equipment_status" -> {
                    int days = intArg(args, "days", 7);
                    yield AiToolResult.ok(toJson(Map.of("days", days,
                            "equipment", equipmentList(),
                            "repairStats", statsService.equipment(days))));
                }
                case "wip_query" -> AiToolResult.ok(toJson(Map.of("rows", execService.wip(longArgOrNull(args, "lineId")))));
                default -> AiToolResult.fail("未知的 action：" + action);
            };
        } catch (Exception e) {
            return AiToolResult.fail("工具执行失败：" + e.getMessage());
        }
    }

    /** 工单进度列表：默认在制（已下达/生产中），limit 20 */
    private List<Map<String, Object>> orderProgress(Map<String, Object> args) {
        Integer status = intArgOrNull(args, "status");
        LambdaQueryWrapper<MesOrder> query = new LambdaQueryWrapper<MesOrder>()
                .orderByDesc(MesOrder::getPlanEnd).last("LIMIT 20");
        if (status != null) {
            query.eq(MesOrder::getStatus, status);
        } else {
            query.in(MesOrder::getStatus, MesOrderService.ST_RELEASED, MesOrderService.ST_PRODUCING);
        }
        return orderMapper.selectList(query).stream().map(order -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderCode", order.getCode());
            row.put("productId", order.getProductId());
            row.put("status", order.getStatus());
            row.put("planQty", order.getPlanQty());
            row.put("finishedQty", order.getFinishedQty());
            row.put("okQty", order.getOkQty());
            row.put("ngQty", order.getNgQty());
            row.put("planEnd", String.valueOf(order.getPlanEnd()));
            return row;
        }).toList();
    }

    private List<Map<String, Object>> equipmentList() {
        return equipmentMapper.selectList(new LambdaQueryWrapper<MesEquipment>().orderByAsc(MesEquipment::getCode))
                .stream().map(equipment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", equipment.getCode());
                    row.put("name", equipment.getName());
                    row.put("model", equipment.getModel());
                    // 三期实时源：窗口内有数采用实时状态并标记 online，否则回退台账状态
                    var latest = iotService.latest(equipment.getId());
                    boolean online = latest != null && latest.getCollectTime()
                            .isAfter(java.time.LocalDateTime.now().minusMinutes(MesIotService.REALTIME_WINDOW_MINUTES));
                    row.put("online", online);
                    row.put("status", online ? latest.getStatus() : equipment.getStatus());
                    row.put("statusSource", online ? "iot" : "manual");
                    return row;
                }).toList();
    }

    private String toJson(Object data) {
        return objectMapper.writeValueAsString(data);
    }

    private int intArg(Map<String, Object> args, String key, int defaultValue) {
        Integer value = intArgOrNull(args, key);
        return value == null ? defaultValue : value;
    }

    private Integer intArgOrNull(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || "null".equals(value)) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long longArgOrNull(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || "null".equals(value)) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }
}
