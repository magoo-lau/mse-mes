-- mse-target: master
-- mse-per-tenant: true
SET NAMES utf8mb4;
-- =====================================================================
-- MES 角色种子脚本（独立工程 mse-mes，MySQL，每租户通道，一/二/三期 + 导入导出 + 补充全量）
-- 目标库：业务库（mse-target: master + mse-per-tenant: true：每租户执行一次、按租户记账，${tenant_id} 注入）
-- 内容：9 个租户级角色（sys_role）+ 菜单授权（sys_role_menu，500-514）+ 权限授权
--       （sys_role_permission，4401-4499 + 4701）
-- 规划文档：mse-modules/mse-mes/docs/（03-角色规划.md 为本脚本直接输入）
-- id 约定（确定性生成，共享库多租户不冲突；租户编号取低 8 位参与运算——
--   雪花租户编号直接乘会超出 BIGINT 上限，回归实测溢出，故先 % 100000000 再乘）：
--   角色 id        = ${tenant_id} % 100000000 * 1000 + 11..19
--   菜单绑定 id    = ${tenant_id} % 100000000 * 100000 + 段基(100..260 / 1100 / 2100..2800) + (menu_id - 500 + 1)
--   权限绑定 id    = ${tenant_id} % 100000000 * 100000 + 段基(1000..9000 / 11000..19000 / 21000..28000
--                   / 31000..37000 / 38000..42000) + (permission_id - 4400)
-- 幂等：角色按 (tenant_id, code) 判重、绑定按 (role_id, menu_id/permission_id) 判重，重复应用无害
-- 说明：种子是「开箱即用」的默认授权，租户管理员可在基座「角色管理」微调
-- =====================================================================

-- ==================== 角色定义（sys_role） ====================

-- ---------------- 角色定义（sys_role） ----------------
INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 11, ${tenant_id}, 'MES系统管理员', 'mes_admin', 11, 1, 0, '建模/AI设置/集成配置，全模块管理（种子默认，可在角色管理微调）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_admin');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 12, ${tenant_id}, '计划员', 'mes_planner', 12, 4, 0, '生产订单、排产、进度（本部门及以下）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_planner');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 13, ${tenant_id}, '调度员', 'mes_dispatcher', 13, 3, 0, '派工、工序转移、现场调度（本部门）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_dispatcher');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 14, ${tenant_id}, '车间主任', 'mes_leader', 14, 4, 0, '报工审核、异常处置、返工（本部门及以下）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_leader');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 15, ${tenant_id}, '操作工', 'mes_operator', 15, 5, 0, '报工、转移、领料申请、点检执行（仅本人）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_operator');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 16, ${tenant_id}, '质检员', 'mes_inspector', 16, 3, 0, '检验执行、不良处置、追溯（本部门）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_inspector');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 17, ${tenant_id}, '设备管理员', 'mes_equipment', 17, 3, 0, '设备台账、点保计划、维修工单（本部门）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_equipment');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 18, ${tenant_id}, '仓管员', 'mes_warehouse', 18, 3, 0, '发退料、线边仓、标签打印（本部门）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_warehouse');

INSERT INTO sys_role (id, tenant_id, name, code, sort, data_scope, status, remark, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 1000 + 19, ${tenant_id}, '管理层', 'mes_executive', 19, 1, 0, '看板报表只读、统计导出（全部数据）', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = ${tenant_id} AND code = 'mes_executive');

-- ---------------- 菜单授权（sys_role_menu；id = ${tenant_id}*100000 + 段基 + (menu_id-500+1)） ----------------

