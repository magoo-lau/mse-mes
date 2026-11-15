package vip.mseapp.mes.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.ai.api.RemoteAiAgentService;
import vip.mseapp.ai.api.dto.AiAgentOption;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mes.ai.MesAiRpcConfiguration.MesAiAgentHolder;
import vip.mseapp.mes.mapper.MesAiAgentMapMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 场景 → 智能体映射：MES「AI 智能化设置」页维护（租户隔离，存 {@link MesAiAgentMap}）。
 *
 * <p>解析链：mes_ai_agent_map（本租户，空值=未设置）→ {@link MesAiProperties}（yaml mse.mes.ai.*
 * 兜底 → 代码内置默认）。下拉选项经 mse-ai 智能体选项契约（RemoteAiAgentService）拉取当前租户
 * 可见的已发布智能体；mse-ai 未接入时回退内置默认项（此时 AI 服务通道本就不可用，映射暂不生效）。
 * 无缓存，保存即生效；映射仅作用于 AI 服务模式链路（直连模式不走智能体）。
 */
@Service
public class MesAiAgentMapService {

    private static final Logger log = LoggerFactory.getLogger(MesAiAgentMapService.class);

    /** 全部合法场景（与 {@link MesAiAgentMap} 场景常量一致） */
    private static final List<String> SCENES = List.of(MesAiAgentMap.SCENE_REPORT, MesAiAgentMap.SCENE_QUALITY,
            MesAiAgentMap.SCENE_EQUIP, MesAiAgentMap.SCENE_DATA_QA, MesAiAgentMap.SCENE_SCHEDULE,
            MesAiAgentMap.SCENE_KB, MesAiAgentMap.SCENE_ANDON, MesAiAgentMap.SCENE_SPC);

    private final ObjectProvider<MesAiAgentHolder> agentHolder;
    private final MesAiAgentMapMapper mapMapper;
    private final MesAiProperties properties;

    public MesAiAgentMapService(ObjectProvider<MesAiAgentHolder> agentHolder,
                                MesAiAgentMapMapper mapMapper, MesAiProperties properties) {
        this.agentHolder = agentHolder;
        this.mapMapper = mapMapper;
        this.properties = properties;
    }

    /** 场景 → 当前生效的智能体编码（运行时入口：映射行非空用行值，否则内置默认） */
    public String resolveAgentCode(String scene) {
        MesAiAgentMap row = mapMapper.selectOne(new LambdaQueryWrapper<MesAiAgentMap>()
                .eq(MesAiAgentMap::getScene, scene));
        if (row != null && row.getAgentCode() != null && !row.getAgentCode().isBlank()) {
            return row.getAgentCode().trim();
        }
        return defaultCode(scene);
    }

    /** 下拉选项：mse-ai 可达返回真实已发布智能体；不可达时回退内置默认项（platformAvailable=false 标记） */
    public AgentOptions options() {
        List<AiAgentOption> remote = fetchRemoteOptions();
        if (remote != null) {
            return new AgentOptions(true, remote);
        }
        return new AgentOptions(false, builtinOptions());
    }

    /** 当前映射（六场景全量）：configuredCode=null 表示未设置（走内置默认） */
    public List<SceneMap> map() {
        Map<String, String> configured = new LinkedHashMap<>();
        mapMapper.selectList(new LambdaQueryWrapper<MesAiAgentMap>().in(MesAiAgentMap::getScene, SCENES))
                .forEach(row -> {
                    if (row.getAgentCode() != null && !row.getAgentCode().isBlank()) {
                        configured.putIfAbsent(row.getScene(), row.getAgentCode().trim());
                    }
                });
        return SCENES.stream()
                .map(scene -> new SceneMap(scene, configured.get(scene),
                        configured.getOrDefault(scene, defaultCode(scene)), defaultCode(scene)))
                .toList();
    }

