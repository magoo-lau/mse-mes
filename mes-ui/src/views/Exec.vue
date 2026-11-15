<template>
  <el-tabs v-model="tab" class="page">
    <el-tab-pane label="派工" name="dispatch">
      <div class="toolbar">
        <el-select v-model="dQuery.status" placeholder="状态" clearable style="width: 120px" @change="loadDispatch">
          <el-option label="待开工" :value="0" /><el-option label="生产中" :value="1" /><el-option label="已完成" :value="2" />
        </el-select>
        <el-button type="primary" plain @click="loadDispatch">查询</el-button>
      </div>
      <el-table :data="dispatches" border size="small">
        <el-table-column label="工单" width="150">
          <template #default="{ row }">{{ orderCode(row.orderId) }}</template>
        </el-table-column>
        <el-table-column prop="stepNo" label="工序号" width="70" />
        <el-table-column prop="stepName" label="工序" min-width="110" />
        <el-table-column label="产线" min-width="100">
          <template #default="{ row }">{{ modelName(row.lineId) }}</template>
        </el-table-column>
        <el-table-column label="工位" min-width="100">
          <template #default="{ row }">{{ modelName(row.workstationId) }}</template>
        </el-table-column>
        <el-table-column prop="planQty" label="派工数" width="85" />
        <el-table-column prop="reportedQty" label="已报工" width="85" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'warning', 'success'][row.status]">{{ ['待开工', '生产中', '已完成'][row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status < 2" v-permission="'mes:dispatch:assign'" link type="primary" @click="openAssign(row)">派工</el-button>
            <el-button v-if="row.status < 2" v-permission="'mes:dispatch:transfer'" link type="primary" @click="openTransfer(row)">转入</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="dTotal" v-model:page="dQuery.pageNum" v-model:size="dQuery.pageSize" @change="loadDispatch" />
    </el-tab-pane>

    <el-tab-pane label="生产进度" name="progress">
      <el-table :data="progress" border size="small">
        <el-table-column prop="code" label="工单号" width="150" />
        <el-table-column prop="planQty" label="计划数" width="90" />
        <el-table-column prop="finishedQty" label="完工数" width="90" />
        <el-table-column prop="okQty" label="合格数" width="90" />
        <el-table-column prop="ngQty" label="不良数" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small">{{ ['', '已提交', '已下达', '生产中', '完工', '已关闭'][row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="工序进度" min-width="240">
          <template #default="{ row }">
            <el-tag v-for="s in row.steps" :key="s.dispatchId" size="small" class="step-tag"
              :type="s.status === 2 ? 'success' : s.status === 1 ? 'warning' : 'info'">
              {{ s.stepName || '无工序' }} {{ Number(s.reportedQty) }}/{{ Number(s.planQty) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="在制品" name="wip">
      <div class="toolbar">
        <el-select v-model="wipLine" placeholder="产线" clearable style="width: 150px" @change="loadWip">
          <el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
      </div>
      <el-table :data="wip" border size="small">
        <el-table-column prop="orderCode" label="工单号" width="150" />
        <el-table-column prop="stepNo" label="工序号" width="80" />
        <el-table-column prop="stepName" label="工序" min-width="120" />
        <el-table-column prop="remainQty" label="在制数量" width="110" />
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="异常停工" name="halt">
      <div class="toolbar">
        <el-button v-permission="'mes:halt:save'" type="primary" @click="haltDialog = true">登记停工</el-button>
      </div>
      <el-table :data="halts" border size="small">
        <el-table-column prop="startTime" label="开始" width="160" />
        <el-table-column prop="endTime" label="恢复" width="160" />
        <el-table-column label="原因" width="110">
          <template #default="{ row }">{{ HALT_REASON[row.reason] || row.reason }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.endTime ? 'success' : 'danger'">{{ row.endTime ? '已恢复' : '停工中' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.endTime" v-permission="'mes:halt:save'" link type="success" @click="recover(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="hTotal" v-model:page="hQuery.pageNum" v-model:size="hQuery.pageSize" @change="loadHalts" />
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="assignDialog" title="派工指派" width="420px">
    <el-form :model="assignForm" label-width="80px">
      <el-form-item label="产线">
        <el-select v-model="assignForm.lineId" clearable><el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" /></el-select>
      </el-form-item>
      <el-form-item label="工位">
        <el-select v-model="assignForm.workstationId" clearable><el-option v-for="w in workstations" :key="w.id" :label="w.name" :value="w.id" /></el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="assignDialog = false">取消</el-button>
      <el-button type="primary" @click="submitAssign">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="transferDialog" title="工序转移" width="420px">
    <el-form :model="transferForm" label-width="90px">
      <el-form-item label="转入工序" required>
        <el-select v-model="transferForm.toDispatchId">
          <el-option v-for="d in transferTargets" :key="d.id" :label="`${d.stepNo || '-'} ${d.stepName || '无工序'}`" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="数量" required><el-input-number v-model="transferForm.qty" :min="1" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="transferForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="transferDialog = false">取消</el-button>
      <el-button type="primary" @click="submitTransfer">转移</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="haltDialog" title="登记停工" width="420px">
    <el-form :model="haltForm" label-width="80px">
      <el-form-item label="原因" required>
        <el-select v-model="haltForm.reason">
          <el-option v-for="(label, key) in HALT_REASON" :key="key" :label="label" :value="Number(key)" />
        </el-select>
      </el-form-item>
      <el-form-item label="产线">
        <el-select v-model="haltForm.lineId" clearable><el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" /></el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="haltForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="haltDialog = false">取消</el-button>
      <el-button type="primary" @click="submitHalt">登记</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api/mes'
import { Pagination } from 'mse-ui-shared/pc'

const HALT_REASON: Record<number, string> = { 1: '设备故障', 2: '缺料', 3: '质量异常', 4: '计划变更', 9: '其他' }

const tab = ref('dispatch')
const dispatches = ref<any[]>([])
const dTotal = ref(0)
const dQuery = reactive<any>({ pageNum: 1, pageSize: 10, status: undefined })
const progress = ref<any[]>([])
const wip = ref<any[]>([])
const wipLine = ref<number | undefined>()
const halts = ref<any[]>([])
const hTotal = ref(0)
const hQuery = reactive<any>({ pageNum: 1, pageSize: 10 })
const orders = ref<any[]>([])
const models = ref<any[]>([])

const lines = computed(() => models.value.filter((m) => m.type === 3))
const workstations = computed(() => models.value.filter((m) => m.type === 4))

const orderCode = (id: number) => orders.value.find((o) => o.id === id)?.code || id
const modelName = (id?: number) => (id ? models.value.find((m) => m.id === id)?.name || '-' : '-')

const assignDialog = ref(false)
const assignForm = reactive<any>({})
const transferDialog = ref(false)
const transferForm = reactive<any>({})
const transferTargets = ref<any[]>([])
const haltDialog = ref(false)
const haltForm = reactive<any>({})

async function loadDispatch() {
  const res: any = await api.pageDispatch(dQuery)
  dispatches.value = res.records
  dTotal.value = Number(res.total)
}
async function loadProgress() {
  progress.value = await api.progressList() as any[]
}
async function loadWip() {
  wip.value = await api.wipList(wipLine.value) as any[]
}
async function loadHalts() {
  const res: any = await api.haltPage(hQuery)
  halts.value = res.records
  hTotal.value = Number(res.total)
}

function openAssign(row: any) {
  Object.assign(assignForm, { id: row.id, lineId: row.lineId || undefined, workstationId: row.workstationId || undefined })
  assignDialog.value = true
}
async function submitAssign() {
  await api.assignDispatch(assignForm.id, { lineId: assignForm.lineId, workstationId: assignForm.workstationId })
  assignDialog.value = false
  ElMessage.success('已派工')
  loadDispatch()
}

function openTransfer(row: any) {
  Object.assign(transferForm, { fromDispatchId: row.id, toDispatchId: undefined, qty: 1, remark: '' })
  api.pageDispatch({ pageNum: 1, pageSize: 100, orderId: row.orderId, status: 0 }).then((res: any) => {
    transferTargets.value = res.records.filter((d: any) => d.id !== row.id)
  })
  transferDialog.value = true
}
async function submitTransfer() {
  await api.transferDispatch(transferForm)
  transferDialog.value = false
  ElMessage.success('已转移')
  loadDispatch()
}

async function submitHalt() {
  await api.saveHalt(haltForm)
  haltDialog.value = false
  ElMessage.success('已登记')
  loadHalts()
}
async function recover(row: any) {
  await api.recoverHalt(row.id)
  ElMessage.success('已恢复')
  loadHalts()
}

onMounted(() => {
  loadDispatch()
  loadProgress()
  loadWip()
  loadHalts()
  api.pageOrder({ pageNum: 1, pageSize: 200 }).then((r: any) => (orders.value = r.records))
  api.listModel().then((r: any) => (models.value = r))
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.pager { margin-top: 8px; justify-content: flex-end; }
.step-tag { margin-right: 4px; }
</style>
