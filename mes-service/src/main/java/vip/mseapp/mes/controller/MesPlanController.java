package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesPlan;
import vip.mseapp.mes.mapper.MesPlanMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 排产计划（产线×日期×班次×工单；发布后进入执行视图）。
 */
@Validated
@RestController
@RequestMapping("/mes/plan")
public class MesPlanController {

    private final MesPlanMapper mapper;

    public MesPlanController(MesPlanMapper mapper) {
        this.mapper = mapper;
    }

    /** 按日期区间查询（start/end=yyyy-MM-dd），可按产线/状态过滤 */
    @SaCheckPermission(value = "mes:plan:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/list")
    public Result<List<MesPlan>> list(@RequestParam(required = false) String start,
                                      @RequestParam(required = false) String end,
                                      @RequestParam(required = false) Long lineId,
                                      @RequestParam(required = false) Integer status) {
        // 注意：条件构造器的 condition 只控制 SQL 片段是否拼接，value 实参仍会先求值，日期须判空后再 parse
        LambdaQueryWrapper<MesPlan> wrapper = new LambdaQueryWrapper<MesPlan>()
                .eq(lineId != null, MesPlan::getLineId, lineId)
                .eq(status != null, MesPlan::getStatus, status)
                .orderByAsc(MesPlan::getWorkDate)
                .orderByAsc(MesPlan::getId);
        if (start != null && !start.isBlank()) {
            wrapper.ge(MesPlan::getWorkDate, LocalDate.parse(start));
        }
        if (end != null && !end.isBlank()) {
            wrapper.le(MesPlan::getWorkDate, LocalDate.parse(end));
        }
        return Result.ok(mapper.selectList(wrapper));
    }

    @SaCheckPermission(value = "mes:plan:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> save(@RequestBody @Validated PlanSaveRequest request) {
        MesPlan plan = new MesPlan();
        plan.setOrderId(request.orderId());
        plan.setLineId(request.lineId());
        plan.setWorkDate(LocalDate.parse(request.workDate()));
        plan.setShiftCode(request.shiftCode());
        plan.setPlanQty(request.planQty());
        plan.setStatus(0);
        plan.setRemark(request.remark());
        mapper.insert(plan);
        return Result.ok(plan.getId());
    }

    @SaCheckPermission(value = "mes:plan:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated PlanSaveRequest request) {
        MesPlan plan = mapper.selectById(id);
        if (plan == null) {
            throw new ServiceException(1404, "排产计划不存在");
        }
        if (plan.getStatus() == 1) {
            throw new ServiceException(1409, "已发布的排产不能修改");
        }
        plan.setOrderId(request.orderId());
        plan.setLineId(request.lineId());
        plan.setWorkDate(LocalDate.parse(request.workDate()));
        plan.setShiftCode(request.shiftCode());
        plan.setPlanQty(request.planQty());
        plan.setRemark(request.remark());
        mapper.updateById(plan);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:plan:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        MesPlan plan = mapper.selectById(id);
        if (plan != null && plan.getStatus() == 1) {
            throw new ServiceException(1409, "已发布的排产不能删除");
        }
        mapper.deleteById(id);
        return Result.ok();
    }

    /** 发布（单条或批量）：先全量校验存在性再发布——否则部分发布成功后才抛 1404，
     * 语义变成「部分成功却报整体失败」且已发布者不可回退（2026-10-04 用例 MES-PLAN-008 实测） */
    @SaCheckPermission(value = "mes:plan:publish", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/publish")
    public Result<Void> publish(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            if (mapper.selectById(id) == null) {
                throw new ServiceException(1404, "排产计划不存在：" + id);
            }
        }
        for (Long id : ids) {
            MesPlan plan = mapper.selectById(id);
            plan.setStatus(1);
            mapper.updateById(plan);
        }
        return Result.ok();
    }

    public record PlanSaveRequest(@NotNull Long orderId, @NotNull Long lineId, @NotBlank String workDate,
                                  @NotBlank String shiftCode, @NotNull java.math.BigDecimal planQty, String remark) {
    }
}
