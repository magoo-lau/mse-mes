<template>
  <el-tabs v-model="tab" class="page">
    <el-tab-pane label="生产订单" name="order">
      <div class="toolbar">
        <el-input v-model="orderQuery.keyword" placeholder="工单号" clearable style="width: 180px" @keyup.enter="loadOrders" />
        <el-select v-model="orderQuery.status" placeholder="状态" clearable style="width: 130px" @change="loadOrders">
          <el-option v-for="(label, key) in ORDER_STATUS" :key="key" :label="label" :value="Number(key)" />
        </el-select>
        <el-button type="primary" plain @click="loadOrders">查询</el-button>
        <el-button v-permission="'mes:order:save'" type="primary" @click="editOrder()">新建工单</el-button>
        <el-button v-permission="'mes:order:submit'" type="success" plain :disabled="!orderSelected.length" @click="batchOrder('submit')">批量提交</el-button>
        <el-button v-permission="'mes:order:release'" type="success" plain :disabled="!orderSelected.length" @click="batchOrder('release')">批量下达</el-button>
        <el-button v-permission="'mes:order:cancel'" type="warning" plain :disabled="!orderSelected.length" @click="batchOrder('cancel')">批量取消</el-button>
        <el-button v-permission="'mes:order:close'" type="warning" plain :disabled="!orderSelected.length" @click="batchOrder('close')">批量关闭</el-button>
        <el-button v-permission="'mes:order:delete'" type="danger" plain :disabled="!orderSelected.length" @click="removeOrderBatch">批量删除</el-button>
      </div>
      <el-table :data="orders" border size="small" @selection-change="(v: any[]) => (orderSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="工单号" width="150" />
        <el-table-column label="产品" min-width="120">
          <template #default="{ row }">{{ materialName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="planQty" label="计划数" width="90" />
        <el-table-column prop="finishedQty" label="完工数" width="90" />
        <el-table-column prop="okQty" label="合格数" width="90" />
        <el-table-column prop="ngQty" label="不良数" width="90" />
        <el-table-column prop="planStart" label="计划开始" width="105" />
        <el-table-column prop="planEnd" label="计划完成" width="105" />
        <el-table-column label="状态" width="90" fixed="right">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TYPE[row.status]">{{ ORDER_STATUS[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'mes:order:save'" link type="primary" @click="editOrder(row)">编辑</el-button>
            <el-button v-if="row.status === 0" v-permission="'mes:order:submit'" link type="primary" @click="act(() => api.submitOrder(row.id))">提交</el-button>
            <el-button v-if="row.status === 1" v-permission="'mes:order:release'" link type="success" @click="act(() => api.releaseOrder(row.id))">下达</el-button>
            <el-button v-if="row.status <= 1" v-permission="'mes:order:cancel'" link type="warning" @click="act(() => api.cancelOrder(row.id))">取消</el-button>
            <el-button v-if="row.status === 2 || row.status === 3" v-permission="'mes:order:close'" link type="warning" @click="act(() => api.closeOrder(row.id))">关闭</el-button>
            <el-button v-if="row.status === 0" v-permission="'mes:order:delete'" link type="danger" @click="removeOrder(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="orderTotal" v-model:page="orderQuery.pageNum" v-model:size="orderQuery.pageSize" @change="loadOrders" />
    </el-tab-pane>

    <el-tab-pane label="排产计划" name="plan">
      <div class="toolbar">
        <el-select v-model="planLine" placeholder="产线" clearable style="width: 150px" @change="loadPlans">
          <el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
        <el-button v-permission="'mes:plan:save'" type="primary" @click="editPlan()">新增排产</el-button>
        <el-button v-permission="'mes:ai:schedule'" type="warning" plain :loading="advising" @click="openAdvise">AI 排产建议</el-button>
        <el-button v-permission="'mes:plan:publish'" type="success" plain :disabled="!planSelected.length" @click="publishPlanBatch">批量发布</el-button>
      </div>
      <el-table :data="plans" border size="small" @selection-change="(v: any[]) => (planSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="workDate" label="日期" width="110" />
        <el-table-column prop="shiftCode" label="班次" width="80" />
        <el-table-column label="产线" min-width="110">
          <template #default="{ row }">{{ modelName(row.lineId) }}</template>
        </el-table-column>
        <el-table-column label="工单" min-width="140">
          <template #default="{ row }">{{ orderCode(row.orderId) }}</template>
        </el-table-column>
        <el-table-column prop="planQty" label="计划数" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'mes:plan:save'" link type="primary" @click="editPlan(row)">编辑</el-button>
            <el-button v-if="row.status === 0" v-permission="'mes:plan:publish'" link type="success" @click="act(() => api.publishPlan([row.id]))">发布</el-button>
            <el-button v-if="row.status === 0" v-permission="'mes:plan:save'" link type="danger" @click="removePlan(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="orderDialog" :title="orderForm.id ? '编辑工单' : '新建工单'" width="480px">
    <el-form :model="orderForm" label-width="90px">
      <el-form-item label="产品" required>
        <el-select v-model="orderForm.productId" filterable>
          <el-option v-for="m in materials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="BOM">
        <el-select v-model="orderForm.bomId" clearable filterable>
          <el-option v-for="b in boms" :key="b.id" :label="b.code" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="工艺路线">
        <el-select v-model="orderForm.routeId" clearable filterable>
          <el-option v-for="r in publishedRoutes" :key="r.id" :label="`${r.code} ${r.name}`" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划数量" required><el-input-number v-model="orderForm.planQty" :min="1" /></el-form-item>
      <el-form-item label="计划开始"><el-date-picker v-model="orderForm.planStart" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="计划完成"><el-date-picker v-model="orderForm.planEnd" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="orderForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="orderDialog = false">取消</el-button>
      <el-button type="primary" @click="submitOrder">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="planDialog" :title="planForm.id ? '编辑排产' : '新增排产'" width="440px">
    <el-form :model="planForm" label-width="80px">
      <el-form-item label="工单" required>
        <el-select v-model="planForm.orderId" filterable>
          <el-option v-for="o in releasedOrders" :key="o.id" :label="`${o.code}（${ORDER_STATUS[o.status]}）`" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="产线" required>
        <el-select v-model="planForm.lineId"><el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" /></el-select>
      </el-form-item>
      <el-form-item label="日期" required><el-date-picker v-model="planForm.workDate" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="班次" required>
        <el-select v-model="planForm.shiftCode"><el-option label="白班 D" value="D" /><el-option label="夜班 N" value="N" /></el-select>
      </el-form-item>
      <el-form-item label="计划数量" required><el-input-number v-model="planForm.planQty" :min="1" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="planDialog = false">取消</el-button>
      <el-button type="primary" @click="submitPlan">保存</el-button>
    </template>
  </el-dialog>
  <!-- AI 排产建议草稿：AI 只出草稿，勾选后经既有排产保存接口生效（人工确认） -->
  <el-dialog v-model="adviseDialog" title="AI 排产建议（草稿）" width="820px" destroy-on-close>
    <div v-loading="advising" element-loading-text="AI 正在生成排产草稿…" class="advise-body">
      <template v-if="!advising">
        <el-table :data="adviseRows" border size="small" @selection-change="(rows: any[]) => (adviseSelected = rows)">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="orderCode" label="工单" width="150" />
          <el-table-column label="产线" min-width="110">
            <template #default="{ row }">{{ modelName(row.lineId) || row.lineId }}</template>
          </el-table-column>
          <el-table-column prop="workDate" label="日期" width="105" />
          <el-table-column label="班次" width="70">
            <template #default="{ row }">{{ row.shiftCode === 'N' ? '夜班' : '白班' }}</template>
          </el-table-column>
          <el-table-column prop="planQty" label="计划数" width="85" />
          <el-table-column prop="reason" label="AI 理由" min-width="160" show-overflow-tooltip />
        </el-table>
        <div class="ai-notice">以上内容由 AI 生成，仅供参考；勾选后点「应用选中项」写入排产计划（草稿态，需发布后生效）。</div>
      </template>
    </div>
    <template #footer>
      <el-button @click="adviseDialog = false">关闭</el-button>
      <el-button v-permission="'mes:plan:save'" type="primary" :disabled="!adviseSelected.length" @click="applyAdvise">
        应用选中项（{{ adviseSelected.length }}）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { showBatchResult } from '../utils/batch'
