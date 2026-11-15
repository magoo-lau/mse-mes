package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.math.BigDecimal;

/**
 * 设备数采配置（三期，「设备数采监控」页维护；(tenant_id, equipment_id) 唯一）。
 *
 * <p>额定小时产能供 OEE 性能指数分母（未配置或 ≤0 时性能指数按 1 计并在响应标注）；
 * enabled=false 的设备不参与实时监控看板。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_iot_config")
public class MesIotConfig extends BaseEntity {

    /** 设备 id（mes_equipment.id） */
    private Long equipmentId;

    /** 额定小时产量（OEE 性能指数基准；可空） */
    private BigDecimal ratedCapacity;

    /** 是否启用实时监控（0 否 1 是） */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
