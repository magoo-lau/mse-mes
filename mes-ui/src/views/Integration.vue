<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <span>集成配置（ERP 入站同步经 mse-open 开放能力引入）</span>
      </template>
      <el-form label-width="130px" size="small" class="config-form" v-loading="configLoading">
        <el-divider content-position="left">ERP 入站同步（订单/物料/BOM 引入）</el-divider>
        <el-form-item label="启用入站同步">
          <el-switch v-model="erpSync.enabled" :active-value="1" :inactive-value="0" />
          <span class="sub ml">停用后 ERP 经开放能力的引入接口将被拒绝；开放能力清单随服务自动注册到 mse-open</span>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'mes:integration:config:save'" type="primary" @click="saveAll">保存配置</el-button>
          <span class="sub ml">工单完工通知已统一经框架通知中枢 (mes,ORDER_FINISHED) 群发，渠道在基座「通知渠道设置」配置</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header>
        <div class="card-header">
          <span>同步日志</span>
          <div class="filters">
            <el-select v-model="query.type" placeholder="类型" size="small" clearable style="width: 120px">
              <el-option label="订单" value="order" />
              <el-option label="物料" value="material" />
              <el-option label="BOM" value="bom" />
            </el-select>
            <el-select v-model="query.status" placeholder="状态" size="small" clearable style="width: 120px">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="2" />
            </el-select>
            <el-input v-model="query.bizCode" placeholder="业务单号" size="small" clearable style="width: 150px" />
            <el-button size="small" @click="loadLogs">查询</el-button>
          </div>
        </div>
      </template>
      <el-table :data="logs" border size="small" v-loading="logLoading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="方向" width="110">
          <template #default="{ row }">
            <el-tag type="primary" size="small" effect="plain">入站</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="externalCode" label="外部单号" width="130" />
        <el-table-column prop="bizCode" label="业务单号" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="total" label="条数" width="70" />
        <el-table-column prop="error" label="失败原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNum" v-model:size="query.pageSize" @change="loadLogs" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api/mes'
import { Pagination } from 'mse-ui-shared/pc'

const erpSync = reactive({ enabled: 0 })
const configLoading = ref(false)
const logLoading = ref(false)
const logs = ref<any[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, type: '', status: undefined as number | undefined, bizCode: '' })

const typeLabels: Record<string, string> = { order: '订单', material: '物料', bom: 'BOM' }
const typeLabel = (type: string) => typeLabels[type] || type

const statusLabels: Record<number, string> = { 1: '成功', 2: '失败' }
const statusLabel = (status: number) => statusLabels[status] ?? status
const statusType = (status: number) => (status === 1 ? 'success' : 'warning')

async function loadConfig() {
  configLoading.value = true
  try {
    const rows = (await api.integrationConfig()) as { type: string; enabled: number }[]
    for (const row of rows) {
      if (row.type === 'erp_sync') {
        erpSync.enabled = row.enabled
      }
    }
  } finally {
    configLoading.value = false
  }
}

async function saveAll() {
  await api.saveIntegrationConfig({ type: 'erp_sync', enabled: erpSync.enabled })
  ElMessage.success('配置已保存')
  loadConfig()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const page = (await api.pageIntegrationLog({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      direction: 'in',
      type: query.type || undefined,
      status: query.status,
      bizCode: query.bizCode || undefined
    })) as { total: number; records: any[] }
    logs.value = page.records || []
    total.value = page.total
  } finally {
    logLoading.value = false
  }
}

onMounted(() => {
  loadConfig()
  loadLogs()
})
</script>

<style scoped>
.page {
  padding: 12px;
}
.mt {
  margin-top: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.filters {
  display: flex;
  gap: 8px;
}
.config-form {
  max-width: 720px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ml {
  margin-left: 8px;
}
</style>
