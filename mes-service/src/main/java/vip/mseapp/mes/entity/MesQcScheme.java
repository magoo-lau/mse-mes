package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检验方案头（业务表 mes_qc_scheme）；检验项目见 {@link MesQcSchemeItem}。
 *
 * <p>qcType：1 IQC（来料）、2 IPQC（过程）、3 FQC（完工）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_scheme")
public class MesQcScheme extends BaseEntity {

    /** 编码（租户内唯一） */
    private String code;

    /** 名称 */
    private String name;

    /** 检验类型：1 IQC 2 IPQC 3 FQC */
    private Integer qcType;

    /** 适用物料 id（mes_material.id，可空=通用） */
    private Long materialId;

    /** 抽样规则（全检/按 AQL 抽检等文字说明） */
    private String sampleRule;

    /** 备注 */
    private String remark;
}
