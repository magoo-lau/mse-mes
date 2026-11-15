package vip.mseapp.mes.ai;

import org.springframework.stereotype.Component;
import vip.mseapp.ai.api.AiBizModuleProvider;
import vip.mseapp.ai.api.dto.AiBizModuleDescriptor;

import java.util.List;

/**
 * MES 业务模块声明：把「制造执行」模块自声明给 mse-ai 模块注册中心——通用业务分析入口
 * （biz_query 路由门面）据此把工单/生产/质量等问题路由到本模块查询工具 mes_biz_query
 * （{@code MesBizQueryTool}，在 mse-mes 进程内执行）。单体本地扫描 / 微服务推送自动登记。
 */
@Component
public class MesBizModuleProvider implements AiBizModuleProvider {

    @Override
    public List<AiBizModuleDescriptor> modules() {
        AiBizModuleDescriptor descriptor = new AiBizModuleDescriptor();
        descriptor.setModuleCode("mes");
        descriptor.setName("制造执行");
        descriptor.setDescription("工单与生产进度、报工、质量检验、设备状态、物料、安灯、SPC、追溯、看板");
        descriptor.setServiceName("mse-mes");
        descriptor.setKeywords("工单,生产,报工,排产,质量,检验,设备,物料,安灯,追溯,看板,SPC,合格率");
        descriptor.setTablePrefixes("mes_");
        descriptor.setToolCode("mes_biz_query");
        return List.of(descriptor);
    }
}
