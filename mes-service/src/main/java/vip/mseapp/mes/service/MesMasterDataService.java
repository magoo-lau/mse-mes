package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mes.entity.MesBom;
import vip.mseapp.mes.entity.MesBomLine;
import vip.mseapp.mes.entity.MesCalendar;
import vip.mseapp.mes.entity.MesCheckPlan;
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.entity.MesIotConfig;
import vip.mseapp.mes.entity.MesMaterial;
import vip.mseapp.mes.entity.MesModel;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.entity.MesQcOrder;
import vip.mseapp.mes.entity.MesQcScheme;
import vip.mseapp.mes.entity.MesQcSchemeItem;
import vip.mseapp.mes.entity.MesRepair;
import vip.mseapp.mes.entity.MesRoute;
import vip.mseapp.mes.entity.MesRouteStep;
import vip.mseapp.mes.excel.BomExcelRow;
import vip.mseapp.mes.excel.EquipmentExcelRow;
import vip.mseapp.mes.excel.MaterialExcelRow;
import vip.mseapp.mes.excel.ModelExcelRow;
import vip.mseapp.mes.excel.QcSchemeExcelRow;
import vip.mseapp.mes.excel.RouteExcelRow;
import vip.mseapp.mes.mapper.MesBomLineMapper;
import vip.mseapp.mes.mapper.MesBomMapper;
import vip.mseapp.mes.mapper.MesCalendarMapper;
import vip.mseapp.mes.mapper.MesCheckPlanMapper;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.mapper.MesIotConfigMapper;
import vip.mseapp.mes.mapper.MesMaterialMapper;
import vip.mseapp.mes.mapper.MesModelMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.mapper.MesQcOrderMapper;
import vip.mseapp.mes.mapper.MesQcSchemeItemMapper;
import vip.mseapp.mes.mapper.MesQcSchemeMapper;
import vip.mseapp.mes.mapper.MesRepairMapper;
import vip.mseapp.mes.mapper.MesRouteMapper;
import vip.mseapp.mes.mapper.MesRouteStepMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * 主数据批量操作：Excel 导入（按编码 upsert）、导出行构建、批量删除（引用校验）与编码唯一查重。
 *
 * <p>部分成功语义：逐行/逐条处理，失败跳过并收集原因（导入报「第N行：…」，批量操作报「id=xxx：…」）。
 * 导入方法整段事务（先整行/整组校验后写入，失败行零写入）；批量删除循环体无外层事务（单条删各自生效，
 * 与既有单条删除行为一致）。
 */
@Service
public class MesMasterDataService {

    private static final int DB_IN_BATCH_SIZE = 500;
    private static final Map<String, Integer> MATERIAL_TYPES = Map.of("原材料", 1, "半成品", 2, "成品", 3);
    private static final Map<Integer, String> MATERIAL_TYPE_LABELS = Map.of(1, "原材料", 2, "半成品", 3, "成品");
    private static final Map<String, Integer> TRACE_MODES = Map.of("按批次", 1, "按序列号", 2);
    private static final Map<Integer, String> TRACE_MODE_LABELS = Map.of(1, "按批次", 2, "按序列号");
    private static final Map<String, Integer> MODEL_TYPES = Map.of("工厂", 1, "车间", 2, "产线", 3, "工位", 4);
    private static final Map<Integer, String> MODEL_TYPE_LABELS = Map.of(1, "工厂", 2, "车间", 3, "产线", 4, "工位");
    private static final Map<String, Integer> EQUIPMENT_STATUSES = Map.of("运行", 1, "待机", 2, "维修", 3, "停机", 4);
    private static final Map<Integer, String> EQUIPMENT_STATUS_LABELS = Map.of(1, "运行", 2, "待机", 3, "维修", 4, "停机");
    private static final Map<String, Integer> QC_TYPES = Map.of("IQC", 1, "IPQC", 2, "FQC", 3);
    private static final Map<Integer, String> QC_TYPE_LABELS = Map.of(1, "IQC", 2, "IPQC", 3, "FQC");
    private static final Set<String> TRUE_LABELS = Set.of("是", "Y", "YES", "TRUE", "1");

    private final MesMaterialMapper materialMapper;
    private final MesModelMapper modelMapper;
    private final MesBomMapper bomMapper;
    private final MesBomLineMapper bomLineMapper;
    private final MesRouteMapper routeMapper;
    private final MesRouteStepMapper routeStepMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final MesQcSchemeMapper schemeMapper;
    private final MesQcSchemeItemMapper schemeItemMapper;
    private final MesOrderMapper orderMapper;
    private final MesCalendarMapper calendarMapper;
    private final MesCheckPlanMapper checkPlanMapper;
    private final MesRepairMapper repairMapper;
    private final MesIotConfigMapper iotConfigMapper;
    private final MesQcOrderMapper qcOrderMapper;
    private final MesBatchRunner batchRunner;

    public MesMasterDataService(MesMaterialMapper materialMapper, MesModelMapper modelMapper,
                                MesBomMapper bomMapper, MesBomLineMapper bomLineMapper,
                                MesRouteMapper routeMapper, MesRouteStepMapper routeStepMapper,
                                MesEquipmentMapper equipmentMapper, MesQcSchemeMapper schemeMapper,
                                MesQcSchemeItemMapper schemeItemMapper, MesOrderMapper orderMapper,
                                MesCalendarMapper calendarMapper, MesCheckPlanMapper checkPlanMapper,
                                MesRepairMapper repairMapper, MesIotConfigMapper iotConfigMapper,
                                MesQcOrderMapper qcOrderMapper, MesBatchRunner batchRunner) {
        this.materialMapper = materialMapper;
        this.modelMapper = modelMapper;
        this.bomMapper = bomMapper;
        this.bomLineMapper = bomLineMapper;
        this.routeMapper = routeMapper;
        this.routeStepMapper = routeStepMapper;
        this.equipmentMapper = equipmentMapper;
        this.schemeMapper = schemeMapper;
        this.schemeItemMapper = schemeItemMapper;
        this.orderMapper = orderMapper;
        this.calendarMapper = calendarMapper;
        this.checkPlanMapper = checkPlanMapper;
        this.repairMapper = repairMapper;
        this.iotConfigMapper = iotConfigMapper;
        this.qcOrderMapper = qcOrderMapper;
        this.batchRunner = batchRunner;
    }

