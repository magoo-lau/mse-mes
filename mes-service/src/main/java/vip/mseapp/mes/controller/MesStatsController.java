package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.service.MesStatsService;

import java.util.List;
import java.util.Map;

/**
 * 看板与统计：总览指标、产量/质量趋势、设备统计、CSV 导出。
 */
@RestController
@RequestMapping("/mes")
public class MesStatsController {

    private final MesStatsService statsService;

    public MesStatsController(MesStatsService statsService) {
        this.statsService = statsService;
    }

    @SaCheckPermission(value = "mes:dashboard:view", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @SaCheckPermission(value = "mes:stats:query", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/stats/production")
    public Result<List<Map<String, Object>>> production(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.production(days));
    }

    @SaCheckPermission(value = "mes:stats:query", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/stats/quality")
    public Result<List<Map<String, Object>>> quality(@RequestParam(defaultValue = "7") int days,
                                                     @RequestParam(defaultValue = "10") int top) {
        return Result.ok(statsService.quality(days, top));
    }

    @SaCheckPermission(value = "mes:stats:query", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/stats/equipment")
    public Result<List<Map<String, Object>>> equipment(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(statsService.equipment(days));
    }

    /** 报表导出（CSV 文本，type: production/quality/equipment） */
    @SaCheckPermission(value = "mes:stats:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/stats/export")
    public Result<String> export(@RequestParam(defaultValue = "production") String type,
                                 @RequestParam(defaultValue = "30") int days) {
        return Result.ok(statsService.exportCsv(type, days));
    }
}
