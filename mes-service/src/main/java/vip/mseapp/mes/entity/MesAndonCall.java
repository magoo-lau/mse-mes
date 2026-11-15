package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.time.LocalDateTime;

/**
 * 安灯呼叫（三期，「安灯管理」页）。
 *
 * <p>type：1 设备、2 质量、3 物料。status：0 待响应、1 处理中（已响应）、2 已关闭。
 * 超时逐级升级：待响应超 15 分钟升级一级（escalate_level 0→2 上限），由后台扫描自动触发并记
 * last_escalate_time；看板按升级级别高亮（绿→黄→红）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_andon_call")
public class MesAndonCall extends BaseEntity {

    public static final int TYPE_EQUIPMENT = 1, TYPE_QUALITY = 2, TYPE_MATERIAL = 3;
    public static final int ST_WAITING = 0, ST_HANDLING = 1, ST_CLOSED = 2;

    /** 呼叫单号（租户内唯一，如 AL202609250001） */
    private String code;

    /** 呼叫类型：1 设备 2 质量 3 物料 */
    private Integer type;

    /** 产线 id（mes_model.id type=3，可空） */
    private Long lineId;

    /** 工位 id（mes_model.id type=4，可空） */
    private Long stationId;

    /** 问题描述 */
    private String description;

    /** 状态：0 待响应 1 处理中 2 已关闭 */
    private Integer status;

    /** 升级级别（0 未升级；后台扫描超时逐级 +1，上限 2） */
    private Integer escalateLevel;

    /** 最近升级时间 */
    private LocalDateTime lastEscalateTime;

    /** 呼叫人 */
    private Long callBy;

    /** 呼叫时间 */
    private LocalDateTime callTime;

    /** 响应人 */
    private Long respondBy;

    /** 响应时间 */
    private LocalDateTime respondTime;

    /** 关闭人 */
    private Long closeBy;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 处理结果 */
    private String handleResult;
}