-- mes_admin：500-509 全部
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 100 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 502 UNION ALL SELECT 503 UNION ALL SELECT 504
      UNION ALL SELECT 505 UNION ALL SELECT 506 UNION ALL SELECT 507 UNION ALL SELECT 508 UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_planner：500/501/502/503/509
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 120 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 502 UNION ALL SELECT 503 UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_planner'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_dispatcher：500/502/503/504/505/506/508/509
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 140 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 502 UNION ALL SELECT 503 UNION ALL SELECT 504 UNION ALL SELECT 505
      UNION ALL SELECT 506 UNION ALL SELECT 508 UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_dispatcher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_leader：500-509 全部
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 160 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 502 UNION ALL SELECT 503 UNION ALL SELECT 504
      UNION ALL SELECT 505 UNION ALL SELECT 506 UNION ALL SELECT 507 UNION ALL SELECT 508 UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_leader'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_operator：500/503/504/506/507/509
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 180 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 503 UNION ALL SELECT 504 UNION ALL SELECT 506 UNION ALL SELECT 507
      UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_operator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_inspector：500/501/504/505/508/509
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 200 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 504 UNION ALL SELECT 505 UNION ALL SELECT 508
      UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_inspector'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_equipment：500/501/506/509
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 220 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 506 UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_equipment'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_warehouse：500/501/504/507/508
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 240 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 504 UNION ALL SELECT 507 UNION ALL SELECT 508) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_warehouse'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_executive：500/501/502/503/505/506/508/509
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 260 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 500 AS menu_id UNION ALL SELECT 501 UNION ALL SELECT 502 UNION ALL SELECT 503 UNION ALL SELECT 505
      UNION ALL SELECT 506 UNION ALL SELECT 508 UNION ALL SELECT 509) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_executive'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- ---------------- 权限授权（sys_role_permission；id = ${tenant_id}*100000 + 段基 + (permission_id-4400)） ----------------

-- mes_admin：4401-4470 全部
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 1000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 4401 AND 4470
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_planner：订单全流程 + 排产 + 建模只读参考 + 进度 + 看板统计
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4404, 4407, 4410, 4414,
    4417, 4418, 4419, 4420, 4421, 4422, 4423, 4424, 4425, 4426, 4430, 4468, 4469, 4470)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_planner'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_dispatcher：订单/排产查询 + 执行全流程 + 报工审核 + 异常/状态/追溯/看板只读
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 3000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4417, 4424,
    4427, 4428, 4429, 4430, 4431, 4432, 4434, 4435, 4444, 4450, 4464, 4468, 4469)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_dispatcher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_leader：建模只读 + 订单查询/关闭 + 执行全流程 + 报工审核/撤销/返工 + 异常管理 + 追溯导出 + 看板统计
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 4000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4401, 4404, 4407, 4410, 4414,
    4417, 4423, 4424, 4427, 4428, 4429, 4430, 4431, 4432,
    4434, 4435, 4436, 4437, 4441, 4444, 4445, 4446, 4447, 4450, 4457, 4461, 4464, 4465, 4468, 4469, 4470)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_leader'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_operator：转移/进度/停工 + 报工提交/查询/撤销 + 点检执行/报修 + 领料申请/标签 + 看板
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 5000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4429, 4430, 4432,
    4433, 4434, 4436, 4453, 4455, 4458, 4463, 4468)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_operator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_inspector：建模只读参考 + 报工查询 + 质量全流程 + 追溯全流程 + 质量统计
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 6000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4404, 4410, 4434,
    4438, 4439, 4440, 4441, 4442, 4443, 4444, 4445, 4446, 4464, 4465, 4466, 4467, 4469)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_inspector'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_equipment：建模只读 + 设备全流程 + 设备统计
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 7000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4401,
    4447, 4448, 4449, 4450, 4451, 4452, 4453, 4454, 4455, 4456, 4469)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_equipment'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_warehouse：建模只读参考 + 报工查询 + 物料全流程 + 批次追溯
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 8000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4404, 4407, 4434,
    4457, 4458, 4459, 4460, 4461, 4462, 4463, 4464)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_warehouse'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_executive：各模块只读 + 看板统计导出
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 9000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4401, 4417, 4424, 4430, 4444, 4450, 4464, 4465, 4468, 4469, 4470)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_executive'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- ==================== 二期：AI/集成授权（4471-4481 + 菜单 510/511） ====================

