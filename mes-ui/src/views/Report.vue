<template>
  <div class="page">
    <div class="kb-bar">
      <el-button v-permission="'mes:ai:kb-qa'" type="warning" plain @click="openKbQa">AI 工艺/SOP 问答</el-button>
      <span class="kb-tip">工艺路线 / SOP 问答（AI 生成，仅供参考）</span>
    </div>
    <el-row :gutter="12">
      <el-col :span="10">
        <el-card header="快速报工">
          <el-form :model="form" label-width="90px">
            <el-form-item label="派工单" required>
              <el-select v-model="form.dispatchId" filterable placeholder="选择派工单（无工序工单可选工单直报）" @change="onDispatchChange">
                <el-option v-for="d in openDispatches" :key="d.id" :value="d.id"
                  :label="`${orderCode(d.orderId)} / ${d.stepName || '无工序'}`" />
              </el-select>
            </el-form-item>
            <el-form-item label="工位">
              <el-select v-model="form.workstationId" clearable>
                <el-option v-for="w in workstations" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-radio-group v-model="form.type">
                <el-radio :value="1">正常报工</el-radio>
                <el-radio :value="2">返工报工</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="合格数量" required><el-input-number v-model="form.okQty" :min="0" /></el-form-item>
            <el-form-item label="不良数量" required><el-input-number v-model="form.ngQty" :min="0" /></el-form-item>
            <el-form-item v-if="form.ngQty > 0" label="不良原因" required><el-input v-model="form.defectReason" /></el-form-item>
            <el-form-item>
              <el-button v-permission="'mes:report:do'" type="primary" @click="submit">提交报工</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card header="报工记录">
          <div class="toolbar">
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="loadReports">
              <el-option label="待审核" :value="0" /><el-option label="已审核" :value="1" /><el-option label="已驳回" :value="2" />
            </el-select>
            <el-button type="primary" plain @click="loadReports">查询</el-button>
            <el-button v-permission="'mes:report:audit'" type="success" plain :disabled="!reportSelected.length" @click="batchAudit(true)">批量通过</el-button>
            <el-button v-permission="'mes:report:audit'" type="danger" plain :disabled="!reportSelected.length" @click="batchAudit(false)">批量驳回</el-button>
          </div>
          <el-table :data="reports" border size="small" @selection-change="(v: any[]) => (reportSelected = v)">
            <el-table-column type="selection" width="42" />
            <el-table-column prop="code" label="报工单号" width="145" />
            <el-table-column label="工单" width="145">
              <template #default="{ row }">{{ orderCode(row.orderId) }}</template>
            </el-table-column>
            <el-table-column prop="okQty" label="合格" width="70" />
            <el-table-column prop="ngQty" label="不良" width="70" />
            <el-table-column prop="defectReason" label="不良原因" min-width="100" />
            <el-table-column label="状态" width="85">
              <template #default="{ row }">
                <el-tag size="small" :type="['warning', 'success', 'danger'][row.status]">{{ ['待审核', '已审核', '已驳回'][row.status] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" v-permission="'mes:report:audit'" link type="success" @click="audit(row, true)">通过</el-button>
                <el-button v-if="row.status === 0" v-permission="'mes:report:audit'" link type="danger" @click="audit(row, false)">驳回</el-button>
                <el-button v-if="row.status < 2" v-permission="'mes:report:undo'" link type="warning" @click="undo(row)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <Pagination :total="total" v-model:page="query.pageNum" v-model:size="query.pageSize" @change="loadReports" />
        </el-card>
      </el-col>
    </el-row>
    <AiResultDialog ref="kbDialog" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { showBatchResult } from '../utils/batch'
import AiResultDialog from '../components/AiResultDialog.vue'
import { Pagination } from 'mse-ui-shared/pc'

const form = reactive<any>({ dispatchId: undefined, orderId: undefined, workstationId: undefined, type: 1, okQty: 0, ngQty: 0, defectReason: '' })
const dispatches = ref<any[]>([])
const orders = ref<any[]>([])
const models = ref<any[]>([])
const reports = ref<any[]>([])
const total = ref(0)
const query = reactive<any>({ pageNum: 1, pageSize: 10, status: undefined })
const reportSelected = ref<any[]>([])

const workstations = computed(() => models.value.filter((m) => m.type === 4))
const openDispatches = computed(() => dispatches.value.filter((d) => d.status < 2))
const orderCode = (id: number) => orders.value.find((o) => o.id === id)?.code || id

function onDispatchChange(dispatchId: number) {
  const d = dispatches.value.find((x) => x.id === dispatchId)
  form.orderId = d?.orderId
  form.workstationId = d?.workstationId || undefined
}

async function submit() {
  await api.submitReport(form)
  ElMessage.success('报工成功')
  form.okQty = 0
  form.ngQty = 0
  form.defectReason = ''
  loadReports()
  loadDispatches()
}

async function loadReports() {
  const res: any = await api.pageReport(query)
  reports.value = res.records
  total.value = Number(res.total)
}
async function loadDispatches() {
  const res: any = await api.pageDispatch({ pageNum: 1, pageSize: 200 })
  dispatches.value = res.records
}

async function audit(row: any, pass: boolean) {
  let remark = ''
  if (!pass) {
    const input = await ElMessageBox.prompt('请输入驳回原因', '驳回报工')
    remark = input.value || ''
  }
  await api.auditReport(row.id, { pass, remark })
  ElMessage.success(pass ? '已通过' : '已驳回（数量已回冲）')
  loadReports()
  loadDispatches()
}
/** 批量审核：同一决定逐条执行，非待审核状态的记录由后端跳过并报原因 */
async function batchAudit(pass: boolean) {
  const ids = reportSelected.value.filter((r) => r.status === 0).map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('勾选中没有待审核的报工记录')
    return
  }
  let remark = ''
  if (!pass) {
    const input = await ElMessageBox.prompt(`将驳回选中的 ${ids.length} 条报工记录（数量回冲），请输入驳回原因`, '批量驳回报工')
    remark = input.value || ''
  } else {
    await ElMessageBox.confirm(`确认通过选中的 ${ids.length} 条待审核报工记录？`)
  }
  showBatchResult(await api.batchAuditReport({ pass, remark, ids }), pass ? '批量通过' : '批量驳回')
  loadReports()
  loadDispatches()
}
async function undo(row: any) {
  await ElMessageBox.confirm(`确认撤销报工「${row.code}」？数量将回冲。`)
  await api.undoReport(row.id)
  ElMessage.success('已撤销')
  loadReports()
  loadDispatches()
}

// ==================== AI 工艺/SOP 知识问答（二期） ====================
const kbDialog = ref()
const kbQuestion = ref('')

function openKbQa() {
  ElMessageBox.prompt('请输入工艺/SOP 问题（如「这道工序的标准工时是多少」）', 'AI 工艺/SOP 问答', {
    confirmButtonText: '提问',
    cancelButtonText: '取消',
    inputPlaceholder: '问题'
  }).then(({ value }) => {
    const q = (value || '').trim()
    if (!q) return
    kbDialog.value.run(`AI 工艺/SOP 问答：${q}`, () => api.aiKbQa(q))
  }).catch(() => {})
}

onMounted(() => {
  loadReports()
  loadDispatches()
  api.pageOrder({ pageNum: 1, pageSize: 200 }).then((r: any) => (orders.value = r.records))
  api.listModel().then((r: any) => (models.value = r))
})
</script>

<style scoped>
.kb-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.kb-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
.pager { margin-top: 8px; justify-content: flex-end; }
</style>