    // ==================== Excel 导入（按编码 upsert，整行覆盖） ====================

    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult importMaterials(List<MaterialExcelRow> rows, boolean updateSupport) {
        List<String> errors = new ArrayList<>();
        int success = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int lineNo = i + 2;
            MaterialExcelRow row = rows.get(i);
            try {
                String code = required(row.getCode(), "物料编码");
                if (!seen.add(code)) {
                    throw new ServiceException(400, "物料编码在文件内重复");
                }
                String name = required(row.getName(), "物料名称");
                Integer type = parseLabel(MATERIAL_TYPES, row.getType(), "类型", 1);
                Integer traceMode = parseLabel(TRACE_MODES, row.getTraceMode(), "追溯粒度", 1);
                String unit = orDefault(row.getUnit(), "件");
                MesMaterial exist = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                        .eq(MesMaterial::getCode, code).last("LIMIT 1"));
                if (exist == null) {
                    MesMaterial material = new MesMaterial();
                    material.setCode(code);
                    material.setName(name);
                    material.setSpec(blankToNull(row.getSpec()));
                    material.setUnit(unit);
                    material.setType(type);
                    material.setTraceMode(traceMode);
                    material.setRemark(blankToNull(row.getRemark()));
                    materialMapper.insert(material);
                } else if (updateSupport) {
                    exist.setName(name);
                    exist.setSpec(blankToNull(row.getSpec()));
                    exist.setUnit(unit);
                    exist.setType(type);
                    exist.setTraceMode(traceMode);
                    exist.setRemark(blankToNull(row.getRemark()));
                    materialMapper.updateById(exist);
                } else {
                    throw new ServiceException(400, "物料编码已存在（勾选「更新已存在」后覆盖）");
                }
                success++;
            } catch (Exception e) {
                errors.add("第" + lineNo + "行：" + message(e));
            }
        }
        return new BatchOpResult(success, rows.size() - success, errors);
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult importModels(List<ModelExcelRow> rows, boolean updateSupport) {
        record Entry(int lineNo, int type, String code, String name, String parentCode, String remark) {
        }
        List<String> errors = new ArrayList<>();
        List<Entry> valid = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int lineNo = i + 2;
            ModelExcelRow row = rows.get(i);
            try {
                Integer type = parseLabel(MODEL_TYPES, row.getType(), "类型", null);
                String code = required(row.getCode(), "编码");
                String name = required(row.getName(), "名称");
                valid.add(new Entry(lineNo, type, code, name, blankToNull(row.getParentCode()),
                        blankToNull(row.getRemark())));
            } catch (Exception e) {
                errors.add("第" + lineNo + "行：" + message(e));
            }
        }
        // 文件内编码去重（跨类型同编码视为重复）
        Set<String> seen = new HashSet<>();
        valid.removeIf(e -> {
            if (!seen.add(e.code())) {
                errors.add("第" + e.lineNo() + "行：编码在文件内重复，已跳过");
                return true;
            }
            return false;
        });
        // 按类型升序（稳定）处理，保证工厂→车间→产线→工位先建上级
        valid.sort((a, b) -> {
            int byType = Integer.compare(a.type(), b.type());
            return byType != 0 ? byType : Integer.compare(a.lineNo(), b.lineNo());
        });
        Map<String, Long> knownIds = new HashMap<>();
        modelMapper.selectList(null).forEach(m -> knownIds.put(m.getCode(), m.getId()));
        int success = 0;
        for (Entry entry : valid) {
            try {
                Long parentId = 0L;
                if (entry.parentCode() != null) {
                    parentId = knownIds.get(entry.parentCode());
                    if (parentId == null) {
                        throw new ServiceException(400, "上级编码「" + entry.parentCode() + "」不存在");
                    }
                }
                MesModel exist = modelMapper.selectOne(new LambdaQueryWrapper<MesModel>()
                        .eq(MesModel::getCode, entry.code()).last("LIMIT 1"));
                if (exist == null) {
                    MesModel model = new MesModel();
                    model.setType(entry.type());
                    model.setParentId(parentId);
                    model.setCode(entry.code());
                    model.setName(entry.name());
                    model.setRemark(entry.remark());
                    modelMapper.insert(model);
                    knownIds.put(entry.code(), model.getId());
                } else if (updateSupport) {
                    exist.setType(entry.type());
                    exist.setParentId(parentId);
                    exist.setName(entry.name());
                    exist.setRemark(entry.remark());
                    modelMapper.updateById(exist);
                    knownIds.put(entry.code(), exist.getId());
                } else {
                    throw new ServiceException(400, "编码已存在（勾选「更新已存在」后覆盖）");
                }
                success++;
            } catch (Exception e) {
                errors.add("第" + entry.lineNo() + "行：" + message(e));
            }
        }
        return new BatchOpResult(success, rows.size() - success, errors);
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult importBoms(List<BomExcelRow> rows, boolean updateSupport) {
        List<String> errors = new ArrayList<>();
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        int blank = 0;
        for (int i = 0; i < rows.size(); i++) {
            String code = trim(rows.get(i).getBomCode());
            if (code != null) {
                groups.computeIfAbsent(code, k -> new ArrayList<>()).add(i);
            } else {
                errors.add("第" + (i + 2) + "行：BOM编码不能为空");
                blank++;
            }
        }
        int success = 0;
        for (Map.Entry<String, List<Integer>> group : groups.entrySet()) {
            try {
                saveBomGroup(group.getKey(), group.getValue(), rows, updateSupport);
                success++;
            } catch (LineException e) {
                errors.add("第" + e.lineNo + "行：" + e.getMessage());
            } catch (Exception e) {
                errors.add("第" + (group.getValue().getFirst() + 2) + "行：BOM「" + group.getKey() + "」导入失败：" + message(e));
            }
        }
        return new BatchOpResult(success, groups.size() - success + blank, errors);
    }

    private void saveBomGroup(String bomCode, List<Integer> indexes, List<BomExcelRow> rows,
                              boolean updateSupport) {
        BomExcelRow head = rows.get(indexes.getFirst());
        String parentCode = required(head.getParentMaterialCode(), "父项物料编码");
        MesMaterial parent = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                .eq(MesMaterial::getCode, parentCode).last("LIMIT 1"));
        if (parent == null) {
            throw new LineException(indexes.getFirst() + 2, "父项物料编码「" + parentCode + "」不存在");
        }
        record Line(Long materialId, BigDecimal qty, BigDecimal lossRate) {
        }
        List<Line> lines = new ArrayList<>();
        for (int index : indexes) {
            int lineNo = index + 2;
            BomExcelRow row = rows.get(index);
            if (index != indexes.getFirst()) {
                String otherParent = trim(row.getParentMaterialCode());
                if (otherParent != null && !otherParent.equals(parentCode)) {
                    throw new LineException(lineNo, "与同组首行父项物料不一致");
                }
            }
            String childCode = required(row.getChildMaterialCode(), "子件物料编码");
            MesMaterial child = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                    .eq(MesMaterial::getCode, childCode).last("LIMIT 1"));
            if (child == null) {
                throw new LineException(lineNo, "子件物料编码「" + childCode + "」不存在");
            }
            if (row.getQty() == null || row.getQty().signum() <= 0) {
                throw new LineException(lineNo, "用量必须大于 0");
            }
            lines.add(new Line(child.getId(), row.getQty(), row.getLossRate()));
        }
        MesBom exist = bomMapper.selectOne(new LambdaQueryWrapper<MesBom>()
                .eq(MesBom::getCode, bomCode).last("LIMIT 1"));
        MesBom bom;
        if (exist == null) {
            bom = new MesBom();
            bom.setCode(bomCode);
        } else if (updateSupport) {
            bom = exist;
        } else {
            throw new LineException(indexes.getFirst() + 2, "BOM编码已存在（勾选「更新已存在」后覆盖）");
        }
        bom.setMaterialId(parent.getId());
        bom.setVersion(orDefault(head.getVersion(), "V1.0"));
        bom.setRemark(blankToNull(head.getRemark()));
        if (exist == null) {
            bomMapper.insert(bom);
        } else {
            bomMapper.updateById(bom);
        }
        bomLineMapper.delete(new LambdaQueryWrapper<MesBomLine>().eq(MesBomLine::getBomId, bom.getId()));
        for (Line line : lines) {
            MesBomLine row = new MesBomLine();
            row.setBomId(bom.getId());
            row.setMaterialId(line.materialId());
            row.setQty(line.qty());
            row.setLossRate(line.lossRate());
            bomLineMapper.insert(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult importRoutes(List<RouteExcelRow> rows, boolean updateSupport) {
        List<String> errors = new ArrayList<>();
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        int blank = 0;
        for (int i = 0; i < rows.size(); i++) {
            String code = trim(rows.get(i).getRouteCode());
            if (code != null) {
                groups.computeIfAbsent(code, k -> new ArrayList<>()).add(i);
            } else {
                errors.add("第" + (i + 2) + "行：路线编码不能为空");
                blank++;
            }
        }
        int success = 0;
        for (Map.Entry<String, List<Integer>> group : groups.entrySet()) {
            try {
                saveRouteGroup(group.getKey(), group.getValue(), rows, updateSupport);
                success++;
            } catch (LineException e) {
                errors.add("第" + e.lineNo + "行：" + e.getMessage());
            } catch (Exception e) {
                errors.add("第" + (group.getValue().getFirst() + 2) + "行：路线「" + group.getKey() + "」导入失败：" + message(e));
            }
        }
        return new BatchOpResult(success, groups.size() - success + blank, errors);
    }

    private void saveRouteGroup(String routeCode, List<Integer> indexes, List<RouteExcelRow> rows,
                                boolean updateSupport) {
        RouteExcelRow head = rows.get(indexes.getFirst());
        String routeName = required(head.getRouteName(), "路线名称");
        Long materialId = null;
        if (trim(head.getMaterialCode()) != null) {
            MesMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                    .eq(MesMaterial::getCode, trim(head.getMaterialCode())).last("LIMIT 1"));
            if (material == null) {
                throw new LineException(indexes.getFirst() + 2, "适用物料编码「" + head.getMaterialCode() + "」不存在");
            }
            materialId = material.getId();
        }
        record Step(Integer stepNo, String stepName, Long lineId, Integer standardTime, int qcRequired) {
        }
        List<Step> steps = new ArrayList<>();
        for (int index : indexes) {
            int lineNo = index + 2;
            RouteExcelRow row = rows.get(index);
            String stepName = required(row.getStepName(), "工序名称");
            Long lineId = null;
            String lineCode = trim(row.getLineCode());
            if (lineCode != null) {
                MesModel line = modelMapper.selectOne(new LambdaQueryWrapper<MesModel>()
                        .eq(MesModel::getCode, lineCode).eq(MesModel::getType, 3).last("LIMIT 1"));
                if (line == null) {
                    throw new LineException(lineNo, "产线编码「" + lineCode + "」不存在（须为类型=产线的建模节点）");
                }
                lineId = line.getId();
            }
            boolean qcRequired = row.getQcRequired() != null && !row.getQcRequired().isBlank()
                    && TRUE_LABELS.contains(row.getQcRequired().trim().toUpperCase());
            steps.add(new Step(row.getStepNo(), stepName, lineId, row.getStandardTime(), qcRequired ? 1 : 0));
        }
        // 工序号可空：空按文件行序，非空优先；写入时统一重排 1..n
        steps.sort((a, b) -> Integer.compare(a.stepNo() == null ? Integer.MAX_VALUE : a.stepNo(),
                b.stepNo() == null ? Integer.MAX_VALUE : b.stepNo()));
        MesRoute exist = routeMapper.selectOne(new LambdaQueryWrapper<MesRoute>()
                .eq(MesRoute::getCode, routeCode).last("LIMIT 1"));
        MesRoute route;
        if (exist == null) {
            route = new MesRoute();
            route.setCode(routeCode);
            route.setStatus(0);
        } else {
            if (exist.getStatus() != null && exist.getStatus() == 1) {
                throw new LineException(indexes.getFirst() + 2, "已发布的工艺路线不能导入覆盖，请先停用");
            }
            if (!updateSupport) {
                throw new LineException(indexes.getFirst() + 2, "路线编码已存在（勾选「更新已存在」后覆盖）");
            }
            route = exist;
        }
        route.setName(routeName);
        route.setMaterialId(materialId);
        route.setVersion(orDefault(head.getVersion(), "V1.0"));
        route.setRemark(blankToNull(head.getRemark()));
        if (exist == null) {
            routeMapper.insert(route);
        } else {
            routeMapper.updateById(route);
        }
        routeStepMapper.delete(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getRouteId, route.getId()));
        int no = 1;
        for (Step step : steps) {
            MesRouteStep row = new MesRouteStep();
            row.setRouteId(route.getId());
            row.setStepNo(no++);
            row.setStepName(step.stepName());
            row.setLineId(step.lineId());
            row.setStandardTime(step.standardTime());
            row.setQcRequired(step.qcRequired());
            routeStepMapper.insert(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult importEquipments(List<EquipmentExcelRow> rows, boolean updateSupport) {
        List<String> errors = new ArrayList<>();
        int success = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int lineNo = i + 2;
            EquipmentExcelRow row = rows.get(i);
            try {
                String code = required(row.getCode(), "设备编码");
                if (!seen.add(code)) {
                    throw new ServiceException(400, "设备编码在文件内重复");
                }
                String name = required(row.getName(), "设备名称");
                Integer status = parseLabel(EQUIPMENT_STATUSES, row.getStatus(), "状态", 2);
                LocalDate buyDate = null;
                if (trim(row.getBuyDate()) != null) {
                    try {
                        buyDate = LocalDate.parse(trim(row.getBuyDate()));
                    } catch (DateTimeParseException e) {
                        throw new ServiceException(400, "购置日期须为 yyyy-MM-dd 格式");
                    }
                }
                Long workstationId = null;
                String workstationCode = trim(row.getWorkstationCode());
                if (workstationCode != null) {
                    MesModel workstation = modelMapper.selectOne(new LambdaQueryWrapper<MesModel>()
                            .eq(MesModel::getCode, workstationCode).eq(MesModel::getType, 4).last("LIMIT 1"));
                    if (workstation == null) {
                        throw new ServiceException(400, "工位编码「" + workstationCode + "」不存在（须为类型=工位的建模节点）");
                    }
                    workstationId = workstation.getId();
                }
                MesEquipment exist = equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                        .eq(MesEquipment::getCode, code).last("LIMIT 1"));
                if (exist == null) {
                    MesEquipment equipment = new MesEquipment();
                    equipment.setCode(code);
                    applyEquipment(equipment, name, row, status, buyDate, workstationId);
                    equipmentMapper.insert(equipment);
                } else if (updateSupport) {
                    applyEquipment(exist, name, row, status, buyDate, workstationId);
                    equipmentMapper.updateById(exist);
                } else {
                    throw new ServiceException(400, "设备编码已存在（勾选「更新已存在」后覆盖）");
                }
                success++;
            } catch (Exception e) {
                errors.add("第" + lineNo + "行：" + message(e));
            }
        }
        return new BatchOpResult(success, rows.size() - success, errors);
    }

    private void applyEquipment(MesEquipment equipment, String name, EquipmentExcelRow row,
                                Integer status, LocalDate buyDate, Long workstationId) {
        equipment.setName(name);
        equipment.setModel(blankToNull(row.getModel()));
        equipment.setWorkstationId(workstationId);
        equipment.setStatus(status);
        equipment.setBuyDate(buyDate);
        equipment.setRemark(blankToNull(row.getRemark()));
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchOpResult importQcSchemes(List<QcSchemeExcelRow> rows, boolean updateSupport) {
        List<String> errors = new ArrayList<>();
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        int blank = 0;
        for (int i = 0; i < rows.size(); i++) {
            String code = trim(rows.get(i).getSchemeCode());
            if (code != null) {
                groups.computeIfAbsent(code, k -> new ArrayList<>()).add(i);
            } else {
                errors.add("第" + (i + 2) + "行：方案编码不能为空");
                blank++;
            }
        }
        int success = 0;
        for (Map.Entry<String, List<Integer>> group : groups.entrySet()) {
            try {
                saveSchemeGroup(group.getKey(), group.getValue(), rows, updateSupport);
                success++;
            } catch (LineException e) {
                errors.add("第" + e.lineNo + "行：" + e.getMessage());
            } catch (Exception e) {
                errors.add("第" + (group.getValue().getFirst() + 2) + "行：方案「" + group.getKey() + "」导入失败：" + message(e));
            }
        }
        return new BatchOpResult(success, groups.size() - success + blank, errors);
    }

    private void saveSchemeGroup(String schemeCode, List<Integer> indexes, List<QcSchemeExcelRow> rows,
                                 boolean updateSupport) {
        QcSchemeExcelRow head = rows.get(indexes.getFirst());
        String schemeName = required(head.getSchemeName(), "方案名称");
        Integer qcType = parseLabel(QC_TYPES, head.getQcType(), "检验类型", null);
        Long materialId = null;
        if (trim(head.getMaterialCode()) != null) {
            MesMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                    .eq(MesMaterial::getCode, trim(head.getMaterialCode())).last("LIMIT 1"));
            if (material == null) {
                throw new LineException(indexes.getFirst() + 2, "适用物料编码「" + head.getMaterialCode() + "」不存在");
            }
            materialId = material.getId();
        }
        record Item(String itemName, String standard, String method) {
        }
        List<Item> items = new ArrayList<>();
        for (int index : indexes) {
            QcSchemeExcelRow row = rows.get(index);
            String itemName = required(row.getItemName(), "检验项目名称");
            items.add(new Item(itemName, blankToNull(row.getStandard()), blankToNull(row.getMethod())));
        }
        MesQcScheme exist = schemeMapper.selectOne(new LambdaQueryWrapper<MesQcScheme>()
                .eq(MesQcScheme::getCode, schemeCode).last("LIMIT 1"));
        MesQcScheme scheme;
        if (exist == null) {
            scheme = new MesQcScheme();
            scheme.setCode(schemeCode);
        } else if (updateSupport) {
            scheme = exist;
        } else {
            throw new LineException(indexes.getFirst() + 2, "方案编码已存在（勾选「更新已存在」后覆盖）");
        }
        scheme.setName(schemeName);
        scheme.setQcType(qcType);
        scheme.setMaterialId(materialId);
        scheme.setSampleRule(blankToNull(head.getSampleRule()));
        scheme.setRemark(blankToNull(head.getRemark()));
        if (exist == null) {
            schemeMapper.insert(scheme);
        } else {
            schemeMapper.updateById(scheme);
        }
        schemeItemMapper.delete(new LambdaQueryWrapper<MesQcSchemeItem>().eq(MesQcSchemeItem::getSchemeId, scheme.getId()));
        for (Item item : items) {
            MesQcSchemeItem row = new MesQcSchemeItem();
            row.setSchemeId(scheme.getId());
            row.setItemName(item.itemName());
            row.setStandard(item.standard());
            row.setMethod(item.method());
            schemeItemMapper.insert(row);
        }
    }

    // ==================== 导出行构建 ====================

    public List<MaterialExcelRow> materialExportRows(String keyword) {
        List<MesMaterial> list = materialMapper.selectList(new LambdaQueryWrapper<MesMaterial>()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(MesMaterial::getCode, keyword).or().like(MesMaterial::getName, keyword))
                .orderByAsc(MesMaterial::getCode));
        return list.stream().map(m -> {
            MaterialExcelRow row = new MaterialExcelRow();
            row.setCode(m.getCode());
            row.setName(m.getName());
            row.setSpec(m.getSpec());
            row.setUnit(m.getUnit());
            row.setType(MATERIAL_TYPE_LABELS.getOrDefault(m.getType(), String.valueOf(m.getType())));
            row.setTraceMode(TRACE_MODE_LABELS.getOrDefault(m.getTraceMode(), String.valueOf(m.getTraceMode())));
            row.setRemark(m.getRemark());
            return row;
        }).toList();
    }

    public List<ModelExcelRow> modelExportRows() {
        List<MesModel> all = modelMapper.selectList(new LambdaQueryWrapper<MesModel>()
                .orderByAsc(MesModel::getType).orderByAsc(MesModel::getCode));
        Map<Long, String> codes = new HashMap<>();
        all.forEach(m -> codes.put(m.getId(), m.getCode()));
        return all.stream().map(m -> {
            ModelExcelRow row = new ModelExcelRow();
            row.setType(MODEL_TYPE_LABELS.getOrDefault(m.getType(), String.valueOf(m.getType())));
            row.setCode(m.getCode());
            row.setName(m.getName());
            if (m.getParentId() != null && m.getParentId() != 0L) {
                row.setParentCode(codes.get(m.getParentId()));
            }
            row.setRemark(m.getRemark());
            return row;
        }).toList();
    }

    public List<BomExcelRow> bomExportRows() {
        List<MesBom> boms = bomMapper.selectList(new LambdaQueryWrapper<MesBom>().orderByAsc(MesBom::getCode));
        Map<Long, String> materialCodes = materialCodeMap();
        Map<Long, List<MesBomLine>> linesByBom = new HashMap<>();
        if (!boms.isEmpty()) {
            List<Long> bomIds = boms.stream().map(MesBom::getId).toList();
            for (int from = 0; from < bomIds.size(); from += DB_IN_BATCH_SIZE) {
                List<Long> batch = bomIds.subList(from, Math.min(from + DB_IN_BATCH_SIZE, bomIds.size()));
                for (MesBomLine line : bomLineMapper.selectList(new LambdaQueryWrapper<MesBomLine>()
                        .select(MesBomLine::getId, MesBomLine::getBomId, MesBomLine::getMaterialId,
                                MesBomLine::getQty, MesBomLine::getLossRate)
                        .in(MesBomLine::getBomId, batch)
                        .orderByAsc(MesBomLine::getBomId).orderByAsc(MesBomLine::getId))) {
                    linesByBom.computeIfAbsent(line.getBomId(), ignored -> new ArrayList<>()).add(line);
                }
            }
        }
        List<BomExcelRow> result = new ArrayList<>();
        for (MesBom bom : boms) {
            List<MesBomLine> lines = linesByBom.getOrDefault(bom.getId(), List.of());
            if (lines.isEmpty()) {
                BomExcelRow row = new BomExcelRow();
                fillBomHead(row, bom, materialCodes);
                result.add(row);
                continue;
            }
            for (MesBomLine line : lines) {
                BomExcelRow row = new BomExcelRow();
                fillBomHead(row, bom, materialCodes);
                row.setChildMaterialCode(materialCodes.getOrDefault(line.getMaterialId(),
                        String.valueOf(line.getMaterialId())));
                row.setQty(line.getQty());
                row.setLossRate(line.getLossRate());
                result.add(row);
            }
        }
        return result;
    }

    private void fillBomHead(BomExcelRow row, MesBom bom, Map<Long, String> materialCodes) {
        row.setBomCode(bom.getCode());
        row.setParentMaterialCode(materialCodes.getOrDefault(bom.getMaterialId(),
                String.valueOf(bom.getMaterialId())));
        row.setVersion(bom.getVersion());
        row.setRemark(bom.getRemark());
    }

    public List<RouteExcelRow> routeExportRows() {
        List<MesRoute> routes = routeMapper.selectList(new LambdaQueryWrapper<MesRoute>().orderByAsc(MesRoute::getCode));
        Map<Long, String> materialCodes = materialCodeMap();
        Map<Long, String> lineCodes = new HashMap<>();
        modelMapper.selectList(new LambdaQueryWrapper<MesModel>().eq(MesModel::getType, 3))
                .forEach(m -> lineCodes.put(m.getId(), m.getCode()));
        Map<Long, List<MesRouteStep>> stepsByRoute = new HashMap<>();
        if (!routes.isEmpty()) {
            List<Long> routeIds = routes.stream().map(MesRoute::getId).toList();
            for (int from = 0; from < routeIds.size(); from += DB_IN_BATCH_SIZE) {
                List<Long> batch = routeIds.subList(from, Math.min(from + DB_IN_BATCH_SIZE, routeIds.size()));
                for (MesRouteStep step : routeStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                        .select(MesRouteStep::getRouteId, MesRouteStep::getStepNo, MesRouteStep::getStepName,
                                MesRouteStep::getLineId, MesRouteStep::getStandardTime, MesRouteStep::getQcRequired)
                        .in(MesRouteStep::getRouteId, batch)
                        .orderByAsc(MesRouteStep::getRouteId).orderByAsc(MesRouteStep::getStepNo))) {
                    stepsByRoute.computeIfAbsent(step.getRouteId(), ignored -> new ArrayList<>()).add(step);
                }
            }
        }
        List<RouteExcelRow> result = new ArrayList<>();
        for (MesRoute route : routes) {
            List<MesRouteStep> steps = stepsByRoute.getOrDefault(route.getId(), List.of());
            if (steps.isEmpty()) {
                RouteExcelRow row = new RouteExcelRow();
                fillRouteHead(row, route, materialCodes);
                result.add(row);
                continue;
            }
            for (MesRouteStep step : steps) {
                RouteExcelRow row = new RouteExcelRow();
                fillRouteHead(row, route, materialCodes);
                row.setStepNo(step.getStepNo());
                row.setStepName(step.getStepName());
                row.setLineCode(step.getLineId() == null ? null : lineCodes.get(step.getLineId()));
                row.setStandardTime(step.getStandardTime());
                row.setQcRequired(step.getQcRequired() != null && step.getQcRequired() == 1 ? "是" : "否");
                result.add(row);
            }
        }
        return result;
    }

    private void fillRouteHead(RouteExcelRow row, MesRoute route, Map<Long, String> materialCodes) {
        row.setRouteCode(route.getCode());
        row.setRouteName(route.getName());
        row.setVersion(route.getVersion());
        row.setMaterialCode(route.getMaterialId() == null ? null
                : materialCodes.getOrDefault(route.getMaterialId(), String.valueOf(route.getMaterialId())));
        row.setRemark(route.getRemark());
    }

    public List<EquipmentExcelRow> equipmentExportRows(String keyword) {
        List<MesEquipment> list = equipmentMapper.selectList(new LambdaQueryWrapper<MesEquipment>()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(MesEquipment::getCode, keyword).or().like(MesEquipment::getName, keyword))
                .orderByAsc(MesEquipment::getCode));
        Map<Long, String> workstationCodes = new HashMap<>();
        modelMapper.selectList(new LambdaQueryWrapper<MesModel>().eq(MesModel::getType, 4))
                .forEach(m -> workstationCodes.put(m.getId(), m.getCode()));
        return list.stream().map(e -> {
            EquipmentExcelRow row = new EquipmentExcelRow();
            row.setCode(e.getCode());
            row.setName(e.getName());
            row.setModel(e.getModel());
            row.setWorkstationCode(e.getWorkstationId() == null ? null : workstationCodes.get(e.getWorkstationId()));
            row.setStatus(EQUIPMENT_STATUS_LABELS.getOrDefault(e.getStatus(), String.valueOf(e.getStatus())));
            row.setBuyDate(e.getBuyDate() == null ? null : e.getBuyDate().toString());
            row.setRemark(e.getRemark());
            return row;
        }).toList();
    }

    public List<QcSchemeExcelRow> qcSchemeExportRows() {
        List<MesQcScheme> schemes = schemeMapper.selectList(new LambdaQueryWrapper<MesQcScheme>()
                .orderByAsc(MesQcScheme::getCode));
        Map<Long, String> materialCodes = materialCodeMap();
        Map<Long, List<MesQcSchemeItem>> itemsByScheme = new HashMap<>();
        if (!schemes.isEmpty()) {
            List<Long> schemeIds = schemes.stream().map(MesQcScheme::getId).toList();
            for (int from = 0; from < schemeIds.size(); from += DB_IN_BATCH_SIZE) {
                List<Long> batch = schemeIds.subList(from, Math.min(from + DB_IN_BATCH_SIZE, schemeIds.size()));
                for (MesQcSchemeItem item : schemeItemMapper.selectList(new LambdaQueryWrapper<MesQcSchemeItem>()
                        .select(MesQcSchemeItem::getId, MesQcSchemeItem::getSchemeId,
                                MesQcSchemeItem::getItemName, MesQcSchemeItem::getStandard,
                                MesQcSchemeItem::getMethod)
                        .in(MesQcSchemeItem::getSchemeId, batch)
                        .orderByAsc(MesQcSchemeItem::getSchemeId).orderByAsc(MesQcSchemeItem::getId))) {
                    itemsByScheme.computeIfAbsent(item.getSchemeId(), ignored -> new ArrayList<>()).add(item);
                }
            }
        }
        List<QcSchemeExcelRow> result = new ArrayList<>();
        for (MesQcScheme scheme : schemes) {
            List<MesQcSchemeItem> items = itemsByScheme.getOrDefault(scheme.getId(), List.of());
            if (items.isEmpty()) {
                QcSchemeExcelRow row = new QcSchemeExcelRow();
                fillSchemeHead(row, scheme, materialCodes);
                result.add(row);
                continue;
            }
            for (MesQcSchemeItem item : items) {
                QcSchemeExcelRow row = new QcSchemeExcelRow();
                fillSchemeHead(row, scheme, materialCodes);
                row.setItemName(item.getItemName());
                row.setStandard(item.getStandard());
                row.setMethod(item.getMethod());
                result.add(row);
            }
        }
        return result;
    }

    private void fillSchemeHead(QcSchemeExcelRow row, MesQcScheme scheme, Map<Long, String> materialCodes) {
        row.setSchemeCode(scheme.getCode());
        row.setSchemeName(scheme.getName());
        row.setQcType(QC_TYPE_LABELS.getOrDefault(scheme.getQcType(), String.valueOf(scheme.getQcType())));
        row.setMaterialCode(scheme.getMaterialId() == null ? null
                : materialCodes.getOrDefault(scheme.getMaterialId(), String.valueOf(scheme.getMaterialId())));
        row.setSampleRule(scheme.getSampleRule());
        row.setRemark(scheme.getRemark());
    }

    private Map<Long, String> materialCodeMap() {
        Map<Long, String> codes = new HashMap<>();
        materialMapper.selectList(new LambdaQueryWrapper<MesMaterial>()
                .select(MesMaterial::getId, MesMaterial::getCode)).forEach(m -> codes.put(m.getId(), m.getCode()));
        return codes;
    }

    // ==================== 删除（引用校验，单条与批量共用） ====================

    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterialChecked(Long id) {
        MesMaterial material = materialMapper.selectById(id);
        if (material == null) {
            throw new ServiceException(1404, "物料不存在");
        }
        if (bomMapper.selectCount(new LambdaQueryWrapper<MesBom>().eq(MesBom::getMaterialId, id)) > 0
                || bomLineMapper.selectCount(new LambdaQueryWrapper<MesBomLine>().eq(MesBomLine::getMaterialId, id)) > 0) {
            throw new ServiceException(1409, "物料已被 BOM 引用，不能删除");
        }
        if (routeMapper.selectCount(new LambdaQueryWrapper<MesRoute>().eq(MesRoute::getMaterialId, id)) > 0) {
            throw new ServiceException(1409, "物料已被工艺路线引用，不能删除");
        }
        if (schemeMapper.selectCount(new LambdaQueryWrapper<MesQcScheme>().eq(MesQcScheme::getMaterialId, id)) > 0) {
            throw new ServiceException(1409, "物料已被检验方案引用，不能删除");
        }
        if (orderMapper.selectCount(new LambdaQueryWrapper<MesOrder>().eq(MesOrder::getProductId, id)) > 0) {
            throw new ServiceException(1409, "物料已被工单引用，不能删除");
        }
        materialMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModelChecked(Long id) {
        MesModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new ServiceException(1404, "建模节点不存在");
        }
        if (modelMapper.selectCount(new LambdaQueryWrapper<MesModel>().eq(MesModel::getParentId, id)) > 0) {
            throw new ServiceException(1409, "存在下级节点，不能删除");
        }
        if (routeStepMapper.selectCount(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getLineId, id)) > 0
                || routeStepMapper.selectCount(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getWorkstationId, id)) > 0) {
            throw new ServiceException(1409, "节点已被工艺路线工序引用，不能删除");
        }
        if (calendarMapper.selectCount(new LambdaQueryWrapper<MesCalendar>().eq(MesCalendar::getLineId, id)) > 0) {
            throw new ServiceException(1409, "节点已被工作日历引用，不能删除");
        }
        if (equipmentMapper.selectCount(new LambdaQueryWrapper<MesEquipment>().eq(MesEquipment::getWorkstationId, id)) > 0) {
            throw new ServiceException(1409, "节点已被设备台账引用，不能删除");
        }
        modelMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBomChecked(Long id) {
        MesBom bom = bomMapper.selectById(id);
        if (bom == null) {
            throw new ServiceException(1404, "BOM 不存在");
        }
        if (orderMapper.selectCount(new LambdaQueryWrapper<MesOrder>().eq(MesOrder::getBomId, id)) > 0) {
            throw new ServiceException(1409, "BOM 已被工单引用，不能删除");
        }
        bomMapper.deleteById(id);
        bomLineMapper.delete(new LambdaQueryWrapper<MesBomLine>().eq(MesBomLine::getBomId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRouteChecked(Long id) {
        MesRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new ServiceException(1404, "工艺路线不存在");
        }
        if (route.getStatus() != null && route.getStatus() == 1) {
            throw new ServiceException(1409, "已发布的工艺路线不能删除，请先停用");
        }
        if (orderMapper.selectCount(new LambdaQueryWrapper<MesOrder>().eq(MesOrder::getRouteId, id)) > 0) {
            throw new ServiceException(1409, "工艺路线已被工单引用，不能删除");
        }
        routeMapper.deleteById(id);
        routeStepMapper.delete(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getRouteId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteEquipmentChecked(Long id) {
        MesEquipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new ServiceException(1404, "设备不存在");
        }
        if (checkPlanMapper.selectCount(new LambdaQueryWrapper<MesCheckPlan>().eq(MesCheckPlan::getEquipmentId, id)) > 0
                || repairMapper.selectCount(new LambdaQueryWrapper<MesRepair>().eq(MesRepair::getEquipmentId, id)) > 0
                || iotConfigMapper.selectCount(new LambdaQueryWrapper<MesIotConfig>().eq(MesIotConfig::getEquipmentId, id)) > 0) {
            throw new ServiceException(1409, "设备已被点检计划/维修工单/数采配置引用，不能删除");
        }
        equipmentMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteQcSchemeChecked(Long id) {
        MesQcScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            throw new ServiceException(1404, "检验方案不存在");
        }
        if (qcOrderMapper.selectCount(new LambdaQueryWrapper<MesQcOrder>().eq(MesQcOrder::getSchemeId, id)) > 0) {
            throw new ServiceException(1409, "检验方案已被检验单引用，不能删除");
        }
        schemeMapper.deleteById(id);
        schemeItemMapper.delete(new LambdaQueryWrapper<MesQcSchemeItem>().eq(MesQcSchemeItem::getSchemeId, id));
    }

    /** 批量删除（部分成功：逐条调用删除方法，失败项收集原因） */
    public BatchOpResult deleteBatch(List<Long> ids, LongConsumer deleteAction) {
        return batchRunner.run(ids, deleteAction);
    }

    public BatchOpResult deleteMaterialsBatch(List<Long> ids) {
        return deleteBatch(ids, this::deleteMaterialChecked);
    }

    public BatchOpResult deleteModelsBatch(List<Long> ids) {
        return deleteBatch(ids, this::deleteModelChecked);
    }

    public BatchOpResult deleteBomsBatch(List<Long> ids) {
        return deleteBatch(ids, this::deleteBomChecked);
    }

    public BatchOpResult deleteRoutesBatch(List<Long> ids) {
        return deleteBatch(ids, this::deleteRouteChecked);
    }

    public BatchOpResult deleteEquipmentsBatch(List<Long> ids) {
        return deleteBatch(ids, this::deleteEquipmentChecked);
    }

    public BatchOpResult deleteQcSchemesBatch(List<Long> ids) {
        return deleteBatch(ids, this::deleteQcSchemeChecked);
    }

    // ==================== 编码唯一查重（新增/编辑共用） ====================

    public void requireMaterialCodeUnique(String code, Long excludeId) {
        MesMaterial exist = materialMapper.selectOne(new LambdaQueryWrapper<MesMaterial>()
                .eq(MesMaterial::getCode, code).last("LIMIT 1"));
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new ServiceException(1409, "物料编码「" + code + "」已存在");
        }
    }

    public void requireModelCodeUnique(String code, Long excludeId) {
        MesModel exist = modelMapper.selectOne(new LambdaQueryWrapper<MesModel>()
                .eq(MesModel::getCode, code).last("LIMIT 1"));
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new ServiceException(1409, "建模编码「" + code + "」已存在");
        }
    }

    public void requireBomCodeUnique(String code, Long excludeId) {
        MesBom exist = bomMapper.selectOne(new LambdaQueryWrapper<MesBom>()
                .eq(MesBom::getCode, code).last("LIMIT 1"));
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new ServiceException(1409, "BOM 编码「" + code + "」已存在");
        }
    }

    public void requireRouteCodeUnique(String code, Long excludeId) {
        MesRoute exist = routeMapper.selectOne(new LambdaQueryWrapper<MesRoute>()
                .eq(MesRoute::getCode, code).last("LIMIT 1"));
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new ServiceException(1409, "路线编码「" + code + "」已存在");
        }
    }

    public void requireEquipmentCodeUnique(String code, Long excludeId) {
        MesEquipment exist = equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getCode, code).last("LIMIT 1"));
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new ServiceException(1409, "设备编码「" + code + "」已存在");
        }
    }

    public void requireQcSchemeCodeUnique(String code, Long excludeId) {
        MesQcScheme exist = schemeMapper.selectOne(new LambdaQueryWrapper<MesQcScheme>()
                .eq(MesQcScheme::getCode, code).last("LIMIT 1"));
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new ServiceException(1409, "方案编码「" + code + "」已存在");
        }
    }

    // ==================== 私有工具 ====================

    /** 分组导入（BOM/路线/检验方案）中带行号的业务异常 */
    private static final class LineException extends RuntimeException {
        private final int lineNo;

        private LineException(int lineNo, String message) {
            super(message);
            this.lineNo = lineNo;
        }
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(400, what + "不能为空");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return trim(value);
    }

    private static String orDefault(String value, String defaultValue) {
        return trim(value) == null ? defaultValue : value.trim();
    }

    /** 枚举中文标签解析：空取默认值（默认值为 null 表示必填），无法识别报错 */
    private static Integer parseLabel(Map<String, Integer> labels, String value, String what, Integer defaultValue) {
        if (value == null || value.isBlank()) {
            if (defaultValue == null) {
                throw new ServiceException(400, what + "不能为空");
            }
            return defaultValue;
        }
        Integer parsed = labels.get(value.trim());
        if (parsed == null) {
            throw new ServiceException(400, what + "「" + value.trim() + "」无法识别");
        }
        return parsed;
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
