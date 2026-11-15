package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不良品处置单（业务表 mes_defect）。
 *
 * <p>dispose：1 返工、2 报废、3 让步接收。status：0 待处置、1 已处置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_defect")
public class MesDefect extends BaseEntity {

    /** 来源检验单 id（mes_qc_order.id，可空=报工不良直接登记） */
    private Long qcOrderId;

    /** 生产订单 id（可空） */
    private Long orderId;

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 批次号 */
    private String batchNo;

    /** 不良数量 */
    private BigDecimal qty;

    /** 不良原因 */
    private String reason;

    /** 处置方式：1 返工 2 报废 3 让步接收 */
    private Integer dispose;

    /** 处置说明 */
    private String disposeRemark;

    /** 处置人用户 id */
    private Long disposeBy;

    /** 处置时间 */
    private LocalDateTime disposeTime;

    /** 状态：0 待处置 1 已处置 */
    private Integer status;
}
