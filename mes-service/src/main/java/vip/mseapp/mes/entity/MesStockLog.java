package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水（业务表 mes_stock_log）。
 *
 * <p>refType：1 领料、2 退料、3 报工产出、4 手工调整。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_stock_log")
public class MesStockLog extends BaseEntity {

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 批次号 */
    private String batchNo;

    /** 变动数量（正=入库/产出，负=出库/消耗） */
    private BigDecimal changeQty;

    /** 变动后余额 */
    private BigDecimal balanceQty;

    /** 来源类型：1 领料 2 退料 3 报工产出 4 手工调整 */
    private Integer refType;

    /** 来源单据 id（mes_mat_req.id / mes_report.id，可空） */
    private Long refId;

    /** 操作人用户 id */
    private Long operatorId;

    /** 操作时间 */
    private LocalDateTime opTime;

    /** 备注 */
    private String remark;
}
