package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备台账（业务表 mes_equipment）。
 *
 * <p>status：1 运行、2 待机、3 维修、4 停机（一期手工/点检驱动，三期接数采实时化）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_equipment")
public class MesEquipment extends BaseEntity {

    /** 设备编码（租户内唯一） */
    private String code;

    /** 设备名称 */
    private String name;

    /** 规格型号 */
    private String model;

    /** 所在工位 id（mes_model.id type=4，可空） */
    private Long workstationId;

    /** 状态：1 运行 2 待机 3 维修 4 停机 */
    private Integer status;

    /** 购置日期 */
    private LocalDate buyDate;

    /** 备注 */
    private String remark;
}
