package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesReport;
import vip.mseapp.mes.mapper.MesReportMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;
import vip.mseapp.mes.service.MesReportService;

import java.util.List;

/**
 * 报工工作台：扫码/手工报工、记录查询、审核（含批量审核）、撤销。
 */
@Validated
@RestController
@RequestMapping("/mes/report")
public class MesReportController {

    private final MesReportMapper mapper;
    private final MesReportService reportService;
    private final MesBatchRunner batchRunner;

    public MesReportController(MesReportMapper mapper, MesReportService reportService, MesBatchRunner batchRunner) {
        this.mapper = mapper;
        this.reportService = reportService;
        this.batchRunner = batchRunner;
    }

    /** 报工提交（type: 1 正常 2 返工；dispatchId 对扫码场景由工单/派工定位） */
    @SaCheckPermission(value = "mes:report:do", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<MesReport> submit(@RequestBody @Validated ReportRequest request) {
        return Result.ok(reportService.submit(request.orderId(), request.dispatchId(), request.workstationId(),
                request.type() == null ? 1 : request.type(), request.okQty(), request.ngQty(),
                request.defectReason()));
    }

    @SaCheckPermission(value = "mes:report:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesReport>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) Long orderId,
                                              @RequestParam(required = false) Integer status) {
        Page<MesReport> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesReport>()
                        .eq(orderId != null, MesReport::getOrderId, orderId)
                        .eq(status != null, MesReport::getStatus, status)
                        .orderByDesc(MesReport::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 审核（pass=false 驳回并回冲数量） */
    @SaCheckPermission(value = "mes:report:audit", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody AuditRequest request) {
        reportService.audit(id, request.pass(), request.remark());
        return Result.ok();
    }

    /** 批量审核（同一决定逐条执行，部分成功：状态不允许的记录跳过并报原因） */
    @SaCheckPermission(value = "mes:report:audit", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/audit")
    public Result<BatchOpResult> batchAudit(@RequestBody @Validated BatchAuditRequest request) {
        return Result.ok(batchRunner.run(request.ids(),
                id -> reportService.audit(id, request.pass(), request.remark())));
    }

    /** 撤销（数量回冲后删除） */
    @SaCheckPermission(value = "mes:report:undo", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/undo")
    public Result<Void> undo(@PathVariable Long id) {
        reportService.undo(id);
        return Result.ok();
    }

    /** 返工处理：按工序生成返工派工单 */
    @SaCheckPermission(value = "mes:rework:do", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/rework")
    public Result<Long> rework(@RequestBody @Validated ReworkRequest request) {
        return Result.ok(reportService.rework(request.orderId(), request.stepId(), request.qty(),
                request.reason()));
    }

    public record ReportRequest(@NotNull Long orderId, Long dispatchId, Long workstationId, Integer type,
                                @NotNull java.math.BigDecimal okQty, @NotNull java.math.BigDecimal ngQty,
                                String defectReason) {
    }

    public record AuditRequest(boolean pass, String remark) {
    }

    public record BatchAuditRequest(boolean pass, String remark,
                                    @jakarta.validation.constraints.NotEmpty List<Long> ids) {
    }

    public record ReworkRequest(@NotNull Long orderId, Long stepId, @NotNull java.math.BigDecimal qty,
                                String reason) {
    }
}
