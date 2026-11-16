# 验证要点（MES 回归清单）

完整用例目录在 ai-mse 仓 docs/test/31-33-*.md（历史存档）；本仓维护最终态回归要点。

## 一、主链路（计划→执行→报工）

1. 建模：工厂/产线/工位/物料/BOM/工艺路线维护，中文数据经接口造数（mysql 客户端 --default-character-set=utf8mb4）。
2. 计划：生产订单下达 → 生成工单/派工；计划变更联动执行域。
3. 执行：工单开工 → 报工（数量/工时/不良）→ 完工；批次与序列号追溯链完整（MesSn）。
4. 质量：检验方案（MesQcScheme）→ 检验记录（MesCheckRecord）→ 不良处置闭环。

## 二、设备与物料

- 设备台账/点检/维修（MesEquipmentService）；安灯呼叫（MesAndonCall）与升级扫描（escalateDue 跨租户扫描禁事务范式）。
- 物料库存（MesStock）/集成日志（MesIntegrationLog）；外部集成配置（MesIntegrationConfig）密钥不落日志。

## 三、租户与权限

1. 共享租户行级隔离：租户 A 不可见租户 B 工单；伪造 X-Tenant-Id 被覆盖。
2. 独立库租户：业务表落专属库（master 通道自动迁移）。
3. 权限点（mes 段号）挂页面菜单参与套餐裁剪；越权直调 403。

## 四、AI 集成

1. mse-ai 在线：MesQueryTools 工具登记（工具中心可见）；8 智能体按 MesAiAgentProvider 清单登记。
2. 对话取数：biz 分析师/领域智能体一句话结论+图表（chartDirect）；`mes.ai.mode` 开关语义（off/auto/platform/direct）。
3. agentCode 冲突防护：他方已登记码跳过；租户自有 mes_ai_agent_map 行不触碰。

## 五、工程验证底线

- `mvn install -DskipTests` 通过；mes-ui / mes-mobile-ui `npm run build` 通过。
- 镜像 `docker build -f mes-boot/Dockerfile .` 成功；k8s 探针（X-Actuator-Token）通过。
- 安灯升级/跨租户扫描路径无 @Transactional 包裹（对照 AGENTS.md 红线）。
