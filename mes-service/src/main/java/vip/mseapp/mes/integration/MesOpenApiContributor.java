package vip.mseapp.mes.integration;

import vip.mseapp.open.api.OpenApiContributor;
import vip.mseapp.open.api.OpenApiDescriptor;

import java.util.List;

/**
 * MES 开放能力声明（二期系统集成，开放平台自注册；对齐 {@code OaOpenApiContributor} 模式）。
 *
 * <p>查询类：ERP/第三方经开放平台拉工单进度、在制工单、产量与质量摘要；
 * 入站类：ERP 推生产订单与物料/BOM 主数据（mes.integration.erp_sync 启用后接受）。
 * 新增开放能力 = mes-api 契约加方法 + 本清单加一条描述，mse-open 零改动。
 */
public class MesOpenApiContributor implements OpenApiContributor {

    public static final String OPEN_CONTRACT = "vip.mseapp.mes.api.RemoteMesOpenService";

    @Override
    public List<OpenApiDescriptor> descriptors() {
        return List.of(
                // ==================== 查询（ERP/第三方拉取） ====================
                descriptor("open:mes:order-progress", "查询工单进度", "GET", "/open/api/mes/order-progress",
                        OPEN_CONTRACT + "#orderProgress",
                        "query: orderCode（MES 工单号）；返回状态/计划/完工/合格/不良/交期"),
                descriptor("open:mes:producing-orders", "查询在制工单", "GET", "/open/api/mes/producing-orders",
                        OPEN_CONTRACT + "#producingOrders",
                        "已下达/生产中工单进度列表（按下达时间倒序，限 100 条）"),
                descriptor("open:mes:production-stats", "查询产量统计", "GET", "/open/api/mes/production-stats",
                        OPEN_CONTRACT + "#productionStats",
                        "query: days（默认 7，最大 30）；按日产量与合格率"),
                descriptor("open:mes:quality-stats", "查询质量统计", "GET", "/open/api/mes/quality-stats",
                        OPEN_CONTRACT + "#qualityStats",
                        "query: days/top；合格率趋势与不良原因 TOP"),
                // ==================== 入站（ERP 推送） ====================
                descriptor("open:mes:import-order", "引入生产订单", "POST", "/open/api/mes/import-order",
                        OPEN_CONTRACT + "#importOrder",
                        "body: externalCode(ERP 单号，幂等键)/productCode(MES 物料编码)/planQty/planStart/planEnd/bomId/routeId/remark/operatorUserId；"
                                + "引为草稿态待计划员确认下达"),
                descriptor("open:mes:import-materials", "引入物料主数据", "POST", "/open/api/mes/import-materials",
                        OPEN_CONTRACT + "#importMaterials",
                        "body: materials:[{code/name/spec/unit/type}]/operatorUserId；按编码 upsert，ERP 为准"),
                descriptor("open:mes:import-boms", "引入 BOM", "POST", "/open/api/mes/import-boms",
                        OPEN_CONTRACT + "#importBoms",
                        "body: boms:[{parentCode/version/lines:[{materialCode/qty}]}]/operatorUserId；版本缺省 v1.0，存在则整单重建明细"));
    }

    private static OpenApiDescriptor descriptor(String code, String name, String method, String path,
                                                String target, String description) {
        OpenApiDescriptor item = new OpenApiDescriptor();
        item.setCode(code);
        item.setName(name);
        item.setHttpMethod(method);
        item.setPath(path);
        item.setTarget(target);
        item.setDescription(description);
        return item;
    }
}
