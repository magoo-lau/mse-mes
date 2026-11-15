package vip.mseapp.mes.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import vip.mseapp.ai.api.RemoteAiAgentService;
import vip.mseapp.ai.api.RemoteAiChatService;
import vip.mseapp.api.system.RemoteConfigService;
import vip.mseapp.core.annotation.RpcReference;

/**
 * MES→基座/mse-ai RPC 引用装配（对齐 mse-oa 的 {@code OaAiRpcConfiguration} 范式）。
 * AI 能力开关不走 yaml：是否启用与走哪条通道由基座 sys_config 参数 {@code mes.ai.mode}
 * 运行期决定（{@link MesAiModeResolver}），因此装配无条件存在、引用在**首次访问时**才解析（{@code @Lazy}）——
 *
 * <ul>
 *   <li>dubbo 模式：{@code @RpcReference}（check 缺省 false）注册懒加载代理，mse-ai/mse-system 未部署时调用期才报错；</li>
 *   <li>单体 local 模式：按类型解析容器内实现，缺失时首次访问抛异常，由使用方捕获降级
 *       （AI 服务通道不可用 / 参数按 off 处理），不影响应用启动。</li>
 * </ul>
 *
 * <p>对话、参数与智能体选项拆成三个持有器：local 模式按持有器整体解析，各自独立降级互不连累
 * （如单体未引 ai-service 但引了 service-system：AI 服务通道与选项列表不可用，参数读取仍正常，直连模式可用）。
 */
@Configuration
public class MesAiRpcConfiguration {

    /** AI 服务对话契约持有者（mse-ai 未接入时首次访问失败 → AI 服务通道按不可用降级） */
    @Bean
    @Lazy
    public MesAiChatHolder mesAiChatHolder() {
        return new MesAiChatHolder();
    }

    /** 基座参数契约持有者（service-system 未接入时首次访问失败 → 能力模式按 off 降级） */
    @Bean
    @Lazy
    public MesAiConfigHolder mesAiConfigHolder() {
        return new MesAiConfigHolder();
    }

    /** 智能体选项契约持有者（mse-ai 未接入时首次访问失败 → 下拉回退内置默认项） */
    @Bean
    @Lazy
    public MesAiAgentHolder mesAiAgentHolder() {
        return new MesAiAgentHolder();
    }

    /** RPC 引用持有者：mse-ai 对话契约（生成耗时 1~3 分钟，超时须大于单次 LLM 调用上限，注解默认 5s 必掐） */
    public static class MesAiChatHolder {

        @RpcReference(dubbo = @RpcReference.Dubbo(timeout = 180_000))
        private RemoteAiChatService chatService;

        public RemoteAiChatService chat() {
            return chatService;
        }
    }

    /** RPC 引用持有者：基座参数契约（AI 能力模式开关读取） */
    public static class MesAiConfigHolder {

        @RpcReference
        private RemoteConfigService configService;

        public RemoteConfigService config() {
            return configService;
        }
    }

    /** RPC 引用持有者：mse-ai 智能体选项列表契约（「AI 智能化设置」页下拉数据源） */
    public static class MesAiAgentHolder {

        @RpcReference
        private RemoteAiAgentService agentService;

        public RemoteAiAgentService agent() {
            return agentService;
        }
    }
}
