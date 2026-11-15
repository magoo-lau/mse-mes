package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesBatch;
import vip.mseapp.mes.entity.MesDispatch;
import vip.mseapp.mes.entity.MesMatReq;
import vip.mseapp.mes.entity.MesMaterial;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.entity.MesReport;
import vip.mseapp.mes.entity.MesSn;
import vip.mseapp.mes.mapper.MesBatchMapper;
import vip.mseapp.mes.mapper.MesDispatchMapper;
import vip.mseapp.mes.mapper.MesMatReqMapper;
import vip.mseapp.mes.mapper.MesMaterialMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.mapper.MesReportMapper;
import vip.mseapp.mes.mapper.MesSnMapper;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 追溯业务：批次正向（去向）/反向（来源）追溯与序列号绑定。
 *
 * <p>正向：原料批次 → 消耗它的领料单/工单 → 工单产出批次；
 * 反向：产品批次 → 生产工单 → 工序报工明细 + 耗用原料（领料单）。
 */
@Service
public class MesTraceService {

    private static final int DB_IN_BATCH_SIZE = 500;

    private final MesBatchMapper batchMapper;
    private final MesMatReqMapper matReqMapper;
    private final MesOrderMapper orderMapper;
    private final MesMaterialMapper materialMapper;
    private final MesReportMapper reportMapper;
    private final MesDispatchMapper dispatchMapper;
    private final MesSnMapper snMapper;

    public MesTraceService(MesBatchMapper batchMapper, MesMatReqMapper matReqMapper, MesOrderMapper orderMapper,
                           MesMaterialMapper materialMapper, MesReportMapper reportMapper,
                           MesDispatchMapper dispatchMapper, MesSnMapper snMapper) {
        this.batchMapper = batchMapper;
        this.matReqMapper = matReqMapper;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.reportMapper = reportMapper;
        this.dispatchMapper = dispatchMapper;
        this.snMapper = snMapper;
    }

    /** 正向追溯：按批次查去向（被哪些工单领用 → 产出哪些批次） */
    public Map<String, Object> forward(String batchNo) {
        MesBatch batch = requireBatch(batchNo);
        // 领用该批次的领料单 → 工单
        List<MesMatReq> consumedRequests = matReqMapper.selectList(new LambdaQueryWrapper<MesMatReq>()
                .eq(MesMatReq::getBatchNo, batchNo).eq(MesMatReq::getType, MesMatReqService.TYPE_ISSUE));
        // 若为生产批次：该批次所在工单的产出批次（同工单后续产出，粗粒度并列）
        List<MesBatch> batchOutputs = batch.getOrderId() == null ? List.of()
                : batchMapper.selectList(new LambdaQueryWrapper<MesBatch>()
                        .eq(MesBatch::getOrderId, batch.getOrderId()).eq(MesBatch::getSource, 1));
        Set<Long> orderIds = new HashSet<>();
        addIfPresent(orderIds, batch.getOrderId());
        Set<Long> materialIds = new HashSet<>();
        addIfPresent(materialIds, batch.getMaterialId());
        for (MesMatReq req : consumedRequests) {
            addIfPresent(orderIds, req.getOrderId());
            addIfPresent(materialIds, req.getMaterialId());
        }
        for (MesBatch output : batchOutputs) {
            addIfPresent(materialIds, output.getMaterialId());
        }
        Map<Long, MesOrder> orders = loadOrders(orderIds);
        Map<Long, String> materialNames = loadMaterialNames(materialIds);
        Map<String, Object> result = baseBatch(batch, orders, materialNames);
        List<Map<String, Object>> consumed = new ArrayList<>();
        for (MesMatReq req : consumedRequests) {
            MesOrder order = req.getOrderId() == null ? null : orders.get(req.getOrderId());
            consumed.add(node("matReq", req.getCode(), req.getOrderId(), order == null ? null : order.getCode(),
                    req.getMaterialId() == null ? null : materialNames.get(req.getMaterialId()), req.getReqQty()));
        }
        result.put("consumedBy", consumed);
        List<Map<String, Object>> outputNodes = new ArrayList<>();
        for (MesBatch out : batchOutputs) {
            outputNodes.add(node("batch", out.getBatchNo(), out.getOrderId(), null,
                    out.getMaterialId() == null ? null : materialNames.get(out.getMaterialId()), out.getQty()));
        }
        result.put("outputs", outputNodes);
        return result;
    }

