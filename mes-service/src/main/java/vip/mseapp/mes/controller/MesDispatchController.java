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
import vip.mseapp.mes.entity.MesDispatch;
import vip.mseapp.mes.mapper.MesDispatchMapper;
import vip.mseapp.mes.service.MesDispatchService;

/**
 * 派工：派工单查询、指派（产线/工位/操作工）、工序转移。
 */
@Validated
@RestController
@RequestMapping("/mes/dispatch")
public class MesDispatchController {

    private final MesDispatchMapper mapper;
    private final MesDispatchService dispatchService;

    public MesDispatchController(MesDispatchMapper mapper, MesDispatchService dispatchService) {
        this.mapper = mapper;
        this.dispatchService = dispatchService;
    }

    @SaCheckPermission(value = "mes:dispatch:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesDispatch>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) Long orderId,
                                                @RequestParam(required = false) Long lineId,
                                                @RequestParam(required = false) Integer status) {
        Page<MesDispatch> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesDispatch>()
                        .eq(orderId != null, MesDispatch::getOrderId, orderId)
                        .eq(lineId != null, MesDispatch::getLineId, lineId)
                        .eq(status != null, MesDispatch::getStatus, status)
                        .orderByDesc(MesDispatch::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 派工指派 */
    @SaCheckPermission(value = "mes:dispatch:assign", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestBody @Validated AssignRequest request) {
        dispatchService.assign(id, request.lineId(), request.workstationId(), request.assigneeId());
        return Result.ok();
    }

    /** 工序转移 */
    @SaCheckPermission(value = "mes:dispatch:transfer", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/transfer")
    public Result<Void> transfer(@RequestBody @Validated TransferRequest request) {
        dispatchService.transfer(request.fromDispatchId(), request.toDispatchId(), request.qty(), request.remark());
        return Result.ok();
    }

    public record AssignRequest(Long lineId, Long workstationId, Long assigneeId) {
    }

    public record TransferRequest(@NotNull Long fromDispatchId, @NotNull Long toDispatchId,
                                  @NotNull java.math.BigDecimal qty, String remark) {
    }
}
