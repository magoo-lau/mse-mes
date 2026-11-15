package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesStock;
import vip.mseapp.mes.entity.MesStockLog;
import vip.mseapp.mes.mapper.MesStockLogMapper;
import vip.mseapp.mes.mapper.MesStockMapper;
import vip.mseapp.mes.service.MesStockService;

/**
 * 线边仓库存：台账查询、调整、流水。
 */
@Validated
@RestController
@RequestMapping("/mes/stock")
public class MesStockController {

    private final MesStockMapper stockMapper;
    private final MesStockLogMapper logMapper;
    private final MesStockService stockService;

    public MesStockController(MesStockMapper stockMapper, MesStockLogMapper logMapper, MesStockService stockService) {
        this.stockMapper = stockMapper;
        this.logMapper = logMapper;
        this.stockService = stockService;
    }

    @SaCheckPermission(value = "mes:stock:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesStock>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) Long materialId) {
        Page<MesStock> page = stockMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesStock>()
                        .eq(materialId != null, MesStock::getMaterialId, materialId)
                        .orderByAsc(MesStock::getMaterialId)
                        .orderByAsc(MesStock::getBatchNo));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 手工调整（盘盈/盘亏，changeQty 正负） */
    @SaCheckPermission(value = "mes:stock:adjust", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/adjust")
    public Result<Void> adjust(@RequestBody @Validated AdjustRequest request) {
        stockService.change(request.materialId(), request.batchNo(), request.changeQty(),
                MesStockService.REF_ADJUST, null, request.remark());
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:stock:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/log/page")
    public Result<PageResult<MesStockLog>> logPage(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) Long materialId) {
        Page<MesStockLog> page = logMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesStockLog>()
                        .eq(materialId != null, MesStockLog::getMaterialId, materialId)
                        .orderByDesc(MesStockLog::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    public record AdjustRequest(@NotNull Long materialId, String batchNo,
                                @NotNull java.math.BigDecimal changeQty, String remark) {
    }
}
