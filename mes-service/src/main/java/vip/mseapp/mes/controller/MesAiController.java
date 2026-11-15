package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
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
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.ai.MesAiAgentMapService;
import vip.mseapp.mes.ai.MesAiService;

import java.util.List;

/**
 * MES AI 入口（二期）：全部场景经 MES 后端转发到 mse-ai 平台/直连模型——权限校验与审计收敛在 MES 侧，
 * 操作工等现场角色无需 mse-ai 侧权限。AI 结果均为草稿/建议，前端统一标注「AI 生成，仅供参考」。
 *
 * <p>权限点（docs/02 第五节）：日报 4471（挂 509）、质量诊断 4472（挂 505）、设备诊断 4473（挂 506）、
 * 问数 4474（挂 509）、排产建议 4475（挂 502）、SOP 问答 4476（挂 504）、设置管理 4477（挂 510）。
 */
@RestController
@Validated
@RequestMapping("/mes/ai")
public class MesAiController {

    private final MesAiService aiService;
    private final MesAiAgentMapService agentMapService;

    public MesAiController(MesAiService aiService, MesAiAgentMapService agentMapService) {
        this.aiService = aiService;
        this.agentMapService = agentMapService;
    }

    /** AI 能力状态：模式/两条通道可用性（前端按钮置灰与提示，登录即可查） */
    @SaCheckLogin(type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/capability")
    public Result<MesAiService.AiCapability> capability() {
        return Result.ok(aiService.capability());
    }

    /** AI 生产日报/周报草稿生成（近 N 天，默认 1） */
    @SaCheckPermission(value = "mes:ai:report", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/daily-report")
    public Result<String> dailyReport(@RequestParam(defaultValue = "1") int days) {
        return Result.ok(aiService.dailyReport(days));
    }

    /** AI 质量异常诊断 */
    @SaCheckPermission(value = "mes:ai:quality-diag", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/quality-diag")
    public Result<String> qualityDiag(@RequestBody @Valid DiagRequest request) {
        return Result.ok(aiService.qualityDiag(request.id()));
    }

    /** AI 设备故障诊断 */
    @SaCheckPermission(value = "mes:ai:equip-diag", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/equip-diag")
    public Result<String> equipDiag(@RequestBody @Valid DiagRequest request) {
        return Result.ok(aiService.equipDiag(request.id()));
    }

    /** AI 智能问数（自然语言查生产数据，mes_query 反向工具受控供数，仅平台通道） */
    @SaCheckPermission(value = "mes:ai:data-qa", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/data-qa")
    public Result<String> dataQa(@RequestBody @Valid QaRequest request) {
        return Result.ok(aiService.dataQa(request.question()));
    }

    /** AI 排产建议草稿（JSON 数组：产线×日期×班次×工单；人工确认后经排产保存接口生效） */
    @SaCheckPermission(value = "mes:ai:schedule", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/schedule-advise")
    public Result<String> scheduleAdvise() {
        return Result.ok(aiService.scheduleAdvise());
    }

    /** AI 工艺/SOP 知识问答 */
    @SaCheckPermission(value = "mes:ai:kb-qa", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/kb-qa")
    public Result<String> kbQa(@RequestBody @Valid QaRequest request) {
        return Result.ok(aiService.kbQa(request.question()));
    }

    /** AI 安灯异常摘要（三期，挂 513） */
    @SaCheckPermission(value = "mes:ai:andon-summary", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/andon-summary")
    public Result<String> andonSummary(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(aiService.andonSummary(days));
    }

    /** AI SPC 判读（三期，挂 514） */
    @SaCheckPermission(value = "mes:ai:spc-interpret", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/spc-interpret")
    public Result<String> spcInterpret(@RequestBody @Valid DiagRequest request,
                                       @RequestParam(defaultValue = "30") int days) {
        return Result.ok(aiService.spcInterpret(request.id(), days));
    }

    /** AI 智能化设置：场景下拉选项（mse-ai 已发布智能体；平台未接入回退内置默认项） */
    @SaCheckPermission(value = "mes:ai:config", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/agent-options")
    public Result<MesAiAgentMapService.AgentOptions> agentOptions() {
        return Result.ok(agentMapService.options());
    }

    /** AI 智能化设置：六场景当前映射（configuredCode=null 未设置走内置默认） */
    @SaCheckPermission(value = "mes:ai:config", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/agent-map")
    public Result<List<MesAiAgentMapService.SceneMap>> agentMap() {
        return Result.ok(agentMapService.map());
    }

    /** AI 智能化设置：保存场景映射（agentCode 空=恢复内置默认，保存即生效） */
    @SaCheckPermission(value = "mes:ai:config", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/agent-map")
    public Result<Void> saveAgentMap(@RequestBody @Valid AgentMapSaveRequest request) {
        agentMapService.save(request.scene(), request.agentCode());
        return Result.ok();
    }

    public record DiagRequest(@NotNull(message = "单据 id 不能为空") Long id) {
    }

    public record QaRequest(@NotBlank(message = "问题不能为空") String question) {
    }

    public record AgentMapSaveRequest(@NotBlank(message = "场景不能为空") String scene, String agentCode) {
    }
}
