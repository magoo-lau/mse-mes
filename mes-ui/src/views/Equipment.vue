<template>
  <el-tabs v-model="tab" class="page">
    <el-tab-pane label="设备台账" name="equipment">
      <div class="toolbar">
        <el-button v-permission="'mes:equip:save'" type="primary" @click="editEquipment()">新增设备</el-button>
        <el-button v-permission="'mes:equip:import'" @click="importDialogRef?.open()">导入</el-button>
        <el-button v-permission="'mes:equip:export'" @click="exportEquipment">导出</el-button>
        <el-button v-permission="'mes:equip:delete'" type="danger" plain :disabled="!eqSelected.length" @click="removeEquipmentBatch">批量删除</el-button>
      </div>
      <el-table :data="equipments" border size="small" @selection-change="(v: any[]) => (eqSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="model" label="型号" width="120" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag size="small" :type="EQ_TYPE[row.status]">{{ EQ_STATUS[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buyDate" label="购置日期" width="105" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:equip:save'" link type="primary" @click="editEquipment(row)">编辑</el-button>
            <el-button v-permission="'mes:equip:delete'" link type="danger" @click="removeEquipment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="点检保养" name="check">
      <div class="toolbar">
        <el-button v-permission="'mes:check:plan:save'" type="primary" @click="editPlan()">新增计划</el-button>
        <el-button v-permission="'mes:check:submit'" type="success" @click="editRecord()">执行打卡</el-button>
        <el-button v-permission="'mes:check:plan:save'" type="success" plain :disabled="!planSelected.length" @click="togglePlanBatch(1)">批量启用</el-button>
        <el-button v-permission="'mes:check:plan:save'" type="warning" plain :disabled="!planSelected.length" @click="togglePlanBatch(0)">批量停用</el-button>
      </div>
      <el-table :data="checkPlans" border size="small" @selection-change="(v: any[]) => (planSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="name" label="计划" min-width="130" />
        <el-table-column label="设备" min-width="120">
          <template #default="{ row }">{{ equipName(row.equipmentId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.type === 1 ? '点检' : '保养' }}</template>
        </el-table-column>
        <el-table-column label="周期" width="80">
          <template #default="{ row }">{{ ['', '日', '周', '月'][row.cycle] }}</template>
        </el-table-column>
        <el-table-column prop="nextDate" label="下次执行" width="105" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:check:plan:save'" link :type="row.status === 1 ? 'warning' : 'success'"
              @click="togglePlan(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider>执行记录</el-divider>
      <el-table :data="checkRecords" border size="small">
        <el-table-column prop="checkDate" label="日期" width="105" />
        <el-table-column label="设备" min-width="120">
          <template #default="{ row }">{{ equipName(row.equipmentId) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '正常' : '异常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="160" />
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="维修工单" name="repair">
      <div class="toolbar">
        <el-button v-permission="'mes:repair:create'" type="primary" @click="editRepair()">设备报修</el-button>
      </div>
      <el-table :data="repairs" border size="small">
        <el-table-column prop="code" label="工单号" width="145" />
        <el-table-column label="设备" min-width="120">
          <template #default="{ row }">{{ equipName(row.equipmentId) }}</template>
        </el-table-column>
        <el-table-column prop="faultDesc" label="故障" min-width="150" />
        <el-table-column prop="reportTime" label="报修时间" width="160" />
        <el-table-column prop="downtimeMinutes" label="停机(分)" width="90" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag size="small" :type="['danger', 'warning', 'success'][row.status]">{{ ['报修', '维修中', '已完工'][row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'mes:repair:handle'" link type="primary" @click="takeRepair(row)">受理</el-button>
            <el-button v-if="row.status === 1" v-permission="'mes:repair:handle'" link type="success" @click="finishRepair(row)">完工</el-button>
            <el-button v-permission="'mes:ai:equip-diag'" link type="warning" @click="aiDiag(row)">AI 诊断</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <AiResultDialog ref="aiDiagDialog" />

  <BatchImportDialog ref="importDialogRef" title="设备" template-url="/mes/equipment/import-template"
    import-url="/mes/equipment/import" @imported="loadEquipments" />

  <el-dialog v-model="eqDialog" :title="eqForm.id ? '编辑设备' : '新增设备'" width="440px">
    <el-form :model="eqForm" label-width="85px">
      <el-form-item label="编码" required><el-input v-model="eqForm.code" /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="eqForm.name" /></el-form-item>
      <el-form-item label="型号"><el-input v-model="eqForm.model" /></el-form-item>
      <el-form-item label="购置日期"><el-date-picker v-model="eqForm.buyDate" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="eqForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="eqDialog = false">取消</el-button>
      <el-button type="primary" @click="submitEquipment">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="planDialog" title="新增点保计划" width="420px">
    <el-form :model="planForm" label-width="85px">
      <el-form-item label="名称" required><el-input v-model="planForm.name" /></el-form-item>
      <el-form-item label="设备" required>
        <el-select v-model="planForm.equipmentId"><el-option v-for="e in equipments" :key="e.id" :label="e.name" :value="e.id" /></el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="planForm.type"><el-option label="点检" :value="1" /><el-option label="保养" :value="2" /></el-select>
      </el-form-item>
      <el-form-item label="周期">
        <el-select v-model="planForm.cycle"><el-option label="日" :value="1" /><el-option label="周" :value="2" /><el-option label="月" :value="3" /></el-select>
      </el-form-item>
      <el-form-item label="下次执行"><el-date-picker v-model="planForm.nextDate" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="内容"><el-input v-model="planForm.content" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="planDialog = false">取消</el-button>
      <el-button type="primary" @click="submitPlan">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="recordDialog" title="点检/保养打卡" width="420px">
    <el-form :model="recordForm" label-width="85px">
      <el-form-item label="设备" required>
        <el-select v-model="recordForm.equipmentId"><el-option v-for="e in equipments" :key="e.id" :label="e.name" :value="e.id" /></el-select>
      </el-form-item>
      <el-form-item label="结果" required>
        <el-select v-model="recordForm.result"><el-option label="正常" :value="1" /><el-option label="异常" :value="2" /></el-select>
      </el-form-item>
      <el-form-item label="说明"><el-input v-model="recordForm.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="recordDialog = false">取消</el-button>
      <el-button type="primary" @click="submitRecord">提交</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="repairDialog" title="设备报修" width="420px">
    <el-form :model="repairForm" label-width="85px">
      <el-form-item label="设备" required>
        <el-select v-model="repairForm.equipmentId"><el-option v-for="e in equipments" :key="e.id" :label="e.name" :value="e.id" /></el-select>
      </el-form-item>
      <el-form-item label="故障描述" required><el-input v-model="repairForm.faultDesc" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="repairDialog = false">取消</el-button>
      <el-button type="primary" @click="submitRepair">报修</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { download } from '../api/request'
import { showBatchResult } from '../utils/batch'
import AiResultDialog from '../components/AiResultDialog.vue'
import BatchImportDialog from '../components/BatchImportDialog.vue'

const EQ_STATUS: Record<number, string> = { 1: '运行', 2: '待机', 3: '维修', 4: '停机' }
const EQ_TYPE: Record<number, string> = { 1: 'success', 2: 'info', 3: 'danger', 4: 'warning' }

/** tab 状态与路由 query 双向同步：刷新/深链/聊天分享卡片打开均可还原到指定页签
 * （「页面分享状态写进 query」约定的参考样板，缺省页签不占 query 保持地址干净） */
const route = useRoute()
const router = useRouter()
const TABS = ['equipment', 'check', 'repair']
const tab = ref(TABS.includes(route.query.tab as string) ? (route.query.tab as string) : 'equipment')
watch(tab, (v) => {
  const next = v === 'equipment' ? undefined : v
  if ((route.query.tab ?? undefined) === next) return
  router.replace({ query: { ...route.query, tab: next } }).catch(() => { /* 同址导航忽略 */ })
})
watch(() => route.query.tab, (v) => {
  const t = TABS.includes(v as string) ? (v as string) : 'equipment'
  if (t !== tab.value) tab.value = t
})
const equipments = ref<any[]>([])
const checkPlans = ref<any[]>([])
const checkRecords = ref<any[]>([])
const repairs = ref<any[]>([])

const eqSelected = ref<any[]>([])
const planSelected = ref<any[]>([])
const importDialogRef = ref()
const exportEquipment = () => download('/mes/equipment/export', undefined, '设备台账.xlsx')

const eqDialog = ref(false)
const eqForm = reactive<any>({})
const planDialog = ref(false)
const planForm = reactive<any>({})
const recordDialog = ref(false)
const recordForm = reactive<any>({})
const repairDialog = ref(false)
const repairForm = reactive<any>({})

const equipName = (id: number) => equipments.value.find((e) => e.id === id)?.name || id

async function loadEquipments() {
  equipments.value = (await api.pageEquipment({ pageNum: 1, pageSize: 100 })).records as any[]
}
async function loadChecks() {
  checkPlans.value = (await api.pageCheckPlan({ pageNum: 1, pageSize: 100 })).records as any[]
  checkRecords.value = (await api.pageCheckRecord({ pageNum: 1, pageSize: 100 })).records as any[]
}
async function loadRepairs() {
  repairs.value = (await api.pageRepair({ pageNum: 1, pageSize: 100 })).records as any[]
}

function editEquipment(row?: any) {
  Object.assign(eqForm, { id: undefined, code: '', name: '', model: '', buyDate: '', remark: '' }, row || {})
  eqDialog.value = true
}
async function submitEquipment() {
  if (eqForm.id) await api.updateEquipment(eqForm.id, eqForm)
  else await api.saveEquipment(eqForm)
  eqDialog.value = false
  ElMessage.success('已保存')
  loadEquipments()
}
async function removeEquipment(row: any) {
  await ElMessageBox.confirm(`确认删除设备「${row.name}」？`)
  await api.deleteEquipment(row.id)
  loadEquipments()
}
async function removeEquipmentBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${eqSelected.value.length} 台设备？（被点检计划/维修工单/数采配置引用的将跳过）`)
  showBatchResult(await api.deleteEquipmentBatch(eqSelected.value.map((r) => r.id)), '批量删除')
  loadEquipments()
}
async function togglePlanBatch(status: number) {
  showBatchResult(await api.batchChangeCheckPlanStatus(status, planSelected.value.map((r) => r.id)),
    status === 1 ? '批量启用' : '批量停用')
  loadChecks()
}

function editPlan() {
  Object.assign(planForm, { name: '', equipmentId: undefined, type: 1, cycle: 1, nextDate: '', content: '' })
  planDialog.value = true
}
async function submitPlan() {
  await api.saveCheckPlan(planForm)
  planDialog.value = false
  ElMessage.success('已保存')
  loadChecks()
}
async function togglePlan(row: any) {
  await api.changeCheckPlanStatus(row.id, row.status === 1 ? 0 : 1)
  loadChecks()
}

function editRecord() {
  Object.assign(recordForm, { planId: undefined, equipmentId: undefined, result: 1, remark: '' })
  recordDialog.value = true
}
async function submitRecord() {
  await api.submitCheck(recordForm)
  recordDialog.value = false
  ElMessage.success('已打卡（异常自动生成维修工单）')
  loadChecks()
  loadRepairs()
}

function editRepair() {
  Object.assign(repairForm, { equipmentId: undefined, faultDesc: '' })
  repairDialog.value = true
}
async function submitRepair() {
  await api.createRepair(repairForm)
  repairDialog.value = false
  ElMessage.success('已报修')
  loadRepairs()
  loadEquipments()
}
async function takeRepair(row: any) {
  await api.takeRepair(row.id)
  ElMessage.success('已受理')
  loadRepairs()
}
async function finishRepair(row: any) {
  const input = await ElMessageBox.prompt('处理说明', '维修完工')
  await api.finishRepair(row.id, input.value || '')
  ElMessage.success('已完工')
  loadRepairs()
  loadEquipments()
}

// ==================== AI 设备故障诊断（二期，AI 只出建议） ====================
const aiDiagDialog = ref()
const aiDiag = (row: any) => aiDiagDialog.value.run(`AI 设备故障诊断：${row.code}`, () => api.aiEquipDiag(row.id))

onMounted(() => {
  loadEquipments()
  loadChecks()
  loadRepairs()
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
</style>
