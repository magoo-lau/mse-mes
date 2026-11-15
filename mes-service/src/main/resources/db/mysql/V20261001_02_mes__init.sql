-- mse-target: master
SET NAMES utf8mb4;
-- =====================================================================
-- MES 制造执行系统业务表初始化脚本（独立工程 mse-mes，MySQL，一/二/三期全量）
-- 目标库：业务主库 + 各租户独立库（mse-target: master 随启动自动迁移）
-- 内容：一期 27 张业务表（建模/计划/执行/报工/质量/设备/物料/追溯域）+ Seata undo_log
--       + 二期 AI 智能化（mes_ai_agent_map）/系统集成（mes_integration_config/log）
--       + 三期 IoT 数采（mes_iot_config/collect）/安灯（mes_andon_call）/SPC（mes_spc_item/sample）
-- 配套：V20261001_01_mes__base.sql（mse-target: base：子应用注册 + 页面菜单 + 权限点）、
--       V20261001_03_mes__roles.sql（mse-target: master + mse-per-tenant: true：角色种子）
-- 表结构规范：全部继承 BaseEntity 列（id 雪花主键/tenant_id/create_by/create_time/update_by/
-- update_time/deleted 逻辑删除），tenant_id 由租户插件行级隔离，业务代码禁止手工赋值；
-- mes_integration_log 建表直接带 create_by/update_by 审计列
-- 幂等：CREATE TABLE IF NOT EXISTS，重复应用无害
-- =====================================================================

-- ==================== 基础数据建模域 ====================

CREATE TABLE IF NOT EXISTS mes_model
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    type        INT          NOT NULL COMMENT '类型：1 工厂 2 车间 3 产线 4 工位',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级节点id（根为0）',
    code        VARCHAR(50)  NOT NULL COMMENT '编码',
    name        VARCHAR(100) NOT NULL COMMENT '名称',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB COMMENT ='制造模型节点（工厂/车间/产线/工位）';

CREATE TABLE IF NOT EXISTS mes_material
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code        VARCHAR(50)  NOT NULL COMMENT '物料编码',
    name        VARCHAR(100) NOT NULL COMMENT '物料名称',
    spec        VARCHAR(100) NULL COMMENT '规格型号',
    unit        VARCHAR(20)  NULL COMMENT '单位',
    type        INT          NOT NULL DEFAULT 1 COMMENT '类型：1 原材料 2 半成品 3 成品',
    trace_mode  INT          NOT NULL DEFAULT 1 COMMENT '追溯粒度：1 按批次 2 按序列号',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_code (code)
) ENGINE = InnoDB COMMENT ='物料主数据';

CREATE TABLE IF NOT EXISTS mes_bom
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code        VARCHAR(50)  NOT NULL COMMENT 'BOM编码',
    material_id BIGINT       NOT NULL COMMENT '父项物料id',
    version     VARCHAR(20)  NULL COMMENT '版本',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_material (material_id)
) ENGINE = InnoDB COMMENT ='BOM 头';

CREATE TABLE IF NOT EXISTS mes_bom_line
(
    id          BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    bom_id      BIGINT        NOT NULL COMMENT 'BOM头id',
    material_id BIGINT        NOT NULL COMMENT '子件物料id',
    qty         DECIMAL(18, 4) NOT NULL DEFAULT 1 COMMENT '单位用量',
    loss_rate   DECIMAL(8, 4) NULL COMMENT '损耗率（%）',
    create_by   BIGINT        NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT        NULL,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_bom (bom_id)
) ENGINE = InnoDB COMMENT ='BOM 明细行';

CREATE TABLE IF NOT EXISTS mes_route
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code        VARCHAR(50)  NOT NULL COMMENT '工艺路线编码',
    name        VARCHAR(100) NOT NULL COMMENT '名称',
    material_id BIGINT       NULL COMMENT '适用物料id',
    version     VARCHAR(20)  NULL COMMENT '版本',
    status      INT          NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 已发布',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_material (material_id)
) ENGINE = InnoDB COMMENT ='工艺路线头';

