package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 点检保养计划（业务表 mes_check_plan）。
 *
 * <p>type：1 点检、2 保养。cycle：1 日、2 周、3 月。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_check_plan")
public class MesCheckPlan extends BaseEntity {

    /** 计划名称 */
    private String name;

    /** 设备 id（mes_equipment.id） */
    private Long equipmentId;

    /** 类型：1 点检 2 保养 */
    private Integer type;

    /** 周期：1 日 2 周 3 月 */
    private Integer cycle;

    /** 下次执行日期 */
    private LocalDate nextDate;

    /** 执行内容说明 */
    private String content;

    /** 状态：1 启用 0 停用 */
    private Integer status;
}
