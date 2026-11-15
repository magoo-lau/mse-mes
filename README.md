# mse-mes —— MES 制造执行系统

面向多租户的制造执行系统：承接「计划 → 排产 → 派工 → 报工 → 检验 → 追溯 → 看板」制造执行闭环。
独立业务工程（积木式组装，微服务形态），仅通过 BOM 消费基座发布件，不进根 pom 聚合。

**产品系统规划见 [docs/](docs/README.md)**（功能点 / 权限 / 角色 / AI 智能化 / 二三期路线图）。

## 功能（一期 + 二期 + 三期，已实现）

| 模块 | 页面菜单 | 内容 |
| --- | --- | --- |
| 基础数据建模 | 501 | 工厂/车间/产线/工位四级建模、物料、BOM（头+明细）、工艺路线（工序/质检点/发布）、工作日历与班次 |
| 生产计划 | 502 | 生产订单（提交/下达/取消/关闭）、下达按工艺路线自动展开派工单、排产计划（产线×班次，发布制）、**AI 排产建议**（草稿，人工确认后写入） |
| 生产执行 | 503 | 派工指派、工序转移（留痕）、工单/工序进度、在制品（WIP）、异常停工登记/恢复 |
| 报工工作台 | 504 | 快速报工（合格/不良）、质检点自动生成 IPQC 检验单、合格品自动累计批次并入线边仓、审核/驳回（数量回冲）、撤销、返工派工、**AI 工艺/SOP 问答** |
| 质量管理 | 505 | 检验方案（含检验项目）、检验单执行（不良自动生成待处置单）、不良品处置（返工/报废/让步）、质量异常单（登记/处理/关闭）、**AI 质量异常诊断** |
| 设备管理 | 506 | 设备台账与状态、点检保养计划与打卡（异常自动转报修）、维修工单（受理/完工/停机时长自动累计）、**AI 设备故障诊断** |
| 物料管理 | 507 | 领料/退料申请与执行（线边仓台账+流水同事务联动）、库存调整、标签打印数据 |
| 追溯查询 | 508 | 批次正向/反向追溯（领用→工单→产出 / 工单→工序报工→耗用）、追溯报告 CSV 导出、序列号绑定 |
| 看板报表 | 509 | 总览指标、近 7 日产量趋势、不良 TOP、设备维修统计、CSV 导出、**AI 生产日报生成**、**AI 智能问数**（自然语言查生产数据） |
| AI 智能化设置 | 510（二期） | 能力模式与双通道状态展示、六场景 → mse-ai 智能体映射（保存即生效） |
| 系统集成 | 511（二期） | ERP 入站同步（订单/物料/BOM 经 mse-open 开放平台引入，externalCode 幂等）、同步日志；工单完工通知经框架通知中枢 (mes,ORDER_FINISHED) 群发（原出站回传已退役 2026-10-20，渠道在基座「通知渠道设置」配置） |
| 设备数采监控 | 512（三期） | 标准化数采上报（IoT 网关/模拟同通道）、设备状态实时化（5 分钟窗口在线判定，离线回退台账状态）、OEE 完整口径（可用率×性能×质量，额定产能可配）、数采明细、设备配置 |
| 安灯管理 | 513（三期） | 工位安灯呼叫（设备/质量/物料）、响应/关闭全留痕、待响应超时逐级升级（15 分钟/级上限 2 级，后台扫描自动触发 + 手动入口）、统计（类型分布/平均响应/高频工位）、AI 安灯异常摘要 |
| SPC 分析 | 514（三期） | 监控项（USL/LSL/目标/子组 n）、样本录入、X̄-R 控制图（Shewhart 系数内置，超限/超规格报警）、过程能力 Cp/Cpk、AI SPC 判读 |

**AI 场景覆盖**：二期 1-6 + 三期 7-8（安灯摘要 `mes_andon_summary`、SPC 判读 `mes_spc_interpret`），共八场景；智能问数/设备状态已接数采实时源。

## AI 智能化（二期）

对齐 mse-oa × mse-ai 的 AI 能力范式（规划见 docs/01 第十三节）：

