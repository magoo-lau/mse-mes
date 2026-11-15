package vip.mseapp.mes.rpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.api.RemoteMesService;
import vip.mseapp.mes.api.dto.MesOrderProgressDTO;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.mapper.MesOrderMapper;
import vip.mseapp.core.annotation.RpcService;

import java.util.List;

/**
 * MES 契约实现：单体 local 模式本地直调，微服务 Dubbo 模式自动导出（消费方零改动）。
 *
 * <p>注册方式：MesServiceAutoConfiguration 中 {@code @Bean + @ConditionalOnMissingBean}，
 * 接入方声明同契约 Bean 即可整体替换本默认实现。
 */
@RpcService(RemoteMesService.class)
public class RemoteMesServiceImpl implements RemoteMesService {

    private final MesOrderMapper orderMapper;

    public RemoteMesServiceImpl(MesOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public MesOrderProgressDTO getOrderProgress(String orderCode) {
        MesOrder order = orderMapper.selectOne(new LambdaQueryWrapper<MesOrder>()
                .eq(MesOrder::getCode, orderCode).last("LIMIT 1"));
        return order == null ? null : toDto(order);
    }

    @Override
    public List<MesOrderProgressDTO> listProducingOrders() {
        return orderMapper.selectList(new LambdaQueryWrapper<MesOrder>()
                        .in(MesOrder::getStatus, MesOrderServiceHolder.ST_RELEASED, MesOrderServiceHolder.ST_PRODUCING)
                        .orderByDesc(MesOrder::getReleaseTime)
                        .last("LIMIT 100"))
                .stream().map(RemoteMesServiceImpl::toDto).toList();
    }

    private static MesOrderProgressDTO toDto(MesOrder order) {
        MesOrderProgressDTO dto = new MesOrderProgressDTO();
        dto.setOrderCode(order.getCode());
        dto.setProductId(order.getProductId());
        dto.setPlanQty(order.getPlanQty());
        dto.setFinishedQty(order.getFinishedQty());
        dto.setOkQty(order.getOkQty());
        dto.setNgQty(order.getNgQty());
        dto.setStatus(order.getStatus());
        return dto;
    }

    /** 状态常量中转（避免 rpc 包直接依赖 service 实现类） */
    final class MesOrderServiceHolder {
        static final int ST_RELEASED = 2, ST_PRODUCING = 3;
    }
}
