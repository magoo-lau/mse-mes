<template>
  <div class="page">
    <!-- AI 工具条（二期）：日报草稿生成 + 自然语言问数（mes_query 反向工具受控供数） -->
    <el-card shadow="never" class="ai-bar">
      <div class="ai-bar-row">
        <el-button v-permission="'mes:ai:report'" type="warning" plain :loading="reporting" @click="genReport">AI 生成日报</el-button>
        <div class="qa-row">
          <el-input v-model="question" v-permission="'mes:ai:data-qa'" placeholder="AI 智能问数：如「未完成工单有哪些」「上周合格率」"
            clearable :disabled="qaLoading" @keyup.enter="ask" />
          <el-button v-permission="'mes:ai:data-qa'" type="primary" plain :loading="qaLoading" @click="ask">提问</el-button>
        </div>
      </div>
    </el-card>

    <!-- 总览指标 -->
    <el-row :gutter="12" class="mt12">
      <el-col :span="4" v-for="card in cards" :key="card.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="mt12">
      <el-col :span="14">
        <el-card header="近 7 日产量趋势">
          <el-table :data="production" border size="small">
            <el-table-column prop="date" label="日期" width="110" />
            <el-table-column prop="okQty" label="合格数" width="100" />
            <el-table-column prop="ngQty" label="不良数" width="100" />
            <el-table-column label="合格率">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.rate)" :stroke-width="12" :color="Number(row.rate) >= 95 ? '#67c23a' : '#e6a23c'" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card header="不良原因 TOP（近 7 日）">
          <el-table :data="quality" border size="small">
            <el-table-column prop="reason" label="原因" min-width="130" />
            <el-table-column prop="qty" label="数量" width="90" />
          </el-table>
        </el-card>
        <el-card header="设备维修统计（近 30 日）" class="mt12">
          <el-table :data="equipment" border size="small">
            <el-table-column prop="equipmentName" label="设备" min-width="110" />
            <el-table-column prop="repairCount" label="维修次数" width="90" />
            <el-table-column prop="downtimeMinutes" label="停机(分)" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <AiResultDialog ref="aiDialog" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import * as api from '../api/mes'
import AiResultDialog from '../components/AiResultDialog.vue'

const overview = ref<any>({})
const production = ref<any[]>([])
const quality = ref<any[]>([])
const equipment = ref<any[]>([])

const cards = computed(() => [
  { label: '今日合格数', value: overview.value.todayOkQty ?? '-', color: '#67c23a' },
  { label: '今日不良数', value: overview.value.todayNgQty ?? '-', color: '#f56c6c' },
  { label: '今日合格率 %', value: overview.value.todayRate ?? '-', color: '#409eff' },
  { label: '在制工单', value: overview.value.producingOrders ?? '-', color: '#e6a23c' },
  { label: '待检检验单', value: overview.value.pendingQc ?? '-', color: '#909399' },
  { label: '停工中/未关异常', value: `${overview.value.halting ?? '-'} / ${overview.value.openExceptions ?? '-'}`, color: '#f56c6c' }
])

// ==================== AI 日报 / 智能问数（二期，AI 只出草稿） ====================
const aiDialog = ref()
const reporting = ref(false)
const qaLoading = ref(false)
const question = ref('')

function genReport() {
  aiDialog.value.run('AI 生产日报（近 1 日草稿）', () => api.aiDailyReport(1))
}

async function ask() {
  const q = question.value.trim()
  if (!q) return
  qaLoading.value = true
  try {
    const answer = await api.aiDataQa(q)
    aiDialog.value.run(`AI 智能问数：${q}`, async () => answer)
    question.value = ''
  } finally {
    qaLoading.value = false
  }
}

onMounted(async () => {
  overview.value = await api.dashboardOverview() as any
  production.value = await api.statsProduction(7) as any[]
  quality.value = await api.statsQuality(7, 10) as any[]
  equipment.value = await api.statsEquipment(30) as any[]
})
</script>

<style scoped>
.page { padding: 12px; }
.ai-bar-row { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; }
.qa-row { display: flex; gap: 8px; flex: 1; min-width: 320px; }
.qa-row .el-input { max-width: 420px; }

.stat-card { text-align: center; }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
.stat-value { font-size: 24px; font-weight: 600; margin-top: 4px; }
.mt12 { margin-top: 12px; }
</style>
