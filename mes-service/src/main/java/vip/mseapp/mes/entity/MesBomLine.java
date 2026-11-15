package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * BOM 明细行（业务表 mes_bom_line）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_bom_line")
public class MesBomLine extends BaseEntity {

    /** BOM 头 id（mes_bom.id） */
    private Long bomId;

    /** 子件物料 id（mes_material.id） */
    private Long materialId;

    /** 单位用量 */
    private BigDecimal qty;

    /** 损耗率（%） */
    private BigDecimal lossRate;
}
