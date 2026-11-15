<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="控制图" name="chart">
        <div class="toolbar">
          <el-select v-model="chartItemId" placeholder="监控项" filterable style="width: 240px" @change="loadChart">
            <el-option v-for="i in items" :key="i.id" :label="`${i.code} ${i.name}`" :value="i.id" />
          </el-select>
          <el-select v-model="chartDays" style="width: 120px" @change="loadChart">
            <el-option label="近 7 天" :value="7" />
            <el-option label="近 30 天" :value="30" />
            <el-option label="近 90 天" :value="90" />
          </el-select>
          <el-button type="primary" plain @click="loadChart">刷新</el-button>
          <el-button v-permission="'mes:spc:list'" @click="openSample">录入样本</el-button>
          <el-button v-permission="'mes:ai:spc-interpret'" type="warning" plain :loading="interpreting"
            @click="interpret">AI SPC 判读</el-button>
        </div>

        <template v-if="chart">
          <el-row :gutter="12" class="mt12">
            <el-col :span="6">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">X̄̄ / 控制限</div>
                <div class="stat-value">{{ limits ? `${limits.xBar} [${limits.xBarLcl}, ${limits.xBarUcl}]` : '样本不足' }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">R̄ / R 上限</div>
                <div class="stat-value">{{ limits ? `${limits.rBar} / ${limits.rUcl}` : '样本不足' }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">Cp / Cpk</div>
                <div class="stat-value">{{ capability ? `${capability.cp ?? '-'} / ${capability.cpk ?? '-'}` : '需双侧规格限' }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">超限报警</div>
                <div class="stat-value">
                  <el-tag v-if="chart.outOfControl > 0 || chart.outOfSpec > 0" type="danger" size="small">
                    失控 {{ chart.outOfControl }} / 超规格 {{ chart.outOfSpec }}
                  </el-tag>
                  <el-tag v-else type="success" size="small">受控</el-tag>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="mt12" header="X̄-R 控制图（分组均值与极差）">
            <el-table :data="chart.groups" border size="small" :row-class-name="rowClass" max-height="420">
              <el-table-column prop="group" label="子组" width="70" />
              <el-table-column prop="collectTime" label="起始采样" width="165" />
              <el-table-column prop="mean" label="X̄（均值）" width="110" />
              <el-table-column label="X̄ 判定" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.incomplete" size="small" type="info">不完整组</el-tag>
                  <el-tag v-else-if="row.xBarViolate" size="small" type="danger">超限</el-tag>
                  <el-tag v-else size="small" type="success">受控</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="range" label="R（极差）" width="110" />
              <el-table-column label="R 判定" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.rViolate" size="small" type="danger">超限</el-tag>
                  <el-tag v-else size="small" type="success">受控</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div class="tip">控制限：X̄ 图 = X̄̄ ± A2·R̄，R 图 UCL = D4·R̄（Shewhart 系数按子组大小 n 内置）；样本不足两个完整子组时不评定。</div>
          </el-card>
        </template>
        <el-empty v-else description="选择监控项查看控制图" />
      </el-tab-pane>

      <el-tab-pane label="监控项" name="item">
        <div class="toolbar">
          <el-input v-model="itemQuery.keyword" placeholder="名称" clearable style="width: 180px" @keyup.enter="loadItems" />
          <el-button type="primary" plain @click="loadItems">查询</el-button>
          <el-button v-permission="'mes:spc:list'" type="primary" @click="editItem()">新增监控项</el-button>
          <el-button v-permission="'mes:spc:list'" type="danger" plain :disabled="!itemSelected.length" @click="removeItemBatch">批量删除</el-button>
        </div>
        <el-table :data="items" border size="small" v-loading="itemLoading" @selection-change="(v: any[]) => (itemSelected = v)">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="code" label="编码" width="130" />
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="stepName" label="工序" width="100" />
          <el-table-column prop="featureName" label="质量特性" min-width="110" />
          <el-table-column label="规格限 [LSL, USL]" min-width="150">
            <template #default="{ row }">[{{ row.lsl ?? '-' }}, {{ row.usl ?? '-' }}] {{ row.unit || '' }}</template>
          </el-table-column>
          <el-table-column prop="sampleSize" label="子组 n" width="75" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'mes:spc:list'" link type="primary" @click="editItem(row)">编辑</el-button>
              <el-button v-permission="'mes:spc:list'" link type="danger" @click="removeItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination :total="itemTotal" v-model:page="itemQuery.pageNum" v-model:size="itemQuery.pageSize" @change="loadItems" />
      </el-tab-pane>
    </el-tabs>

    <!-- 监控项编辑 -->
    <el-dialog v-model="itemDialog" :title="itemForm.id ? '编辑监控项' : '新增监控项'" width="560px">
      <el-form :model="itemForm" label-width="90px">
        <el-form-item label="编码" required><el-input v-model="itemForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="itemForm.name" /></el-form-item>
        <el-form-item label="工序"><el-input v-model="itemForm.stepName" /></el-form-item>
        <el-form-item label="质量特性"><el-input v-model="itemForm.featureName" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="itemForm.unit" style="width: 120px" /></el-form-item>
        <el-form-item label="规格下限"><el-input-number v-model="itemForm.lsl" :controls="false" style="width: 140px" /></el-form-item>
        <el-form-item label="规格上限"><el-input-number v-model="itemForm.usl" :controls="false" style="width: 140px" /></el-form-item>
        <el-form-item label="目标值"><el-input-number v-model="itemForm.target" :controls="false" style="width: 140px" /></el-form-item>
        <el-form-item label="子组大小 n">
          <el-input-number v-model="itemForm.sampleSize" :min="2" :max="10" controls-position="right" />
          <span class="tip-inline">2-10（X̄-R 图系数内置）</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="itemForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog = false">取消</el-button>
        <el-button type="primary" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>

    <!-- 样本录入 -->
    <el-dialog v-model="sampleDialog" title="录入样本测量值" width="480px">
      <el-form :model="sampleForm" label-width="90px">
        <el-form-item label="监控项" required>
          <el-select v-model="sampleForm.itemId" filterable style="width: 320px">
            <el-option v-for="i in items" :key="i.id" :label="`${i.code} ${i.name}`" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="测量值" required>
          <el-input v-model="sampleForm.valuesText" type="textarea" :rows="3"
            placeholder="逗号/空格分隔，建议一次录一个完整子组（n 个值）" />
        </el-form-item>
        <el-form-item label="关联工单"><el-input v-model="sampleForm.orderCode" style="width: 200px" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sampleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSample">录入</el-button>
      </template>
    </el-dialog>

    <AiResultDialog ref="interpretDialog" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { showBatchResult } from '../utils/batch'
import AiResultDialog from '../components/AiResultDialog.vue'
import { Pagination } from 'mse-ui-shared/pc'

const tab = ref('chart')
const items = ref<any[]>([])
const chartItemId = ref<number>()
const chartDays = ref(30)
const chart = ref<any>(null)
const interpreting = ref(false)
const interpretDialog = ref()

const itemQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const itemTotal = ref(0)
const itemLoading = ref(false)
const itemDialog = ref(false)
const itemForm = reactive<any>({})
const itemSelected = ref<any[]>([])

const sampleDialog = ref(false)
const sampleForm = reactive<any>({ itemId: undefined, valuesText: '', orderCode: '' })

const limits = computed(() => chart.value?.limits)
const capability = computed(() => chart.value?.capability)

function rowClass({ row }: { row: any }) {
  return row.xBarViolate || row.rViolate ? 'spc-violate-row' : ''
}

async function loadItems() {
  itemLoading.value = true
  try {
    const page = await api.pageSpcItem({ ...itemQuery }) as any
    items.value = page.records || []
    itemTotal.value = page.total
  } finally {
    itemLoading.value = false
  }
}

async function loadChart() {
  if (!chartItemId.value) {
    chart.value = null
    return
  }
  chart.value = await api.spcChart(chartItemId.value, chartDays.value) as any
}

function editItem(row?: any) {
  Object.assign(itemForm, row || {
    id: null, code: '', name: '', stepName: '', featureName: '', unit: '',
    usl: undefined, lsl: undefined, target: undefined, sampleSize: 5, remark: ''
  })
  itemDialog.value = true
}

async function submitItem() {
  await api.saveSpcItem({ ...itemForm })
  ElMessage.success('已保存')
  itemDialog.value = false
  loadItems()
}

async function removeItem(row: any) {
  await ElMessageBox.confirm(`删除监控项「${row.name}」及其全部样本？`, '删除确认', { type: 'warning' })
  await api.deleteSpcItem(row.id)
  ElMessage.success('已删除')
  loadItems()
}
async function removeItemBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${itemSelected.value.length} 个监控项及其全部样本？`, '删除确认', { type: 'warning' })
  showBatchResult(await api.deleteSpcItemBatch(itemSelected.value.map((r) => r.id)), '批量删除')
  loadItems()
}

function openSample() {
  if (!items.value.length) {
    ElMessage.warning('请先创建监控项')
    return
  }
  sampleForm.itemId = items.value[0].id
  sampleForm.valuesText = ''
  sampleForm.orderCode = ''
  sampleDialog.value = true
}

async function submitSample() {
  const values = sampleForm.valuesText.split(/[,，\s]+/).filter(Boolean).map(Number)
  if (!values.length) {
    ElMessage.warning('请填写测量值')
    return
  }
  const count = await api.addSpcSample({ itemId: sampleForm.itemId, values, orderCode: sampleForm.orderCode || null, source: 1 })
  ElMessage.success(`已录入 ${count} 个测量值`)
  sampleDialog.value = false
  if (sampleForm.itemId === chartItemId.value) loadChart()
}

function interpret() {
  if (!chartItemId.value) {
    ElMessage.warning('请先选择监控项')
    return
  }
  interpreting.value = true
  interpretDialog.value.run('AI SPC 判读', () => api.aiSpcInterpret(chartItemId.value!, chartDays.value)).finally(() => {
    interpreting.value = false
  })
}

onMounted(loadItems)
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; flex-wrap: wrap; }
.pager { margin-top: 8px; justify-content: flex-end; }
.mt12 { margin-top: 12px; }
.stat-card { text-align: center; }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
.stat-value { font-size: 16px; font-weight: 600; margin-top: 4px; }
.tip { margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.tip-inline { margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
:deep(.spc-violate-row) { --el-table-tr-bg-color: var(--el-color-danger-light-9); }
</style>
