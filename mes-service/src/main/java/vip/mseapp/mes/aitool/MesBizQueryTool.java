package vip.mseapp.mes.aitool;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.ai.api.support.AbstractBizQueryTool;
import vip.mseapp.ai.api.support.AiBizQueryProperties;
import vip.mseapp.mybatis.datasource.TenantRawConnectionRouter;

import java.util.List;

/**
 * 制造执行业务查询工具（mes_biz_query）：本模块业务表（mes_ 前缀）的受控 text2sql 直查通道，
 * 继承 ai-api 基类在 mse-mes 进程内执行——连接按本模块 @TenantServiceModule("mse-mes") 路由
 * 本模块世界，数据不出归属服务。mse-ai 的 biz_query 路由门面按模块注册表转发到本工具；
 * 既有 mes_data_analyst（mes_query 结构化通道）不受影响，本工具承接跨模块通用入口的
 * mes 域 ad-hoc 查询。
 */
@Component
public class MesBizQueryTool extends AbstractBizQueryTool {

    public MesBizQueryTool(JsonMapper objectMapper,
                           ObjectProvider<TenantRawConnectionRouter> masterConnections,
                           AiBizQueryProperties properties) {
        super(objectMapper, masterConnections, properties);
    }

    @Override
    protected String toolCode() {
        return "mes_biz_query";
    }

    @Override
    protected String toolName() {
        return "制造执行业务查询";
    }

    @Override
    protected String toolDescription() {
        return "查询制造执行（MES）模块的生产业务表：工单、报工、质量检验、设备、物料、安灯、追溯等";
    }

    @Override
    public String moduleCode() {
        return "mes";
    }

    @Override
    public String moduleName() {
        return "制造执行";
    }

    @Override
    protected List<String> tablePrefixes() {
        return List.of("mes_");
    }
}
