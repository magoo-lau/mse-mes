package vip.mseapp.mes.notify;

import java.util.List;

import org.springframework.stereotype.Component;
import vip.mseapp.api.notify.NotifySourceProvider;
import vip.mseapp.api.notify.dto.NotifySourceDescriptor;
import vip.mseapp.api.notify.dto.NotifyTypeDescriptor;

/**
 * MES 通知业务来源声明（框架目录自注册）：mes 制造执行 + 工单完工群发类型。
 * 发送不携带渠道，由框架中枢按租户路由 (mes,type) 解析（基座系统管理「通知渠道设置」维护）。
 */
@Component
public class MesNotifySourceProvider implements NotifySourceProvider {

    /** MES 业务来源码（框架来源目录/路由匹配口径） */
    public static final String BIZ_MES = "mes";

    /** 工单完工（群发事件，无个人接收人；站内信渠道按需接收人语义跳过） */
    public static final String TYPE_ORDER_FINISHED = "ORDER_FINISHED";

    @Override
    public List<NotifySourceDescriptor> describeSources() {
        return List.of(new NotifySourceDescriptor(BIZ_MES, "制造执行", List.of(
                new NotifyTypeDescriptor(TYPE_ORDER_FINISHED, "工单完工"))));
    }
}