    /** 保存映射：agentCode 空 = 恢复内置默认（存空串，不删行）；AI 服务可达时校验编码真实存在 */
    @Transactional(rollbackFor = Exception.class)
    public void save(String scene, String agentCode) {
        if (!SCENES.contains(scene)) {
            throw new ServiceException(400, "未知场景: " + scene);
        }
        String code = agentCode == null ? "" : agentCode.trim();
        if (!code.isEmpty()) {
            List<AiAgentOption> remote = fetchRemoteOptions();
            if (remote != null && remote.stream().noneMatch(o -> code.equals(o.getAgentCode()))) {
                throw new ServiceException(400, "智能体不存在或未发布: " + code);
            }
        }
        MesAiAgentMap row = mapMapper.selectOne(new LambdaQueryWrapper<MesAiAgentMap>()
                .eq(MesAiAgentMap::getScene, scene));
        if (row == null) {
            row = new MesAiAgentMap();
            row.setScene(scene);
            row.setAgentCode(code);
            mapMapper.insert(row);
        } else {
            row.setAgentCode(code);
            mapMapper.updateById(row);
        }
    }

    /** 经选项契约拉取 mse-ai 已发布智能体；未接入/解析失败返回 null（调用方自行回退） */
    private List<AiAgentOption> fetchRemoteOptions() {
        try {
            MesAiAgentHolder holder = agentHolder.getIfAvailable();
            if (holder != null) {
                RemoteAiAgentService agent = holder.agent();
                if (agent != null) {
                    return agent.listOptions(null);
                }
            }
        } catch (Exception e) {
            log.debug("mse-ai 智能体选项拉取失败，按 AI 服务未接入处理", e);
        }
        return null;
    }

    /** 内置默认项（mse-ai 未接入时页面仍可展示；AI 服务模式不可用时映射暂不生效） */
    private List<AiAgentOption> builtinOptions() {
        List<AiAgentOption> options = new ArrayList<>();
        SCENES.forEach(scene -> {
            String code = defaultCode(scene);
            AiAgentOption option = new AiAgentOption();
            option.setAgentCode(code);
            option.setName(builtinName(code));
            option.setDescription("内置默认项（AI 服务未接入，仅作兜底展示）");
            option.setScope("platform");
            options.add(option);
        });
        return options;
    }

    private String builtinName(String code) {
        return switch (code) {
            case "mes_report_writer" -> "MES 生产日报写手";
            case "mes_quality_diag" -> "MES 质量诊断";
            case "mes_equip_diag" -> "MES 设备诊断";
            case "mes_data_analyst" -> "MES 智能问数";
            case "mes_schedule_advisor" -> "MES 排产顾问";
            case "mes_kb_qa" -> "MES 工艺/SOP 问答";
            case "mes_andon_summary" -> "MES 安灯异常摘要";
            case "mes_spc_interpret" -> "MES SPC 判读";
            default -> code;
        };
    }

    private String defaultCode(String scene) {
        return switch (scene) {
            case MesAiAgentMap.SCENE_REPORT -> properties.getReportAgent();
            case MesAiAgentMap.SCENE_QUALITY -> properties.getQualityAgent();
            case MesAiAgentMap.SCENE_EQUIP -> properties.getEquipAgent();
            case MesAiAgentMap.SCENE_DATA_QA -> properties.getDataAgent();
            case MesAiAgentMap.SCENE_SCHEDULE -> properties.getScheduleAgent();
            case MesAiAgentMap.SCENE_KB -> properties.getKbAgent();
            case MesAiAgentMap.SCENE_ANDON -> properties.getAndonAgent();
            case MesAiAgentMap.SCENE_SPC -> properties.getSpcAgent();
            default -> throw new ServiceException(400, "未知场景: " + scene);
        };
    }

    /** 下拉选项响应：platformAvailable=false 时 options 为内置默认项 */
    public record AgentOptions(boolean platformAvailable, List<AiAgentOption> options) {
    }

    /** 单场景映射：configuredCode=null 未设置（走默认）；effectiveCode=当前生效编码 */
    public record SceneMap(String scene, String configuredCode, String effectiveCode, String defaultCode) {
    }
}
