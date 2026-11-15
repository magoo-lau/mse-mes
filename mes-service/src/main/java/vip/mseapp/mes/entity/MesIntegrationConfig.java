package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

/**
 * 系统集成配置（二期，租户隔离，「系统集成」页维护）。
 *
 * <p>type：erp_sync ERP 入站同步总开关（订单/物料/BOM 引入，经 mse-open 开放平台入站）。
 * 原 result_push 生产结果回传出站已退役（2026-10-20）——工单完工通知改经框架通知中枢
 * (mes,ORDER_FINISHED) 群发，target_url/secret 列随退役脚本一并删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_integration_config")
public class MesIntegrationConfig extends BaseEntity {

    /** 类型：ERP 入站同步 */
    public static final String TYPE_ERP_SYNC = "erp_sync";

    /** 类型（(tenant_id, type) 唯一） */
    private String type;

    /** 是否启用（0 停用 1 启用；erp_sync 停用时入站导入接口拒绝） */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
