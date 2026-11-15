package vip.mseapp.mes.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

/**
 * AI 场景 → 智能体映射（租户隔离，「AI 智能化设置」页维护，二期菜单 510）。
 *
 * <p>scene 取值见 {@link MesAiAgentMapService} 场景常量；agentCode 空串 = 使用内置默认
 * （{@code mse.mes.ai.*} yaml 兜底 → 代码默认），保存即生效，不改行不删行（规避软删与唯一键冲突）。
 * 对齐 mse-oa 的 {@code oa_ai_agent_map} 模式（规划 docs/01 第十三节「场景→智能体映射」）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ai_agent_map")
public class MesAiAgentMap extends BaseEntity {

    /** 场景：AI 生产日报/周报生成（挂 509 看板报表） */
    public static final String SCENE_REPORT = "report";
    /** 场景：AI 质量异常诊断（挂 505 质量管理） */
    public static final String SCENE_QUALITY = "quality";
    /** 场景：AI 设备故障诊断（挂 506 设备管理） */
    public static final String SCENE_EQUIP = "equip";
    /** 场景：AI 智能问数（挂 509，默认复用 mse-ai 的 data_analyst + mes_query 反向工具） */
    public static final String SCENE_DATA_QA = "data_qa";
    /** 场景：AI 排产建议（挂 502 生产计划） */
    public static final String SCENE_SCHEDULE = "schedule";
    /** 场景：AI 工艺/SOP 知识问答（挂 504 报工工作台） */
    public static final String SCENE_KB = "kb";
    /** 场景：AI 安灯异常摘要（三期，挂 513 安灯管理） */
    public static final String SCENE_ANDON = "andon";
    /** 场景：AI SPC 判读（三期，挂 514 SPC 分析） */
    public static final String SCENE_SPC = "spc";

    /** 场景标识（(tenant_id, scene) 唯一） */
    private String scene;

    /** 映射的智能体编码（ai_agent.agent_code；空串 = 使用内置默认） */
    private String agentCode;
}
