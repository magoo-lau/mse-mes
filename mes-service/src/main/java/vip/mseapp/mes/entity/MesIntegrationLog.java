package vip.mseapp.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.time.LocalDateTime;

/**
 * 系统集成同步日志（二期，「系统集成」页查询）。
 *
 * <p>direction：in ERP→MES（订单/物料/BOM 引入）；out 生产结果回传已退役（2026-10-20，
 * 历史出站行随退役脚本清理，工单完工通知改经框架通知中枢）。
 * status：in 用 1 成功 / 2 失败；0/3 为原出站重试语义（随出站退役）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_integration_log")
public class MesIntegrationLog extends BaseEntity {

    public static final int ST_PENDING = 0, ST_SUCCESS = 1, ST_FAILED = 2, ST_DEAD = 3;

    /** 方向：in 入站 / out 出站 */
    private String direction;

    /** 同步类型（订单 order / 物料 material / BOM bom；result 为原出站回传已退役） */
    private String type;

    /** 业务单号（工单号等，便于检索） */
    private String bizCode;

    /** 外部单号（ERP 侧单号，入站幂等键） */
    private String externalCode;

    /** 状态（见类注释） */
    private Integer status;

    /** 处理条数（批量导入为明细数） */
    private Integer total;

    /** 成功条数 */
    private Integer success;

    /** 来源/目标摘要（如开放平台应用名、回传 URL host） */
    private String channel;

    /** 报文摘要（截断存储，便于排查） */
    private String request;

    /** 响应摘要（截断存储） */
    private String response;

    /** 失败原因 */
    private String error;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（到期待重试行由后台扫描重推） */
    private LocalDateTime nextRetryTime;
}
