package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 样本测量值（三期）：控制图数据点，按采集时间分组为子组（每组 n=监控项 sample_size）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_spc_sample")
public class MesSpcSample extends BaseEntity {

    /** 来源：1 手工录入 2 数采 */
    public static final int SOURCE_MANUAL = 1, SOURCE_IOT = 2;

    /** 监控项 id（mes_spc_item.id） */
    private Long itemId;

    /** 测量值 */
    private BigDecimal value;

    /** 关联工单号（可空，便于追溯） */
    private String orderCode;

    /** 来源：1 手工录入 2 数采 */
    private Integer source;

    /** 采样时间 */
    private LocalDateTime collectTime;

    /** 备注 */
    private String remark;
}
