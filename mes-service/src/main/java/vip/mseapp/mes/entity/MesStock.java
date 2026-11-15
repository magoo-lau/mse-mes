package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 线边仓库存（业务表 mes_stock，物料×批次 一行；数量可为负=账实差异待调整）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_stock")
public class MesStock extends BaseEntity {

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 批次号 */
    private String batchNo;

    /** 库存数量 */
    private BigDecimal qty;

    /** 仓位（默认 LINE 线边仓） */
    private String location;
}
