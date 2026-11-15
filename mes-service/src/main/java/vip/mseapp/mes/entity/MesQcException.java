package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 质量异常单（业务表 mes_qc_exception）。
 *
 * <p>status：0 已登记、1 处理中、2 已关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_exception")
public class MesQcException extends BaseEntity {

    /** 异常单号（租户内唯一，如 QE202609240001） */
    private String code;

    /** 标题 */
    private String title;

    /** 级别：1 一般 2 重大 3 危急 */
    private Integer level;

    /** 关联生产订单 id（可空） */
    private Long orderId;

    /** 异常描述 */
    private String description;

    /** 原因分析（5Why/鱼骨图结论等） */
    private String causeAnalysis;

    /** 纠正措施 */
    private String action;

    /** 登记人用户 id */
    private Long reporterId;

    /** 关闭人用户 id */
    private Long closeBy;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 状态：0 已登记 1 处理中 2 已关闭 */
    private Integer status;
}