CREATE TABLE IF NOT EXISTS mes_route_step
(
    id             BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    route_id       BIGINT       NOT NULL COMMENT '工艺路线id',
    step_no        INT          NOT NULL COMMENT '工序号（1 起递增）',
    step_name      VARCHAR(100) NOT NULL COMMENT '工序名称',
    line_id        BIGINT       NULL COMMENT '默认产线id',
    workstation_id BIGINT       NULL COMMENT '默认工位id',
    standard_time  INT          NULL COMMENT '标准工时（秒/件）',
    qc_required    INT          NOT NULL DEFAULT 0 COMMENT '是否质检点：0 否 1 是',
    create_by      BIGINT       NULL,
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      BIGINT       NULL,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_route (route_id)
) ENGINE = InnoDB COMMENT ='工艺路线工序';

CREATE TABLE IF NOT EXISTS mes_calendar
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    work_date   DATE         NOT NULL COMMENT '日期',
    shift_code  VARCHAR(20)  NOT NULL COMMENT '班次编码',
    shift_name  VARCHAR(50)  NOT NULL COMMENT '班次名称',
    start_time  TIME         NULL COMMENT '开始时间',
    end_time    TIME         NULL COMMENT '结束时间',
    type        INT          NOT NULL DEFAULT 1 COMMENT '类型：1 工作 2 休息',
    line_id     BIGINT       NULL COMMENT '适用产线id（空=全厂）',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_date (work_date)
) ENGINE = InnoDB COMMENT ='工作日历与班次';

-- ==================== 生产计划域 ====================

