<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="批次追溯" name="trace">
        <div class="toolbar">
          <el-input v-model="batchNo" placeholder="输入批次号（如 B20260924-0001）" style="width: 260px" @keyup.enter="doTrace" />
          <el-button type="primary" @click="doTrace">追溯</el-button>
          <el-button v-permission="'mes:trace:export'" type="success" plain @click="doExport" :disabled="!result">导出报告</el-button>
        </div>

        <template v-if="result">
          <el-descriptions :column="3" border size="small" class="mt8" :title="direction === 'forward' ? '正向追溯（去向）' : '反向追溯（来源）'">
            <el-descriptions-item label="批次号">{{ result.batchNo }}</el-descriptions-item>
            <el-descriptions-item label="物料">{{ result.materialName }}</el-descriptions-item>
            <el-descriptions-item label="数量">{{ result.qty }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ result.source === 2 ? '采购' : '生产' }}</el-descriptions-item>
            <el-descriptions-item label="工单">{{ result.orderCode || '-' }}</el-descriptions-item>
          </el-descriptions>

          <template v-if="direction === 'forward'">
            <el-divider content-position="left">被哪些工单领用</el-divider>
            <el-table :data="result.consumedBy" border size="small">
              <el-table-column prop="code" label="领料单" width="150" />
              <el-table-column prop="refLabel" label="工单" width="150" />
              <el-table-column prop="materialName" label="物料" min-width="120" />
              <el-table-column prop="qty" label="数量" width="90" />
            </el-table>
            <el-divider content-position="left">产出批次</el-divider>
            <el-table :data="result.outputs" border size="small">
              <el-table-column prop="code" label="批次号" width="160" />
              <el-table-column prop="materialName" label="物料" min-width="120" />
              <el-table-column prop="qty" label="数量" width="90" />
            </el-table>
          </template>

          <template v-else>
            <el-divider content-position="left">工序报工链</el-divider>
            <el-table :data="result.reports" border size="small">
              <el-table-column prop="reportCode" label="报工单" width="150" />
              <el-table-column prop="stepName" label="工序" min-width="120" />
              <el-table-column prop="okQty" label="合格" width="85" />
              <el-table-column prop="ngQty" label="不良" width="85" />
              <el-table-column prop="reportTime" label="时间" width="170" />
            </el-table>
            <el-divider content-position="left">耗用原料</el-divider>
            <el-table :data="result.materials" border size="small">
              <el-table-column prop="code" label="领料单" width="150" />
              <el-table-column prop="refLabel" label="物料" min-width="120" />
              <el-table-column prop="qty" label="数量" width="90" />
            </el-table>
          </template>
        </template>
      </el-tab-pane>

      <el-tab-pane label="序列号" name="sn">
        <div class="toolbar">
          <el-input v-model="snQuery.keyword" placeholder="序列号" clearable style="width: 180px" @keyup.enter="loadSns" />
          <el-button type="primary" plain @click="loadSns">查询</el-button>
          <el-button v-permission="'mes:sn:bind'" type="primary" @click="editSn()">绑定序列号</el-button>
        </div>
        <el-table :data="sns" border size="small">
          <el-table-column prop="sn" label="序列号" width="180" />
          <el-table-column label="物料" min-width="130">
            <template #default="{ row }">{{ materialName(row.materialId) }}</template>
          </el-table-column>
          <el-table-column prop="batchNo" label="批次" width="140" />
          <el-table-column label="状态" width="85">
            <template #default="{ row }">
              <el-tag size="small" :type="['', 'primary', 'success', 'danger'][row.status]">{{ ['', '在制', '完工', '报废'][row.status] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="snDialog" title="绑定序列号" width="440px">
      <el-form :model="snForm" label-width="85px">
        <el-form-item label="序列号" required><el-input v-model="snForm.sn" /></el-form-item>
        <el-form-item label="物料" required>
          <el-select v-model="snForm.materialId" filterable>
            <el-option v-for="m in materials" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次"><el-input v-model="snForm.batchNo" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="snForm.status"><el-option label="在制" :value="1" /><el-option label="完工" :value="2" /><el-option label="报废" :value="3" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="snDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSn">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api/mes'

const tab = ref('trace')
const batchNo = ref('')
const direction = ref<'forward' | 'backward'>('backward')
const result = ref<any>(null)
const sns = ref<any[]>([])
const snQuery = reactive({ pageNum: 1, pageSize: 50, keyword: '' })
const materials = ref<any[]>([])
const snDialog = ref(false)
const snForm = reactive<any>({})

const materialName = (id: number) => materials.value.find((m) => m.id === id)?.name || id

async function doTrace() {
  if (!batchNo.value) return
  direction.value = 'backward'
  result.value = await api.traceBackward(batchNo.value)
}
async function doExport() {
  const csv = await api.exportTrace(batchNo.value)
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `追溯报告_${batchNo.value}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出')
}
async function loadSns() {
  sns.value = (await api.pageSn(snQuery)).records as any[]
}
function editSn() {
  Object.assign(snForm, { sn: '', materialId: undefined, batchNo: '', status: 1 })
  snDialog.value = true
}
async function submitSn() {
  await api.bindSn(snForm)
  snDialog.value = false
  ElMessage.success('已绑定')
  loadSns()
}

onMounted(() => {
  loadSns()
  api.listMaterial().then((r: any) => (materials.value = r))
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
</style>
