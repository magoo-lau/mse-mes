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
import vip.mseapp.mes.entity.MesHalt;
import vip.mseapp.mes.mapper.MesHaltMapper;
import vip.mseapp.mes.service.MesExecService;

import java.util.List;

/**
 * 生产执行视图：进度、在制品、异常停工。
 */
@Validated
@RestController
@RequestMapping("/mes/exec")
public class MesExecController {

    private final MesExecService execService;
    private final MesHaltMapper haltMapper;

    public MesExecController(MesExecService execService, MesHaltMapper haltMapper) {
        this.execService = execService;
        this.haltMapper = haltMapper;
    }

    @SaCheckPermission(value = "mes:progress:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/progress")
    public Result<List<MesExecService.OrderProgress>> progress(@RequestParam(required = false) Long orderId) {
        return Result.ok(execService.progress(orderId));
    }

    @SaCheckPermission(value = "mes:wip:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/wip")
    public Result<List<MesExecService.WipRow>> wip(@RequestParam(required = false) Long lineId) {
        return Result.ok(execService.wip(lineId));
    }

    @SaCheckPermission(value = "mes:halt:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/halt/list")
    public Result<List<MesHalt>> halts() {
        return Result.ok(haltMapper.selectList(null));
    }

    @SaCheckPermission(value = "mes:halt:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/halt/page")
    public Result<PageResult<MesHalt>> pageHalts(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize) {
        Page<MesHalt> page = haltMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesHalt>().orderByDesc(MesHalt::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:halt:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/halt")
    public Result<Long> haltSave(@RequestBody @Validated HaltRequest request) {
        return Result.ok(execService.haltSave(request.orderId(), request.lineId(), request.reason(), request.remark()));
    }

    @SaCheckPermission(value = "mes:halt:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/halt/{id}/recover")
    public Result<Void> haltRecover(@PathVariable Long id) {
        execService.haltRecover(id);
        return Result.ok();
    }

    public record HaltRequest(Long orderId, Long lineId, @NotNull Integer reason, String remark) {
    }
}
