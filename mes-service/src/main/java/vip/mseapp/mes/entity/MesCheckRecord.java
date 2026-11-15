package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 点检保养执行记录（业务表 mes_check_record）。
 *
 * <p>result：1 正常、2 异常（异常自动生成维修工单，由 {@code MesRepairService} 承接）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_check_record")
public class MesCheckRecord extends BaseEntity {

    /** 计划 id（mes_check_plan.id） */
    private Long planId;

    /** 设备 id（mes_equipment.id） */
    private Long equipmentId;

    /** 执行日期 */
    private LocalDate checkDate;

    /** 结果：1 正常 2 异常 */
    private Integer result;

    /** 执行人用户 id */
    private Long checkerId;

    /** 执行时间 */
    private LocalDateTime checkTime;

    /** 检查说明/异常描述 */
    private String remark;
}