CREATE TABLE IF NOT EXISTS mes_order
(
    id           BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    code         VARCHAR(50)   NOT NULL COMMENT '工单号',
    product_id   BIGINT        NOT NULL COMMENT '产品物料id',
    bom_id       BIGINT        NULL COMMENT 'BOM id',
    route_id     BIGINT        NULL COMMENT '工艺路线id',
    plan_qty     DECIMAL(18, 4) NOT NULL COMMENT '计划数量',
    finished_qty DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '完工数量',
    ok_qty       DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '合格数量',
    ng_qty       DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '不良数量',
    plan_start   DATE          NULL COMMENT '计划开始日期',
    plan_end     DATE          NULL COMMENT '计划完成日期',
    status       INT           NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 已提交 2 已下达 3 生产中 4 完工 5 已关闭 6 已取消',
    release_time DATETIME      NULL COMMENT '下达时间',
    finish_time  DATETIME      NULL COMMENT '完工时间',
    remark       VARCHAR(255)  NULL COMMENT '备注',
    create_by    BIGINT        NULL,
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    BIGINT        NULL,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='生产订单（工单）';

CREATE TABLE IF NOT EXISTS mes_plan
(
    id         BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id  BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    order_id   BIGINT        NOT NULL COMMENT '生产订单id',
    line_id    BIGINT        NOT NULL COMMENT '产线id',
    work_date  DATE          NOT NULL COMMENT '排产日期',
    shift_code VARCHAR(20)   NOT NULL COMMENT '班次编码',
    plan_qty   DECIMAL(18, 4) NOT NULL COMMENT '计划数量',
    status     INT           NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 已发布',
    remark     VARCHAR(255)  NULL COMMENT '备注',
    create_by  BIGINT        NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  BIGINT        NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_date (work_date)
) ENGINE = InnoDB COMMENT ='排产计划';

-- ==================== 生产执行域 ====================

CREATE TABLE IF NOT EXISTS mes_dispatch
(
    id             BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    order_id       BIGINT        NOT NULL COMMENT '生产订单id',
    step_id        BIGINT        NULL COMMENT '工序id（无工序工单为空）',
    step_no        INT           NULL COMMENT '工序号',
    step_name      VARCHAR(100)  NULL COMMENT '工序名称',
    line_id        BIGINT        NULL COMMENT '产线id',
    workstation_id BIGINT        NULL COMMENT '工位id',
    assignee_id    BIGINT        NULL COMMENT '指派操作工用户id',
    plan_qty       DECIMAL(18, 4) NOT NULL COMMENT '派工数量',
    reported_qty   DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已报工数量',
    status         INT           NOT NULL DEFAULT 0 COMMENT '状态：0 待开工 1 生产中 2 已完成',
    start_time     DATETIME      NULL COMMENT '开工时间',
    finish_time    DATETIME      NULL COMMENT '完成时间',
    rework         INT           NOT NULL DEFAULT 0 COMMENT '是否返工工序：0 否 1 是',
    create_by      BIGINT        NULL,
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      BIGINT        NULL,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='派工单';

CREATE TABLE IF NOT EXISTS mes_transfer
(
    id               BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    order_id         BIGINT        NOT NULL COMMENT '生产订单id',
    from_dispatch_id BIGINT        NOT NULL COMMENT '源派工单id',
    to_dispatch_id   BIGINT        NOT NULL COMMENT '目标派工单id',
    qty              DECIMAL(18, 4) NOT NULL COMMENT '转移数量',
    operator_id      BIGINT        NULL COMMENT '操作人用户id',
    transfer_time    DATETIME      NOT NULL COMMENT '转移时间',
    remark           VARCHAR(255)  NULL COMMENT '备注',
    create_by        BIGINT        NULL,
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        BIGINT        NULL,
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='工序转移记录';

CREATE TABLE IF NOT EXISTS mes_halt
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    order_id    BIGINT       NULL COMMENT '生产订单id',
    line_id     BIGINT       NULL COMMENT '产线id',
    reason      INT          NOT NULL COMMENT '原因：1 设备故障 2 缺料 3 质量异常 4 计划变更 9 其他',
    start_time  DATETIME     NOT NULL COMMENT '开始时间',
    end_time    DATETIME     NULL COMMENT '恢复时间（空=停工中）',
    reporter_id BIGINT       NULL COMMENT '登记人用户id',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='异常停工记录';

-- ==================== 报工域 ====================

CREATE TABLE IF NOT EXISTS mes_report
(
    id            BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    code          VARCHAR(50)   NOT NULL COMMENT '报工单号',
    order_id      BIGINT        NOT NULL COMMENT '生产订单id',
    dispatch_id   BIGINT        NULL COMMENT '派工单id',
    step_id       BIGINT        NULL COMMENT '工序id',
    workstation_id BIGINT       NULL COMMENT '工位id',
    reporter_id   BIGINT        NULL COMMENT '报工人用户id',
    type          INT           NOT NULL DEFAULT 1 COMMENT '类型：1 正常 2 返工',
    ok_qty        DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '合格数量',
    ng_qty        DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '不良数量',
    defect_reason VARCHAR(255)  NULL COMMENT '不良原因说明',
    report_time   DATETIME      NOT NULL COMMENT '报工时间',
    status        INT           NOT NULL DEFAULT 0 COMMENT '状态：0 待审核 1 已审核 2 已驳回',
    audit_by      BIGINT        NULL COMMENT '审核人用户id',
    audit_time    DATETIME      NULL COMMENT '审核时间',
    audit_remark  VARCHAR(255)  NULL COMMENT '审核意见',
    create_by     BIGINT        NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     BIGINT        NULL,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='报工记录';

-- ==================== 质量域 ====================

CREATE TABLE IF NOT EXISTS mes_qc_scheme
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code        VARCHAR(50)  NOT NULL COMMENT '方案编码',
    name        VARCHAR(100) NOT NULL COMMENT '方案名称',
    qc_type     INT          NOT NULL COMMENT '检验类型：1 IQC 2 IPQC 3 FQC',
    material_id BIGINT       NULL COMMENT '适用物料id（空=通用）',
    sample_rule VARCHAR(255) NULL COMMENT '抽样规则',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT       NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='检验方案头';

CREATE TABLE IF NOT EXISTS mes_qc_scheme_item
(
    id         BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    scheme_id  BIGINT       NOT NULL COMMENT '检验方案id',
    item_name  VARCHAR(100) NOT NULL COMMENT '检验项目名称',
    standard   VARCHAR(255) NULL COMMENT '标准要求',
    method     VARCHAR(255) NULL COMMENT '检验方法',
    create_by  BIGINT       NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  BIGINT       NULL,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_scheme (scheme_id)
) ENGINE = InnoDB COMMENT ='检验方案项目';

CREATE TABLE IF NOT EXISTS mes_qc_order
(
    id           BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    code         VARCHAR(50)   NOT NULL COMMENT '检验单号',
    qc_type      INT           NOT NULL COMMENT '检验类型：1 IQC 2 IPQC 3 FQC',
    scheme_id    BIGINT        NULL COMMENT '检验方案id',
    order_id     BIGINT        NULL COMMENT '生产订单id',
    step_id      BIGINT        NULL COMMENT '工序id',
    report_id    BIGINT        NULL COMMENT '来源报工单id',
    material_id  BIGINT        NULL COMMENT '物料id',
    batch_no     VARCHAR(50)   NULL COMMENT '批次号',
    sample_qty   DECIMAL(18, 4) NULL COMMENT '送检数量',
    ok_qty       DECIMAL(18, 4) NULL COMMENT '合格数量',
    ng_qty       DECIMAL(18, 4) NULL COMMENT '不良数量',
    result       INT           NOT NULL DEFAULT 0 COMMENT '结果：0 待检 1 合格 2 不合格 3 让步',
    inspect_data TEXT          NULL COMMENT '检验数据/描述',
    inspector_id BIGINT        NULL COMMENT '检验人用户id',
    inspect_time DATETIME      NULL COMMENT '检验时间',
    status       INT           NOT NULL DEFAULT 0 COMMENT '状态：0 待检 1 已完成',
    create_by    BIGINT        NULL,
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    BIGINT        NULL,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='检验单';

CREATE TABLE IF NOT EXISTS mes_defect
(
    id            BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    qc_order_id   BIGINT        NULL COMMENT '来源检验单id',
    order_id      BIGINT        NULL COMMENT '生产订单id',
    material_id   BIGINT        NULL COMMENT '物料id',
    batch_no      VARCHAR(50)   NULL COMMENT '批次号',
    qty           DECIMAL(18, 4) NOT NULL COMMENT '不良数量',
    reason        VARCHAR(255)  NULL COMMENT '不良原因',
    dispose       INT           NULL COMMENT '处置方式：1 返工 2 报废 3 让步接收',
    dispose_remark VARCHAR(255) NULL COMMENT '处置说明',
    dispose_by    BIGINT        NULL COMMENT '处置人用户id',
    dispose_time  DATETIME      NULL COMMENT '处置时间',
    status        INT           NOT NULL DEFAULT 0 COMMENT '状态：0 待处置 1 已处置',
    create_by     BIGINT        NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     BIGINT        NULL,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='不良品处置单';

CREATE TABLE IF NOT EXISTS mes_qc_exception
(
    id             BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code           VARCHAR(50)  NOT NULL COMMENT '异常单号',
    title          VARCHAR(200) NOT NULL COMMENT '标题',
    level          INT          NOT NULL DEFAULT 1 COMMENT '级别：1 一般 2 重大 3 危急',
    order_id       BIGINT       NULL COMMENT '关联生产订单id',
    description    TEXT         NULL COMMENT '异常描述',
    cause_analysis TEXT         NULL COMMENT '原因分析',
    action         TEXT         NULL COMMENT '纠正措施',
    reporter_id    BIGINT       NULL COMMENT '登记人用户id',
    close_by       BIGINT       NULL COMMENT '关闭人用户id',
    close_time     DATETIME     NULL COMMENT '关闭时间',
    status         INT          NOT NULL DEFAULT 0 COMMENT '状态：0 已登记 1 处理中 2 已关闭',
    create_by      BIGINT       NULL,
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      BIGINT       NULL,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='质量异常单';

-- ==================== 设备域 ====================

CREATE TABLE IF NOT EXISTS mes_equipment
(
    id             BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code           VARCHAR(50)  NOT NULL COMMENT '设备编码',
    name           VARCHAR(100) NOT NULL COMMENT '设备名称',
    model          VARCHAR(100) NULL COMMENT '规格型号',
    workstation_id BIGINT       NULL COMMENT '所在工位id',
    status         INT          NOT NULL DEFAULT 2 COMMENT '状态：1 运行 2 待机 3 维修 4 停机',
    buy_date       DATE         NULL COMMENT '购置日期',
    remark         VARCHAR(255) NULL COMMENT '备注',
    create_by      BIGINT       NULL,
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      BIGINT       NULL,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='设备台账';

CREATE TABLE IF NOT EXISTS mes_check_plan
(
    id           BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    name         VARCHAR(100) NOT NULL COMMENT '计划名称',
    equipment_id BIGINT       NOT NULL COMMENT '设备id',
    type         INT          NOT NULL DEFAULT 1 COMMENT '类型：1 点检 2 保养',
    cycle        INT          NOT NULL DEFAULT 1 COMMENT '周期：1 日 2 周 3 月',
    next_date    DATE         NULL COMMENT '下次执行日期',
    content      VARCHAR(500) NULL COMMENT '执行内容说明',
    status       INT          NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    create_by    BIGINT       NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    BIGINT       NULL,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_equipment (equipment_id)
) ENGINE = InnoDB COMMENT ='点检保养计划';

CREATE TABLE IF NOT EXISTS mes_check_record
(
    id          BIGINT      NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '租户编号',
    plan_id     BIGINT      NULL COMMENT '计划id',
    equipment_id BIGINT     NOT NULL COMMENT '设备id',
    check_date  DATE        NOT NULL COMMENT '执行日期',
    result      INT         NOT NULL COMMENT '结果：1 正常 2 异常',
    checker_id  BIGINT      NULL COMMENT '执行人用户id',
    check_time  DATETIME    NOT NULL COMMENT '执行时间',
    remark      VARCHAR(500) NULL COMMENT '检查说明/异常描述',
    create_by   BIGINT      NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT      NULL,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_equipment (equipment_id)
) ENGINE = InnoDB COMMENT ='点检保养执行记录';

CREATE TABLE IF NOT EXISTS mes_repair
(
    id               BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code             VARCHAR(50)  NOT NULL COMMENT '维修工单号',
    equipment_id     BIGINT       NOT NULL COMMENT '设备id',
    check_record_id  BIGINT       NULL COMMENT '来源点检记录id',
    fault_desc       VARCHAR(500) NULL COMMENT '故障描述',
    report_by        BIGINT       NULL COMMENT '报修人用户id',
    report_time      DATETIME     NOT NULL COMMENT '报修时间',
    repair_by        BIGINT       NULL COMMENT '维修人用户id',
    handle_desc      VARCHAR(500) NULL COMMENT '处理说明',
    finish_time      DATETIME     NULL COMMENT '完工时间',
    downtime_minutes BIGINT       NULL COMMENT '停机时长（分钟）',
    status           INT          NOT NULL DEFAULT 0 COMMENT '状态：0 报修 1 维修中 2 已完工',
    create_by        BIGINT       NULL,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        BIGINT       NULL,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_equipment (equipment_id)
) ENGINE = InnoDB COMMENT ='设备维修工单';

-- ==================== 物料域 ====================

CREATE TABLE IF NOT EXISTS mes_mat_req
(
    id          BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    code        VARCHAR(50)   NOT NULL COMMENT '单号',
    type        INT           NOT NULL COMMENT '类型：1 领料 2 退料',
    order_id    BIGINT        NULL COMMENT '生产订单id',
    material_id BIGINT        NOT NULL COMMENT '物料id',
    batch_no    VARCHAR(50)   NULL COMMENT '批次号（退料必填）',
    req_qty     DECIMAL(18, 4) NOT NULL COMMENT '申请数量',
    applicant_id BIGINT       NULL COMMENT '申请人用户id',
    req_time    DATETIME      NOT NULL COMMENT '申请时间',
    issue_by    BIGINT        NULL COMMENT '发料/退料执行人用户id',
    issue_time  DATETIME      NULL COMMENT '执行时间',
    status      INT           NOT NULL DEFAULT 0 COMMENT '状态：0 已申请 1 已执行 2 已驳回',
    remark      VARCHAR(255)  NULL COMMENT '备注',
    create_by   BIGINT        NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT        NULL,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='领退料单';

CREATE TABLE IF NOT EXISTS mes_stock
(
    id          BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    material_id BIGINT        NOT NULL COMMENT '物料id',
    batch_no    VARCHAR(50)   NOT NULL DEFAULT '-' COMMENT '批次号',
    qty         DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '库存数量',
    location    VARCHAR(50)   NOT NULL DEFAULT 'LINE' COMMENT '仓位',
    create_by   BIGINT        NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT        NULL,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_material (material_id, batch_no)
) ENGINE = InnoDB COMMENT ='线边仓库存';

CREATE TABLE IF NOT EXISTS mes_stock_log
(
    id          BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    material_id BIGINT        NOT NULL COMMENT '物料id',
    batch_no    VARCHAR(50)   NOT NULL DEFAULT '-' COMMENT '批次号',
    change_qty  DECIMAL(18, 4) NOT NULL COMMENT '变动数量（正入负出）',
    balance_qty DECIMAL(18, 4) NOT NULL COMMENT '变动后余额',
    ref_type    INT           NOT NULL COMMENT '来源：1 领料 2 退料 3 报工产出 4 手工调整',
    ref_id      BIGINT        NULL COMMENT '来源单据id',
    operator_id BIGINT        NULL COMMENT '操作人用户id',
    op_time     DATETIME      NOT NULL COMMENT '操作时间',
    remark      VARCHAR(255)  NULL COMMENT '备注',
    create_by   BIGINT        NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT        NULL,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_material (material_id)
) ENGINE = InnoDB COMMENT ='库存流水';

-- ==================== 追溯域 ====================

CREATE TABLE IF NOT EXISTS mes_batch
(
    id         BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id  BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    batch_no   VARCHAR(50)   NOT NULL COMMENT '批次号',
    material_id BIGINT       NOT NULL COMMENT '物料id',
    source     INT           NOT NULL DEFAULT 1 COMMENT '来源：1 生产 2 采购',
    order_id   BIGINT        NULL COMMENT '生产订单id（生产批次）',
    qty        DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '数量',
    remark     VARCHAR(255)  NULL COMMENT '备注',
    create_by  BIGINT        NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  BIGINT        NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_batch (tenant_id, batch_no),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='批次（追溯主档）';

CREATE TABLE IF NOT EXISTS mes_sn
(
    id         BIGINT      NOT NULL COMMENT '主键ID',
    tenant_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '租户编号',
    sn         VARCHAR(50) NOT NULL COMMENT '序列号',
    material_id BIGINT     NOT NULL COMMENT '物料id',
    order_id   BIGINT      NULL COMMENT '生产订单id',
    batch_no   VARCHAR(50) NULL COMMENT '批次号',
    step_id    BIGINT      NULL COMMENT '当前工序id',
    status     INT         NOT NULL DEFAULT 1 COMMENT '状态：1 在制 2 完工 3 报废',
    create_by  BIGINT      NULL,
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  BIGINT      NULL,
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_sn (tenant_id, sn),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='序列号（按件追溯）';

-- Seata undo_log（AT 模式按分支数据源记录回滚日志；独立库租户由克隆+迁移自动带上同构对象）
CREATE TABLE IF NOT EXISTS undo_log
(
    branch_id     BIGINT       NOT NULL COMMENT '分支事务 ID',
    xid           VARCHAR(128) NOT NULL COMMENT '全局事务 ID',
    context       VARCHAR(128) NOT NULL COMMENT '回滚上下文（序列化方式等）',
    rollback_info LONGBLOB     NOT NULL COMMENT '回滚数据快照（前后镜像）',
    log_status    INT          NOT NULL COMMENT '日志状态（0 正常 1 全局已完成）',
    log_created   DATETIME(6)  NOT NULL COMMENT '日志创建时间',
    log_modified  DATETIME(6)  NOT NULL COMMENT '日志修改时间',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE = InnoDB COMMENT ='Seata AT 模式回滚日志';

-- 权限点（4401-4470）在 V20260924_01_mes__base.sql（mse-target: base）
-- 角色种子（mes_planner 等 9 角色）在 V20260924_02_mes__roles.sql（mse-per-tenant: true）

-- ==================== 二期：AI 智能化 + 系统集成 ====================

-- ---------------- AI 场景 → 智能体映射（「AI 智能化设置」页维护；agent_code 空=使用内置默认） ----------------
CREATE TABLE IF NOT EXISTS mes_ai_agent_map
(
    id          BIGINT      NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '租户编号',
    scene       VARCHAR(32) NOT NULL COMMENT '场景（report 日报 / quality 质量诊断 / equip 设备诊断 / data_qa 问数 / schedule 排产建议 / kb 工艺问答）',
    agent_code  VARCHAR(64) NULL COMMENT '映射的智能体编码（ai_agent.agent_code；空串=使用内置默认）',
    create_by   BIGINT      NULL COMMENT '创建者',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT      NULL COMMENT '更新者',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_scene (tenant_id, scene)
) ENGINE = InnoDB COMMENT ='AI 场景 → 智能体映射（租户隔离；保存即生效，不删行）';

-- ---------------- 系统集成配置（「系统集成」页维护；(tenant_id, type) 唯一） ----------------
CREATE TABLE IF NOT EXISTS mes_integration_config
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    type        VARCHAR(32)  NOT NULL COMMENT '类型（erp_sync ERP 入站同步 / result_push 生产结果回传出站）',
    enabled     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用（0 停用 1 启用）',
    target_url  VARCHAR(500) NULL COMMENT '出站回传目标 URL（result_push 用；POST JSON）',
    secret      VARCHAR(128) NULL COMMENT 'HMAC-SHA256 签名密钥（可选；不回显）',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NULL COMMENT '创建者',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新者',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_type (tenant_id, type)
) ENGINE = InnoDB COMMENT ='系统集成配置（租户隔离）';

-- ---------------- 系统集成同步日志（入站引入 + 出站回传重试队列：超限转死信） ----------------
CREATE TABLE IF NOT EXISTS mes_integration_log
(
    id              BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    direction       VARCHAR(8)   NOT NULL COMMENT '方向（in ERP→MES / out MES→ERP）',
    type            VARCHAR(32)  NOT NULL COMMENT '同步类型（order 订单 / material 物料 / bom BOM / result 结果回传）',
    external_code   VARCHAR(64)  NULL COMMENT '外部单号（ERP 侧单号，入站幂等键）',
    biz_code        VARCHAR(64)  NULL COMMENT '业务单号（工单号等，便于检索）',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态（0 待推送 1 成功 2 失败待重试 3 死信）',
    total           INT          NOT NULL DEFAULT 0 COMMENT '处理条数',
    success         INT          NOT NULL DEFAULT 0 COMMENT '成功条数',
    channel         VARCHAR(64)  NULL COMMENT '来源/目标摘要（mse-open、回传 URL host 等）',
    request         TEXT         NULL COMMENT '报文摘要（截断存储）',
    response        TEXT         NULL COMMENT '响应摘要（截断存储）',
    error           VARCHAR(500) NULL COMMENT '失败原因',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    create_by     BIGINT       NULL COMMENT '创建者',
    next_retry_time DATETIME     NULL COMMENT '下次重试时间（到期待重试行由后台扫描重推）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新者',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant_dir_type (tenant_id, direction, type, status),
    KEY idx_tenant_retry (tenant_id, status, next_retry_time),
    KEY idx_tenant_ext (tenant_id, external_code)
) ENGINE = InnoDB COMMENT ='系统集成同步日志（重试队列：超限转死信）';

-- ==================== 三期：IoT 数采 + 安灯 + SPC ====================

-- ---------------- 设备数采配置（(tenant_id, equipment_id) 唯一；额定产能供 OEE 性能指数） ----------------
CREATE TABLE IF NOT EXISTS mes_iot_config
(
    id             BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    equipment_id   BIGINT        NOT NULL COMMENT '设备 id（mes_equipment.id）',
    rated_capacity DECIMAL(12, 2) NULL COMMENT '额定小时产量（OEE 性能指数基准；未配置按 1 计）',
    enabled        TINYINT       NOT NULL DEFAULT 0 COMMENT '是否启用实时监控（0 否 1 是）',
    remark         VARCHAR(255)  NULL COMMENT '备注',
    create_by      BIGINT        NULL COMMENT '创建者',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT        NULL COMMENT '更新者',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_equipment (tenant_id, equipment_id)
) ENGINE = InnoDB COMMENT ='设备数采配置（三期；租户隔离）';

-- ---------------- 设备数采明细（状态 + 累计产量计数快照；增量按设备前后差值折算） ----------------
CREATE TABLE IF NOT EXISTS mes_iot_collect
(
    id             BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    equipment_id   BIGINT        NOT NULL COMMENT '设备 id（mes_equipment.id）',
    equipment_code VARCHAR(64)   NULL COMMENT '设备编码（上报解析后冗余，便于检索）',
    status         TINYINT       NOT NULL COMMENT '设备状态：1 运行 2 待机 3 维修 4 停机',
    output_qty     DECIMAL(14, 2) NULL COMMENT '累计产量计数（上报原值，计数器语义）',
    output_delta   DECIMAL(14, 2) NOT NULL DEFAULT 0 COMMENT '产量增量（相对上一条折算）',
    collect_time   DATETIME      NOT NULL COMMENT '上报时间',
    create_by      BIGINT        NULL COMMENT '创建者',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT        NULL COMMENT '更新者',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant_equip_time (tenant_id, equipment_id, collect_time)
) ENGINE = InnoDB COMMENT ='设备数采明细（三期；IoT 网关/模拟上报）';

-- ---------------- 安灯呼叫（超时逐级升级：escalate_level 0→2，后台扫描触发） ----------------
CREATE TABLE IF NOT EXISTS mes_andon_call
(
    id                 BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id          BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    code               VARCHAR(64)  NOT NULL COMMENT '呼叫单号（租户内唯一，如 AL202609250001）',
    type               TINYINT      NOT NULL COMMENT '呼叫类型：1 设备 2 质量 3 物料',
    line_id            BIGINT       NULL COMMENT '产线 id（mes_model.id type=3）',
    station_id         BIGINT       NULL COMMENT '工位 id（mes_model.id type=4）',
    description        VARCHAR(500) NULL COMMENT '问题描述',
    status             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 待响应 1 处理中 2 已关闭',
    escalate_level     TINYINT      NOT NULL DEFAULT 0 COMMENT '升级级别（0 未升级；超时逐级 +1，上限 2）',
    last_escalate_time DATETIME     NULL COMMENT '最近升级时间',
    call_by            BIGINT       NULL COMMENT '呼叫人',
    call_time          DATETIME     NOT NULL COMMENT '呼叫时间',
    respond_by         BIGINT       NULL COMMENT '响应人',
    respond_time       DATETIME     NULL COMMENT '响应时间',
    close_by           BIGINT       NULL COMMENT '关闭人',
    close_time         DATETIME     NULL COMMENT '关闭时间',
    handle_result      VARCHAR(500) NULL COMMENT '处理结果',
    create_by          BIGINT       NULL COMMENT '创建者',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          BIGINT       NULL COMMENT '更新者',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_code (tenant_id, code),
    KEY idx_tenant_status (tenant_id, status, call_time),
    KEY idx_tenant_time (tenant_id, call_time)
) ENGINE = InnoDB COMMENT ='安灯呼叫（三期；超时逐级升级）';

-- ---------------- SPC 监控项（X̄-R 控制图的规格与子组配置） ----------------
CREATE TABLE IF NOT EXISTS mes_spc_item
(
    id           BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    code         VARCHAR(64)   NOT NULL COMMENT '监控项编码（租户内唯一）',
    name         VARCHAR(128)  NOT NULL COMMENT '监控项名称（如「灌装量」）',
    material_id  BIGINT        NULL COMMENT '关联物料 id（mes_material.id）',
    step_name    VARCHAR(64)   NULL COMMENT '工序名（关键工序）',
    feature_name VARCHAR(128)  NULL COMMENT '质量特性名（如「净含量 g」）',
    unit         VARCHAR(32)   NULL COMMENT '计量单位',
    usl          DECIMAL(14, 4) NULL COMMENT '规格上限 USL',
    lsl          DECIMAL(14, 4) NULL COMMENT '规格下限 LSL',
    target       DECIMAL(14, 4) NULL COMMENT '目标值',
    sample_size  INT           NOT NULL DEFAULT 5 COMMENT '子组大小 n（2-10）',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0 停用 1 启用',
    remark       VARCHAR(255)  NULL COMMENT '备注',
    create_by    BIGINT        NULL COMMENT '创建者',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT        NULL COMMENT '更新者',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_tenant_code (tenant_id, code)
) ENGINE = InnoDB COMMENT ='SPC 监控项（三期；X̄-R 控制图配置）';

-- ---------------- SPC 样本测量值（按采样时间分组为子组） ----------------
CREATE TABLE IF NOT EXISTS mes_spc_sample
(
    id           BIGINT        NOT NULL COMMENT '主键ID',
    tenant_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '租户编号',
    item_id      BIGINT        NOT NULL COMMENT '监控项 id（mes_spc_item.id）',
    value        DECIMAL(14, 4) NOT NULL COMMENT '测量值',
    order_code   VARCHAR(64)   NULL COMMENT '关联工单号（可空，便于追溯）',
    source       TINYINT       NOT NULL DEFAULT 1 COMMENT '来源：1 手工录入 2 数采',
    collect_time DATETIME      NOT NULL COMMENT '采样时间',
    remark       VARCHAR(255)  NULL COMMENT '备注',
    create_by    BIGINT        NULL COMMENT '创建者',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT        NULL COMMENT '更新者',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant_item_time (tenant_id, item_id, collect_time)
) ENGINE = InnoDB COMMENT ='SPC 样本测量值（三期；控制图数据点）';
