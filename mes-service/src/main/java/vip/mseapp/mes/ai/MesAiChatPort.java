package vip.mseapp.mes.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import vip.mseapp.ai.api.dto.AiChatRequest;
import vip.mseapp.ai.api.dto.AiChatResult;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mes.ai.MesAiRpcConfiguration.MesAiChatHolder;

/**
 * MES→LLM 调用端口：AI 场景唯一模型出口，屏蔽「模型在哪」的差异（对齐 mse-oa 的 {@code OaAiChatPort}）。
 *
 * <ul>
 *   <li><b>AI 服务通道</b>：mse-ai 智能体（system prompt 在服务侧智能体上，可挂 mes_query 工具按需查数，
 *       模型/用量/审计归 mse-ai）；mse-ai 未部署（单体未引入 ai-service）时不可用。</li>
 *   <li><b>直连通道</b>：框架区 starter-ai 装配的 {@code mseChatClient} 直连模型（OpenAI 兼容协议，
 *       连接参数走 spring.ai.* 配置）；未引入模型 starter/未配置时不可用——即「没有 mse-ai」部署形态的兜底。</li>
 * </ul>
 *
 * <p>mode=auto：AI 服务失败（未部署/调用报错）自动回退直连，调用方无感。
 */
@Component
public class MesAiChatPort {

    private static final Logger log = LoggerFactory.getLogger(MesAiChatPort.class);

    private final ObjectProvider<MesAiChatHolder> chatHolder;
    private final ObjectProvider<ChatClient> directClient;

    public MesAiChatPort(ObjectProvider<MesAiChatHolder> chatHolder,
                         @Qualifier("mseChatClient") ObjectProvider<ChatClient> directClient) {
        this.chatHolder = chatHolder;
        this.directClient = directClient;
    }

    /** AI 服务通道是否可用（本地模式缺失实现/引用解析失败均按不可用；dubbo 懒代理无法预判远端 provider，乐观可用） */
    public boolean platformAvailable() {
        try {
            return chatHolderRef() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 直连通道是否可用（需 starter-ai mseChatClient：引入模型 starter 并配置 spring.ai.*） */
    public boolean directAvailable() {
        return directClient.getIfAvailable() != null;
    }

    /**
     * 按模式发起一次无状态补全。
     *
     * @param mode               能力模式（OFF 由调用方提前拦截）
     * @param agentCode          AI 服务模式智能体编码（直连模式忽略）
     * @param directSystemPrompt 直连模式 system prompt（AI 服务模式忽略，智能体自带）
     * @param userMessage        用户消息（含装配好的业务上下文）
     */
    public String chat(MesAiMode mode, String agentCode, String directSystemPrompt, String userMessage) {
        if (mode == MesAiMode.PLATFORM || mode == MesAiMode.AUTO) {
            if (!platformAvailable()) {
                if (mode == MesAiMode.PLATFORM) {
                    throw new ServiceException("AI 服务通道不可用：未检测到 AI 服务"
                            + "（需部署 mse-ai：单体形态请在聚合工程引入 ai-service，微服务形态请部署 mse-ai）");
                }
                log.info("AI 服务未接入，AI 生成回退直连模式");
            } else {
                try {
                    return chatViaPlatform(agentCode, userMessage);
                } catch (Exception e) {
                    if (mode == MesAiMode.PLATFORM) {
                        throw e instanceof ServiceException se ? se : new ServiceException("AI 服务调用失败：" + e.getMessage());
                    }
                    log.warn("AI 服务通道调用失败，回退直连模式", e);
                }
            }
        }
        if (!directAvailable()) {
            throw new ServiceException("AI 直连通道不可用：请引入 spring-ai 模型 starter 并配置 spring.ai.*"
                    + "（或部署 AI 服务 mse-ai 后把参数 " + MesAiMode.CONFIG_KEY + " 设为 platform/auto）");
        }
        try {
            return directClient.getIfAvailable().prompt()
                    .system(directSystemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI 直连通道调用失败", e);
            throw new ServiceException("AI 直连调用失败：" + e.getMessage());
        }
    }

    /** AI 服务模式单次调用（需 AI 服务侧智能体承载 system prompt / mes_query 工具的场景直接使用） */
    public String chatViaPlatform(String agentCode, String userMessage) {
        try {
            MesAiChatHolder holder = chatHolderRef();
            AiChatRequest request = new AiChatRequest();
            request.setAgentCode(agentCode);
            request.setMessage(userMessage);
            AiChatResult result = holder.chat().chat(request);
            return result == null ? null : result.getContent();
        } catch (Exception e) {
            throw e instanceof ServiceException se ? se : new ServiceException("AI 服务调用失败：" + e.getMessage());
        }
    }

    /** 解析 AI 服务契约引用；@Lazy 持有器首次访问时才解析（本地模式缺失即抛，由调用方降级） */
    private MesAiChatHolder chatHolderRef() {
        MesAiChatHolder holder = chatHolder.getIfAvailable();
        if (holder == null || holder.chat() == null) {
            throw new ServiceException("AI 服务未接入：单体形态请在聚合工程引入 ai-service，微服务形态请部署 mse-ai");
        }
        return holder;
    }
}
