package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesDispatch;
import vip.mseapp.mes.entity.MesTransfer;
import vip.mseapp.mes.mapper.MesDispatchMapper;
import vip.mseapp.mes.mapper.MesTransferMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 派工与工序转移业务。
 */
@Service
public class MesDispatchService {

    /** 状态常量：0 待开工 1 生产中 2 已完成 */
    public static final int ST_WAITING = 0, ST_RUNNING = 1, ST_DONE = 2;

    private final MesDispatchMapper dispatchMapper;
    private final MesTransferMapper transferMapper;

    public MesDispatchService(MesDispatchMapper dispatchMapper, MesTransferMapper transferMapper) {
        this.dispatchMapper = dispatchMapper;
        this.transferMapper = transferMapper;
    }

    /** 派工：指定产线/工位/操作工 */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, Long lineId, Long workstationId, Long assigneeId) {
        MesDispatch dispatch = require(id);
        if (dispatch.getStatus() == ST_DONE) {
            throw new ServiceException(1409, "派工单已完成，不能重新派工");
        }
        dispatch.setLineId(lineId);
        dispatch.setWorkstationId(workstationId);
        dispatch.setAssigneeId(assigneeId);
        dispatchMapper.updateById(dispatch);
    }

    /** 工序转移：从源派工单转移数量至目标派工单（同工单、目标未完成） */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromDispatchId, Long toDispatchId, BigDecimal qty, String remark) {
        MesDispatch from = require(fromDispatchId);
        MesDispatch to = require(toDispatchId);
        if (!from.getOrderId().equals(to.getOrderId())) {
            throw new ServiceException(1409, "只能同一工单内转移工序");
        }
        if (from.getStatus() != null && from.getStatus() == ST_DONE) {
            throw new ServiceException(1409, "源工序已完成");
        }
        if (to.getStatus() == ST_DONE) {
            throw new ServiceException(1409, "目标工序已完成");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new ServiceException(1401, "转移数量必须大于 0");
        }
        // 转移量不得超过源工序可用量（计划量 - 已报工量）：否则转移留痕与实际产出守恒被打破
        java.math.BigDecimal available = from.getPlanQty() == null ? null
                : from.getPlanQty().subtract(from.getReportedQty() == null ? java.math.BigDecimal.ZERO : from.getReportedQty());
        if (available != null && qty.compareTo(available) > 0) {
            throw new ServiceException(1409, "转移数量超过源工序可用数量（可用 " + available + "）");
        }
        MesTransfer record = new MesTransfer();
        record.setOrderId(from.getOrderId());
        record.setFromDispatchId(from.getId());
        record.setToDispatchId(to.getId());
        record.setQty(qty);
        record.setOperatorId(UserContext.getUserId());
        record.setTransferTime(LocalDateTime.now());
        record.setRemark(remark);
        transferMapper.insert(record);
        if (to.getStatus() == ST_WAITING) {
            to.setStatus(ST_RUNNING);
            if (to.getStartTime() == null) {
                to.setStartTime(LocalDateTime.now());
            }
            dispatchMapper.updateById(to);
        }
    }

    private MesDispatch require(Long id) {
        MesDispatch dispatch = dispatchMapper.selectById(id);
        if (dispatch == null) {
            throw new ServiceException(1404, "派工单不存在");
        }
        return dispatch;
    }

    /** 按工单查派工单（升序按工序号） */
    public List<MesDispatch> listByOrder(Long orderId) {
        return dispatchMapper.selectList(new LambdaQueryWrapper<MesDispatch>()
                .eq(MesDispatch::getOrderId, orderId)
                .orderByAsc(MesDispatch::getStepNo)
                .orderByAsc(MesDispatch::getId));
    }
}
