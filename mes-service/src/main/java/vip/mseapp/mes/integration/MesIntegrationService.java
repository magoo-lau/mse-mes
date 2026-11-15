package vip.mseapp.mes.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mes.entity.MesBom;
import vip.mseapp.mes.entity.MesBomLine;
import vip.mseapp.mes.entity.MesIntegrationConfig;
import vip.mseapp.mes.entity.MesIntegrationLog;
import vip.mseapp.mes.entity.MesMaterial;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.mapper.MesBomLineMapper;
import vip.mseapp.mes.mapper.MesBomMapper;
import vip.mseapp.mes.mapper.MesIntegrationConfigMapper;
import vip.mseapp.mes.mapper.MesIntegrationLogMapper;
import vip.mseapp.mes.mapper.MesMaterialMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.service.MesOrderService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统集成服务（二期）：ERP 入站同步（订单/物料/BOM 引入，经 mse-open 开放平台入站）+ 同步日志。
 *
 * <p>入站以 ERP 为准（物料/BOM upsert、订单引为草稿态由计划员确认），「系统集成」页维护
 * erp_sync 总开关。出站生产结果回传（result_push webhook）已退役（2026-10-20）——工单完工
 * 通知改经框架通知中枢 (mes,ORDER_FINISHED) 群发（{@code MesOrderFinishedNotifyListener}），
 * 渠道由租户路由在基座「通知渠道设置」解析。
 */
@Service
public class MesIntegrationService {

    /** 报文摘要截断长度 */
    private static final int BODY_MAX = 2000;
    /** IN 查询分批上限，兼容数据库参数数量限制 */
    private static final int QUERY_BATCH_SIZE = 500;

    private final JsonMapper objectMapper;
    private final MesIntegrationConfigMapper configMapper;
    private final MesIntegrationLogMapper logMapper;
    private final MesMaterialMapper materialMapper;
    private final MesBomMapper bomMapper;
    private final MesBomLineMapper bomLineMapper;
    private final MesOrderMapper orderServiceMapper;
    private final MesOrderService orderService;

    public MesIntegrationService(JsonMapper objectMapper, MesIntegrationConfigMapper configMapper,
                                 MesIntegrationLogMapper logMapper, MesMaterialMapper materialMapper,
                                 MesBomMapper bomMapper, MesBomLineMapper bomLineMapper,
                                 MesOrderMapper orderServiceMapper, MesOrderService orderService) {
        this.objectMapper = objectMapper;
        this.configMapper = configMapper;
        this.logMapper = logMapper;
        this.materialMapper = materialMapper;
        this.bomMapper = bomMapper;
        this.bomLineMapper = bomLineMapper;
        this.orderServiceMapper = orderServiceMapper;
        this.orderService = orderService;
    }

    // ==================== 配置 ====================

    /** ERP 入站同步配置（未设置补默认行） */
    public List<Map<String, Object>> configMap() {
        MesIntegrationConfig row = configByType(MesIntegrationConfig.TYPE_ERP_SYNC);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", MesIntegrationConfig.TYPE_ERP_SYNC);
        item.put("enabled", row == null || row.getEnabled() == null ? 0 : row.getEnabled());
        item.put("remark", row == null ? null : row.getRemark());
        return List.of(item);
    }

    /** 保存配置 */
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(String type, Integer enabled, String remark) {
        if (!MesIntegrationConfig.TYPE_ERP_SYNC.equals(type)) {
            throw new ServiceException(400, "未知集成类型: " + type);
        }
        MesIntegrationConfig row = configByType(type);
        if (row == null) {
            row = new MesIntegrationConfig();
            row.setType(type);
        }
        row.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        row.setRemark(remark);
        if (row.getId() == null) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
    }

    /** 类型行（可能为 null，调用方按缺省处理） */
    private MesIntegrationConfig configByType(String type) {
        return configMapper.selectOne(new LambdaQueryWrapper<MesIntegrationConfig>()
                .eq(MesIntegrationConfig::getType, type).last("LIMIT 1"));
    }

