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
import vip.mseapp.mes.entity.MesDefect;
import vip.mseapp.mes.mapper.MesDefectMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;
import vip.mseapp.mes.service.MesQcService;

import java.util.List;

/**
 * 不良品处置：查询与处置（返工/报废/让步接收，支持同处置方式批量）。
 */
@Validated
@RestController
@RequestMapping("/mes/qc/defect")
public class MesDefectController {

    private final MesDefectMapper mapper;
    private final MesQcService qcService;
    private final MesBatchRunner batchRunner;

    public MesDefectController(MesDefectMapper mapper, MesQcService qcService, MesBatchRunner batchRunner) {
        this.mapper = mapper;
        this.qcService = qcService;
        this.batchRunner = batchRunner;
    }

    @SaCheckPermission(value = "mes:qc:defect:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesDefect>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) Integer status) {
        Page<MesDefect> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesDefect>()
                        .eq(status != null, MesDefect::getStatus, status)
                        .orderByDesc(MesDefect::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 处置（dispose: 1 返工 2 报废 3 让步接收） */
    @SaCheckPermission(value = "mes:qc:defect:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody @Validated HandleRequest request) {
        qcService.defectHandle(id, request.dispose(), request.disposeRemark());
        return Result.ok();
    }

    /** 批量处置（同一处置方式逐条执行，部分成功：已处置的记录跳过并报原因） */
    @SaCheckPermission(value = "mes:qc:defect:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/handle")
    public Result<BatchOpResult> batchHandle(@RequestBody @Validated BatchHandleRequest request) {
        return Result.ok(batchRunner.run(request.ids(),
                id -> qcService.defectHandle(id, request.dispose(), request.disposeRemark())));
    }

    public record HandleRequest(@NotNull Integer dispose, String disposeRemark) {
    }

    public record BatchHandleRequest(@NotNull Integer dispose, String disposeRemark,
                                     @jakarta.validation.constraints.NotEmpty List<Long> ids) {
    }
}
