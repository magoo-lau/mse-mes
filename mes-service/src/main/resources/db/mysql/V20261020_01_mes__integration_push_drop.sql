-- mse-target: master
SET NAMES utf8mb4;
-- =====================================================================
-- MES 生产结果回传退役·业务库（独立工程 mse-modules/mse-mes，MySQL）
-- 内容：清理 result_push 出站回传——自建 webhook 上收框架通知中枢：工单完工改
--       (mes,ORDER_FINISHED) 群发（MesOrderFinishedNotifyListener），渠道由租户在
--       基座系统管理「通知渠道设置」路由解析（勾选内置 webhook 即群机器人送达，2026-10-20 v3.1）
-- 动作：① 删 result_push 配置行与出站日志行（重试队列死数据）② DROP target_url/secret
--       列（仅出站回传使用）——分册账本保证单次应用，重跑由已应用校验拦截
-- 配套：V20261020_02_mes__integration_push_drop_base.sql（基座域：退役权限点 4480）
-- =====================================================================

-- ---------------- 出站配置行（历史配置不迁移） ----------------
DELETE FROM mes_integration_config WHERE type = 'result_push';

-- ---------------- 出站日志行（重试/死信队列随功能退役） ----------------
DELETE FROM mes_integration_log WHERE direction = 'out';

-- ---------------- 仅出站使用的列 ----------------
ALTER TABLE mes_integration_config DROP COLUMN target_url, DROP COLUMN secret;
