# mse-mes —— MES 制造执行系统（产品规划）

> **状态：一期 + 二期 + 三期已全部落地**（工程创建于 2026-09-24；二期 AI 智能化 + 系统集成、三期 IoT 数采/安灯/SPC + AI 场景 7-8 均于 2026-09-25 落地）。
> 本文档集为产品规划留档，实际落库以工程内版本化脚本为准（`mes-service/src/main/resources/db/mysql/`）；02/03 中的规划 id 即种子脚本直接输入。

## 一、产品定位

面向**多租户 SaaS** 的制造执行系统（Manufacturing Execution System）：承接生产计划，管理「计划 → 排产 → 派工 → 报工 → 检验 → 追溯 → 看板」的制造执行闭环，二期打通 ERP、引入 AI 智能化，三期叠加 IoT 数采与安灯/SPC。

在本仓库中的形态：**mse-modules 业务工程**（独立 Maven 工程，不进根 pom 聚合），wujie 微前端子应用 + 微服务启动器，积木拼法与 mse-demo / mse-oa 一致：

- `mes-api`：跨服务 RPC 契约（含对外开放能力契约，经 mse-open 自注册接入）
- `mes-service`：业务积木（SPI 自装配，Controller / Service / Mapper 随 jar 就绪）
- `mes-boot`：微服务启动器（micro-starter + mes-service，纯 `@SpringBootApplication`）
- `mes-ui`：子应用前端（Vue3 + Element Plus 紧凑型，wujie 嵌入基座）

**多租户**：`tenant_id` 行级隔离为基线（可按租户独立库路由），基础数据建模按租户维护各自的工厂/产线/工艺；**AI 能力不自建**——统一经 mse-ai 智能体平台接入（见 [01-功能规划](01-功能规划.md) 第十三节）。

## 二、文档导读

| 分册 | 内容 | 读者 |
| --- | --- | --- |
| [01-功能规划.md](01-功能规划.md) | 11 大功能模块与功能点清单（含分期标记）、AI 智能化特色功能专章、分期路线图 | 产品 / 全员 |
| [02-权限规划.md](02-权限规划.md) | 子应用注册、页面菜单（sys_menu）规划、权限点（sys_permission）全量清单与编码规范 | 后端 / 前端 |
| [03-角色规划.md](03-角色规划.md) | 角色清单、角色 × 模块授权矩阵、数据权限（data_scope）设计、种子落库规划 | 后端 / 实施 |
| [04-操作说明书/](04-操作说明书/README.md) | **分角色操作说明书**（总览 + 9 个岗位手册：系统管理员 / 计划员 / 调度员 / 车间主任 / 操作工 / 质检员 / 设备管理员 / 仓管员 / 管理层），含通用操作、状态字典与常见问题 | 最终用户 / 培训交付 |
| [05-配置手册-零基础版.md](05-配置手册-零基础版.md) | **面向门外汉的部署配置指南**：名词卡、容器一键部署（单体）、租户/套餐/角色初始化、AI 开启、ERP 集成与 IoT 接入、微服务进阶、排查表与上线清单 | 实施 / 现场 IT |

阅读顺序：01 → 02 → 03。工程落地时 02/03 的表格即 SQL 种子的直接输入。**04 为面向最终用户的交付文档、05 为面向实施的零基础部署文档**，均以系统实际界面与配置为准。

## 三、资源分配规划（建议值）

> ⚠️ 以下均为**规划值**，工程实际创建时按 `zcode-plugin/plugins/mse-dev/references/alloc-tables.md` 的登记纪律正式占用并回写该表（当前均未被占用，无冲突）。

