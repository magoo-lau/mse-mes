package vip.mseapp.mes.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.core.annotation.RpcService;
import vip.mseapp.mes.api.RemoteMesOpenService;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.service.MesOrderService;
import vip.mseapp.mes.service.MesStatsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放集成契约实现（mse-open 泛化分发 + 业务内复用）：机器语义 String 进 String 出。
 *
 * <p>注册：MesServiceAutoConfiguration 以 {@code @Bean @ConditionalOnMissingBean} 声明（可整体替换）；
 * 标 {@code @RpcService} 后微服务形态由 starter-rpc 自动 Dubbo 导出（无 group），
 * mse-open 按接口名泛化调用；单体 local 模式 mse-open 反射直调容器内实现。
 */
@RpcService(RemoteMesOpenService.class)
public class MesOpenServiceImpl implements RemoteMesOpenService {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final MesOrderMapper orderMapper;
    private final MesStatsService statsService;
    private final MesIntegrationService integrationService;

    public MesOpenServiceImpl(MesOrderMapper orderMapper, MesStatsService statsService,
                              MesIntegrationService integrationService) {
        this.orderMapper = orderMapper;
        this.statsService = statsService;
        this.integrationService = integrationService;
    }

    @Override
    public String orderProgress(String requestJson) {
        Map<String, Object> args = parse(requestJson);
        String orderCode = str(args.get("orderCode"));
        if (orderCode == null || orderCode.isBlank()) {
            throw new IllegalArgumentException("orderCode 不能为空");
        }
        MesOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MesOrder>()
                .eq(MesOrder::getCode, orderCode).last("LIMIT 1"));
        return toJson(order == null ? null : row(order));
    }

    @Override
    public String producingOrders(String requestJson) {
        List<MesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MesOrder>()
                .in(MesOrder::getStatus, MesOrderService.ST_RELEASED, MesOrderService.ST_PRODUCING)
                .orderByDesc(MesOrder::getReleaseTime)
                .last("LIMIT 100"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MesOrder order : orders) {
            rows.add(row(order));
        }
        return toJson(rows);
    }

    @Override
    public String productionStats(String requestJson) {
        int days = intOf(parse(requestJson).get("days"), 7);
        return toJson(statsService.production(days));
    }

    @Override
    public String qualityStats(String requestJson) {
        Map<String, Object> args = parse(requestJson);
        int days = intOf(args.get("days"), 7);
        int top = intOf(args.get("top"), 5);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend", statsService.production(days));
        result.put("topDefects", statsService.quality(days, top));
        return toJson(result);
    }

    @Override
    public String importOrder(String requestJson) {
        Map<String, Object> args = parse(requestJson);
        Map<String, Object> body = args.containsKey("externalCode") ? args : mapOf(args.get("order"));
        MesIntegrationService.ImportOrder item = new MesIntegrationService.ImportOrder(
                str(body.get("externalCode")),
                str(body.get("productCode")),
                decimalOf(body.get("planQty")),
                dateOf(body.get("planStart")),
                dateOf(body.get("planEnd")),
                longOf(body.get("bomId")),
                longOf(body.get("routeId")),
                str(body.get("remark")));
        return toJson(integrationService.importOrder(item));
    }

    @Override
    public String importMaterials(String requestJson) {
        Map<String, Object> args = parse(requestJson);
        List<Map<String, Object>> raw = listOf(args.get("materials"));
        List<MesIntegrationService.ImportMaterial> materials = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            materials.add(new MesIntegrationService.ImportMaterial(
                    str(item.get("code")), str(item.get("name")), str(item.get("spec")),
                    str(item.get("unit")), intOf(item.get("type"), null)));
        }
        return toJson(integrationService.importMaterials(materials));
    }

    @Override
    public String importBoms(String requestJson) {
        Map<String, Object> args = parse(requestJson);
        List<Map<String, Object>> raw = listOf(args.get("boms"));
        List<MesIntegrationService.ImportBom> boms = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            List<MesIntegrationService.ImportBomLine> lines = new ArrayList<>();
            for (Map<String, Object> line : listOf(item.get("lines"))) {
                lines.add(new MesIntegrationService.ImportBomLine(
                        str(line.get("materialCode")), decimalOf(line.get("qty"))));
            }
            boms.add(new MesIntegrationService.ImportBom(
                    str(item.get("parentCode")), str(item.get("version")), lines));
        }
        return toJson(integrationService.importBoms(boms));
    }

    // ==================== 序列化与参数工具 ====================

    private Map<String, Object> row(MesOrder order) {
        Map<String, Object> rowMap = new LinkedHashMap<>();
        rowMap.put("orderCode", order.getCode());
        rowMap.put("productId", order.getProductId());
        rowMap.put("status", order.getStatus());
        rowMap.put("planQty", order.getPlanQty());
        rowMap.put("finishedQty", order.getFinishedQty());
        rowMap.put("okQty", order.getOkQty());
        rowMap.put("ngQty", order.getNgQty());
        rowMap.put("planStart", String.valueOf(order.getPlanStart()));
        rowMap.put("planEnd", String.valueOf(order.getPlanEnd()));
        return rowMap;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private String toJson(Object data) {
        return MAPPER.writeValueAsString(data);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intOf(Object value, Integer defaultValue) {
        return value == null ? defaultValue : Integer.valueOf(String.valueOf(value));
    }

    private Long longOf(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private BigDecimal decimalOf(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private LocalDate dateOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD: " + value);
        }
    }
}
