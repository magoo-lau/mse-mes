import { createRouter, createWebHistory } from 'vue-router'

const embedded = !!window.__POWERED_BY_WUJIE__
const base = embedded ? window.$wujie?.props?.urlPrefix || '/sub/mes-mobile' : '/'

const router = createRouter({
  history: createWebHistory(base),
  routes: [
    { path: '/', redirect: '/report' },
    // 报工：工单选择/扫码 + 报工表单 + 我的报工记录
    { path: '/report', component: () => import('../views/Report.vue'), meta: { title: '报工' } },
    // 设备点检：计划列表 + 执行提交 + 我的点检记录
    { path: '/check', component: () => import('../views/Check.vue'), meta: { title: '设备点检' } },
    // 设备状态总览
    { path: '/equipment', component: () => import('../views/Equipment.vue'), meta: { title: '设备状态' } }
  ]
})

export default router