    /** 反向追溯：按批次查来源（生产工单 + 工序报工 + 耗用原料批次） */
    public Map<String, Object> backward(String batchNo) {
        MesBatch batch = requireBatch(batchNo);
        MesOrder order = batch.getOrderId() == null ? null
                : loadOrders(Set.of(batch.getOrderId())).get(batch.getOrderId());
        List<MesReport> reports = batch.getOrderId() == null ? List.of()
                : reportMapper.selectList(new LambdaQueryWrapper<MesReport>()
                        .eq(MesReport::getOrderId, batch.getOrderId()).orderByAsc(MesReport::getReportTime));
        List<MesMatReq> materialRequests = batch.getOrderId() == null ? List.of()
                : matReqMapper.selectList(new LambdaQueryWrapper<MesMatReq>()
                        .eq(MesMatReq::getOrderId, batch.getOrderId())
                        .eq(MesMatReq::getType, MesMatReqService.TYPE_ISSUE));
        Set<Long> dispatchIds = new HashSet<>();
        for (MesReport report : reports) {
            addIfPresent(dispatchIds, report.getDispatchId());
        }
        Map<Long, MesDispatch> dispatches = loadDispatches(dispatchIds);
        Set<Long> materialIds = new HashSet<>();
        addIfPresent(materialIds, batch.getMaterialId());
        for (MesMatReq req : materialRequests) {
            addIfPresent(materialIds, req.getMaterialId());
        }
        Map<Long, String> materialNames = loadMaterialNames(materialIds);
        Map<Long, MesOrder> orders = order == null ? Map.of() : Map.of(order.getId(), order);
        Map<String, Object> result = baseBatch(batch, orders, materialNames);
        if (batch.getOrderId() != null) {
            result.put("order", order == null ? null : Map.of(
                    "id", order.getId(), "code", order.getCode(), "planQty", order.getPlanQty(),
                    "okQty", order.getOkQty(), "status", order.getStatus()));
            // 工序报工链（人员/工序/时间）
            List<Map<String, Object>> steps = new ArrayList<>();
            for (MesReport report : reports) {
                MesDispatch dispatch = report.getDispatchId() == null ? null : dispatches.get(report.getDispatchId());
                steps.add(Map.of(
                        "reportCode", report.getCode(),
                        "stepName", dispatch == null ? "-" : String.valueOf(dispatch.getStepName()),
                        "okQty", report.getOkQty(), "ngQty", report.getNgQty(),
                        "workstationId", report.getWorkstationId() == null ? "-" : report.getWorkstationId(),
                        "reportTime", String.valueOf(report.getReportTime())));
            }
            result.put("reports", steps);
        }
        // 耗用原料（领料单）
        List<Map<String, Object>> materials = new ArrayList<>();
        for (MesMatReq req : materialRequests) {
            materials.add(node("matReq", req.getCode(), req.getMaterialId(),
                    req.getMaterialId() == null ? null : materialNames.get(req.getMaterialId()),
                    req.getBatchNo(), req.getReqQty()));
        }
        result.put("materials", materials);
        return result;
    }

    /** 序列号绑定（新建或更新归属） */
    @Transactional(rollbackFor = Exception.class)
    public Long snBind(String sn, Long materialId, Long orderId, String batchNo, Long stepId, Integer status) {
        MesSn exist = snMapper.selectOne(new LambdaQueryWrapper<MesSn>().eq(MesSn::getSn, sn));
        MesSn row = exist == null ? new MesSn() : exist;
        row.setSn(sn);
        row.setMaterialId(materialId);
        row.setOrderId(orderId);
        row.setBatchNo(batchNo);
        row.setStepId(stepId);
        if (row.getStatus() == null) {
            row.setStatus(status == null ? 1 : status);
        } else if (status != null) {
            row.setStatus(status);
        }
        if (exist == null) {
            snMapper.insert(row);
        } else {
            snMapper.updateById(row);
        }
        return row.getId();
    }

    private MesBatch requireBatch(String batchNo) {
        MesBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<MesBatch>()
                .eq(MesBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new ServiceException(1404, "批次不存在：" + batchNo);
        }
        return batch;
    }

    private Map<String, Object> baseBatch(MesBatch batch, Map<Long, MesOrder> orders,
                                         Map<Long, String> materialNames) {
        Map<String, Object> map = new HashMap<>();
        map.put("batchNo", batch.getBatchNo());
        map.put("materialId", batch.getMaterialId());
        map.put("materialName", batch.getMaterialId() == null ? null : materialNames.get(batch.getMaterialId()));
        map.put("source", batch.getSource());
        map.put("orderId", batch.getOrderId());
        MesOrder order = batch.getOrderId() == null ? null : orders.get(batch.getOrderId());
        map.put("orderCode", order == null ? null : order.getCode());
        map.put("qty", batch.getQty());
        return map;
    }

    private Map<String, Object> node(String type, String code, Long refId, String refLabel,
                                     String materialName, Object qty) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("code", code);
        map.put("refId", refId);
        map.put("refLabel", refLabel);
        map.put("materialName", materialName);
        map.put("qty", qty);
        return map;
    }

    private Map<Long, MesOrder> loadOrders(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, MesOrder> result = new HashMap<>();
        List<Long> values = new ArrayList<>(ids);
        for (int from = 0; from < values.size(); from += DB_IN_BATCH_SIZE) {
            List<Long> batch = values.subList(from, Math.min(from + DB_IN_BATCH_SIZE, values.size()));
            for (MesOrder order : orderMapper.selectBatchIds(batch)) {
                result.put(order.getId(), order);
            }
        }
        return result;
    }

    private Map<Long, String> loadMaterialNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        List<Long> values = new ArrayList<>(ids);
        for (int from = 0; from < values.size(); from += DB_IN_BATCH_SIZE) {
            List<Long> batch = values.subList(from, Math.min(from + DB_IN_BATCH_SIZE, values.size()));
            for (MesMaterial material : materialMapper.selectBatchIds(batch)) {
                result.put(material.getId(), material.getName());
            }
        }
        return result;
    }

    private Map<Long, MesDispatch> loadDispatches(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, MesDispatch> result = new HashMap<>();
        List<Long> values = new ArrayList<>(ids);
        for (int from = 0; from < values.size(); from += DB_IN_BATCH_SIZE) {
            List<Long> batch = values.subList(from, Math.min(from + DB_IN_BATCH_SIZE, values.size()));
            for (MesDispatch dispatch : dispatchMapper.selectBatchIds(batch)) {
                result.put(dispatch.getId(), dispatch);
            }
        }
        return result;
    }

    private void addIfPresent(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }
}
