package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数采明细（三期）：IoT 网关标准化上报的状态与产量计数快照。
 *
 * <p>上报通道：POST /mes/iot/collect（登录态，IoT 网关配置服务账号调用）；
 * 产量计数为累计值（断电保持型计数器语义），增量由本系统按设备前后差值折算（负值视为计数器重置，按 0 计）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_iot_collect")
public class MesIotCollect extends BaseEntity {

    /** 设备 id（mes_equipment.id） */
    private Long equipmentId;

    /** 设备编码（上报解析后冗余，便于检索） */
    private String equipmentCode;

    /** 设备状态：1 运行 2 待机 3 维修 4 停机 */
    private Integer status;

    /** 累计产量计数（上报原值） */
    private BigDecimal outputQty;

    /** 产量增量（相对上一条折算） */
    private BigDecimal outputDelta;

    /** 上报时间（缺省取入库时间） */
    private LocalDateTime collectTime;
}
