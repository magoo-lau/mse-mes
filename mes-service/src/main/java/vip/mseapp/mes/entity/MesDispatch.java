package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派工单（业务表 mes_dispatch，工单×工序 一行，下达时按工艺路线展开生成）。
 *
 * <p>status：0 待开工、1 生产中、2 已完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dispatch")
public class MesDispatch extends BaseEntity {

    /** 生产订单 id（mes_order.id） */
    private Long orderId;

    /** 工序 id（mes_route_step.id，无工艺路线工单为空） */
    private Long stepId;

    /** 工序号 */
    private Integer stepNo;

    /** 工序名称 */
    private String stepName;

    /** 产线 id（mes_model.id type=3） */
    private Long lineId;

    /** 工位 id（mes_model.id type=4，可空） */
    private Long workstationId;

    /** 指派操作工用户 id（可空=班组任一） */
    private Long assigneeId;

    /** 派工数量 */
    private BigDecimal planQty;

    /** 已报工数量 */
    private BigDecimal reportedQty;

    /** 状态：0 待开工 1 生产中 2 已完成 */
    private Integer status;

    /** 开工时间 */
    private LocalDateTime startTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 是否返工工序：0 否 1 是 */
    private Integer rework;
}
