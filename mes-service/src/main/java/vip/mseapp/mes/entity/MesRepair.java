package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备维修工单（业务表 mes_repair）。
 *
 * <p>status：0 报修、1 维修中、2 已完工。停机时长（分钟）由完工时自动累计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_repair")
public class MesRepair extends BaseEntity {

    /** 维修工单号（租户内唯一，如 RP202609240001） */
    private String code;

    /** 设备 id（mes_equipment.id） */
    private Long equipmentId;

    /** 来源点检记录 id（异常点检自动转报修，可空） */
    private Long checkRecordId;

    /** 故障描述 */
    private String faultDesc;

    /** 报修人用户 id */
    private Long reportBy;

    /** 报修时间 */
    private LocalDateTime reportTime;

    /** 维修人用户 id */
    private Long repairBy;

    /** 处理说明 */
    private String handleDesc;

    /** 完工时间 */
    private LocalDateTime finishTime;

    /** 停机时长（分钟，完工自动计算） */
    private Long downtimeMinutes;

    /** 状态：0 报修 1 维修中 2 已完工 */
    private Integer status;
}
