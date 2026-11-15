package vip.mseapp.mes.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Mes 控制器自动装配（Spring Boot SPI）。
 *
 * <p>仅在 Servlet Web 环境注册；可用 {@code mse.service.mes.controller.enabled=false}
 * 整体关闭默认控制器，由接入方自有控制器替代（基座同款开关语义）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "mse.service.mes.controller", name = "enabled", matchIfMissing = true)
@ComponentScan(basePackages = "vip.mseapp.mes.controller")
public class MesControllerAutoConfiguration {
}