- **能力开关**：基座「参数设置」sys_config 参数 `mes.ai.mode`（两端独立：各库默认模板行 id=53200，本租户覆盖行优先）——`off`（默认）/ `auto`（AI 服务优先失败回退直连）/ `platform` / `direct`（starter-ai `mseChatClient` + OpenAI 兼容协议，未配 `spring.ai.*` 时零装配）。
- **场景 → 智能体映射**：AI 智能化设置页（510）下拉选择 mse-ai 已发布智能体按场景映射，存 `mes_ai_agent_map`（租户隔离），兜底默认 `mes_report_writer / mes_quality_diag / mes_equip_diag / mes_data_analyst / mes_schedule_advisor / mes_kb_qa / mes_andon_summary / mes_spc_interpret`（yaml `mse.mes.ai.*` 可覆盖）。八个默认智能体由 `MesAiAgentProvider` 声明（`AiAgentProvider` 契约）随服务启动自动注册 mse-ai 智能体管理（单体启动扫描 / 微服务周期重推，幂等；平台行为本体、各租户首次使用懒补齐可编辑副本），无需手工创建；问数智能体 `mes_data_analyst` 绑定 `mes_query` 受控取数工具。
- **反向工具 `mes_query`**：`MesQueryTools`（一服务一工具，action 分发：order_progress/production_stats/quality_stats/equipment_status/wip_query），SQL 语义写死在 Service/Mapper（无注入面），单体由 mse-ai 自动收集容器 Bean 登记，微服务由 mes-boot 以 group=`mse-mes` Dubbo 导出（ai_tool 自动登记）。
- **红线**：AI 只出草稿/建议，一切写操作（排产写入、发布）人工确认；AI 输出页统一标注「AI 生成，仅供参考」；绝不让模型直连业务库拼 SQL。
- **实现偏离说明**：SOP 问答规划为前端直连网关 SSE（依赖 mse-ai 的 `ai:chat:use` 跨应用权限）；实际落地为 MES 后端转发（`/mes/ai/kb-qa` 非流式），权限收敛在 `mes:ai:kb-qa`——现场操作工无需 mse-ai 侧权限即可使用，审计统一在 MES 侧。

## 系统集成（二期）

遵循仓库「能力开放接入标准」：业务自注册、mse-open 统一窗口、业务新增开放能力零改动 mse-open。

- **开放查询**（ERP/第三方经开放平台拉取）：工单进度 / 在制工单 / 产量统计 / 质量摘要，契约 `mes-api` 的 `RemoteMesOpenService`（String 进 String 出机器语义）+ `MesOpenApiContributor` 自注册 mse-open 目录。
- **入站同步**（ERP 推送）：生产订单引入（externalCode 幂等，草稿态待计划员确认下达）、物料/BOM 主数据 upsert（ERP 为准）；总开关 `mes_integration_config(erp_sync)`。
- **工单完工通知**：报工达产发 Spring 完工事件 → `MesOrderFinishedNotifyListener` 经框架通知中枢群发 (mes,ORDER_FINISHED)（`MesNotifySourceProvider` 声明来源），渠道由租户路由解析；原出站 webhook 回传（result_push）已退役 2026-10-20，配置行/出站日志随 V20261020_01_mes 清理。
- **监控日志**：`mes_integration_log` 入/出站全量记录，「系统集成」页按方向/类型/状态/单号检索。

## 批量操作与 Excel 导入导出（四期）

全平台列表批量化的统一范式：**部分成功语义**——逐条执行、失败项跳过并收集原因（`BatchOpResult{successCount, failCount, errors}`），前端统一弹结果框展示。

