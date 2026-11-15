package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 异常停工记录（业务表 mes_halt）。
 *
 * <p>reason：1 设备故障、2 缺料、3 质量异常、4 计划变更、9 其他。end_time 为空=停工中。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_halt")
public class MesHalt extends BaseEntity {

    /** 生产订单 id（可空） */
    private Long orderId;

    /** 产线 id（mes_model.id type=3，可空） */
    private Long lineId;

    /** 停工原因：1 设备故障 2 缺料 3 质量异常 4 计划变更 9 其他 */
    private Integer reason;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 恢复时间（空=停工中） */
    private LocalDateTime endTime;

    /** 登记人用户 id */
    private Long reporterId;

    /** 备注 */
    private String remark;
}
