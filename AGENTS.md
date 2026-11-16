# AGENTS.md —— mse-mes（MES 制造执行系统）协作约定

本文件面向所有在本仓库工作的 AI 助手与开发者。本仓库自 ai-mse 单仓（源 commit `cc5410a3`）拆分独立，仓根即工程根。

## 一、工程结构

```
mse-mes/
├── pom.xml          # parent = mse-starter-parent（<relativePath/>，仓库坐标解析）
├── mes-api/         # 对外契约（RemoteMesOpenService 等，依赖仅基座 common-core）
├── mes-service/     # 业务积木（建模/计划/执行/报工/质量/设备/物料/追溯/看板 + AI 工具与智能体）
├── mes-boot/        # 启动器（vip.mseapp.boot.mes；micro-starter + mes-service；starter-ai optional）
├── mes-ui/          # PC 子应用（--base=/sub-apps/mes/）
├── mes-mobile-ui/   # 移动子应用（Vant 4，--base=/m-apps/mes/）
├── docs/            # 业务文档（01-04：功能规划/概要设计/角色规划/操作说明书）
└── docker/          # nacos/config/mse-mes.yaml + mes-boot/Dockerfile + k8s（见 docker/README.md）
```

## 二、工程拼法（对齐基座，硬性要求）

1. parent = mse-starter-parent（前置：mse-framework 仓 `sh build.sh`）；依赖 `starter-web/mybatis/security/rpc` 四件 + 契约 `mes-api`；**跨仓契约**：`ai-api` / `open-api`（来自 mse-modules 仓，1.0.0-SNAPSHOT）——该仓先构建。
2. SPI 装配：`MesServiceAutoConfiguration`（`@TenantServiceModule("mse-mes")`）+ `MesControllerAutoConfiguration`（`mse.service.mes.controller.enabled` 开关）经 imports 登记；启动器纯 `@SpringBootApplication`。
3. 微服务形态：mse-micro-db-biz 共享 Nacos 基线接入；业务表脚本 `mes-service/src/main/resources/db/mysql/V*_mes__*.sql`（幂等，已应用不改）。
4. 权限号段登记：mse-agent-plugin 仓 `references/alloc-tables.md`（mes 段）。

## 三、AI 能力接入（自声明、零 SQL 种子）

- 反向工具：`MesQueryTools`（RemoteAiTool，describe/invoke 按 action 分发）；多工具经 ai-api `DispatchingAiTool` 单门面导出（同 group 只能导出一个 RemoteAiTool，微服务 boot 侧以 group=mse-mes Dubbo 导出）。
- 场景智能体：`MesAiAgentProvider`（8 智能体描述清单）；「场景→智能体」映射存 `mes_ai_agent_map` 表，开关走 sys_config `mes.ai.mode`。
- 同码冲突防护：agentCode 已被其他提供方登记时跳过，租户自有行不触碰。

## 四、租户与事务红线

- 写操作 `@Transactional(rollbackFor = Exception.class)`；跨服务/跨库入口叠加 `@GlobalTransactional`（组件扫描注册）。
- 跨租户后台扫描（如 MES 安灯升级 `MesAndonService.escalateDue` 范式）禁止 @Transactional 包裹——走语句级路由/TransactionTemplate 先 runAs。

## 五、构建、镜像与验证

- `sh build.sh`（MSE_RUN_ALL_TESTS=1 放开测试）；`sh build-ui.sh`（mes-ui + mes-mobile-ui）。
- 镜像 `docker build -f mes-boot/Dockerfile -t mse/mes-boot:1.0.0 .`（context=本仓根）；本地运行见 `docker/README.md`（基础设施真源=mse-framework 仓 docker/）。
- 验证底线：`mvn install -DskipTests` 通过；两 UI `npm run build` 通过；业务回归见 docs/verification.md。

## 六、提交纪律

本仓独立提交；改 ai-api/open-api 契约需先在 mse-modules 仓发版再适配本仓。