- **主数据 Excel 三件套**（模板/导入/导出，EasyExcel 4.0.3）：物料、制造建模、BOM、工艺路线、设备、检验方案。导入按编码 upsert（勾选「更新已存在」覆盖，否则报已存在跳过），整行覆盖语义、枚举列填中文；BOM/工艺路线/检验方案为「一行一个子件/工序/检验项、按头编码归组」整体保存（已发布路线拒导）。端点：`GET /mes/<域>/import-template`、`POST /mes/<域>/import?updateSupport=`、`GET /mes/<域>/export`（EasyExcelRow 在 `vip.mseapp.mes.excel`，导入/导出逻辑集中 `MesMasterDataService`）。
- **批量删除**（`POST /mes/<域>/batch-delete`）：物料/建模/BOM/工艺路线/设备/检验方案（带引用校验，被引用行 1409 跳过）+ 日历/SPC 监控项/草稿工单；单条删除同步走同一校验（此前 deleteById 无校验，属顺手修复）。
- **批量流转**（权限复用既有权限码）：工单 `POST /mes/order/batch/submit|release|cancel|close` 与 `/batch-delete`（仅草稿可删）、报工 `/mes/report/batch/audit`、不良处置 `/mes/qc/defect/batch/handle`（同一处置方式）、质量异常 `/mes/qc/exception/batch/close`、领退料 `/mes/matreq/batch/issue|reject`、点检计划 `/mes/check/plan/batch/status/{status}`；排产发布 `/mes/plan/publish` 本就收 ids。批量执行统一经 `MesBatchRunner`（方法引用指向代理 Bean，行级事务照常生效）。
- **编码唯一查重**：物料/建模/BOM/路线/设备/检验方案的新增/编辑补按 code 查重（1409；此前仅靠文档单号表唯一键，主数据表无约束无校验）。
- **前端**：通用导入对话框 `mes-ui/src/components/BatchImportDialog.vue`（模板下载/上传/updateSupport/结果展示）、`request.ts` 的 `download()` 助手、`utils/batch.ts` 的 `showBatchResult`；全部列表页补勾选列与批量按钮，BOM/路线补分页、日历删除补确认。
- 明确不做批量（需逐单填写业务数据）：检验单提交（逐项录入检验值）、维修接单/完工、派工分配、报工撤销。

## 模块

| 模块 | 说明 |
| --- | --- |
| mes-api | 对外 RPC 契约：`RemoteMesService`（工单进度查询）、`RemoteMesOpenService`（开放集成，mse-open 泛化分发） |
| mes-ui | 子应用（Vue3 + Element Plus 紧凑型，wujie 嵌入；路由与页面菜单 501-511 一一对应） |
| mes-service | 业务积木（纯 jar，SPI 自装配）：24 个 Controller / 14 个 Service / 30 张业务表；jar 内含版本化迁移脚本（见下） |
| mes-boot | 微服务启动器：micro-starter + mes-service（+ optional starter-ai 直连通道），application.yaml 经 spring.config.import 接 Nacos，网关 `lb://mse-mes` |

## 关键参数

- 应用名 `mse-mes`；端口 `8098`；网关前缀 `/mes/**`（无白名单，接口全部需登录）；前端 dev `4020`，构建 `/sub-apps/mes/`
- 子应用注册 id=11、页面菜单 500-514、权限点 4401-4499（三期 4482-4487、四期批量操作/导入导出 4488-4499 已用）+ 补充段 47xx（4701 异常停工查询，2026-09-27 补播一期漏播），号段登记见 `zcode-plugin/plugins/mse-dev/references/alloc-tables.md`
- 服务身份：`@TenantServiceModule("mse-mes")`（单体聚合时租户隔离策略零配置生效）

## 快速开始

前置：框架区已 `mvn install -DskipTests`（mse-starter-parent 随框架发布）；mse-modules/mse-ai、mse-modules/mse-open 已 `mvn install`（mes 依赖其 ai-api/open-api 契约发布件）。

```bash
# 1. 构建本工程
mvn install -DskipTests -f mse-modules/mse-mes/pom.xml

# 2. 微服务形态：Nacos 就绪后启动（配置经 mse-micro-common/db-biz/dubbo.yaml 共享 + mse-mes.yaml 专属）
#    专属配置留档 docker/nacos/config/mse-mes.yaml，导入：
CONFIG_DIR=<仓库根>/mse-modules/mse-mes/docker/nacos/config sh mse-framework/docker/nacos/init-configs.sh
java -jar mes-boot/target/mes-boot-1.0.0-SNAPSHOT.jar

# 3. 前端（独立开发调试；嵌入基座经子应用管理/SQL 种子注册）
cd mes-ui && npm install && npm run dev   # http://localhost:4020，代理 /api → 网关 48080
```

