package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工艺路线头（业务表 mes_route）；工序见 {@link MesRouteStep}。
 *
 * <p>status：0 草稿、1 已发布（发布后不可改，可停用重编辑再发布）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route")
public class MesRoute extends BaseEntity {

    /** 编码（租户内唯一） */
    private String code;

    /** 名称 */
    private String name;

    /** 适用物料 id（mes_material.id） */
    private Long materialId;

    /** 版本 */
    private String version;

    /** 状态：0 草稿 1 已发布 */
    private Integer status;

    /** 备注 */
    private String remark;
}
