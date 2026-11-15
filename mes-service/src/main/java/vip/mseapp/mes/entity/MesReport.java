package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报工记录（业务表 mes_report）。
 *
 * <p>type：1 正常报工、2 返工报工。status：0 待审核、1 已审核、2 已驳回（驳回时数量已回冲）。
 * 报工提交即累计派工单/工单数量；审核驳回由 {@code MesReportService} 回冲。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_report")
public class MesReport extends BaseEntity {

    /** 报工单号（租户内唯一，如 MR202609240001） */
    private String code;

    /** 生产订单 id（mes_order.id） */
    private Long orderId;

    /** 派工单 id（mes_dispatch.id，无工序工单可空） */
    private Long dispatchId;

    /** 工序 id（可空） */
    private Long stepId;

    /** 工位 id（可空） */
    private Long workstationId;

    /** 报工人用户 id */
    private Long reporterId;

    /** 类型：1 正常 2 返工 */
    private Integer type;

    /** 合格数量 */
    private BigDecimal okQty;

    /** 不良数量 */
    private BigDecimal ngQty;

    /** 不良原因说明 */
    private String defectReason;

    /** 报工时间 */
    private LocalDateTime reportTime;

    /** 状态：0 待审核 1 已审核 2 已驳回 */
    private Integer status;

    /** 审核人用户 id */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见 */
    private String auditRemark;
}
