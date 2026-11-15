package vip.mseapp.mes.autoconfigure;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import vip.mseapp.mes.ai.MesAiProperties;
import vip.mseapp.mes.api.RemoteMesOpenService;
import vip.mseapp.mes.api.RemoteMesService;
import vip.mseapp.mes.integration.MesOpenServiceImpl;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.mes.rpc.RemoteMesServiceImpl;
import vip.mseapp.core.annotation.TenantServiceModule;
import vip.mseapp.mybatis.config.MseMybatisAutoConfiguration;

/**
 * MES 制造执行系统——业务服务自动装配（Spring Boot SPI）。
 *
 * <p>接入方仅需引入 mes-service 依赖：Service / Mapper 均由此注册，
 * 无需 component-scan 业务包名，也无需 @MapperScan。控制器由
 * {@link MesControllerAutoConfiguration} 装配，两者均经
 * {@code META-INF/spring/...AutoConfiguration.imports} 登记。
 *
 * <p>RPC 契约实现采用 {@code @Bean + @ConditionalOnMissingBean} 显式注册：
 * 接入方声明自己的同契约 Bean 即可整体替换默认实现。
 *
 * <p>{@link TenantServiceModule @TenantServiceModule} 声明模块服务身份（服务名 + mapper 包根）：
 * 单体聚合接入时「租户隔离策略」的服务级配置按此服务名零配置生效（微服务形态即应用名，双形态一致）。
 *
 * <p>二期：ai（AI 智能化）/ aitool（mes_query 反向工具）/ integration（系统集成，ERP 入站同步）/
 * notify（通知中枢来源声明与群发监听）包组件随此扫描注册；{@link MesAiProperties}
 * （mse.mes.ai.* 智能体编码兜底默认）在此启用。
 */
@TenantServiceModule("mse-mes")
@AutoConfiguration(before = MseMybatisAutoConfiguration.class)
@MapperScan(basePackages = "vip.mseapp.mes.mapper", annotationClass = Mapper.class)
@ComponentScan(basePackages = {"vip.mseapp.mes.service", "vip.mseapp.mes.ai",
        "vip.mseapp.mes.aitool", "vip.mseapp.mes.integration", "vip.mseapp.mes.iot",
        "vip.mseapp.mes.andon", "vip.mseapp.mes.spc", "vip.mseapp.mes.notify"})
@EnableConfigurationProperties(MesAiProperties.class)
public class MesServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RemoteMesService.class)
    public RemoteMesService remoteMesServiceImpl(MesOrderMapper orderMapper) {
        return new RemoteMesServiceImpl(orderMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RemoteMesOpenService.class)
    public RemoteMesOpenService mesOpenServiceImpl(MesOrderMapper orderMapper,
                                                   vip.mseapp.mes.service.MesStatsService statsService,
                                                   vip.mseapp.mes.integration.MesIntegrationService integrationService) {
        return new MesOpenServiceImpl(orderMapper, statsService, integrationService);
    }

    /** 开放能力自注册（OpenApiRegistrationPusher 就绪后推送 mse-open 目录并周期重推自愈） */
    @Bean
    @ConditionalOnMissingBean(name = "mesOpenApiContributor")
    public vip.mseapp.open.api.OpenApiContributor mesOpenApiContributor() {
        return new vip.mseapp.mes.integration.MesOpenApiContributor();
    }

}
