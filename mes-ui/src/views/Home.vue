<template>
  <el-card header="接入信息">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="当前模式">
        <el-tag :type="authStore.embedded ? 'success' : 'info'">
          {{ authStore.embedded ? '嵌入主应用（wujie）' : '独立运行' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="路由前缀（urlPrefix）">{{ urlPrefix }}</el-descriptions-item>
      <el-descriptions-item label="登录用户">{{ authStore.nickname || '-' }}</el-descriptions-item>
      <el-descriptions-item label="租户 / 平台">
        {{ authStore.platform ? '平台端' : `租户 ${authStore.tenantId || '-'}` }}
      </el-descriptions-item>
      <el-descriptions-item label="token">
        {{ authStore.token ? authStore.token.slice(0, 12) + '...' : '（无，独立运行未登录）' }}
      </el-descriptions-item>
    </el-descriptions>
    <el-alert class="tips" type="info" :closable="false"
      title="业务页面经 gen-ui-page 生成后登记到 src/router/index.ts，并在基座「菜单管理」登记类型=子应用页面的菜单（路由地址与 router 一致）" />
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const urlPrefix = computed(() => window.$wujie?.props?.urlPrefix || '/')
</script>

<style scoped>
.tips {
  margin-top: 16px;
}
</style>
