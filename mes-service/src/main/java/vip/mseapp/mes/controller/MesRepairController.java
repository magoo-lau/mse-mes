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
import vip.mseapp.mes.entity.MesRepair;
import vip.mseapp.mes.mapper.MesRepairMapper;
import vip.mseapp.mes.service.MesEquipmentService;

/**
 * 设备维修工单：报修、受理、完工。
 */
@Validated
@RestController
@RequestMapping("/mes/repair")
public class MesRepairController {

    private final MesRepairMapper mapper;
    private final MesEquipmentService equipmentService;

    public MesRepairController(MesRepairMapper mapper, MesEquipmentService equipmentService) {
        this.mapper = mapper;
        this.equipmentService = equipmentService;
    }

    @SaCheckPermission(value = "mes:repair:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesRepair>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) Long equipmentId,
                                              @RequestParam(required = false) Integer status) {
        Page<MesRepair> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesRepair>()
                        .eq(equipmentId != null, MesRepair::getEquipmentId, equipmentId)
                        .eq(status != null, MesRepair::getStatus, status)
                        .orderByDesc(MesRepair::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:repair:create", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> create(@RequestBody @Validated CreateRequest request) {
        return Result.ok(equipmentService.repairReport(request.equipmentId(), request.faultDesc()));
    }

    @SaCheckPermission(value = "mes:repair:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/take")
    public Result<Void> take(@PathVariable Long id, @RequestBody(required = false) TakeRequest request) {
        equipmentService.repairTake(id, request == null ? null : request.repairBy());
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:repair:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @RequestBody(required = false) FinishRequest request) {
        equipmentService.repairFinish(id, request == null ? null : request.handleDesc());
        return Result.ok();
    }

    public record CreateRequest(@NotNull Long equipmentId, @NotBlank String faultDesc) {
    }

    public record TakeRequest(Long repairBy) {
    }

    public record FinishRequest(String handleDesc) {
    }
}
