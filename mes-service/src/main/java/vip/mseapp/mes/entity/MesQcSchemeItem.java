package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检验方案项目（业务表 mes_qc_scheme_item）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_scheme_item")
public class MesQcSchemeItem extends BaseEntity {

    /** 检验方案 id（mes_qc_scheme.id） */
    private Long schemeId;

    /** 检验项目名称 */
    private String itemName;

    /** 标准要求 */
    private String standard;

    /** 检验方法 */
    private String method;
}
