package vip.mseapp.mes.api;

/**
 * MES 开放集成契约（mes-service 实现，二期系统集成）。
 *
 * <p>机器语义契约：方法签名统一 {@code String 方法名(String requestJson)}，入参/出参均为 JSON 字符串，
 * 供 mse-open 开放平台泛化分发（dubbo GenericService / 单体反射）调用——外部系统（ERP 等）经
 * 开放平台凭证受控调用；操作者显式携带 {@code operatorUserId}，租户经开放平台鉴权后隐式注入。
 *
 * <p>开放能力清单经 {@code MesOpenApiContributor}（OpenApiContributor）自注册到 mse-open 目录，
 * 业务新增开放能力 mse-open 零改动。实现类标 {@code @RpcService} 由 mes-service 装配注册，
 * 微服务形态自动 Dubbo 导出（无 group，mse-open 按接口名泛化引用）。
 */
public interface RemoteMesOpenService {

    /**
     * 工单进度查询（开放查询：供 ERP/第三方看板拉取）。
     * <p>入参 {@code {"orderCode":"MO202609240001"}}；出参该工单进度对象（状态/计划/完工/合格/不良/交期）。
     */
    String orderProgress(String requestJson);

    /**
     * 在制工单列表（开放查询：状态 2 已下达/3 生产中，限 100 条）。
     * <p>入参 {@code {}}；出参数组，元素同 orderProgress。
     */
    String producingOrders(String requestJson);

    /**
     * 产量统计摘要（开放查询：近 N 日按日产量）。
     * <p>入参 {@code {"days":7}}；出参数组 [{date, okQty, ngQty}]。
     */
    String productionStats(String requestJson);

    /**
     * 质量统计摘要（开放查询：近 N 日合格率与不良 TOP）。
     * <p>入参 {@code {"days":7,"top":5}}；出参 {trend:[{date,rate}], topDefects:[{name,qty}]}。
     */
    String qualityStats(String requestJson);

    /**
     * ERP 生产订单引入（入站同步：ERP 销售订单/生产计划 → MES 生产订单，草稿态待计划员确认下达）。
     * <p>入参 {@code {"productCode":"M001","planQty":100,"planStart":"2026-09-25","planEnd":"2026-09-30",
     * "bomId":1,"routeId":2,"remark":"...","operatorUserId":1}}（productCode 为 MES 物料编码，须已同步存在）；
     * 出参 {@code {"orderCode":"MO..."}}。同请求重复引入幂等（externalCode 一致时返回已有工单）。
     */
    String importOrder(String requestJson);

    /**
     * ERP 物料主数据引入（入站同步：upsert，物料编码为唯一键，ERP 为准覆盖名称/规格/单位/类型）。
     * <p>入参 {@code {"materials":[{"code":"M001","name":"...","spec":"...","unit":"件","type":1}],
     * "operatorUserId":1}}；出参 {@code {"total":10,"created":3,"updated":7}}。
     */
    String importMaterials(String requestJson);

    /**
     * ERP BOM 引入（入站同步：父项物料编码 + 子项清单 upsert，版本号缺省 v1.0）。
     * <p>入参 {@code {"boms":[{"parentCode":"P001","version":"v1.0",
     * "lines":[{"materialCode":"M001","qty":2}]}],"operatorUserId":1}}；
     * 出参 {@code {"total":1,"created":0,"updated":1}}。
     */
    String importBoms(String requestJson);
}
