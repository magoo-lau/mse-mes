package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesDefect;
import vip.mseapp.mes.entity.MesDispatch;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.entity.MesQcOrder;
import vip.mseapp.mes.entity.MesReport;
import vip.mseapp.mes.entity.MesRouteStep;
import vip.mseapp.mes.mapper.MesDefectMapper;
import vip.mseapp.mes.mapper.MesDispatchMapper;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.mapper.MesQcOrderMapper;
import vip.mseapp.mes.mapper.MesReportMapper;
import vip.mseapp.mes.mapper.MesRouteStepMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mes.integration.MesOrderFinishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报工业务：提交即生效（累计派工单/工单数量、质检点自动生成 IPQC 检验单、产出批次与线边仓入库），
 * 审核驳回/撤销时数量回冲。
 */
@Service
public class MesReportService {

    /** 报工状态：0 待审核 1 已审核 2 已驳回 */
    public static final int ST_PENDING = 0, ST_AUDITED = 1, ST_REJECTED = 2;

    private final MesReportMapper reportMapper;
    private final MesOrderMapper orderMapper;
    private final MesDispatchMapper dispatchMapper;
    private final MesRouteStepMapper routeStepMapper;
    private final MesQcOrderMapper qcOrderMapper;
    private final MesDefectMapper defectMapper;
    private final MesStockService stockService;
    private final MesCodeGenerator codeGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public MesReportService(MesReportMapper reportMapper, MesOrderMapper orderMapper,
                            MesDispatchMapper dispatchMapper, MesRouteStepMapper routeStepMapper,
                            MesQcOrderMapper qcOrderMapper, MesDefectMapper defectMapper,
                            MesStockService stockService, MesCodeGenerator codeGenerator,
                            ApplicationEventPublisher eventPublisher) {
        this.reportMapper = reportMapper;
        this.orderMapper = orderMapper;
        this.dispatchMapper = dispatchMapper;
        this.routeStepMapper = routeStepMapper;
        this.qcOrderMapper = qcOrderMapper;
        this.defectMapper = defectMapper;
        this.stockService = stockService;
        this.codeGenerator = codeGenerator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 报工提交。
     *
     * @param type 1 正常报工（数量计入工单产出）、2 返工报工（返修合格计入合格数，不再计完工数）
     */
    @Transactional(rollbackFor = Exception.class)
    public MesReport submit(Long orderId, Long dispatchId, Long workstationId, int type,
                            BigDecimal okQty, BigDecimal ngQty, String defectReason) {
        if (okQty == null || ngQty == null || okQty.signum() < 0 || ngQty.signum() < 0
                || okQty.add(ngQty).signum() == 0) {
            throw new ServiceException(1401, "合格/不良数量必须不小于 0 且合计大于 0");
        }
        MesOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getStatus() != MesOrderService.ST_RELEASED
                && order.getStatus() != MesOrderService.ST_PRODUCING)) {
            throw new ServiceException(1409, "工单不存在或未下达");
        }
        MesDispatch dispatch = null;
        if (dispatchId != null) {
            dispatch = dispatchMapper.selectById(dispatchId);
            if (dispatch == null || !dispatch.getOrderId().equals(orderId)) {
                throw new ServiceException(1409, "派工单与工单不匹配");
            }
            if (dispatch.getStatus() == MesDispatchService.ST_DONE) {
                throw new ServiceException(1409, "该工序派工单已完成");
            }
        }
        BigDecimal total = okQty.add(ngQty);
        LocalDateTime now = LocalDateTime.now();
        String batchNo = null;

        // 1. 报工记录
        MesReport report = new MesReport();
        report.setCode(codeGenerator.next("MR"));
        report.setOrderId(orderId);
        report.setDispatchId(dispatchId);
        report.setStepId(dispatch == null ? null : dispatch.getStepId());
        report.setWorkstationId(workstationId);
        report.setReporterId(UserContext.getUserId());
        report.setType(type);
        report.setOkQty(okQty);
        report.setNgQty(ngQty);
        report.setDefectReason(ngQty.signum() > 0 ? defectReason : null);
        report.setReportTime(now);
        report.setStatus(ST_PENDING);
        reportMapper.insert(report);

        // 2. 数量生效：派工单累计 + 工单累计；不良自动生成待处置单
        if (dispatch != null) {
            if (dispatch.getStatus() == MesDispatchService.ST_WAITING) {
                dispatch.setStatus(MesDispatchService.ST_RUNNING);
                dispatch.setStartTime(now);
            }
            dispatch.setReportedQty(dispatch.getReportedQty().add(total));
            if (dispatch.getReportedQty().compareTo(dispatch.getPlanQty()) >= 0) {
                dispatch.setStatus(MesDispatchService.ST_DONE);
                dispatch.setFinishTime(now);
            }
            dispatchMapper.updateById(dispatch);
        }
        applyToOrder(order, okQty, ngQty, type == 2);
        if (ngQty.signum() > 0) {
            MesDefect defect = new MesDefect();
            defect.setOrderId(orderId);
            defect.setMaterialId(order.getProductId());
            defect.setQty(ngQty);
            defect.setReason(defectReason);
            defect.setStatus(0);
            defectMapper.insert(defect);
        }

        // 3. 质检点：报工自动生成 IPQC 检验单
        if (dispatch != null && dispatch.getStepId() != null) {
            MesRouteStep step = routeStepMapper.selectById(dispatch.getStepId());
            if (step != null && step.getQcRequired() != null && step.getQcRequired() == 1) {
                MesQcOrder qc = new MesQcOrder();
                qc.setCode(codeGenerator.next("QC"));
                qc.setQcType(2);
                qc.setOrderId(orderId);
                qc.setStepId(dispatch.getStepId());
                qc.setReportId(report.getId());
                qc.setMaterialId(order.getProductId());
                qc.setSampleQty(total);
                qc.setResult(0);
                qc.setStatus(0);
                qcOrderMapper.insert(qc);
            }
        }

