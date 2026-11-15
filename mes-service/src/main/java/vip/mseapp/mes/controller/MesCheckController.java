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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesCheckPlan;
import vip.mseapp.mes.entity.MesCheckRecord;
import vip.mseapp.mes.mapper.MesCheckPlanMapper;
import vip.mseapp.mes.mapper.MesCheckRecordMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;
import vip.mseapp.mes.service.MesEquipmentService;

import java.time.LocalDate;
import java.util.List;

/**
 * 点检保养：计划维护与执行记录。
 */
@Validated
@RestController
@RequestMapping("/mes/check")
public class MesCheckController {

    private final MesCheckPlanMapper planMapper;
    private final MesCheckRecordMapper recordMapper;
    private final MesEquipmentService equipmentService;
    private final MesBatchRunner batchRunner;

    public MesCheckController(MesCheckPlanMapper planMapper, MesCheckRecordMapper recordMapper,
                              MesEquipmentService equipmentService, MesBatchRunner batchRunner) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.equipmentService = equipmentService;
        this.batchRunner = batchRunner;
    }

    @SaCheckPermission(value = "mes:check:plan:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/plan/page")
    public Result<PageResult<MesCheckPlan>> planPage(@RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize,
                                                     @RequestParam(required = false) Long equipmentId) {
        Page<MesCheckPlan> page = planMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesCheckPlan>()
                        .eq(equipmentId != null, MesCheckPlan::getEquipmentId, equipmentId)
                        .orderByDesc(MesCheckPlan::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:check:plan:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/plan")
    public Result<Long> savePlan(@RequestBody @Validated PlanRequest request) {
        MesCheckPlan plan = new MesCheckPlan();
        plan.setName(request.name());
        plan.setEquipmentId(request.equipmentId());
        plan.setType(request.type());
        plan.setCycle(request.cycle());
        plan.setNextDate(request.nextDate() == null || request.nextDate().isBlank()
                ? null : LocalDate.parse(request.nextDate()));
        plan.setContent(request.content());
        plan.setStatus(1);
        planMapper.insert(plan);
        return Result.ok(plan.getId());
    }

    @SaCheckPermission(value = "mes:check:plan:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/plan/{id}/status/{status}")
    public Result<Void> planStatus(@PathVariable Long id, @PathVariable Integer status) {
        MesCheckPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new ServiceException(1404, "点检保养计划不存在");
        }
        plan.setStatus(status);
        planMapper.updateById(plan);
        return Result.ok();
    }

    /** 批量启停（status 同单条语义；部分成功：不存在的计划跳过并报原因） */
    @SaCheckPermission(value = "mes:check:plan:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/plan/batch/status/{status}")
    public Result<BatchOpResult> planStatusBatch(@PathVariable Integer status,
                                                 @RequestBody @jakarta.validation.constraints.NotEmpty List<Long> ids) {
        return Result.ok(batchRunner.run(ids, id -> {
            MesCheckPlan plan = planMapper.selectById(id);
            if (plan == null) {
                throw new ServiceException(1404, "点检保养计划不存在");
            }
            plan.setStatus(status);
            planMapper.updateById(plan);
        }));
    }

    @SaCheckPermission(value = "mes:check:plan:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/record/page")
    public Result<PageResult<MesCheckRecord>> recordPage(@RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "10") long pageSize,
                                                         @RequestParam(required = false) Long equipmentId) {
        Page<MesCheckRecord> page = recordMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesCheckRecord>()
                        .eq(equipmentId != null, MesCheckRecord::getEquipmentId, equipmentId)
                        .orderByDesc(MesCheckRecord::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 点检/保养执行（异常自动转报修） */
    @SaCheckPermission(value = "mes:check:submit", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/record")
    public Result<Void> submit(@RequestBody @Validated RecordRequest request) {
        equipmentService.checkSubmit(request.planId(), request.equipmentId(), request.result(), request.remark());
        return Result.ok();
    }

    public record PlanRequest(@NotBlank String name, @NotNull Long equipmentId, @NotNull Integer type,
                              @NotNull Integer cycle, String nextDate, String content) {
    }

    public record RecordRequest(Long planId, @NotNull Long equipmentId, @NotNull Integer result, String remark) {
    }
}
