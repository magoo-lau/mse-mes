-- mse-target: master
-- =====================================================================
-- MES 看板时间范围查询索引
-- overview / production / quality 按 tenant_id + deleted + report_time 查询报工；
-- equipment stats 按 tenant_id + deleted + report_time 查询维修记录。
-- MySQL 无 ADD INDEX IF NOT EXISTS：按 information_schema 判重并执行动态 DDL，保持重放幂等。
-- =====================================================================

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE mes_report ADD INDEX idx_tenant_deleted_report_time (tenant_id, deleted, report_time)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_report'
      AND INDEX_NAME = 'idx_tenant_deleted_report_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE mes_repair ADD INDEX idx_tenant_deleted_report_time (tenant_id, deleted, report_time)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mes_repair'
      AND INDEX_NAME = 'idx_tenant_deleted_report_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
