package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 工作日历与班次（业务表 mes_calendar，按 日期×班次 一行）。
 *
 * <p>type：1 工作班次、2 休息/加班说明行（type=2 仅供排产参考）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_calendar")
public class MesCalendar extends BaseEntity {

    /** 日期 */
    private LocalDate workDate;

    /** 班次编码（如 D/N） */
    private String shiftCode;

    /** 班次名称（如 白班/夜班） */
    private String shiftName;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 类型：1 工作 2 休息 */
    private Integer type;

    /** 适用产线 id（mes_model.id type=3，空=全厂通用） */
    private Long lineId;
}
