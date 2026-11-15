package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import vip.mseapp.mybatis.data.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工艺路线工序（业务表 mes_route_step，按 stepNo 升序执行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_step")
public class MesRouteStep extends BaseEntity {

    /** 工艺路线 id（mes_route.id） */
    private Long routeId;

    /** 工序号（1 起递增） */
    private Integer stepNo;

    /** 工序名称 */
    private String stepName;

    /** 默认产线 id（mes_model.id type=3，可空） */
    private Long lineId;

    /** 默认工位 id（mes_model.id type=4，可空） */
    private Long workstationId;

    /** 标准工时（秒/件） */
    private Integer standardTime;

    /** 是否质检点：0 否 1 是（报工自动生成 IPQC 检验任务） */
    private Integer qcRequired;
}
