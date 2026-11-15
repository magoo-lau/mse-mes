-- mse-target: base
SET NAMES utf8mb4;
-- =====================================================================
-- MES 制造执行系统基座接入脚本（独立工程 mse-mes，MySQL，一/二/三期 + 移动端全量）
-- 目标库：基座库（mse-target: base；sys_* 全局表所在库）
-- 内容：子应用注册（sys_micro_app，PC + 移动端）+ 页面菜单（sys_menu type=3）+ 权限点（sys_permission）
--       + MES AI 能力模式参数 + 操作日志模块翻译字典
-- 配套：V20261001_02_mes__init.sql（mse-target: master：业务表 + undo_log）、
--       V20261001_03_mes__roles.sql（mse-target: master + mse-per-tenant: true：角色种子）
-- 幂等：全段 NOT EXISTS 判重，重复推送无害
-- 执行：自动——随业务 jar 由 starter-bridge 推送 mse-system 按分册账本应用
--       （就绪即推 + 周期重推自愈，契约 RemoteBaseDataRegistry），无需手工执行；
--       管理端启停/改名等后续人工调整不被推送覆盖
-- 号段：micro_app id=11/102、菜单 500-514（PC）+ 540-543、550 共享分组（移动端）、
--       权限点 4401-4499 + 4701、sys_config 53200、字典 51-56（登记见 references/alloc-tables.md）
-- 规划文档：mse-modules/mse-mes/docs/（02-权限规划.md 为本脚本直接输入）
-- 移动端授权口径：菜单是"入口"按端分离（移动基站工作台渲染 client=2 菜单树，独立授权/套餐裁剪）；
--       权限点是"能力"双端共享，报工/点检/设备接口权限沿用 PC 权限码，移动端 v-permission 直接复用
-- 租户端可见性：页面菜单需在「套餐管理」勾选（页面级裁剪），普通用户另需角色授权
-- =====================================================================

-- ---------------- 子应用注册（按 name 判重） ----------------
INSERT INTO sys_micro_app (id, name, title, dev_entry, prod_entry, side, sort, status, remark, create_time, update_time)
SELECT 11, 'mes', '制造执行', '//localhost:4020', '/sub-apps/mes/', 1, 0, 0, '独立工程 mse-mes（MES 制造执行系统）', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_micro_app WHERE name = 'mes');

INSERT INTO sys_micro_app (id, name, title, dev_entry, prod_entry, side, client, sort, status, remark, create_time, update_time)
SELECT 102, 'mes-mobile', '车间作业', '//localhost:4220', '/m-apps/mes/', 1, 2, 2, 0, 'MES 车间作业移动版（mes-mobile-ui）：报工/设备点检/设备状态，部署于 /m-apps/mes/', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_micro_app WHERE name = 'mes-mobile');

-- ---------------- PC 页面菜单（type=3 挂 500 分组目录；按 id 判重） ----------------
INSERT INTO sys_menu (id, parent_id, name, type, menu_scope, path, micro_name, component, icon, sort, visible, status, create_time, update_time)
SELECT t.id, t.parent_id, t.name, t.type, t.menu_scope, t.path, t.micro_name, NULL, t.icon, t.sort, 0, 0, NOW(), NOW()
FROM (
    SELECT 500 AS id, 0 AS parent_id, '制造执行' AS name, 1 AS type, 2 AS menu_scope, '/sub/mes' AS path, NULL AS micro_name, 'Box' AS icon, 0 AS sort
    UNION ALL SELECT 501, 500, '基础数据', 3, 2, '/base', 'mes', NULL, 1
    UNION ALL SELECT 502, 500, '生产计划', 3, 2, '/plan', 'mes', NULL, 2
    UNION ALL SELECT 503, 500, '生产执行', 3, 2, '/exec', 'mes', NULL, 3
    UNION ALL SELECT 504, 500, '报工工作台', 3, 2, '/report', 'mes', NULL, 4
    UNION ALL SELECT 505, 500, '质量管理', 3, 2, '/quality', 'mes', NULL, 5
    UNION ALL SELECT 506, 500, '设备管理', 3, 2, '/equipment', 'mes', NULL, 6
    UNION ALL SELECT 507, 500, '物料管理', 3, 2, '/material', 'mes', NULL, 7
    UNION ALL SELECT 508, 500, '追溯查询', 3, 2, '/trace', 'mes', NULL, 8
    UNION ALL SELECT 509, 500, '看板报表', 3, 2, '/dashboard', 'mes', NULL, 9
    UNION ALL SELECT 510, 500, 'AI 智能化设置', 3, 2, '/ai-settings', 'mes', NULL, 10
    UNION ALL SELECT 511, 500, '系统集成', 3, 2, '/integration', 'mes', NULL, 11
    UNION ALL SELECT 512, 500, '设备数采监控', 3, 2, '/iot', 'mes', NULL, 12
    UNION ALL SELECT 513, 500, '安灯管理', 3, 2, '/andon', 'mes', NULL, 13
    UNION ALL SELECT 514, 500, 'SPC 分析', 3, 2, '/spc', 'mes', NULL, 14
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.id);

