<template>
  <div class="page">
    <!-- AI 安灯异常摘要（三期场景 7） -->
    <div class="toolbar">
      <el-button v-permission="'mes:andon:call'" type="danger" plain @click="openCall">安灯呼叫</el-button>
      <el-button type="primary" plain @click="loadAll">刷新</el-button>
      <el-button v-permission="'mes:andon:handle'" @click="runEscalate">执行超时升级扫描</el-button>
      <el-button v-permission="'mes:ai:andon-summary'" type="warning" plain :loading="summarizing"
        @click="genSummary">AI 异常摘要</el-button>
      <span class="sub">超时 {{ 15 }} 分钟/级自动升级（后台每 30 秒扫描），看板按级别高亮</span>
    </div>

    <el-row :gutter="12" class="mt12">
      <el-col :span="16">
        <el-card shadow="never" header="活跃呼叫看板">
          <el-table :data="active" border size="small" v-loading="loading" empty-text="暂无活跃呼叫">
            <el-table-column prop="code" label="单号" width="145" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="TYPE_TYPE[row.type]">{{ TYPE_LABEL[row.type] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="位置" min-width="120">
              <template #default="{ row }">{{ positionName(row) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
            <el-table-column label="级别" width="80">
              <template #default="{ row }">
                <el-tag size="small" effect="dark" :type="LEVEL_TYPE[row.escalateLevel] || 'success'">
                  {{ ['正常', '一级', '二级'][row.escalateLevel] || row.escalateLevel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="callTime" label="呼叫时间" width="165" />
            <el-table-column label="状态" width="85">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 0 ? 'danger' : 'warning'">{{ row.status === 0 ? '待响应' : '处理中' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" v-permission="'mes:andon:handle'" link type="primary" @click="respond(row)">响应</el-button>
                <el-button v-if="row.status === 1" v-permission="'mes:andon:handle'" link type="success" @click="close(row)">关闭</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="统计（近 7 天）">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="呼叫总数">{{ stats.total ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备/质量/物料">{{ typeSummary }}</el-descriptions-item>
            <el-descriptions-item label="平均响应(分)">{{ stats.avgRespondMinutes ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="发生升级">{{ stats.escalated ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前待响应">{{ stats.waiting ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="呼叫记录" class="mt12">
      <el-table :data="records" border size="small" v-loading="recordLoading">
        <el-table-column prop="code" label="单号" width="145" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ TYPE_LABEL[row.type] }}</template>
        </el-table-column>
        <el-table-column label="位置" min-width="110">
          <template #default="{ row }">{{ positionName(row) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag size="small" :type="['danger', 'warning', 'success'][row.status]">{{ ['待响应', '处理中', '已关闭'][row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="escalateLevel" label="升级" width="70" />
        <el-table-column prop="callTime" label="呼叫" width="165" />
        <el-table-column prop="respondTime" label="响应" width="165" />
        <el-table-column prop="closeTime" label="关闭" width="165" />
      </el-table>
      <Pagination :total="recordTotal" v-model:page="recordQuery.pageNum" v-model:size="recordQuery.pageSize" @change="loadRecords" />
    </el-card>

    <!-- 呼叫弹窗 -->
    <el-dialog v-model="callDialog" title="安灯呼叫" width="440px">
      <el-form :model="callForm" label-width="80px">
        <el-form-item label="类型" required>
          <el-radio-group v-model="callForm.type">
            <el-radio-button :value="1">设备</el-radio-button>
            <el-radio-button :value="2">质量</el-radio-button>
            <el-radio-button :value="3">物料</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="产线">
          <el-select v-model="callForm.lineId" clearable filterable>
            <el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工位">
          <el-select v-model="callForm.stationId" clearable filterable>
            <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="callForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callDialog = false">取消</el-button>
        <el-button type="danger" @click="submitCall">呼叫</el-button>
      </template>
    </el-dialog>

    <AiResultDialog ref="summaryDialog" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import AiResultDialog from '../components/AiResultDialog.vue'
import { Pagination } from 'mse-ui-shared/pc'

const TYPE_LABEL: Record<number, string> = { 1: '设备', 2: '质量', 3: '物料' }
const TYPE_TYPE: Record<number, string> = { 1: 'warning', 2: 'danger', 3: 'primary' }
const LEVEL_TYPE: Record<string, string> = { 0: 'success', 1: 'warning', 2: 'danger' }

const loading = ref(false)
const active = ref<any[]>([])
const stats = ref<any>({})
const records = ref<any[]>([])
const recordTotal = ref(0)
const recordLoading = ref(false)
const recordQuery = reactive({ pageNum: 1, pageSize: 10 })
const models = ref<any[]>([])
const callDialog = ref(false)
const callForm = reactive<any>({ type: 1, lineId: undefined, stationId: undefined, description: '' })
const summarizing = ref(false)
const summaryDialog = ref()

const lines = computed(() => models.value.filter((m) => m.type === 3))
const stations = computed(() => models.value.filter((m) => m.type === 4))
const typeSummary = computed(() => {
  const byType = stats.value.byType || {}
  return `${byType[1] ?? 0} / ${byType[2] ?? 0} / ${byType[3] ?? 0}`
})

const positionName = (row: any) => {
  const parts: string[] = []
  if (row.lineId) parts.push(models.value.find((m) => m.id === row.lineId)?.name || `产线${row.lineId}`)
  if (row.stationId) parts.push(models.value.find((m) => m.id === row.stationId)?.name || `工位${row.stationId}`)
  return parts.join(' / ') || '-'
}

async function loadAll() {
  loading.value = true
  try {
    active.value = await api.andonActive() as any[]
    stats.value = await api.andonStats(7) as any
    models.value = await api.listModel() as any[]
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const page = await api.pageAndon({ ...recordQuery }) as any
    records.value = page.records || []
    recordTotal.value = page.total
  } finally {
    recordLoading.value = false
  }
}

function openCall() {
  callForm.type = 1
  callForm.lineId = undefined
  callForm.stationId = undefined
  callForm.description = ''
  callDialog.value = true
}

async function submitCall() {
  await api.andonCall({ ...callForm })
  ElMessage.success('呼叫已发出')
  callDialog.value = false
  loadAll()
}

async function respond(row: any) {
  await api.andonRespond(row.id)
  ElMessage.success('已响应')
  loadAll()
}

async function close(row: any) {
  const { value } = await ElMessageBox.prompt('处理结果', `关闭呼叫 ${row.code}`, {
    confirmButtonText: '关闭',
    cancelButtonText: '取消',
    inputPlaceholder: '处理结果（可空）'
  })
  await api.andonClose(row.id, value || '')
  ElMessage.success('已关闭')
  loadAll()
  loadRecords()
}

async function runEscalate() {
  const count = await api.andonEscalateRun()
  ElMessage.success(count > 0 ? `已升级 ${count} 条超时呼叫` : '没有待升级的超时呼叫')
  loadAll()
}

function genSummary() {
  summarizing.value = true
  summaryDialog.value.run('AI 安灯异常摘要（近 7 天）', () => api.aiAndonSummary(7)).finally(() => {
    summarizing.value = false
  })
}

onMounted(() => {
  loadAll()
  loadRecords()
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; flex-wrap: wrap; }
.pager { margin-top: 8px; justify-content: flex-end; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
