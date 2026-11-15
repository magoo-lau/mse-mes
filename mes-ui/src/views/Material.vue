<template>
  <el-tabs v-model="tab" class="page">
    <el-tab-pane label="领退料" name="matreq">
      <div class="toolbar">
        <el-button v-permission="'mes:matreq:create'" type="primary" @click="editReq()">领料申请</el-button>
        <el-button v-permission="'mes:matreq:return'" type="warning" plain @click="editReturn()">退料申请</el-button>
        <el-button v-permission="'mes:matreq:issue'" type="success" plain :disabled="!matreqSelected.length" @click="issueBatch">批量执行</el-button>
        <el-button v-permission="'mes:matreq:issue'" type="danger" plain :disabled="!matreqSelected.length" @click="rejectBatch">批量驳回</el-button>
      </div>
      <el-table :data="matReqs" border size="small" @selection-change="(v: any[]) => (matreqSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="单号" width="145" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }"><el-tag size="small" :type="row.type === 1 ? 'primary' : 'warning'">{{ row.type === 1 ? '领料' : '退料' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="物料" min-width="120">
          <template #default="{ row }">{{ materialName(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column prop="reqQty" label="数量" width="85" />
        <el-table-column prop="reqTime" label="申请时间" width="160" />
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'danger'][row.status]">{{ ['已申请', '已执行', '已驳回'][row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'mes:matreq:issue'" link type="success" @click="issue(row)">执行</el-button>
            <el-button v-if="row.status === 0" v-permission="'mes:matreq:issue'" link type="danger" @click="reject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="线边仓库存" name="stock">
      <div class="toolbar">
        <el-button v-permission="'mes:stock:adjust'" type="primary" plain @click="editAdjust()">库存调整</el-button>
      </div>
      <el-table :data="stocks" border size="small">
        <el-table-column label="物料" min-width="140">
          <template #default="{ row }">{{ materialName(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="140" />
        <el-table-column prop="qty" label="数量" width="110" />
        <el-table-column prop="location" label="仓位" width="90" />
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="库存流水" name="log">
      <el-table :data="logs" border size="small">
        <el-table-column prop="opTime" label="时间" width="160" />
        <el-table-column label="物料" min-width="120">
          <template #default="{ row }">{{ materialName(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column prop="changeQty" label="变动" width="90" />
        <el-table-column prop="balanceQty" label="余额" width="90" />
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ ['', '领料', '退料', '报工产出', '手工调整'][row.refType] }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="reqDialog" :title="reqType === 1 ? '领料申请' : '退料申请'" width="440px">
    <el-form :model="reqForm" label-width="85px">
      <el-form-item label="物料" required>
        <el-select v-model="reqForm.materialId" filterable>
          <el-option v-for="m in materials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="reqType === 2" label="批次" required><el-input v-model="reqForm.batchNo" /></el-form-item>
      <el-form-item label="数量" required><el-input-number v-model="reqForm.qty" :min="0.0001" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="reqForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="reqDialog = false">取消</el-button>
      <el-button type="primary" @click="submitReq">提交</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="adjustDialog" title="库存调整（盘盈/盘亏）" width="420px">
    <el-form :model="adjustForm" label-width="85px">
      <el-form-item label="物料" required>
        <el-select v-model="adjustForm.materialId" filterable>
          <el-option v-for="m in materials" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="批次"><el-input v-model="adjustForm.batchNo" placeholder="空=无批次档" /></el-form-item>
      <el-form-item label="变动数" required><el-input-number v-model="adjustForm.changeQty" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="adjustForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adjustDialog = false">取消</el-button>
      <el-button type="primary" @click="submitAdjust">调整</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { showBatchResult } from '../utils/batch'

const tab = ref('matreq')
const matReqs = ref<any[]>([])
const stocks = ref<any[]>([])
const logs = ref<any[]>([])
const materials = ref<any[]>([])

const matreqSelected = ref<any[]>([])

const materialName = (id: number) => materials.value.find((m) => m.id === id)?.name || id

const reqDialog = ref(false)
const reqType = ref(1)
const reqForm = reactive<any>({})
const adjustDialog = ref(false)
const adjustForm = reactive<any>({})

async function loadMatReqs() {
  matReqs.value = (await api.pageMatReq({ pageNum: 1, pageSize: 100 })).records as any[]
}
async function loadStocks() {
  stocks.value = (await api.pageStock({ pageNum: 1, pageSize: 100 })).records as any[]
}
async function loadLogs() {
  logs.value = (await api.pageStockLog({ pageNum: 1, pageSize: 100 })).records as any[]
}

function editReq() {
  reqType.value = 1
  Object.assign(reqForm, { orderId: undefined, materialId: undefined, batchNo: '', qty: 1, remark: '' })
  reqDialog.value = true
}
function editReturn() {
  reqType.value = 2
  Object.assign(reqForm, { orderId: undefined, materialId: undefined, batchNo: '', qty: 1, remark: '' })
  reqDialog.value = true
}
async function submitReq() {
  if (reqType.value === 1) await api.createMatReq(reqForm)
  else await api.returnMatReq(reqForm)
  reqDialog.value = false
  ElMessage.success('已提交申请')
  loadMatReqs()
}
async function issue(row: any) {
  await api.issueMatReq(row.id)
  ElMessage.success('已执行')
  loadMatReqs()
  loadStocks()
  loadLogs()
}
async function reject(row: any) {
  await ElMessageBox.confirm('确认驳回该申请？')
  await api.rejectMatReq(row.id)
  loadMatReqs()
}
/** 批量执行/驳回：待执行(status=0)的申请逐条处理，其余由后端跳过并报原因 */
async function issueBatch() {
  const ids = matreqSelected.value.map((r) => r.id)
  await ElMessageBox.confirm(`确认对选中的 ${ids.length} 条申请执行发料/收料？（非待执行状态的将跳过）`)
  showBatchResult(await api.batchIssueMatReq(ids), '批量执行')
  loadMatReqs(); loadStocks(); loadLogs()
}
async function rejectBatch() {
  const ids = matreqSelected.value.map((r) => r.id)
  await ElMessageBox.confirm(`确认驳回选中的 ${ids.length} 条申请？（非待执行状态的将跳过）`)
  showBatchResult(await api.batchRejectMatReq(ids), '批量驳回')
  loadMatReqs()
}

function editAdjust() {
  Object.assign(adjustForm, { materialId: undefined, batchNo: '', changeQty: 0, remark: '' })
  adjustDialog.value = true
}
async function submitAdjust() {
  await api.adjustStock(adjustForm)
  adjustDialog.value = false
  ElMessage.success('已调整')
  loadStocks()
  loadLogs()
}

onMounted(() => {
  loadMatReqs()
  loadStocks()
  loadLogs()
  api.listMaterial().then((r: any) => (materials.value = r))
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
</style>