-- ---------------- 移动端页面菜单（type=3 + client=2；工作台宫格入口，按 id 判重） ----------------
-- 550「移动工作台」为移动端共享分组目录（client=2，接入移动端的各业务脚本幂等共建）
INSERT INTO sys_menu (id, parent_id, name, type, menu_scope, client, path, micro_name, component, icon, sort, visible, status, create_time, update_time)
SELECT t.id, t.parent_id, t.name, t.type, t.menu_scope, t.client, t.path, t.micro_name, NULL, t.icon, t.sort, 0, 0, NOW(), NOW()
FROM (
    SELECT 550 AS id, 0 AS parent_id, '移动工作台' AS name, 1 AS type, 2 AS menu_scope, 2 AS client, '/sub/mobile' AS path, NULL AS micro_name, 'Iphone' AS icon, 1 AS sort
    UNION ALL SELECT 540, 550, '车间作业', 1, 2, 2, '/sub/mes-mobile', NULL, 'Operation', 2
    UNION ALL SELECT 541, 540, '报工',     3, 2, 2, '/report',    'mes-mobile', NULL, 1
    UNION ALL SELECT 542, 540, '设备点检', 3, 2, 2, '/check',     'mes-mobile', NULL, 2
    UNION ALL SELECT 543, 540, '设备状态', 3, 2, 2, '/equipment', 'mes-mobile', NULL, 3
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.id);

