package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 领退料单（业务表 mes_mat_req）。
 *
 * <p>type：1 领料、2 退料。status：0 已申请、1 已发料/已退料、2 已驳回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_mat_req")
public class MesMatReq extends BaseEntity {

    /** 单号（租户内唯一，如 MRQ202609240001） */
    private String code;

    /** 类型：1 领料 2 退料 */
    private Integer type;

    /** 生产订单 id（mes_order.id，可空） */
    private Long orderId;

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 批次号（退料时必填） */
    private String batchNo;

    /** 申请数量 */
    private BigDecimal reqQty;

    /** 申请人工用户 id */
    private Long applicantId;

    /** 申请时间 */
    private LocalDateTime reqTime;

    /** 发料/退料执行人用户 id */
    private Long issueBy;

    /** 执行时间 */
    private LocalDateTime issueTime;

    /** 状态：0 已申请 1 已发料/已退料 2 已驳回 */
    private Integer status;

    /** 备注 */
    private String remark;
}
