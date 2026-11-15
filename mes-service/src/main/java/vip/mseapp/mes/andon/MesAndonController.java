package vip.mseapp.mes.andon;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
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
import vip.mseapp.mes.entity.MesAndonCall;

import java.util.List;
import java.util.Map;

/**
 * 安灯管理（三期，菜单 513）：呼叫（4483 mes:andon:call）、响应/关闭（4484 mes:andon:handle）。
 * 权限规划仅 4483/4484 两个点（docs/02 第七节）：看板/记录/统计挂响应权限 4484（管理与调度角色），
 * 现场呼叫入口挂 4483；AI 安灯摘要 4486 独立（MesAiController）。
 */
@RestController
@Validated
@RequestMapping("/mes/andon")
public class MesAndonController {

    private final MesAndonService andonService;

    public MesAndonController(MesAndonService andonService) {
        this.andonService = andonService;
    }

    /** 发起呼叫（设备/质量/物料） */
    @SaCheckPermission(value = "mes:andon:call", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/call")
    public Result<MesAndonCall> call(@RequestBody @Valid CallRequest request) {
        return Result.ok(andonService.call(request.type(), request.lineId(), request.stationId(),
                request.description()));
    }

    /** 响应（待响应 → 处理中） */
    @SaCheckPermission(value = "mes:andon:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/respond")
    public Result<Void> respond(@org.springframework.web.bind.annotation.PathVariable Long id) {
        andonService.respond(id);
        return Result.ok();
    }

    /** 关闭（记录处理结果） */
    @SaCheckPermission(value = "mes:andon:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/close")
    public Result<Void> close(@org.springframework.web.bind.annotation.PathVariable Long id,
                              @RequestBody(required = false) CloseRequest request) {
        andonService.close(id, request == null ? null : request.handleResult());
        return Result.ok();
    }

    /** 活跃呼叫看板（按升级级别倒序；前端按 level 高亮 0 绿 / 1 黄 / 2 红） */
    @SaCheckPermission(value = "mes:andon:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/active")
    public Result<List<MesAndonCall>> active() {
        return Result.ok(andonService.active());
    }

    /** 呼叫记录分页 */
    @SaCheckPermission(value = "mes:andon:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesAndonCall>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) Integer type,
                                                 @RequestParam(required = false) Integer status) {
        return Result.ok(andonService.page(pageNum, pageSize, type, status));
    }

    /** 安灯统计（近 N 天） */
    @SaCheckPermission(value = "mes:andon:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(andonService.stats(days));
    }

    /** 手动触发超时升级扫描（验证/兜底入口；后台 daemon 每 30 秒自动扫） */
    @SaCheckPermission(value = "mes:andon:handle", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/escalate/run")
    public Result<Integer> escalateRun() {
        return Result.ok(andonService.escalateDue());
    }

    public record CallRequest(@NotNull(message = "呼叫类型不能为空") Integer type,
                              Long lineId, Long stationId, String description) {
    }

    public record CloseRequest(String handleResult) {
    }
}
