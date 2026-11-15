package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.math.BigDecimal;

/**
 * SPC 监控项（三期，「SPC 分析」页）：关键工序质量特性的规格与子组配置。
 *
 * <p>控制图采用 X̄-R 图（计量型），子组大小 sample_size 取 2-10（控制限系数内置）；
 * 规格限 USL/LSL 至少填一项，Cp/Cpk 仅在双侧规格限齐备时计算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_spc_item")
public class MesSpcItem extends BaseEntity {

    /** 监控项编码（租户内唯一） */
    private String code;

    /** 监控项名称（如「灌装量」） */
    private String name;

    /** 关联物料 id（mes_material.id，可空） */
    private Long materialId;

    /** 工序名（关键工序，文本） */
    private String stepName;

    /** 质量特性名（如「净含量 g」） */
    private String featureName;

    /** 计量单位 */
    private String unit;

    /** 规格上限 USL（可空） */
    private BigDecimal usl;

    /** 规格下限 LSL（可空） */
    private BigDecimal lsl;

    /** 目标值（可空） */
    private BigDecimal target;

    /** 子组大小 n（2-10，默认 5） */
    private Integer sampleSize;

    /** 状态：0 停用 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