| 资源 | 规划值 | 说明 |
| --- | --- | --- |
| 后端端口 `server.port` | **8098** | 微服务形态 mes-boot；单体聚合不占独立端口 |
| 前端 dev 端口 | **4020** | mes-ui vite dev server |
| 子应用注册 `sys_micro_app.id` | **11** | name=`mes`，side=1（租户端），标题「制造执行」 |
| 页面菜单 `sys_menu.id` | **500-514** | 500=type=1 分组目录；501-509 一期页面；510-511 二期（AI 设置 / 系统集成）；512-514 三期 IoT 预留 |
| 权限点 `sys_permission.id` | **4401-4487** | 一期 4401-4470；二期 AI 4471-4477 + 集成 4478-4481；三期 4482-4487 |
| 权限码前缀 | `mes:` | 格式 `mes:<模块>:<动作>`，与 oa/mp/fs 等工程同风格 |
| 网关路由前缀 | `/mes` | `Path=/mes/** → lb://mse-mes`（Nacos mse-gateway.yaml 登记） |
| sys_config 参数种子 id | **53200+** | 首个参数 `mes.ai.mode`（AI 能力开关，规划 id=53200） |
| 微服务应用名 | `mse-mes` | spring.application.name；`@TenantServiceModule("mse-mes")` |

## 四、分期路线图

| 分期 | 范围 | 交付闭环 |
| --- | --- | --- |
| **一期（标准 MES）** | 基础数据建模、生产计划、生产执行、报工工作台、质量管理、物料管理、设备管理、追溯管理、看板报表 | 工单从下达到完工入库的全流程执行闭环 + 电子化追溯 |
| **二期（集成 + AI）** | ERP 系统集成（经 mse-open 开放平台）、AI 智能化特色功能（日报生成 / 质量诊断 / 设备诊断 / 智能问数 / 排产建议 / 知识问答） | 订单/物料/BOM 自动同步，生产数据 AI 分析辅助决策 |
| **三期（IoT 增强）** | 设备数采（实时状态与 OEE 深化）、安灯 Andon、SPC 统计过程控制，及其 AI 判读（安灯异常摘要 / SPC 判读） | 设备数据自动采集，异常实时响应与过程能力监控 |

## 五、工程结构蓝图（落地时）

```
mse-mes/
├── pom.xml                  # 父 pom 指向 mse-starter-parent（relativePath 置空）
├── README.md                # 工程主文档（功能以本 docs/ 为纲）
├── docs/                    # 本文档集（产品系统规划）
├── mes-api/                 # RPC 契约（接口 + Serializable DTO；开放能力契约经 mse-open 自注册）
├── mes-service/             # 业务积木（SPI 自装配；db/<方言>/ 版本化脚本随 jar，mse-target 头指令分流）
│   └── src/main/resources/db/
│       └── <方言>/           # 业务表 DDL + 种子（含角色种子，缺省 master）+ 基座接入（-- mse-target: base，starter-bridge 推送基座库）
├── mes-boot/                # 启动器：micro-starter + mes-service（ vip.mseapp.boot.mes）
└── mes-ui/                  # 子应用（mse-ui-sub 模板；dev 4020；base=/sub-apps/mes/）
```

部署形态：微服务为默认形态（Nacos + Dubbo，网关 `/mes` 前缀）；单体聚合仅需引入 `mono-starter` + `mes-service` jar（`mse.rpc.mode=local` 本地直调，AI 正向调用经 RPC 本地通道同样零改动）。

## 六、平台协作约定（落地红线）

1. **不改框架区**：一切定制经 SPI 自动装配 + `@ConditionalOnMissingBean` 替换语义实现。
2. **SQL 免手工执行**：业务表脚本（缺省 `-- mse-target: master`）随启动自动迁移；基座注册（头指令 `-- mse-target: base`）由 starter-bridge 自动推送（幂等版本化脚本，命名 `V{yyyyMMdd}_{NN}_mes__<描述>.sql`）。
3. **权限点一律挂靠本工程 type=3 页面菜单**，参与角色授权分组与租户套餐裁剪；前端按钮用 `v-permission` 按权限码控制。
4. **AI 能力走 AI 服务通道（mse-ai）**：正向 `ai-api` 的 `RemoteAiChatService`，反向 `RemoteAiTool` 门面工具；开关走 sys_config 参数 `mes.ai.mode`（详见 [01-功能规划](01-功能规划.md) 第十三节）。
5. **资源用后登记**：工程创建时将第三节规划值正式登记到 `alloc-tables.md`。