-- ---------------- 菜单授权（sys_role_menu） ----------------

-- mes_admin：AI 智能化设置（510）+ 系统集成（511）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 1100 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 510 AS menu_id UNION ALL SELECT 511) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- ---------------- 权限授权（sys_role_permission） ----------------

-- mes_admin：AI 设置管理 + 集成配置/执行/日志（4477-4481）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 11000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4477, 4478, 4479, 4480, 4481)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_planner：AI 排产建议 + AI 智能问数
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 12000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4474, 4475)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_planner'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_dispatcher：AI 工艺/SOP 知识问答
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 13000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4476)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_dispatcher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_leader：AI 日报 + AI 质量诊断 + AI 智能问数
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 14000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4471, 4472, 4474)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_leader'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_operator：AI 工艺/SOP 知识问答
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 15000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4476)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_operator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_inspector：AI 质量异常诊断
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 16000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4472)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_inspector'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_equipment：AI 设备故障诊断
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 17000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4473)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_equipment'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_executive：AI 日报 + AI 智能问数
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 19000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4471, 4474)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_executive'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- ==================== 三期：IoT/安灯/SPC 授权（4482-4487 + 菜单 512-514） ====================

-- ---------------- 菜单授权（sys_role_menu） ----------------

-- mes_admin：512/513/514 全部
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2100 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 512 AS menu_id UNION ALL SELECT 513 UNION ALL SELECT 514) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_equipment：设备数采监控（512）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2800 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 512 AS menu_id) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_equipment'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_operator：安灯管理（513，呼叫入口）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2200 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 513 AS menu_id) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_operator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_dispatcher：安灯管理（513，响应处理）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2300 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 513 AS menu_id) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_dispatcher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_leader：安灯管理（513，AI 摘要）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2400 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 513 AS menu_id) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_leader'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- mes_inspector：SPC 分析（514）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 2500 + (m.menu_id - 500 + 1), ${tenant_id}, r.id, m.menu_id, NOW(), NOW()
FROM sys_role r
JOIN (SELECT 514 AS menu_id) m
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_inspector'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.menu_id AND x.tenant_id = ${tenant_id});

-- ---------------- 权限授权（sys_role_permission） ----------------

-- mes_admin：4482-4487 全部
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 21000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 4482 AND 4487
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_equipment：设备数采（4482）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 28000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4482)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_equipment'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_operator：安灯呼叫（4483）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 22000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4483)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_operator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_dispatcher：安灯响应处理（4484）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 23000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4484)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_dispatcher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_leader：AI 安灯异常摘要（4486）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 24000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4486)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_leader'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_inspector：SPC 查询（4485）+ AI SPC 判读（4487）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 25000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4485, 4487)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_inspector'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- ==================== 四期：导入导出授权（4488-4499） ====================

-- mes_admin：4488-4499 全部
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 31000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id BETWEEN 4488 AND 4499
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_equipment：设备导入导出（4496-4497）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 37000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4496, 4497)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_equipment'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_inspector：检验方案导入导出（4498-4499）
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 36000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id IN (4498, 4499)
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_inspector'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- ==================== 补充：异常停工查询（4701） ====================

-- mes_admin：4701
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 38000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id = 4701
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_planner：4701
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 39000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id = 4701
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_planner'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_dispatcher：4701
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 40000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id = 4701
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_dispatcher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_leader：4701
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 41000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id = 4701
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_leader'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});

-- mes_operator：4701
INSERT INTO sys_role_permission (id, tenant_id, role_id, permission_id, create_time, update_time)
SELECT ${tenant_id} % 100000000 * 100000 + 42000 + (p.id - 4400), ${tenant_id}, r.id, p.id, NOW(), NOW()
FROM sys_role r
JOIN sys_permission p ON p.id = 4701
WHERE r.tenant_id = ${tenant_id} AND r.code = 'mes_operator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id AND x.tenant_id = ${tenant_id});
