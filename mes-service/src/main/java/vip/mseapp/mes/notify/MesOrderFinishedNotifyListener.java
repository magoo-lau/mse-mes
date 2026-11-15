package vip.mseapp.mes.notify;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vip.mseapp.api.notify.RemoteNotifyService;
import vip.mseapp.api.notify.dto.NotifyMessage;
import vip.mseapp.api.notify.dto.NotifySendRequest;
import vip.mseapp.core.annotation.RpcReference;
import vip.mseapp.mes.entity.MesOrder;
import vip.mseapp.mes.integration.MesOrderFinishedEvent;

/**
 * 工单完工 → 框架通知中枢群发（(mes,ORDER_FINISHED)，无个人接收人）：不携带渠道，由
 * 中枢按租户路由解析——租户在基座系统管理「通知渠道设置」勾选内置 webhook 即可群机器人
 * 送达（2026-10-20 起替代原「系统集成」出站 ERP 回传）。事务提交后投递，报工回滚不通知；
 * 无事务上下文发布时立即投递（fallbackExecution）。
 */
@Component
public class MesOrderFinishedNotifyListener {

    @RpcReference
    private RemoteNotifyService notifyService;

    /** 工单完工：群发事件消息（无接收人，站内信渠道按需接收人语义跳过并记日志） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderFinished(MesOrderFinishedEvent event) {
        MesOrder order = event.order();
        notifyService.send(new NotifySendRequest(null, new NotifyMessage(
                MesNotifySourceProvider.BIZ_MES, MesNotifySourceProvider.TYPE_ORDER_FINISHED,
                List.of(), "工单完工",
                "工单 " + order.getCode() + " 已完工：计划 " + order.getPlanQty()
                        + "，完工 " + order.getFinishedQty()
                        + "（合格 " + order.getOkQty() + "／不良 " + order.getNgQty() + "）",
                order.getCode(), null)));
    }
}
