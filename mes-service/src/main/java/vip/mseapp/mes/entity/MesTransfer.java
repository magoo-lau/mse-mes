package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序转移记录（业务表 mes_transfer）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_transfer")
public class MesTransfer extends BaseEntity {

    /** 生产订单 id（mes_order.id） */
    private Long orderId;

    /** 源派工单 id（mes_dispatch.id） */
    private Long fromDispatchId;

    /** 目标派工单 id（mes_dispatch.id） */
    private Long toDispatchId;

    /** 转移数量 */
    private BigDecimal qty;

    /** 操作人用户 id */
    private Long operatorId;

    /** 转移时间 */
    private LocalDateTime transferTime;

    /** 备注 */
    private String remark;
}
