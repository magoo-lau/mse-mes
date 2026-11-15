package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 制造模型节点（工厂/车间/产线/工位四级树，业务表 mes_model）。
 *
 * <p>type：1 工厂、2 车间、3 产线、4 工位；工位可挂设备（mes_equipment.workstation_id 反向关联）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_model")
public class MesModel extends BaseEntity {

    /** 类型：1 工厂 2 车间 3 产线 4 工位 */
    private Integer type;

    /** 上级节点 id，根节点为 0 */
    private Long parentId;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 备注 */
    private String remark;
}
