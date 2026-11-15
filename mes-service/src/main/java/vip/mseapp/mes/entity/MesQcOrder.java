package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验单（业务表 mes_qc_order）。
 *
 * <p>qcType：1 IQC、2 IPQC（报工触发质检点自动生成）、3 FQC。
 * result：0 待检、1 合格、2 不合格、3 让步接收。status：0 待检、1 已完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_order")
public class MesQcOrder extends BaseEntity {

    /** 检验单号（租户内唯一，如 QC202609240001） */
    private String code;

    /** 检验类型：1 IQC 2 IPQC 3 FQC */
    private Integer qcType;

    /** 检验方案 id（可空） */
    private Long schemeId;

    /** 生产订单 id（可空，IQC 可无工单） */
    private Long orderId;

    /** 工序 id（IPQC 时关联，可空） */
    private Long stepId;

    /** 报工单 id（IPQC 来源报工，可空） */
    private Long reportId;

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 批次号 */
    private String batchNo;

    /** 送检数量 */
    private BigDecimal sampleQty;

    /** 合格数量 */
    private BigDecimal okQty;

    /** 不良数量 */
    private BigDecimal ngQty;

    /** 检验结果：0 待检 1 合格 2 不合格 3 让步 */
    private Integer result;

    /** 检验数据/描述（按检验项目逐项录入的汇总 JSON 或文本） */
    private String inspectData;

    /** 检验人用户 id */
    private Long inspectorId;

    /** 检验时间 */
    private LocalDateTime inspectTime;

    /** 状态：0 待检 1 已完成 */
    private Integer status;
}