-- ---------------- 权限点（挂靠页面菜单：参与授权分组与套餐裁剪） ----------------
-- 一期 4401-4470 + 二期 AI/集成 4471-4481 + 三期 IoT/安灯/SPC 4482-4487 + 四期导入导出 4488-4499 + 补充 4701
INSERT INTO sys_permission (id, menu_id, module, name, code, sort, status, create_time, update_time)
SELECT t.id, t.menu_id, 'mes', t.name, t.code, t.sort, 0, NOW(), NOW()
FROM (
    -- 基础数据建模（挂 501）
    SELECT 4401 AS id, 501 AS menu_id, '建模查询' AS name, 'mes:model:list' AS code, 1 AS sort
    UNION ALL SELECT 4402, 501, '建模保存', 'mes:model:save', 2
    UNION ALL SELECT 4403, 501, '建模删除', 'mes:model:delete', 3
    UNION ALL SELECT 4404, 501, '物料查询', 'mes:material:list', 4
    UNION ALL SELECT 4405, 501, '物料保存', 'mes:material:save', 5
    UNION ALL SELECT 4406, 501, '物料删除', 'mes:material:delete', 6
    UNION ALL SELECT 4407, 501, 'BOM查询', 'mes:bom:list', 7
    UNION ALL SELECT 4408, 501, 'BOM保存', 'mes:bom:save', 8
    UNION ALL SELECT 4409, 501, 'BOM删除', 'mes:bom:delete', 9
    UNION ALL SELECT 4410, 501, '工艺路线查询', 'mes:route:list', 10
    UNION ALL SELECT 4411, 501, '工艺路线保存', 'mes:route:save', 11
    UNION ALL SELECT 4412, 501, '工艺路线删除', 'mes:route:delete', 12
    UNION ALL SELECT 4413, 501, '工艺路线发布', 'mes:route:publish', 13
    UNION ALL SELECT 4414, 501, '日历班次查询', 'mes:calendar:list', 14
    UNION ALL SELECT 4415, 501, '日历班次保存', 'mes:calendar:save', 15
    UNION ALL SELECT 4416, 501, '日历班次删除', 'mes:calendar:delete', 16
    UNION ALL SELECT 4488, 501, '物料导入', 'mes:material:import', 17
    UNION ALL SELECT 4489, 501, '物料导出', 'mes:material:export', 18
    UNION ALL SELECT 4490, 501, '建模导入', 'mes:model:import', 19
    UNION ALL SELECT 4491, 501, '建模导出', 'mes:model:export', 20
    UNION ALL SELECT 4492, 501, 'BOM导入', 'mes:bom:import', 21
    UNION ALL SELECT 4493, 501, 'BOM导出', 'mes:bom:export', 22
    UNION ALL SELECT 4494, 501, '工艺路线导入', 'mes:route:import', 23
    UNION ALL SELECT 4495, 501, '工艺路线导出', 'mes:route:export', 24
    -- 生产计划（挂 502）
    UNION ALL SELECT 4417, 502, '生产订单查询', 'mes:order:list', 1
    UNION ALL SELECT 4418, 502, '生产订单保存', 'mes:order:save', 2
    UNION ALL SELECT 4419, 502, '生产订单删除', 'mes:order:delete', 3
    UNION ALL SELECT 4420, 502, '生产订单提交', 'mes:order:submit', 4
    UNION ALL SELECT 4421, 502, '订单下达', 'mes:order:release', 5
    UNION ALL SELECT 4422, 502, '订单取消', 'mes:order:cancel', 6
    UNION ALL SELECT 4423, 502, '订单关闭', 'mes:order:close', 7
    UNION ALL SELECT 4424, 502, '排产计划查询', 'mes:plan:list', 8
    UNION ALL SELECT 4425, 502, '排产计划保存', 'mes:plan:save', 9
    UNION ALL SELECT 4426, 502, '排产发布', 'mes:plan:publish', 10
    -- 生产执行（挂 503）
    UNION ALL SELECT 4427, 503, '派工查询', 'mes:dispatch:list', 1
    UNION ALL SELECT 4428, 503, '工单派工', 'mes:dispatch:assign', 2
    UNION ALL SELECT 4429, 503, '工序转移', 'mes:dispatch:transfer', 3
    UNION ALL SELECT 4430, 503, '生产进度查询', 'mes:progress:list', 4
    UNION ALL SELECT 4431, 503, '在制品查询', 'mes:wip:list', 5
    UNION ALL SELECT 4432, 503, '异常停工登记', 'mes:halt:save', 6
    UNION ALL SELECT 4701, 503, '异常停工查询', 'mes:halt:list', 7
    -- 报工工作台（挂 504）
    UNION ALL SELECT 4433, 504, '报工提交', 'mes:report:do', 1
    UNION ALL SELECT 4434, 504, '报工记录查询', 'mes:report:list', 2
    UNION ALL SELECT 4435, 504, '报工审核', 'mes:report:audit', 3
    UNION ALL SELECT 4436, 504, '报工撤销', 'mes:report:undo', 4
    UNION ALL SELECT 4437, 504, '返工处理', 'mes:rework:do', 5
    -- 质量管理（挂 505）
    UNION ALL SELECT 4438, 505, '检验方案查询', 'mes:qc:scheme:list', 1
    UNION ALL SELECT 4439, 505, '检验方案保存', 'mes:qc:scheme:save', 2
    UNION ALL SELECT 4440, 505, '检验方案删除', 'mes:qc:scheme:delete', 3
    UNION ALL SELECT 4441, 505, '检验单查询', 'mes:qc:inspect:list', 4
    UNION ALL SELECT 4442, 505, '检验结果提交', 'mes:qc:inspect:submit', 5
    UNION ALL SELECT 4443, 505, '不良品处置', 'mes:qc:defect:handle', 6
    UNION ALL SELECT 4444, 505, '质量异常查询', 'mes:qc:exception:list', 7
    UNION ALL SELECT 4445, 505, '质量异常登记处理', 'mes:qc:exception:save', 8
    UNION ALL SELECT 4446, 505, '质量异常关闭', 'mes:qc:exception:close', 9
    UNION ALL SELECT 4498, 505, '检验方案导入', 'mes:qc:scheme:import', 10
    UNION ALL SELECT 4499, 505, '检验方案导出', 'mes:qc:scheme:export', 11
    -- 设备管理（挂 506）
    UNION ALL SELECT 4447, 506, '设备台账查询', 'mes:equip:list', 1
    UNION ALL SELECT 4448, 506, '设备台账保存', 'mes:equip:save', 2
    UNION ALL SELECT 4449, 506, '设备台账删除', 'mes:equip:delete', 3
    UNION ALL SELECT 4450, 506, '设备状态查询', 'mes:equip:status:list', 4
    UNION ALL SELECT 4451, 506, '点检保养计划查询', 'mes:check:plan:list', 5
    UNION ALL SELECT 4452, 506, '点检保养计划保存', 'mes:check:plan:save', 6
    UNION ALL SELECT 4453, 506, '点检保养执行提交', 'mes:check:submit', 7
    UNION ALL SELECT 4454, 506, '维修工单查询', 'mes:repair:list', 8
    UNION ALL SELECT 4455, 506, '设备报修', 'mes:repair:create', 9
    UNION ALL SELECT 4456, 506, '维修处理完工', 'mes:repair:handle', 10
    UNION ALL SELECT 4496, 506, '设备导入', 'mes:equip:import', 11
    UNION ALL SELECT 4497, 506, '设备导出', 'mes:equip:export', 12
    -- 物料管理（挂 507）
    UNION ALL SELECT 4457, 507, '领退料单查询', 'mes:matreq:list', 1
    UNION ALL SELECT 4458, 507, '领料申请', 'mes:matreq:create', 2
    UNION ALL SELECT 4459, 507, '退料申请', 'mes:matreq:return', 3
    UNION ALL SELECT 4460, 507, '发料收料执行', 'mes:matreq:issue', 4
    UNION ALL SELECT 4461, 507, '线边仓库存查询', 'mes:stock:list', 5
    UNION ALL SELECT 4462, 507, '线边仓库存调整', 'mes:stock:adjust', 6
    UNION ALL SELECT 4463, 507, '标签打印', 'mes:label:print', 7
    -- 追溯查询（挂 508）
    UNION ALL SELECT 4464, 508, '批次追溯查询', 'mes:trace:query', 1
    UNION ALL SELECT 4465, 508, '追溯报告导出', 'mes:trace:export', 2
    UNION ALL SELECT 4466, 508, '序列号查询', 'mes:sn:list', 3
    UNION ALL SELECT 4467, 508, '序列号绑定', 'mes:sn:bind', 4
    -- 看板报表（挂 509）
    UNION ALL SELECT 4468, 509, '生产看板查看', 'mes:dashboard:view', 1
    UNION ALL SELECT 4469, 509, '统计报表查询', 'mes:stats:query', 2
    UNION ALL SELECT 4470, 509, '报表导出', 'mes:stats:export', 3
    -- AI 智能化（嵌入式按钮挂所在功能页面菜单；设置管理挂 510）
    UNION ALL SELECT 4471, 509, 'AI 生产日报生成', 'mes:ai:report', 4
    UNION ALL SELECT 4472, 505, 'AI 质量异常诊断', 'mes:ai:quality-diag', 10
    UNION ALL SELECT 4473, 506, 'AI 设备故障诊断', 'mes:ai:equip-diag', 11
    UNION ALL SELECT 4474, 509, 'AI 智能问数', 'mes:ai:data-qa', 5
    UNION ALL SELECT 4475, 502, 'AI 排产建议', 'mes:ai:schedule', 11
    UNION ALL SELECT 4476, 504, 'AI 工艺/SOP 知识问答', 'mes:ai:kb-qa', 6
    UNION ALL SELECT 4477, 510, 'AI 智能化设置管理', 'mes:ai:config', 1
    -- 系统集成（挂 511）
    UNION ALL SELECT 4478, 511, '集成配置查询', 'mes:integration:config:list', 1
    UNION ALL SELECT 4479, 511, '集成配置保存', 'mes:integration:config:save', 2
    UNION ALL SELECT 4480, 511, '同步任务手动执行', 'mes:integration:sync:run', 3
    UNION ALL SELECT 4481, 511, '同步日志查询', 'mes:integration:log:list', 4
    -- IoT/安灯/SPC（挂 512-514）
    UNION ALL SELECT 4482, 512, '设备数采数据查询', 'mes:iot:collect:list', 1
    UNION ALL SELECT 4483, 513, '安灯呼叫', 'mes:andon:call', 1
    UNION ALL SELECT 4484, 513, '安灯响应处理', 'mes:andon:handle', 2
    UNION ALL SELECT 4485, 514, 'SPC 控制图查询', 'mes:spc:list', 1
    UNION ALL SELECT 4486, 513, 'AI 安灯异常摘要', 'mes:ai:andon-summary', 3
    UNION ALL SELECT 4487, 514, 'AI SPC 判读', 'mes:ai:spc-interpret', 2
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.id = t.id);

