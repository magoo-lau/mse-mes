package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物料主数据（业务表 mes_material）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_material")
public class MesMaterial extends BaseEntity {

    /** 物料编码（租户内唯一） */
    private String code;

    /** 物料名称 */
    private String name;

    /** 规格型号 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 类型：1 原材料 2 半成品 3 成品 */
    private Integer type;

    /** 追溯粒度：1 按批次 2 按序列号 */
    private Integer traceMode;

    /** 备注 */
    private String remark;
}
