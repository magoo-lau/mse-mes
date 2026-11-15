package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;
import vip.mseapp.mes.service.MesDispatchService;
import vip.mseapp.mes.service.MesOrderService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 生产订单（工单）：CRUD + 提交/下达/取消/关闭状态流转 + 批量状态流转。
 */
@Validated
@RestController
@RequestMapping("/mes/order")
public class MesOrderController {

    private final MesOrderMapper orderMapper;
    private final MesOrderService orderService;
    private final MesDispatchService dispatchService;
    private final MesBatchRunner batchRunner;

    public MesOrderController(MesOrderMapper orderMapper, MesOrderService orderService,
                              MesDispatchService dispatchService, MesBatchRunner batchRunner) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.dispatchService = dispatchService;
        this.batchRunner = batchRunner;
    }

    @SaCheckPermission(value = "mes:order:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesOrder>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        Page<MesOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesOrder>()
                        .and(keyword != null && !keyword.isBlank(),
                                w -> w.like(MesOrder::getCode, keyword))
                        .eq(status != null, MesOrder::getStatus, status)
                        .orderByDesc(MesOrder::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 详情（含派工单/工序进度） */
    @SaCheckPermission(value = "mes:order:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        MesOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(1404, "工单不存在");
        }
        return Result.ok(Map.of("order", order, "dispatches", dispatchService.listByOrder(id)));
    }

    /** 工单选择列表（排产等下拉用；statuses 逗号分隔，如 1,2=已提交/已下达；空=全部） */
    @SaCheckPermission(value = "mes:order:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/list")
    public Result<List<MesOrder>> list(@RequestParam(required = false) String statuses) {
        List<Integer> statusList = statuses == null || statuses.isBlank() ? List.of()
                : java.util.Arrays.stream(statuses.split(",")).map(String::trim).map(Integer::valueOf).toList();
        return Result.ok(orderMapper.selectList(new LambdaQueryWrapper<MesOrder>()
                .in(!statusList.isEmpty(), MesOrder::getStatus, statusList)
                .orderByDesc(MesOrder::getId)));
    }

    @SaCheckPermission(value = "mes:order:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<MesOrder> create(@RequestBody @Validated OrderSaveRequest request) {
        return Result.ok(orderService.create(request.productId(), request.bomId(), request.routeId(),
                request.planQty(), request.planStartDate(), request.planEndDate(), request.remark()));
    }

    @SaCheckPermission(value = "mes:order:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated OrderSaveRequest request) {
        orderService.update(id, request.productId(), request.bomId(), request.routeId(),
                request.planQty(), request.planStartDate(), request.planEndDate(), request.remark());
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:order:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        MesOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException(1404, "工单不存在");
        }
        if (order.getStatus() != MesOrderService.ST_DRAFT) {
            throw new ServiceException(1409, "仅草稿工单可删除");
        }
        orderMapper.deleteById(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：仅草稿可删，非草稿跳过并报原因） */
    @SaCheckPermission(value = "mes:order:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(batchRunner.run(ids, id -> {
            MesOrder order = orderMapper.selectById(id);
            if (order == null) {
                throw new ServiceException(1404, "工单不存在");
            }
            if (order.getStatus() != MesOrderService.ST_DRAFT) {
                throw new ServiceException(1409, "仅草稿工单可删除");
            }
            orderMapper.deleteById(id);
        }));
    }

    @SaCheckPermission(value = "mes:order:submit", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        orderService.submit(id);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:order:release", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        orderService.release(id);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:order:cancel", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:order:close", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        orderService.close(id);
        return Result.ok();
    }

    // -------------------- 批量状态流转（部分成功：状态不允许的单据跳过并报原因） --------------------

    @SaCheckPermission(value = "mes:order:submit", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/submit")
    public Result<BatchOpResult> batchSubmit(@RequestBody List<Long> ids) {
        return Result.ok(batchRunner.run(ids, orderService::submit));
    }

    @SaCheckPermission(value = "mes:order:release", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/release")
    public Result<BatchOpResult> batchRelease(@RequestBody List<Long> ids) {
        return Result.ok(batchRunner.run(ids, orderService::release));
    }

    @SaCheckPermission(value = "mes:order:cancel", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/cancel")
    public Result<BatchOpResult> batchCancel(@RequestBody List<Long> ids) {
        return Result.ok(batchRunner.run(ids, orderService::cancel));
    }

    @SaCheckPermission(value = "mes:order:close", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch/close")
    public Result<BatchOpResult> batchClose(@RequestBody List<Long> ids) {
        return Result.ok(batchRunner.run(ids, orderService::close));
    }

    public record OrderSaveRequest(@NotNull Long productId, Long bomId, Long routeId, BigDecimal planQty,
                                   String planStart, String planEnd, String remark) {
        /** yyyy-MM-dd 字符串转日期（空归 null），供 service 使用 */
        public LocalDate planStartDate() {
            return parseDate(planStart);
        }

        public LocalDate planEndDate() {
            return parseDate(planEnd);
        }

        private static LocalDate parseDate(String value) {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        }
    }
}
