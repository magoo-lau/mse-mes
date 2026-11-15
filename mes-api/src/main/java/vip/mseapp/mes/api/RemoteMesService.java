package vip.mseapp.mes.api;

import vip.mseapp.mes.api.dto.MesOrderProgressDTO;

import java.util.List;

/**
 * MES 制造执行契约（mes-service 实现）。
 *
 * <p>业务工程自有契约：其他服务（或二期 mse-open 开放平台泛化分发）依赖 mes-api 后经
 * {@code @RpcReference} 消费——单体 local 模式本地直调，微服务 Dubbo 模式远程调用，
 * 消费方代码零改动。租户上下文经 RPC 隐式透传，契约方法无需携带 tenantId。
 */
public interface RemoteMesService {

    /** 按工单号查询生产进度；不存在返回 null */
    MesOrderProgressDTO getOrderProgress(String orderCode);

    /** 在制/已下达工单进度列表（按下达时间倒序，限 100 条） */
    List<MesOrderProgressDTO> listProducingOrders();
}
