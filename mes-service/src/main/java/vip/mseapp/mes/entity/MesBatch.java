package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 批次（业务表 mes_batch，生产/采购批次的追溯主档）。
 *
 * <p>source：1 生产（报工自动生成/累计）、2 采购（领料入库登记）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_batch")
public class MesBatch extends BaseEntity {

    /** 批次号（租户内唯一，如 B20260924-0001） */
    private String batchNo;

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 来源：1 生产 2 采购 */
    private Integer source;

    /** 生产订单 id（source=1 时关联） */
    private Long orderId;

    /** 数量 */
    private BigDecimal qty;

    /** 备注 */
    private String remark;
}
