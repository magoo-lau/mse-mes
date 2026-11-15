package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesDispatch;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.entity.MesRouteStep;
import vip.mseapp.mes.mapper.MesDispatchMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.mapper.MesRouteStepMapper;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产订单业务：状态流转（草稿→提交→下达→生产中→完工/关闭/取消）与下达展开派工。
 *
 * <p>下达按工艺路线工序展开生成派工单（无路线则生成单条无工序派工单）。
 */
@Service
public class MesOrderService {

    /** 状态常量：0 草稿 1 已提交 2 已下达 3 生产中 4 完工 5 已关闭 6 已取消 */
    public static final int ST_DRAFT = 0, ST_SUBMITTED = 1, ST_RELEASED = 2, ST_PRODUCING = 3,
            ST_FINISHED = 4, ST_CLOSED = 5, ST_CANCELLED = 6;

    private final MesOrderMapper orderMapper;
    private final MesRouteStepMapper routeStepMapper;
    private final MesDispatchMapper dispatchMapper;
    private final MesCodeGenerator codeGenerator;

    public MesOrderService(MesOrderMapper orderMapper, MesRouteStepMapper routeStepMapper,
                           MesDispatchMapper dispatchMapper, MesCodeGenerator codeGenerator) {
        this.orderMapper = orderMapper;
        this.routeStepMapper = routeStepMapper;
        this.dispatchMapper = dispatchMapper;
        this.codeGenerator = codeGenerator;
    }

    @Transactional(rollbackFor = Exception.class)
    public MesOrder create(Long productId, Long bomId, Long routeId, BigDecimal planQty, LocalDate planStart,
                           LocalDate planEnd, String remark) {
        if (productId == null || planQty == null || planQty.signum() <= 0) {
            throw new ServiceException(1401, "产品与计划数量必填且数量大于 0");
        }
        MesOrder order = new MesOrder();
        order.setCode(codeGenerator.next("MO"));
        order.setProductId(productId);
        order.setBomId(bomId);
        order.setRouteId(routeId);
        order.setPlanQty(planQty);
        order.setFinishedQty(BigDecimal.ZERO);
        order.setOkQty(BigDecimal.ZERO);
        order.setNgQty(BigDecimal.ZERO);
        order.setPlanStart(planStart);
        order.setPlanEnd(planEnd);
        order.setStatus(ST_DRAFT);
        order.setRemark(remark);
        orderMapper.insert(order);
        return order;
    }

    /** 更新草稿工单的基础字段 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Long productId, Long bomId, Long routeId, BigDecimal planQty,
                       LocalDate planStart, LocalDate planEnd, String remark) {
        MesOrder order = requireInStatus(id, "修改", ST_DRAFT);
        if (productId != null) {
            order.setProductId(productId);
        }
        order.setBomId(bomId);
        order.setRouteId(routeId);
        if (planQty != null) {
            order.setPlanQty(planQty);
        }
        order.setPlanStart(planStart);
        order.setPlanEnd(planEnd);
        order.setRemark(remark);
        orderMapper.updateById(order);
    }

    /** 提交：草稿 → 已提交（下达前置步骤，便于计划确认） */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        MesOrder order = requireInStatus(id, "提交", ST_DRAFT);
        order.setStatus(ST_SUBMITTED);
        orderMapper.updateById(order);
    }

    /** 下达：已提交 → 已下达，按工艺路线展开派工单 */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        MesOrder order = requireInStatus(id, "下达", ST_SUBMITTED);
        List<MesRouteStep> steps = order.getRouteId() == null ? List.of()
                : routeStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                        .eq(MesRouteStep::getRouteId, order.getRouteId())
                        .orderByAsc(MesRouteStep::getStepNo));
        if (steps.isEmpty()) {
            insertDispatch(order, null, null, null);
        } else {
            steps.forEach(step -> insertDispatch(order, step, step.getLineId(), step.getWorkstationId()));
        }
        order.setStatus(ST_RELEASED);
        order.setReleaseTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private void insertDispatch(MesOrder order, MesRouteStep step, Long lineId, Long workstationId) {
        MesDispatch dispatch = new MesDispatch();
        dispatch.setOrderId(order.getId());
        if (step != null) {
            dispatch.setStepId(step.getId());
            dispatch.setStepNo(step.getStepNo());
            dispatch.setStepName(step.getStepName());
        }
        dispatch.setLineId(lineId);
        dispatch.setWorkstationId(workstationId);
        dispatch.setPlanQty(order.getPlanQty());
        dispatch.setReportedQty(BigDecimal.ZERO);
        dispatch.setStatus(MesDispatchService.ST_WAITING);
        dispatch.setRework(0);
        dispatchMapper.insert(dispatch);
    }

    /** 取消：草稿/已提交可取消 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MesOrder order = requireInStatus(id, "取消", ST_DRAFT, ST_SUBMITTED);
        order.setStatus(ST_CANCELLED);
        orderMapper.updateById(order);
    }

    /** 关闭：已下达/生产中可手工关闭（报工完工会自动完工关闭） */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        MesOrder order = requireInStatus(id, "关闭", ST_RELEASED, ST_PRODUCING);
        order.setStatus(ST_CLOSED);
        orderMapper.updateById(order);
    }

    /** 校验订单处于给定状态之一，否则抛业务异常 */
    private MesOrder requireInStatus(Long id, String action, int... statuses) {
        MesOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(1404, "工单不存在");
        }
        for (int s : statuses) {
            if (order.getStatus() == s) {
                return order;
            }
        }
        throw new ServiceException(1409, "工单当前状态不允许「" + action + "」");
    }
}
