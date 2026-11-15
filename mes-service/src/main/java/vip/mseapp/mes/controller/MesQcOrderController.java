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
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesQcOrder;
import vip.mseapp.mes.mapper.MesQcOrderMapper;
import vip.mseapp.mes.service.MesQcService;

/**
 * 检验单：查询与检验执行（结果录入）。
 */
@Validated
@RestController
@RequestMapping("/mes/qc/order")
public class MesQcOrderController {

    private final MesQcOrderMapper mapper;
    private final MesQcService qcService;

    public MesQcOrderController(MesQcOrderMapper mapper, MesQcService qcService) {
        this.mapper = mapper;
        this.qcService = qcService;
    }

    @SaCheckPermission(value = "mes:qc:inspect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesQcOrder>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) Integer qcType,
                                               @RequestParam(required = false) Integer status) {
        Page<MesQcOrder> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesQcOrder>()
                        .eq(qcType != null, MesQcOrder::getQcType, qcType)
                        .eq(status != null, MesQcOrder::getStatus, status)
                        .orderByDesc(MesQcOrder::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:qc:inspect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<MesQcOrder> get(@PathVariable Long id) {
        MesQcOrder order = mapper.selectById(id);
        if (order == null) {
            throw new ServiceException(1404, "检验单不存在");
        }
        return Result.ok(order);
    }

    /** 检验结果提交（不良自动生成待处置单） */
    @SaCheckPermission(value = "mes:qc:inspect:submit", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, @RequestBody @Validated InspectRequest request) {
        qcService.inspectSubmit(id, request.okQty(), request.ngQty(), request.result(), request.inspectData());
        return Result.ok();
    }

    public record InspectRequest(@NotNull java.math.BigDecimal okQty, @NotNull java.math.BigDecimal ngQty,
                                 Integer result, String inspectData) {
    }
}
