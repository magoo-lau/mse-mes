package vip.mseapp.boot.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MES 制造执行系统——启动器（微服务形态）。
 *
 * <p>启动类位于 vip.mseapp.boot.mes，与业务积木包树零重叠——默认扫描不触碰任何积木，
 * 一切组件经 Spring Boot SPI 自动装配：自有积木（mes-service）引入 jar 即得
 * 全部能力（含 Mapper 注册）；基座能力不打包在本服务——鉴权数据源/独立库租户路由经
 * micro-starter 内置的 starter-bridge 以契约消费 mse-system/mse-tenant。
 * 各默认实现均带 @ConditionalOnMissingBean 回退，声明同契约 Bean 即可整体替换。
 *
 * <p>微服务形态（mse.rpc.mode=dubbo）：本地仅 application.yaml（spring.config.import 接 Nacos），数据源/Redis 等统一在
 * Nacos 配置中心管理（服务专属 Data ID 留档 docker/nacos/config/mse-mes.yaml），启动即注册。
 */
@SpringBootApplication
public class MesBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(MesBootApplication.class, args);
    }
}
