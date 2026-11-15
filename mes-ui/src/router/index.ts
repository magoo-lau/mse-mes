import { createRouter, createWebHistory } from 'vue-router'

// 嵌入主应用时以主应用路由前缀为 base（由主应用 props 下发），独立运行时使用根路径
const embedded = !!window.__POWERED_BY_WUJIE__
const base = embedded ? window.$wujie?.props?.urlPrefix || '/sub/mes' : '/'

const router = createRouter({
  history: createWebHistory(base),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/home', component: () => import('../views/Home.vue'), meta: { title: '制造执行' } },
    // 以下路由与基座 sys_menu type=3 页面菜单（id 501-514）的 path 一一对应
    { path: '/base', component: () => import('../views/Base.vue'), meta: { title: '基础数据' } },
    { path: '/plan', component: () => import('../views/Plan.vue'), meta: { title: '生产计划' } },
    { path: '/exec', component: () => import('../views/Exec.vue'), meta: { title: '生产执行' } },
    { path: '/report', component: () => import('../views/Report.vue'), meta: { title: '报工工作台' } },
    { path: '/quality', component: () => import('../views/Quality.vue'), meta: { title: '质量管理' } },
    { path: '/equipment', component: () => import('../views/Equipment.vue'), meta: { title: '设备管理' } },
    { path: '/material', component: () => import('../views/Material.vue'), meta: { title: '物料管理' } },
    { path: '/trace', component: () => import('../views/Trace.vue'), meta: { title: '追溯查询' } },
    { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '看板报表' } },
    // 二期（菜单 510/511）
    { path: '/ai-settings', component: () => import('../views/AiSettings.vue'), meta: { title: 'AI 智能化设置' } },
    { path: '/integration', component: () => import('../views/Integration.vue'), meta: { title: '系统集成' } },
    // 三期（菜单 512-514）
    { path: '/iot', component: () => import('../views/Iot.vue'), meta: { title: '设备数采监控' } },
    { path: '/andon', component: () => import('../views/Andon.vue'), meta: { title: '安灯管理' } },
    { path: '/spc', component: () => import('../views/Spc.vue'), meta: { title: 'SPC 分析' } }
  ]
})

export default router
