package vip.mseapp.mes.spc;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import vip.mseapp.mes.entity.MesSpcItem;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * SPC 统计过程控制（三期，菜单 514）：权限点 4485 mes:spc:list 挂 514，覆盖监控项维护、
 * 样本录入与控制图查询（页面级权限，docs/02 第七节）。
 */
@RestController
@Validated
@RequestMapping("/mes/spc")
public class MesSpcController {

    private final MesSpcService spcService;
    private final MesBatchRunner batchRunner;

    public MesSpcController(MesSpcService spcService, MesBatchRunner batchRunner) {
        this.spcService = spcService;
        this.batchRunner = batchRunner;
    }

    /** 监控项分页 */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/item/page")
    public Result<PageResult<MesSpcItem>> pageItems(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize,
                                                    @RequestParam(required = false) String keyword) {
        return Result.ok(spcService.pageItems(pageNum, pageSize, keyword));
    }

    /** 启用监控项清单（选择器） */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/item/list")
    public Result<List<MesSpcItem>> listItems() {
        return Result.ok(spcService.listItems());
    }

    /** 新增/编辑监控项 */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/item")
    public Result<Long> saveItem(@RequestBody @Valid ItemSaveRequest request) {
        return Result.ok(spcService.saveItem(request.id(), request.code(), request.name(), request.materialId(),
                request.stepName(), request.featureName(), request.unit(), request.usl(), request.lsl(),
                request.target(), request.sampleSize(), request.status(), request.remark()));
    }

    /** 删除监控项（连同样本） */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        spcService.deleteItem(id);
        return Result.ok();
    }

    /** 批量删除监控项（连同样本，部分成功：不存在的项跳过并报原因） */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/item/batch-delete")
    public Result<BatchOpResult> deleteItemBatch(@RequestBody @NotEmpty List<Long> ids) {
        return Result.ok(batchRunner.run(ids, spcService::deleteItem));
    }

    /** 批量录入样本测量值 */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/sample")
    public Result<Integer> addSamples(@RequestBody @Valid SampleAddRequest request) {
        return Result.ok(spcService.addSamples(request.itemId(), request.values(), request.orderCode(),
                request.source(), request.collectTime()));
    }

    /** 控制图（X̄-R + 控制限 + 超限标记 + Cp/Cpk） */
    @SaCheckPermission(value = "mes:spc:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/chart")
    public Result<Map<String, Object>> chart(@RequestParam Long itemId,
                                             @RequestParam(defaultValue = "30") int days) {
        return Result.ok(spcService.chart(itemId, days));
    }

    public record ItemSaveRequest(Long id, String code, String name, Long materialId, String stepName,
                                  String featureName, String unit, BigDecimal usl, BigDecimal lsl,
                                  BigDecimal target, Integer sampleSize, Integer status, String remark) {
    }

    public record SampleAddRequest(@NotNull(message = "监控项不能为空") Long itemId,
                                   @NotEmpty(message = "测量值不能为空") List<BigDecimal> values,
                                   String orderCode, Integer source, java.time.LocalDateTime collectTime) {
    }
}
