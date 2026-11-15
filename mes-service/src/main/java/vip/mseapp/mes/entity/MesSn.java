package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 序列号（业务表 mes_sn，按件管理产品的单件档案）。
 *
 * <p>status：1 在制、2 完工、3 报废。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_sn")
public class MesSn extends BaseEntity {

    /** 序列号（租户内唯一） */
    private String sn;

    /** 物料 id（mes_material.id） */
    private Long materialId;

    /** 生产订单 id（可空） */
    private Long orderId;

    /** 批次号（可空） */
    private String batchNo;

    /** 当前工序 id（可空） */
    private Long stepId;

    /** 状态：1 在制 2 完工 3 报废 */
    private Integer status;
}