；三期 `V20261001_02_mes__init.sql`（mes_iot_config / mes_iot_collect / mes_andon_call / mes_spc_item / mes_spc_sample）、`V20261001_01_mes__base.sql`（菜单 512-514 + 权限 4482-4487）、`V20261001_03_mes__roles.sql`（三期授权，每租户）、`V20261001_02_mes__init.sql`（mes_integration_log 补 create_by/update_by 审计列——首版建表遗漏 BaseEntity 两列，条件变更幂等可重放）——全部随启动/starter-bridge 自动应用，幂等可重放
- **单体聚合**：引入 `mono-starter` + `mes-service` 即得全部能力（参考 mse-monolith 拼法）；聚合工程已引 ai-service 时 `mes_query` 反向工具自动登记、AI 全场景可用
- **角色**：一期种子 9 角色默认授权；二期 AI/集成权限、三期 IoT/安灯/SPC 权限按 03-角色规划追加（mes_admin 全量；equipment 数采；operator 安灯呼叫；dispatcher 安灯响应；leader AI 安灯摘要；inspector SPC+AI SPC 判读；二期授权同前）
- **套餐**：租户可见性需在「套餐管理」勾选 mes 页面菜单（501-511）
- **AI 开箱**：参数 `mes.ai.mode` 默认 off——部署 mse-ai 并发布规划编码的智能体（或配置 spring.ai.* 直连模型）后置为 platform/auto/direct 即生效

## 验证要点（人工回归）

一期闭环（建模 → 订单 → 排产 → 派工 → 报工 → 检验 → 追溯 → 看板）见 git 一期提交说明；二期新增：

1. 「参数设置」把 `mes.ai.mode` 置 platform（mse-ai 就绪）：各页面 AI 按钮点亮，能力状态页双通道显示正确
2. AI 智能化设置页：六场景映射保存即生效；未映射场景走内置默认编码
3. AI 排产建议：生成草稿 → 勾选应用 → 排产计划出现「AI 建议」备注行（草稿态，发布后生效）
4. AI 日报 / 智能问数 / 质量诊断 / 设备诊断 / SOP 问答：输出标注「AI 生成，仅供参考」；mes.ai.mode=off 时按钮置灰并有提示
5. 系统集成：启用 ERP 入站同步 → 经开放平台引入订单/物料/BOM → 幂等与日志正确；工单完工后按租户路由 (mes,ORDER_FINISHED) 收到中枢群发通知
6. 开放平台：创建应用授权 → 调 `/open/api/mes/producing-orders` 等开放能力 → 启用 erp_sync 后经 `/open/api/mes/import-materials`、`import-order` 推入主数据与订单（同 externalCode 重推幂等）
7. 三期数采：「设备数采监控」模拟上报（状态/累计计数）→ 实时看板在线+iot 源、设备状态同步实时化、OEE 三因子（配置额定产能后性能指数生效）
8. 三期安灯：呼叫 → 活跃看板 → 超时升级（回拨 call_time 20 分钟或等后台扫描，级别 1→2 上限）→ 响应 → 关闭留痕 → 统计聚合；AI 安灯摘要（mode 开启后）
9. 三期 SPC：建监控项（USL/LSL、子组 n=5）→ 录入 3 组样本（含超规格值）→ 控制图控制限/超限标记/Cp/Cpk；AI SPC 判读（mode 开启后）
10. 四期批量：模板下载→造数导入（重复编码 updateSupport 开/关、BOM/路线多行归组、建模上级编码不存在报行错）→ 列表导出核对；批量删除含被引用行的部分成功反馈；工单批量下达/报工批量通过（含驳回原因弹窗）；新权限点 4488-4499 随 base 脚本推送重放后按钮可见（角色需在角色管理勾选或在套餐内）

## 已知边界

- 单号生成器为单机内存序号（多实例撞号时唯一键兜底，重提即可）
- 统计口径为内存聚合（当前数据量级足够；三期接数采后可下沉 SQL）
- 标签打印返回打印数据（打印机对接由部署侧按模板渲染）
- AI 各场景智能体（mes_report_writer 等）由模块随服务启动自动注册到 mse-ai 智能体管理（无需手工创建）；未部署 mse-ai 且未配直连模型时 AI 按钮置灰
- 安灯升级等后台扫描为单实例内存调度（多实例部署时可能并发触发，安灯升级幂等即可）；扫描器首轮执行带结构迁移就绪门禁（`MasterSchemaAutoMigration.isSchemaReady`），全新库首启建表完成前自动跳过、下周期自愈，不产生"表不存在"告警噪音
- OEE 性能指数需在「设备配置」维护额定小时产能，未配置时按 1 计（响应中标注）；设备级质量指数暂按全厂报工口径（报工关联设备后可细化）
- SPC 质量特性为计量型（X̄-R 图）；样本可手工录入或由 IoT 网关按同一报文语义推送（source=2）
