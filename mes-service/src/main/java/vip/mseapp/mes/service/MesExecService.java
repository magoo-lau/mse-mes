package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesDispatch;
import vip.mseapp.mes.entity.MesHalt;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.mapper.MesDispatchMapper;
import vip.mseapp.mes.mapper.MesHaltMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产执行业务：工单进度、在制品（WIP）、异常停工登记/恢复。
 */
@Service
public class MesExecService {

    private static final int DB_IN_BATCH_SIZE = 500;

    private final MesOrderMapper orderMapper;
    private final MesDispatchMapper dispatchMapper;
    private final MesHaltMapper haltMapper;

    public MesExecService(MesOrderMapper orderMapper, MesDispatchMapper dispatchMapper, MesHaltMapper haltMapper) {
        this.orderMapper = orderMapper;
        this.dispatchMapper = dispatchMapper;
        this.haltMapper = haltMapper;
    }

    /** 工单进度：工单汇总 + 工序级明细（orderId 为空取全部在制/已下达工单） */
    public List<OrderProgress> progress(Long orderId) {
        LambdaQueryWrapper<MesOrder> wrapper = new LambdaQueryWrapper<MesOrder>()
                .in(MesOrder::getStatus, MesOrderService.ST_RELEASED, MesOrderService.ST_PRODUCING)
                .orderByDesc(MesOrder::getId);
        if (orderId != null) {
            wrapper.eq(MesOrder::getId, orderId);
        }
        List<MesOrder> orders = orderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(MesOrder::getId).toList();
        Map<Long, List<StepProgress>> stepsByOrder = new HashMap<>();
        for (int from = 0; from < orderIds.size(); from += DB_IN_BATCH_SIZE) {
            List<Long> batch = orderIds.subList(from, Math.min(from + DB_IN_BATCH_SIZE, orderIds.size()));
            for (MesDispatch dispatch : dispatchMapper.selectList(new LambdaQueryWrapper<MesDispatch>()
                    .in(MesDispatch::getOrderId, batch)
                    .orderByAsc(MesDispatch::getOrderId)
                    .orderByAsc(MesDispatch::getStepNo))) {
                stepsByOrder.computeIfAbsent(dispatch.getOrderId(), ignored -> new ArrayList<>())
                        .add(new StepProgress(dispatch.getId(), dispatch.getStepNo(), dispatch.getStepName(),
                                dispatch.getPlanQty(), dispatch.getReportedQty(), dispatch.getStatus(),
                                dispatch.getLineId(), dispatch.getWorkstationId()));
            }
        }
        List<OrderProgress> result = new ArrayList<>(orders.size());
        for (MesOrder order : orders) {
            List<StepProgress> steps = stepsByOrder.getOrDefault(order.getId(), List.of());
            result.add(new OrderProgress(order.getId(), order.getCode(), order.getProductId(), order.getPlanQty(),
                    order.getFinishedQty(), order.getOkQty(), order.getNgQty(), order.getStatus(), steps));
        }
        return result;
    }

    /** 在制品：按工单×工序汇总未完成数量（派工数-已报工数） */
    public List<WipRow> wip(Long lineId) {
        List<MesDispatch> dispatches = dispatchMapper.selectList(new LambdaQueryWrapper<MesDispatch>()
                .in(MesDispatch::getStatus, MesDispatchService.ST_WAITING, MesDispatchService.ST_RUNNING)
                .eq(lineId != null, MesDispatch::getLineId, lineId));
        List<Long> orderIds = dispatches.stream().map(MesDispatch::getOrderId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> orderCodes = new HashMap<>();
        for (int from = 0; from < orderIds.size(); from += DB_IN_BATCH_SIZE) {
            List<Long> batch = orderIds.subList(from, Math.min(from + DB_IN_BATCH_SIZE, orderIds.size()));
            for (MesOrder order : orderMapper.selectList(new LambdaQueryWrapper<MesOrder>()
                    .select(MesOrder::getId, MesOrder::getCode)
                    .in(MesOrder::getId, batch))) {
                orderCodes.put(order.getId(), order.getCode());
            }
        }
        Map<String, WipRow> grouped = new LinkedHashMap<>();
        for (MesDispatch d : dispatches) {
            String key = d.getOrderId() + "#" + (d.getStepId() == null ? 0 : d.getStepId());
            BigDecimal remain = d.getPlanQty().subtract(d.getReportedQty()).max(BigDecimal.ZERO);
            WipRow row = grouped.computeIfAbsent(key,
                    k -> new WipRow(d.getOrderId(), d.getOrderId() == null ? null : orderCodes.get(d.getOrderId()),
                            d.getStepId(), d.getStepNo(), d.getStepName(), d.getLineId()));
            row.accumulate(remain);
        }
        return new ArrayList<>(grouped.values());
    }

    /** 停工登记 */
    @Transactional(rollbackFor = Exception.class)
    public Long haltSave(Long orderId, Long lineId, Integer reason, String remark) {
        MesHalt halt = new MesHalt();
        halt.setOrderId(orderId);
        halt.setLineId(lineId);
        halt.setReason(reason);
        halt.setStartTime(LocalDateTime.now());
        halt.setReporterId(UserContext.getUserId());
        halt.setRemark(remark);
        haltMapper.insert(halt);
        return halt.getId();
    }

    /** 停工恢复 */
    @Transactional(rollbackFor = Exception.class)
    public void haltRecover(Long id) {
        MesHalt halt = haltMapper.selectById(id);
        if (halt == null) {
            throw new ServiceException(1404, "停工记录不存在");
        }
        if (halt.getEndTime() != null) {
            throw new ServiceException(1409, "该停工已恢复");
        }
        halt.setEndTime(LocalDateTime.now());
        haltMapper.updateById(halt);
    }

    /** 工单进度（工单汇总 + 工序明细） */
    public record OrderProgress(Long orderId, String code, Long productId, java.math.BigDecimal planQty,
                                java.math.BigDecimal finishedQty, java.math.BigDecimal okQty,
                                java.math.BigDecimal ngQty, Integer status, List<StepProgress> steps) {
    }

    /** 工序进度行 */
    public record StepProgress(Long dispatchId, Integer stepNo, String stepName, java.math.BigDecimal planQty,
                               java.math.BigDecimal reportedQty, Integer status, Long lineId, Long workstationId) {
    }

    /** 在制品行（可变聚合数量） */
    public static final class WipRow {
        private final Long orderId;
        private final String orderCode;
        private final Long stepId;
        private final Integer stepNo;
        private final String stepName;
        private final Long lineId;
        private BigDecimal remainQty;

        WipRow(Long orderId, String orderCode, Long stepId, Integer stepNo, String stepName, Long lineId) {
            this.orderId = orderId;
            this.orderCode = orderCode;
            this.stepId = stepId;
            this.stepNo = stepNo;
            this.stepName = stepName;
            this.lineId = lineId;
            this.remainQty = BigDecimal.ZERO;
        }

        void accumulate(BigDecimal qty) {
            this.remainQty = this.remainQty.add(qty);
        }

        public Long getOrderId() { return orderId; }

        public String getOrderCode() { return orderCode; }

        public Long getStepId() { return stepId; }

        public Integer getStepNo() { return stepNo; }

        public String getStepName() { return stepName; }

        public Long getLineId() { return lineId; }

        public BigDecimal getRemainQty() { return remainQty; }
    }
}