    private boolean enabledOf(String type) {
        MesIntegrationConfig row = configByType(type);
        return row != null && row.getEnabled() != null && row.getEnabled() == 1;
    }

    // ==================== 入站：ERP → MES ====================

    /** 订单引入（externalCode 幂等：同外部单号重复推送返回已有工单；引为草稿态待计划员确认） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importOrder(ImportOrder item) {
        requireErpSync("订单引入");
        if (item.externalCode() == null || item.externalCode().isBlank()) {
            throw new ServiceException(400, "externalCode（ERP 单号）不能为空");
        }
        // 幂等：同外部单号已成功引入过，直接返回已有工单
        MesIntegrationLog existed = logMapper.selectOne(new LambdaQueryWrapper<MesIntegrationLog>()
                .eq(MesIntegrationLog::getDirection, "in")
                .eq(MesIntegrationLog::getType, "order")
                .eq(MesIntegrationLog::getExternalCode, item.externalCode())
                .eq(MesIntegrationLog::getStatus, MesIntegrationLog.ST_SUCCESS)
                .last("LIMIT 1"));
        if (existed != null && existed.getBizCode() != null) {
            MesOrder order = orderMapperSelectByCode(existed.getBizCode());
            if (order != null) {
                return Map.of("orderCode", order.getCode(), "duplicated", true);
            }
        }
        MesMaterial product = requireMaterial(item.productCode());
        MesOrder order = orderService.create(product.getId(), item.bomId(), item.routeId(),
                item.planQty(), item.planStart(), item.planEnd(),
                "ERP 引入（外部单号 " + item.externalCode() + "）" + (item.remark() == null ? "" : "：" + item.remark()));
        writeInLog("order", item.externalCode(), order.getCode(), 1, 1,
                toJson(Map.of("externalCode", item.externalCode(), "productCode", item.productCode(),
                        "planQty", item.planQty())), null);
        return Map.of("orderCode", order.getCode());
    }

    /** 物料主数据 upsert（编码唯一键，ERP 为准） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importMaterials(List<ImportMaterial> materials) {
        requireErpSync("物料引入");
        Set<String> codes = new HashSet<>();
        for (ImportMaterial item : materials) {
            if (item.code() == null || item.code().isBlank()) {
                throw new ServiceException(400, "物料编码不能为空");
            }
            codes.add(item.code());
        }
        Map<String, MesMaterial> existingByCode = loadMaterialsByCodes(codes);
        int created = 0;
        int updated = 0;
        for (ImportMaterial item : materials) {
            MesMaterial row = existingByCode.get(item.code());
            if (row == null) {
                row = new MesMaterial();
                row.setCode(item.code());
                row.setName(item.name());
                row.setSpec(item.spec());
                row.setUnit(item.unit() == null || item.unit().isBlank() ? "件" : item.unit());
                row.setType(item.type() == null ? 1 : item.type());
                row.setTraceMode(1);
                materialMapper.insert(row);
                existingByCode.put(item.code(), row);
                created++;
            } else {
                if (item.name() != null) {
                    row.setName(item.name());
                }
                if (item.spec() != null) {
                    row.setSpec(item.spec());
                }
                if (item.unit() != null && !item.unit().isBlank()) {
                    row.setUnit(item.unit());
                }
                if (item.type() != null) {
                    row.setType(item.type());
                }
                materialMapper.updateById(row);
                updated++;
            }
        }
        writeInLog("material", null, null, materials.size(), created + updated,
                toJson(Map.of("count", materials.size())), null);
        return Map.of("total", materials.size(), "created", created, "updated", updated);
    }

    /** BOM upsert：父项物料编码 + 版本定位，存在则整单重建明细 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importBoms(List<ImportBom> boms) {
        requireErpSync("BOM 引入");
        Set<String> materialCodes = new HashSet<>();
        Set<String> versions = new HashSet<>();
        for (ImportBom item : boms) {
            addCodeIfPresent(materialCodes, item.parentCode());
            versions.add(normalizeBomVersion(item.version()));
            if (item.lines() != null) {
                for (ImportBomLine line : item.lines()) {
                    addCodeIfPresent(materialCodes, line.materialCode());
                }
            }
        }
        Map<String, MesMaterial> materialsByCode = loadMaterialsByCodes(materialCodes);
        Set<Long> parentMaterialIds = new HashSet<>();
        for (ImportBom item : boms) {
            MesMaterial parent = requiredMaterial(materialsByCode, item.parentCode());
            parentMaterialIds.add(parent.getId());
        }
        Map<BomKey, MesBom> bomsByKey = loadBoms(parentMaterialIds, versions);
        int created = 0;
        int updated = 0;
        for (ImportBom item : boms) {
            MesMaterial parent = requiredMaterial(materialsByCode, item.parentCode());
            String version = normalizeBomVersion(item.version());
            BomKey key = new BomKey(parent.getId(), version);
            MesBom bom = bomsByKey.get(key);
            if (bom == null) {
                bom = new MesBom();
                bom.setCode("BOM-" + parent.getCode() + "-" + version);
                bom.setMaterialId(parent.getId());
                bom.setVersion(version);
                bom.setRemark("ERP 引入");
                bomMapper.insert(bom);
                bomsByKey.put(key, bom);
                created++;
            } else {
                bomLineMapper.delete(new LambdaQueryWrapper<MesBomLine>().eq(MesBomLine::getBomId, bom.getId()));
                updated++;
            }
            if (item.lines() != null) {
                for (ImportBomLine line : item.lines()) {
                    MesMaterial material = requiredMaterial(materialsByCode, line.materialCode());
                    MesBomLine row = new MesBomLine();
                    row.setBomId(bom.getId());
                    row.setMaterialId(material.getId());
                    row.setQty(line.qty());
                    row.setLossRate(java.math.BigDecimal.ZERO);
                    bomLineMapper.insert(row);
                }
            }
        }
        writeInLog("bom", null, null, boms.size(), created + updated,
                toJson(Map.of("count", boms.size())), null);
        return Map.of("total", boms.size(), "created", created, "updated", updated);
    }

    private void requireErpSync(String action) {
        if (!enabledOf(MesIntegrationConfig.TYPE_ERP_SYNC)) {
            throw new ServiceException(1403, action + "未启用：请管理员在「系统集成」启用 ERP 同步");
        }
    }

    private MesMaterial requireMaterial(String code) {
        MesMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                .eq(MesMaterial::getCode, code).last("LIMIT 1"));
        if (material == null) {
            throw new ServiceException(1404, "物料不存在: " + code + "（请先同步物料主数据）");
        }
        return material;
    }

    private Map<String, MesMaterial> loadMaterialsByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return new HashMap<>();
        }
        List<String> values = new ArrayList<>(codes);
        Map<String, MesMaterial> result = new HashMap<>();
        for (int from = 0; from < values.size(); from += QUERY_BATCH_SIZE) {
            List<String> batch = values.subList(from, Math.min(from + QUERY_BATCH_SIZE, values.size()));
            for (MesMaterial material : materialMapper.selectList(new LambdaQueryWrapper<MesMaterial>()
                    .in(MesMaterial::getCode, batch))) {
                result.putIfAbsent(material.getCode(), material);
            }
        }
        return result;
    }

    private Map<BomKey, MesBom> loadBoms(Set<Long> materialIds, Set<String> versions) {
        if (materialIds.isEmpty() || versions.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> ids = new ArrayList<>(materialIds);
        List<String> versionValues = new ArrayList<>(versions);
        Map<BomKey, MesBom> result = new HashMap<>();
        for (int idFrom = 0; idFrom < ids.size(); idFrom += QUERY_BATCH_SIZE) {
            List<Long> idBatch = ids.subList(idFrom, Math.min(idFrom + QUERY_BATCH_SIZE, ids.size()));
            for (int versionFrom = 0; versionFrom < versionValues.size(); versionFrom += QUERY_BATCH_SIZE) {
                List<String> versionBatch = versionValues.subList(versionFrom,
                        Math.min(versionFrom + QUERY_BATCH_SIZE, versionValues.size()));
                for (MesBom bom : bomMapper.selectList(new LambdaQueryWrapper<MesBom>()
                        .in(MesBom::getMaterialId, idBatch).in(MesBom::getVersion, versionBatch))) {
                    result.putIfAbsent(new BomKey(bom.getMaterialId(), bom.getVersion()), bom);
                }
            }
        }
        return result;
    }

    private MesMaterial requiredMaterial(Map<String, MesMaterial> materialsByCode, String code) {
        MesMaterial material = materialsByCode.get(code);
        if (material == null) {
            throw new ServiceException(1404, "物料不存在: " + code + "（请先同步物料主数据）");
        }
        return material;
    }

    private void addCodeIfPresent(Set<String> codes, String code) {
        if (code != null && !code.isBlank()) {
            codes.add(code);
        }
    }

    private String normalizeBomVersion(String version) {
        return version == null || version.isBlank() ? "v1.0" : version;
    }

    private record BomKey(Long materialId, String version) {
    }

    private MesOrder orderMapperSelectByCode(String code) {
        return orderServiceMapper.selectOne(new LambdaQueryWrapper<MesOrder>()
                .eq(MesOrder::getCode, code).last("LIMIT 1"));
    }

    private void writeInLog(String type, String externalCode, String bizCode, int total, int success,
                            String request, String error) {
        MesIntegrationLog row = new MesIntegrationLog();
        row.setDirection("in");
        row.setType(type);
        row.setExternalCode(externalCode);
        row.setBizCode(bizCode);
        row.setStatus(error == null ? MesIntegrationLog.ST_SUCCESS : MesIntegrationLog.ST_FAILED);
        row.setTotal(total);
        row.setSuccess(success);
        row.setChannel("mse-open");
        row.setRequest(snippet(request));
        row.setError(error == null ? null : snippet(error));
        row.setRetryCount(0);
        logMapper.insert(row);
    }

    // ==================== 日志查询 ====================

    /** 分页查询同步日志（direction/type/status/bizCode 过滤） */
    public PageResult<MesIntegrationLog> pageLogs(long pageNum, long pageSize, String direction,
                                                  String type, Integer status, String bizCode) {
        LambdaQueryWrapper<MesIntegrationLog> query = new LambdaQueryWrapper<MesIntegrationLog>()
                .eq(direction != null && !direction.isBlank(), MesIntegrationLog::getDirection, direction)
                .eq(type != null && !type.isBlank(), MesIntegrationLog::getType, type)
                .eq(status != null, MesIntegrationLog::getStatus, status)
                .like(bizCode != null && !bizCode.isBlank(), MesIntegrationLog::getBizCode, bizCode)
                .orderByDesc(MesIntegrationLog::getId);
        Page<MesIntegrationLog> page = logMapper.selectPage(Page.of(pageNum, pageSize), query);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    // ==================== 内部工具 ====================

    private String toJson(Object data) {
        return objectMapper.writeValueAsString(data);
    }

    private String snippet(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= BODY_MAX ? text : text.substring(0, BODY_MAX) + "…（已截断）";
    }

    /** 订单引入条目 */
    public record ImportOrder(String externalCode, String productCode, java.math.BigDecimal planQty,
                              java.time.LocalDate planStart, java.time.LocalDate planEnd,
                              Long bomId, Long routeId, String remark) {
    }

    /** 物料引入条目 */
    public record ImportMaterial(String code, String name, String spec, String unit, Integer type) {
    }

    /** BOM 引入条目 */
    public record ImportBom(String parentCode, String version, List<ImportBomLine> lines) {
    }

    /** BOM 明细引入条目 */
    public record ImportBomLine(String materialCode, java.math.BigDecimal qty) {
    }
}
