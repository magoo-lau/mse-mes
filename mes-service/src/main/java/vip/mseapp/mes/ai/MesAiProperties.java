package vip.mseapp.mes.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MES AI 接入配置（mse.mes.ai.*）：八个场景智能体编码的**兜底默认值**——运行期以「AI 智能化设置」页
 * （菜单 510）的场景映射为准（存 mes_ai_agent_map，见 {@link MesAiAgentMapService}），映射未设置的场景
 * 回退到此处的 yaml/代码默认。默认编码即 {@link MesAiAgentProvider} 声明注册的智能体编码（随服务
 * 启动自动登记 mse-ai 智能体管理，各租户开箱可见）。能力开关不走 yaml——由基座「参数设置」sys_config
 * 参数 mes.ai.mode 运行期控制（见 {@link MesAiModeResolver}）；直连模式的模型连接参数走标准 spring.ai.* 配置。
 */
@ConfigurationProperties(prefix = "mse.mes.ai")
public class MesAiProperties {

    /** AI 生产日报智能体编码 */
    private String reportAgent = "mes_report_writer";

    /** AI 质量异常诊断智能体编码 */
    private String qualityAgent = "mes_quality_diag";

    /** AI 设备故障诊断智能体编码 */
    private String equipAgent = "mes_equip_diag";

    /** AI 智能问数智能体编码（MES 专属问数智能体，挂 mes_query 反向工具受控供数；由 MesAiAgentProvider 声明注册） */
    private String dataAgent = "mes_data_analyst";

    /** AI 排产建议智能体编码 */
    private String scheduleAgent = "mes_schedule_advisor";

    /** AI 工艺/SOP 知识问答智能体编码（挂 mse-ai 知识库） */
    private String kbAgent = "mes_kb_qa";

    /** AI 安灯异常摘要智能体编码（三期） */
    private String andonAgent = "mes_andon_summary";

    /** AI SPC 判读智能体编码（三期） */
    private String spcAgent = "mes_spc_interpret";

    public String getReportAgent() {
        return reportAgent;
    }

    public void setReportAgent(String reportAgent) {
        this.reportAgent = reportAgent;
    }

    public String getQualityAgent() {
        return qualityAgent;
    }

    public void setQualityAgent(String qualityAgent) {
        this.qualityAgent = qualityAgent;
    }

    public String getEquipAgent() {
        return equipAgent;
    }

    public void setEquipAgent(String equipAgent) {
        this.equipAgent = equipAgent;
    }

    public String getDataAgent() {
        return dataAgent;
    }

    public void setDataAgent(String dataAgent) {
        this.dataAgent = dataAgent;
    }

    public String getScheduleAgent() {
        return scheduleAgent;
    }

    public void setScheduleAgent(String scheduleAgent) {
        this.scheduleAgent = scheduleAgent;
    }

    public String getKbAgent() {
        return kbAgent;
    }

    public void setKbAgent(String kbAgent) {
        this.kbAgent = kbAgent;
    }

    public String getAndonAgent() {
        return andonAgent;
    }

    public void setAndonAgent(String andonAgent) {
        this.andonAgent = andonAgent;
    }

    public String getSpcAgent() {
        return spcAgent;
    }

    public void setSpcAgent(String spcAgent) {
        this.spcAgent = spcAgent;
    }
}