import { Pagination } from 'mse-ui-shared/pc'

const ORDER_STATUS: Record<number, string> = { 0: '草稿', 1: '已提交', 2: '已下达', 3: '生产中', 4: '完工', 5: '已关闭', 6: '已取消' }
const STATUS_TYPE: Record<number, string> = { 0: 'info', 1: '', 2: 'primary', 3: 'warning', 4: 'success', 5: 'info', 6: 'danger' }

const tab = ref('order')
const orders = ref<any[]>([])
const orderTotal = ref(0)
const orderQuery = reactive<any>({ pageNum: 1, pageSize: 10, keyword: '', status: undefined })
const plans = ref<any[]>([])
const planLine = ref<number | undefined>()
const models = ref<any[]>([])
const materials = ref<any[]>([])
const boms = ref<any[]>([])
const routes = ref<any[]>([])

const orderSelected = ref<any[]>([])
const planSelected = ref<any[]>([])

// 批量状态流转（同一动作逐条执行，状态不允许的工单由后端跳过并报原因）
async function batchOrder(action: 'submit' | 'release' | 'cancel' | 'close') {
  const labels = { submit: '批量提交', release: '批量下达', cancel: '批量取消', close: '批量关闭' } as const
  const ids = orderSelected.value.map((r) => r.id)
  await ElMessageBox.confirm(`确认对选中的 ${ids.length} 个工单执行「${labels[action]}」？（状态不允许的工单将跳过）`)
  const call = { submit: api.batchSubmitOrder, release: api.batchReleaseOrder, cancel: api.batchCancelOrder, close: api.batchCloseOrder }[action]
  showBatchResult(await call(ids), labels[action])
  loadOrders()
}
async function removeOrderBatch() {
  const ids = orderSelected.value.map((r) => r.id)
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个工单？（仅草稿可删，非草稿将跳过）`)
  showBatchResult(await api.deleteOrderBatch(ids), '批量删除')
  loadOrders()
}
async function publishPlanBatch() {
  const ids = planSelected.value.filter((r) => r.status === 0).map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('勾选中没有草稿排产')
    return
  }
  await ElMessageBox.confirm(`确认发布选中的 ${ids.length} 条草稿排产？`)
  await api.publishPlan(ids)
  ElMessage.success('已发布')
  loadPlans()
}

const lines = computed(() => models.value.filter((m) => m.type === 3))
const publishedRoutes = computed(() => routes.value.filter((r) => r.status === 1))
// 排产可选工单（后端专用 list 接口按状态全量取，不依赖分页页）
const releasedOrders = ref<any[]>([])
const materialName = (id: number) => materials.value.find((m) => m.id === id)?.name || id
const modelName = (id: number) => models.value.find((m) => m.id === id)?.name || id
const orderCode = (id: number) => orders.value.find((o) => o.id === id)?.code
  || releasedOrders.value.find((o) => o.id === id)?.code || id

const orderDialog = ref(false)
const orderForm = reactive<any>({})
const planDialog = ref(false)
const planForm = reactive<any>({})

async function loadOrders() {
  const res: any = await api.pageOrder(orderQuery)
  orders.value = res.records
  orderTotal.value = Number(res.total)
  // 排产下拉与工单号回显依赖的「可选工单」全量列表随查询刷新（提交/下达/关闭会改状态）
  releasedOrders.value = await api.listOrder({ statuses: '1,2' }) as any[]
}
async function loadPlans() {
  plans.value = await api.listPlan(planLine.value ? { lineId: planLine.value } : undefined) as any[]
}

function editOrder(row?: any) {
  Object.assign(orderForm, { id: undefined, productId: undefined, bomId: undefined, routeId: undefined, planQty: 1, planStart: '', planEnd: '', remark: '' }, row || {})
  orderDialog.value = true
}
async function submitOrder() {
  if (orderForm.id) await api.updateOrder(orderForm.id, orderForm)
  else await api.createOrder(orderForm)
  orderDialog.value = false
  ElMessage.success('已保存')
  loadOrders()
}
async function removeOrder(row: any) {
  await ElMessageBox.confirm(`确认删除工单「${row.code}」？`)
  await api.deleteOrder(row.id)
  loadOrders()
}
async function act(fn: () => Promise<unknown>) {
  await fn()
  ElMessage.success('操作成功')
  loadOrders()
}

function editPlan(row?: any) {
  Object.assign(planForm, { id: undefined, orderId: undefined, lineId: undefined, workDate: '', shiftCode: 'D', planQty: 1, remark: '' }, row || {})
  planDialog.value = true
}
async function submitPlan() {
  if (planForm.id) await api.updatePlan(planForm.id, planForm)
  else await api.savePlan(planForm)
  planDialog.value = false
  ElMessage.success('已保存')
  loadPlans()
}
async function removePlan(row: any) {
  await ElMessageBox.confirm('确认删除该排产？')
  await api.deletePlan(row.id)
  loadPlans()
}

// ==================== AI 排产建议（二期，AI 只出草稿人工确认） ====================
const adviseDialog = ref(false)
const advising = ref(false)
const adviseRows = ref<any[]>([])
const adviseSelected = ref<any[]>([])

async function openAdvise() {
  adviseDialog.value = true
  advising.value = true
  try {
    const raw = (await api.aiScheduleAdvise()) || '[]'
    const rows = JSON.parse(raw.replace(/^```(json)?/, '').replace(/```$/, ''))
    if (!Array.isArray(rows)) throw new Error('草稿格式异常')
    adviseRows.value = rows
    if (!rows.length) ElMessage.warning('没有待排产的工单（仅已下达/生产中工单参与排产建议）')
  } catch (e: any) {
    ElMessage.error('AI 排产建议生成失败：' + (e.message || '请重试'))
    adviseDialog.value = false
  } finally {
    advising.value = false
  }
}

async function applyAdvise() {
  await ElMessageBox.confirm(`将把选中的 ${adviseSelected.value.length} 条建议写入排产计划（草稿态），确认？`, '应用 AI 排产建议')
  for (const row of adviseSelected.value) {
    await api.savePlan({
      orderId: row.orderId,
      lineId: row.lineId,
      workDate: row.workDate,
      shiftCode: row.shiftCode,
      planQty: row.planQty,
      remark: 'AI 建议：' + (row.reason || '')
    })
  }
  ElMessage.success('已写入排产计划')
  adviseDialog.value = false
  loadPlans()
}

onMounted(async () => {
  loadOrders()
  loadPlans()
  models.value = await api.listModel() as any[]
  materials.value = await api.listMaterial() as any[]
  boms.value = (await api.pageBom({ pageNum: 1, pageSize: 100 })).records as any[]
  routes.value = (await api.pageRoute({ pageNum: 1, pageSize: 100 })).records as any[]
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.advise-body { min-height: 140px; }
.ai-notice { margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.pager { margin-top: 8px; justify-content: flex-end; }
</style>
