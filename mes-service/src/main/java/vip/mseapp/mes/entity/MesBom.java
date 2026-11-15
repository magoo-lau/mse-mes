package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * BOM 头（业务表 mes_bom）；明细见 {@link MesBomLine}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_bom")
public class MesBom extends BaseEntity {

    /** 编码（租户内唯一） */
    private String code;

    /** 父项物料 id（mes_material.id） */
    private Long materialId;

    /** 版本（如 V1.0） */
    private String version;

    /** 备注 */
    private String remark;
}
