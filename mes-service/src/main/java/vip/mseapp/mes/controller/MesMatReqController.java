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
import vip.mseapp.mes.entity.MesMatReq;
import vip.mseapp.mes.mapper.MesMatReqMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;
import vip.mseapp.mes.service.MesMatReqService;

import java.util.List;

/**
 * 领退料：申请、执行（发料/收料）、驳回（支持批量发料/批量驳回）。
 */
@Validated
@RestController
@RequestMapping("/mes/matreq")
public class MesMatReqController {

    private final MesMatReqMapper mapper;
    private final MesMatReqService matReqService;
    private final MesBatchRunner batchRunner;

    public MesMatReqController(MesMatReqMapper mapper, MesMatReqService matReqService, MesBatchRunner batchRunner) {
        this.mapper = mapper;
        this.matReqService = matReqService;
        this.batchRunner = batchRunner;
    }

    @SaCheckPermission(value = "mes:matreq:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesMatReq>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) Integer type,
                                              @RequestParam(required = false) Integer status) {
        Page<MesMatReq> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesMatReq>()
                        .eq(type != null, MesMatReq::getType, type)
                        .eq(status != null, MesMatReq::getStatus, status)
                        .orderByDesc(MesMatReq::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:matreq:create", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/issue-request")
    public Result<Long> create(@RequestBody @Validated ReqRequest request) {
        return Result.ok(matReqService.create(request.orderId(), request.materialId(), request.qty(),
                request.remark()));
    }

    @SaCheckPermission(value = "mes:matreq:return", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/return-request")
    public Result<Long> returnReq(@RequestBody @Validated ReturnRequest request) {
        return Result.ok(matReqService.returnReq(request.orderId(), request.materialId(), request.batchNo(),
                request.qty(), request.remark()));
    }

    /** 发料/收料执行（线边仓台账联动） */
    @SaCheckPermission(value = "mes:matreq:issue", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/issue")
    public Result<Void> issue(@PathVariable Long id) {
        matReqService.issue(id);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:matreq:issue", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        matReqService.reject(id);
        return Result.ok();
    }

    /** 批量发料（部分成功：状态不允许的申请单跳过并报原因） */
    @SaCheckPermission(value = "mes:matreq:issue", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/issue")
    public Result<BatchOpResult> batchIssue(@RequestBody @jakarta.validation.constraints.NotEmpty List<Long> ids) {
        return Result.ok(batchRunner.run(ids, matReqService::issue));
    }

    /** 批量驳回（部分成功） */
    @SaCheckPermission(value = "mes:matreq:issue", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/reject")
    public Result<BatchOpResult> batchReject(@RequestBody @jakarta.validation.constraints.NotEmpty List<Long> ids) {
        return Result.ok(batchRunner.run(ids, matReqService::reject));
    }

    public record ReqRequest(Long orderId, @NotNull Long materialId, @NotNull java.math.BigDecimal qty,
                             String remark) {
    }

    public record ReturnRequest(Long orderId, @NotNull Long materialId, @NotNull String batchNo,
                                @NotNull java.math.BigDecimal qty, String remark) {
    }
}
