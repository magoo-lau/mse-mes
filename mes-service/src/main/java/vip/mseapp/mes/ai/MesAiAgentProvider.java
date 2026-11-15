package vip.mseapp.mes.ai;

import org.springframework.stereotype.Component;
import vip.mseapp.ai.api.AiAgentProvider;
import vip.mseapp.ai.api.dto.AiAgentDescriptor;

import java.util.List;

/**
 * MES 场景智能体声明：8 个智能体随服务启动自动登记 mse-ai 智能体管理（单体聚合形态
 * LocalAgentAutoRegistrar 启动扫描直收；微服务形态经 ai-api 推送器周期重推，均幂等），
 * 平台行为登记本体、各租户首次使用懒补齐可编辑副本——管理员无需再按文档手工创建同名
 * 智能体；编码即「AI 智能化设置」页场景映射的兜底默认（{@link MesAiProperties}）。
 *
 * <p>提示词与直连模式共用 {@link MesAiPrompts}（单一权威）；mes_data_analyst 挂
 * mes_query 反向工具受控取数（本模块 {@code MesQueryTools} 声明，编码全局一致），
 * 问数场景仅 AI 服务模式可用（直连无受控供数通道）。
 */
@Component
public class MesAiAgentProvider implements AiAgentProvider {

    @Override
    public List<AiAgentDescriptor> agents() {
        return List.of(
                agent("mes_report_writer", "MES 生产日报写手", "依据 MES 统计数据生成结构化生产日报/周报草稿（产量/质量/设备/风险/建议）",
                        MesAiPrompts.REPORT, null),
                agent("mes_quality_diag", "MES 质量诊断", "质量异常单可能原因排序与处置建议（临时措施 + 长期纠正）",
                        MesAiPrompts.QUALITY, null),
                agent("mes_equip_diag", "MES 设备诊断", "设备维修单故障假设排序、维修步骤建议与现场排查清单",
                        MesAiPrompts.EQUIP, null),
                agent("mes_data_analyst", "MES 智能问数", "自然语言查生产数据：经 mes_query 工具受控取数（工单进度/产量/质量/设备/WIP），绝不直连业务库",
                        MesAiPrompts.DATA_QA, "mes_query"),
                agent("mes_schedule_advisor", "MES 排产顾问", "依据待排产工单/可用产线/既有排产输出排产草稿（人工确认后生效）",
                        MesAiPrompts.SCHEDULE, null),
                agent("mes_kb_qa", "MES 工艺/SOP 问答", "面向车间操作工的工艺文件/SOP 问答（可挂知识库 RAG 检索增强）",
                        MesAiPrompts.KB, null),
                agent("mes_andon_summary", "MES 安灯异常摘要", "安灯呼叫统计聚类摘要：高频异常/根因线索/响应评价/改善建议",
                        MesAiPrompts.ANDON, null),
                agent("mes_spc_interpret", "MES SPC 判读", "控制图统计量判读：受控状态/过程能力/异常模式/工艺调整建议",
                        MesAiPrompts.SPC, null));
    }

    private AiAgentDescriptor agent(String code, String name, String description,
                                    String systemPrompt, String toolCodes) {
        AiAgentDescriptor descriptor = new AiAgentDescriptor();
        descriptor.setAgentCode(code);
        descriptor.setName(name);
        descriptor.setDescription(description);
        descriptor.setSystemPrompt(systemPrompt);
        descriptor.setToolCodes(toolCodes);
        return descriptor;
    }
}
