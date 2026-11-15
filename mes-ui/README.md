# mes-ui —— 制造执行（微前端子应用）

基于 wujie（无界）的子应用：既可嵌入 `mse-ui` 基座运行（登录态由基座下发），也可独立启动开发调试。技术栈：Vue3 + Vite + TS + Element Plus（全局紧凑风格 `size: 'small'`）。子应用**不自带侧边栏菜单**——应用地址在基座「子应用管理」登记、页面菜单在基座「菜单管理」以类型=子应用页面登记（所属子应用关联本应用标识），由基座菜单树渲染；子应用只负责内容页。

已内置（无需重复实现）：wujie 生命周期、路由 base 适配、登录态 props/`auth:change`、请求封装（token 注入、401 → 基座登出、业务错误码透传）、`v-permission` 按钮级权限。

## 独立运行

```bash
npm install && npm run dev   # http://localhost:4020（proxy /api → http://localhost:8080，单体）
```

## 关键参数（已按本工程配置）

| 项 | 值 |
| --- | --- |
| 构建basePath | `/sub-apps/mes/`（package.json build 脚本） |
| 路由 fallback 前缀 | `/sub/mes`（src/router/index.ts，实际以基座 props 为准） |
| dev 端口 | 4020 |
| dev proxy | `/api` → http://localhost:8080（单体；微服务模式改 48080 网关） |

## 基座注册（二选一，后台优先）

平台端「平台管理 → 子应用管理」新增（推荐，字段见下），或 `mse-framework/mse-ui/src/micro/apps.ts` 内置兜底表 `builtinMicroApps` 追加：

```ts
{
  name: 'mes',              // 路由前缀 /sub/mes
  title: '制造执行',
  devEntry: '//localhost:4020',
  prodEntry: '/sub-apps/mes/',
  side: 'both'              // tenant 租户端 / platform 平台端 / both 双端（后台配置为 1/2/3）
}
// 兜底表只登记应用地址（无菜单字段）；页面菜单统一在基座「菜单管理」登记（类型=子应用页面）
//   或随业务 jar 内 db/<方言>/V…_mes__base.sql 版本化脚本（头指令 -- mse-target: base）幂等落库（starter-bridge 自动推送 mse-system 应用，免手工执行），
//   路由地址与 router 一致（动态参数段用 :id，基座默认取 1）
//   未登记页面菜单的应用由基座渲染单项兜底
```

多租户：套餐勾选本应用任一页面菜单即对该租户启用（未登记页面菜单的应用对所有租户可用）。

## 按钮级权限

```vue
<el-button v-permission="'mes:item:create'">新建</el-button>
```

权限码与后端 `@SaCheckPermission` / sys_permission 一致（超管 `['*']` 直通）。
