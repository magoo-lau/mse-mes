package vip.mseapp.boot.mes.config;

import java.util.List;

import org.apache.dubbo.config.ServiceConfig;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import vip.mseapp.ai.api.RemoteAiTool;
import vip.mseapp.ai.api.support.DispatchingAiTool;
import vip.mseapp.mes.aitool.MesBizQueryTool;
import vip.mseapp.mes.aitool.MesQueryTools;
import vip.mseapp.rpc.dubbo.MseDubboSupport;

/**
 * MES 工具 Dubbo 导出（仅微服务 dubbo 模式）：group=spring.application.name（mse-mes），
 * mse-ai 平台按 group 泛化调用。同 group 只能导出一个 RemoteAiTool 实现，多工具经
 * {@link DispatchingAiTool} 按 toolCode 内部分发——当前承载 mes_query（场景智能体结构化
 * action 通道）与 mes_biz_query（业务库受控查询，biz_query 模块化路由的 mes 分支）。
 *
 * <p>放在 mes-boot 而非 mes-service：Dubbo 依赖属启动器形态（mes-service 纯 jar 无 Dubbo classpath）；
 * 单体聚合形态无需导出——mse-ai 直接收集容器内 RemoteAiTool Bean（local 分发按 toolCode）。
 */
@Configuration
@ConditionalOnProperty(name = "mse.rpc.mode", havingValue = "dubbo")
public class MesAiToolExportConfiguration {

    @Bean(initMethod = "export", destroyMethod = "unexport")
    public ServiceConfig<RemoteAiTool> mesAiToolConfig(MesQueryTools mesQueryTools,
                                                       MesBizQueryTool mesBizQueryTool,
                                                       ConfigurableListableBeanFactory beanFactory,
                                                       Environment environment) {
        ServiceConfig<RemoteAiTool> config = MseDubboSupport.serviceConfig(RemoteAiTool.class,
                new DispatchingAiTool(List.of(mesQueryTools, mesBizQueryTool)), beanFactory);
        config.setGroup(environment.getProperty("spring.application.name"));
        return config;
    }
}