        // 4. 产出：合格品累计生产批次并入线边仓
        if (okQty.signum() > 0 && type == 1) {
            batchNo = stockService.accumulateBatch(order.getProductId(), orderId, okQty, null);
            stockService.change(order.getProductId(), batchNo, okQty, MesStockService.REF_PRODUCE,
                    report.getId(), "报工产出 " + report.getCode());
        }
        return report;
    }

    /** 审核：通过置已审核；驳回置已驳回并回冲数量 */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String remark) {
        MesReport report = require(id);
        if (report.getStatus() != ST_PENDING) {
            throw new ServiceException(1409, "该报工单不在待审核状态");
        }
        report.setStatus(pass ? ST_AUDITED : ST_REJECTED);
        report.setAuditBy(UserContext.getUserId());
        report.setAuditTime(LocalDateTime.now());
        report.setAuditRemark(remark);
        reportMapper.updateById(report);
        if (!pass) {
            reverse(report);
        }
    }

    /** 撤销：待审核/已审核报工可撤销（数量回冲后逻辑删除） */
    @Transactional(rollbackFor = Exception.class)
    public void undo(Long id) {
        MesReport report = require(id);
        if (report.getStatus() == ST_REJECTED) {
            throw new ServiceException(1409, "已驳回的报工单无需撤销");
        }
        reverse(report);
        reportMapper.deleteById(id);
    }

    /** 数量回冲：派工单与工单扣回报工数量，已完工工单退回生产中 */
    private void reverse(MesReport report) {
        BigDecimal total = report.getOkQty().add(report.getNgQty());
        if (report.getDispatchId() != null) {
            MesDispatch dispatch = dispatchMapper.selectById(report.getDispatchId());
            if (dispatch != null) {
                dispatch.setReportedQty(dispatch.getReportedQty().subtract(total).max(BigDecimal.ZERO));
                if (dispatch.getStatus() == MesDispatchService.ST_DONE) {
                    dispatch.setStatus(MesDispatchService.ST_RUNNING);
                    dispatch.setFinishTime(null);
                }
                dispatchMapper.updateById(dispatch);
            }
        }
        MesOrder order = orderMapper.selectById(report.getOrderId());
        if (order != null) {
            boolean rework = report.getType() != null && report.getType() == 2;
            applyToOrder(order, report.getOkQty().negate(), report.getNgQty().negate(), rework);
            if (order.getStatus() == MesOrderService.ST_FINISHED) {
                order.setStatus(MesOrderService.ST_PRODUCING);
                order.setFinishTime(null);
                orderMapper.updateById(order);
            }
        }
    }

    /** 工单数量累计（回冲传负值）；正常报工累计完工数，返工报工只转合格 */
    private void applyToOrder(MesOrder order, BigDecimal ok, BigDecimal ng, boolean rework) {
        order.setOkQty(order.getOkQty().add(ok));
        order.setNgQty(order.getNgQty().add(ng));
        if (!rework) {
            order.setFinishedQty(order.getFinishedQty().add(ok.add(ng)));
        }
        if (order.getStatus() == MesOrderService.ST_RELEASED) {
            order.setStatus(MesOrderService.ST_PRODUCING);
        }
        // 全部数量回冲为 0 且无在制派工时保持原状态；正常报工达计划数即完工（发布完工事件：二期生产结果回传钩子）
        if (order.getStatus() == MesOrderService.ST_PRODUCING
                && order.getFinishedQty().compareTo(order.getPlanQty()) >= 0) {
            order.setStatus(MesOrderService.ST_FINISHED);
            order.setFinishTime(LocalDateTime.now());
            eventPublisher.publishEvent(new MesOrderFinishedEvent(order));
        }
        orderMapper.updateById(order);
    }

    /** 返工处理：为不良数量生成返工派工单（同工序重做，rework=1） */
    @Transactional(rollbackFor = Exception.class)
    public Long rework(Long orderId, Long stepId, java.math.BigDecimal qty, String reason) {
        MesOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getStatus() != MesOrderService.ST_RELEASED
                && order.getStatus() != MesOrderService.ST_PRODUCING)) {
            throw new ServiceException(1409, "工单不存在或未下达");
        }
        MesDispatch source = stepId == null ? null : dispatchMapper.selectOne(
                new LambdaQueryWrapper<MesDispatch>()
                        .eq(MesDispatch::getOrderId, orderId)
                        .eq(MesDispatch::getStepId, stepId)
                        .last("LIMIT 1"));
        MesDispatch dispatch = new MesDispatch();
        dispatch.setOrderId(orderId);
        dispatch.setStepId(stepId);
        dispatch.setStepNo(source == null ? 99 : source.getStepNo());
        dispatch.setStepName((source == null ? "返工" : source.getStepName()) + "（返工）");
        dispatch.setLineId(source == null ? null : source.getLineId());
        dispatch.setWorkstationId(source == null ? null : source.getWorkstationId());
        dispatch.setPlanQty(qty);
        dispatch.setReportedQty(java.math.BigDecimal.ZERO);
        dispatch.setStatus(MesDispatchService.ST_WAITING);
        dispatch.setRework(1);
        dispatchMapper.insert(dispatch);
        return dispatch.getId();
    }

    private MesReport require(Long id) {
        MesReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new ServiceException(1404, "报工单不存在");
        }
        return report;
    }
}
