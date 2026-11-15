package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
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
import vip.mseapp.mes.entity.MesIntegrationLog;
import vip.mseapp.mes.integration.MesIntegrationService;

import java.util.List;
import java.util.Map;

/**
 * 系统集成管理（二期，菜单 511）：ERP 入站同步配置读写（4478/4479）与同步日志查询（4481）。
 * 入站同步执行 = ERP 经 mse-open 推送（开放能力）；出站生产结果回传已退役（2026-10-20），
 * 工单完工通知经框架通知中枢 (mes,ORDER_FINISHED) 群发（原手动执行/重推入口一并退役）。
 */
@RestController
@Validated
@RequestMapping("/mes/integration")
public class MesIntegrationController {

    private final MesIntegrationService integrationService;

    public MesIntegrationController(MesIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    /** 集成配置查询（ERP 入站同步开关） */
    @SaCheckPermission(value = "mes:integration:config:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/config")
    public Result<List<Map<String, Object>>> config() {
        return Result.ok(integrationService.configMap());
    }

    /** 集成配置保存 */
    @SaCheckPermission(value = "mes:integration:config:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/config")
    public Result<Void> saveConfig(@RequestBody @Valid ConfigSaveRequest request) {
        integrationService.saveConfig(request.type(), request.enabled(), request.remark());
        return Result.ok();
    }

    /** 同步日志分页查询 */
    @SaCheckPermission(value = "mes:integration:log:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/log/page")
    public Result<PageResult<MesIntegrationLog>> pageLogs(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                          @RequestParam(required = false) String direction,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) String bizCode) {
        return Result.ok(integrationService.pageLogs(pageNum, pageSize, direction, type, status, bizCode));
    }

    public record ConfigSaveRequest(@NotBlank(message = "类型不能为空") String type,
                                    @NotNull(message = "启用状态不能为空") Integer enabled,
                                    String remark) {
    }
}
