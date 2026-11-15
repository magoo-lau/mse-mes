package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
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
import vip.mseapp.mes.entity.MesQcException;
import vip.mseapp.mes.mapper.MesQcExceptionMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;
import vip.mseapp.mes.service.MesQcService;

import java.util.List;

/**
 * 质量异常单：登记/处理/关闭（含批量关闭）。
 */
@Validated
@RestController
@RequestMapping("/mes/qc/exception")
public class MesQcExceptionController {

    private final MesQcExceptionMapper mapper;
    private final MesQcService qcService;
    private final MesBatchRunner batchRunner;

    public MesQcExceptionController(MesQcExceptionMapper mapper, MesQcService qcService, MesBatchRunner batchRunner) {
        this.mapper = mapper;
        this.qcService = qcService;
        this.batchRunner = batchRunner;
    }

    @SaCheckPermission(value = "mes:qc:exception:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesQcException>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) Integer status) {
        Page<MesQcException> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesQcException>()
                        .eq(status != null, MesQcException::getStatus, status)
                        .orderByDesc(MesQcException::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 登记/更新处理信息 */
    @SaCheckPermission(value = "mes:qc:exception:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> save(@RequestBody @Validated ExceptionRequest request) {
        return Result.ok(qcService.saveException(request.id(), request.title(), request.level(),
                request.orderId(), request.description(), request.causeAnalysis(), request.action(),
                request.status()));
    }

    @SaCheckPermission(value = "mes:qc:exception:close", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        qcService.closeException(id);
        return Result.ok();
    }

    /** 批量关闭（部分成功：状态不允许的异常单跳过并报原因） */
    @SaCheckPermission(value = "mes:qc:exception:close", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/close")
    public Result<BatchOpResult> batchClose(@RequestBody @jakarta.validation.constraints.NotEmpty List<Long> ids) {
        return Result.ok(batchRunner.run(ids, qcService::closeException));
    }

    public record ExceptionRequest(Long id, @NotBlank String title, @NotNull Integer level, Long orderId,
                                   String description, String causeAnalysis, String action, Integer status) {
    }
}
