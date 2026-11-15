-- mse-target: base
SET NAMES utf8mb4;
-- =====================================================================
-- MES 生产结果回传退役·基座域（独立工程 mse-modules/mse-mes，MySQL）
-- 内容：移除权限点 4480（mes:integration:sync:run）及其角色关联——出站回传的手动执行/
--       重推入口随功能退役（工单完工通知改经框架通知中枢，基座「通知渠道设置」配置渠道）；
--       菜单 511「系统集成」保留（ERP 入站同步仍在）
-- 配套：V20261020_01_mes__integration_push_drop.sql（业务库：删配置/日志行 + DROP 列）
-- 幂等：DELETE 语义天然幂等，可重复执行
-- 号段：4480 退役释放（登记见 alloc-tables.md）
-- =====================================================================

-- ---------------- 角色关联（先于权限点删除，避免悬空） ----------------
DELETE FROM sys_role_permission WHERE permission_id = 4480;

-- ---------------- 权限点 ----------------
DELETE FROM sys_permission WHERE id = 4480 OR code = 'mes:integration:sync:run';
