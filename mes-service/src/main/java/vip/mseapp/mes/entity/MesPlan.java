package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 排产计划（业务表 mes_plan，产线×日期×班次×工单 一行）。
 *
 * <p>status：0 草稿、1 已发布（发布后进入执行可见）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_plan")
public class MesPlan extends BaseEntity {

    /** 生产订单 id（mes_order.id） */
    private Long orderId;

    /** 产线 id（mes_model.id type=3） */
    private Long lineId;

    /** 排产日期 */
    private LocalDate workDate;

    /** 班次编码（mes_calendar.shift_code） */
    private String shiftCode;

    /** 计划数量 */
    private BigDecimal planQty;

    /** 状态：0 草稿 1 已发布 */
    private Integer status;

    /** 备注 */
    private String remark;
}