-- ---------------- 系统参数：MES AI 能力模式（基座 sys_config 平台默认行，租户可在「参数设置」覆盖） ----------------
-- 取值：off 关闭（默认）/ auto 优先 AI 服务失败回退直连 / platform 仅 AI 服务 / direct 直连模型（spring.ai.*）
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, config_type, remark, create_time, update_time)
SELECT 53200, 0, 'MES AI 能力模式', 'mes.ai.mode', 'off', 0,
       'MES 智能（AI 日报/质量诊断/设备诊断/智能问数/排产建议/SOP 问答）模式：off 关闭；auto 优先走 AI 服务、失败自动回退直连模型；platform 仅走 AI 服务（不回退）；direct 仅直连模型（依赖 spring.ai.* 配置）',
       NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.tenant_id = 0 AND c.config_key = 'mes.ai.mode');

-- ---------------- 操作日志「系统模块」翻译字典（字典类型行由基座基线维护） ----------------
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, list_class, is_default, status, remark, create_time, update_time)
SELECT t.id, t.tenant_id, t.dict_type, t.dict_label, t.dict_value, t.dict_sort, t.list_class, t.is_default, t.status, t.remark, NOW(), NOW()
FROM (
    SELECT 51 AS id, 0 AS tenant_id, 'sys_oper_module' AS dict_type, '制造模型' AS dict_label, 'mes:model' AS dict_value, 30 AS dict_sort, NULL AS list_class, 0 AS is_default, 0 AS status, NULL AS remark FROM DUAL
    UNION ALL SELECT 52, 0, 'sys_oper_module', '工艺路线', 'mes:route', 31, NULL, 0, 0, NULL FROM DUAL
    UNION ALL SELECT 53, 0, 'sys_oper_module', '物料管理', 'mes:material', 32, NULL, 0, 0, NULL FROM DUAL
    UNION ALL SELECT 54, 0, 'sys_oper_module', 'BOM管理', 'mes:bom', 33, NULL, 0, 0, NULL FROM DUAL
    UNION ALL SELECT 55, 0, 'sys_oper_module', '设备管理', 'mes:equip', 34, NULL, 0, 0, NULL FROM DUAL
    UNION ALL SELECT 56, 0, 'sys_oper_module', '检验方案', 'mes:qc:scheme', 35, NULL, 0, 0, NULL FROM DUAL
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data x WHERE x.id = t.id);
