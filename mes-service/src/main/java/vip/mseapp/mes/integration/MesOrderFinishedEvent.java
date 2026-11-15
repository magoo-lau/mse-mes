package vip.mseapp.mes.integration;

import org.springframework.context.ApplicationEvent;
import vip.mseapp.mes.entity.MesOrder;

/**
 * 工单完工事件（报工达产触发）：通知模块监听后经框架通知中枢群发 (mes,ORDER_FINISHED)
 * （渠道由租户路由解析；出站 ERP 回传已退役 2026-10-20）。
 * 用 Spring 应用事件解耦——报工服务不依赖通知实现（事件无订阅者时零开销）。
 */
public class MesOrderFinishedEvent extends ApplicationEvent {

    private final transient MesOrder order;

    public MesOrderFinishedEvent(MesOrder order) {
        super(order);
        this.order = order;
    }

    public MesOrder order() {
        return order;
    }
}
