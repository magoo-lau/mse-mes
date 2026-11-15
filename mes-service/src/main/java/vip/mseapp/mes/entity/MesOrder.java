package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产订单/工单（业务表 mes_order）。
 *
 * <p>status：0 草稿、1 已提交、2 已下达、3 生产中、4 完工、5 已关闭、6 已取消。
 * finishedQty/okQty/ngQty 由报工累计回写（{@code MesReportService}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_order")
public class MesOrder extends BaseEntity {

    /** 工单号（租户内唯一，如 MO202609240001） */
    private String code;

    /** 产品物料 id（mes_material.id） */
    private Long productId;

    /** BOM id（mes_bom.id，可空） */
    private Long bomId;

    /** 工艺路线 id（mes_route.id，可空=无工序直接报工） */
    private Long routeId;

    /** 计划数量 */
    private BigDecimal planQty;

    /** 完工数量（报工累计） */
    private BigDecimal finishedQty;

    /** 合格数量 */
    private BigDecimal okQty;

    /** 不良数量 */
    private BigDecimal ngQty;

    /** 计划开始日期 */
    private LocalDate planStart;

    /** 计划完成日期 */
    private LocalDate planEnd;

    /** 状态：0 草稿 1 已提交 2 已下达 3 生产中 4 完工 5 已关闭 6 已取消 */
    private Integer status;

    /** 下达时间 */
    private LocalDateTime releaseTime;

    /** 完工时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;
}
