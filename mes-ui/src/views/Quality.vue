<template>
  <el-tabs v-model="tab" class="page">
    <el-tab-pane label="检验方案" name="scheme">
      <div class="toolbar">
        <el-button v-permission="'mes:qc:scheme:save'" type="primary" @click="editScheme()">新增方案</el-button>
        <el-button v-permission="'mes:qc:scheme:import'" @click="importDialogRef?.open()">导入</el-button>
        <el-button v-permission="'mes:qc:scheme:export'" @click="exportScheme">导出</el-button>
        <el-button v-permission="'mes:qc:scheme:delete'" type="danger" plain :disabled="!schemeSelected.length" @click="removeSchemeBatch">批量删除</el-button>
      </div>
      <el-table :data="schemes" border size="small" @selection-change="(v: any[]) => (schemeSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ ['', 'IQC', 'IPQC', 'FQC'][row.qcType] }}</template>
        </el-table-column>
        <el-table-column prop="sampleRule" label="抽样规则" min-width="130" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:qc:scheme:save'" link type="primary" @click="editScheme(row)">编辑</el-button>
            <el-button v-permission="'mes:qc:scheme:delete'" link type="danger" @click="removeScheme(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="检验单" name="inspect">
      <div class="toolbar">
        <el-select v-model="qcQuery.status" placeholder="状态" clearable style="width: 110px" @change="loadQcOrders">
          <el-option label="待检" :value="0" /><el-option label="已完成" :value="1" />
        </el-select>
        <el-button type="primary" plain @click="loadQcOrders">查询</el-button>
      </div>
      <el-table :data="qcOrders" border size="small">
        <el-table-column prop="code" label="检验单号" width="145" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ ['', 'IQC', 'IPQC', 'FQC'][row.qcType] }}</template>
        </el-table-column>
        <el-table-column prop="sampleQty" label="送检数" width="85" />
        <el-table-column label="结果" width="85">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" size="small" :type="row.result === 1 ? 'success' : row.result === 3 ? 'warning' : 'danger'">
              {{ ['', '合格', '不合格', '让步'][row.result] }}
            </el-tag>
            <el-tag v-else size="small" type="info">待检</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inspectTime" label="检验时间" width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'mes:qc:inspect:submit'" link type="primary" @click="openInspect(row)">录入</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="不良处置" name="defect">
      <div class="toolbar">
        <el-button v-permission="'mes:qc:defect:handle'" type="primary" plain :disabled="!defectSelected.length" @click="openDefectBatch()">批量处置</el-button>
      </div>
      <el-table :data="defects" border size="small" @selection-change="(v: any[]) => (defectSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="qty" label="不良数" width="90" />
        <el-table-column prop="reason" label="原因" min-width="140" />
        <el-table-column label="处置" width="110">
          <template #default="{ row }">{{ ['', '返工', '报废', '让步接收'][row.dispose] || '待处置' }}</template>
        </el-table-column>
        <el-table-column prop="disposeRemark" label="处置说明" min-width="140" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-permission="'mes:qc:defect:handle'" link type="primary" @click="openDefect(row)">处置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="质量异常" name="exception">
      <div class="toolbar">
        <el-button v-permission="'mes:qc:exception:save'" type="primary" @click="editException()">登记异常</el-button>
        <el-button v-permission="'mes:qc:exception:close'" type="success" plain :disabled="!exceptionSelected.length" @click="closeExceptionBatch">批量关闭</el-button>
      </div>
      <el-table :data="exceptions" border size="small" @selection-change="(v: any[]) => (exceptionSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="单号" width="140" />
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">{{ ['', '一般', '重大', '危急'][row.level] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'primary', 'success'][row.status]">{{ ['已登记', '处理中', '已关闭'][row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="185" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status < 2" v-permission="'mes:qc:exception:save'" link type="primary" @click="editException(row)">处理</el-button>
            <el-button v-if="row.status < 2" v-permission="'mes:qc:exception:close'" link type="success" @click="closeException(row)">关闭</el-button>
            <el-button v-permission="'mes:ai:quality-diag'" link type="warning" @click="aiDiag(row)">AI 诊断</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <AiResultDialog ref="aiDiagDialog" />

  <BatchImportDialog ref="importDialogRef" title="检验方案" template-url="/mes/qc/scheme/import-template"
    import-url="/mes/qc/scheme/import" @imported="loadSchemes" />

  <el-dialog v-model="schemeDialog" :title="schemeForm.id ? '编辑方案' : '新增方案'" width="640px">
    <el-form :model="schemeForm" label-width="80px" inline>
      <el-form-item label="编码" required><el-input v-model="schemeForm.code" /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="schemeForm.name" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="schemeForm.qcType">
          <el-option label="IQC 来料" :value="1" /><el-option label="IPQC 过程" :value="2" /><el-option label="FQC 完工" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="抽样规则"><el-input v-model="schemeForm.sampleRule" /></el-form-item>
    </el-form>
    <el-table :data="schemeForm.items" border size="small">
      <el-table-column label="检验项目"><template #default="{ row }"><el-input v-model="row.itemName" size="small" /></template></el-table-column>
      <el-table-column label="标准"><template #default="{ row }"><el-input v-model="row.standard" size="small" /></template></el-table-column>
      <el-table-column label="方法"><template #default="{ row }"><el-input v-model="row.method" size="small" /></template></el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }"><el-button link type="danger" @click="schemeForm.items.splice($index, 1)">删</el-button></template>
      </el-table-column>
    </el-table>
    <el-button class="mt8" size="small" @click="schemeForm.items.push({ itemName: '', standard: '', method: '' })">加项目</el-button>
    <template #footer>
      <el-button @click="schemeDialog = false">取消</el-button>
      <el-button type="primary" @click="submitScheme">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="inspectDialog" title="检验结果录入" width="420px">
    <el-form :model="inspectForm" label-width="90px">
      <el-form-item label="合格数量" required><el-input-number v-model="inspectForm.okQty" :min="0" /></el-form-item>
      <el-form-item label="不良数量" required><el-input-number v-model="inspectForm.ngQty" :min="0" /></el-form-item>
      <el-form-item label="结论">
        <el-select v-model="inspectForm.result">
          <el-option label="合格" :value="1" /><el-option label="不合格" :value="2" /><el-option label="让步接收" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="检验数据"><el-input v-model="inspectForm.inspectData" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="inspectDialog = false">取消</el-button>
      <el-button type="primary" @click="submitInspect">提交</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="defectDialog" :title="defectForm.ids ? '批量处置' : '不良处置'" width="420px">
    <el-alert v-if="defectForm.ids" type="info" :closable="false" show-icon
      :title="`将对选中的 ${defectForm.ids.length} 条不良记录执行同一处置方式（已处置的自动跳过）`" style="margin-bottom: 10px" />
    <el-form :model="defectForm" label-width="90px">
      <el-form-item label="处置方式" required>
        <el-select v-model="defectForm.dispose">
          <el-option label="返工" :value="1" /><el-option label="报废" :value="2" /><el-option label="让步接收" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="处置说明"><el-input v-model="defectForm.disposeRemark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="defectDialog = false">取消</el-button>
      <el-button type="primary" @click="submitDefect">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="exceptionDialog" :title="exceptionForm.id ? '处理异常' : '登记异常'" width="480px">
    <el-form :model="exceptionForm" label-width="90px">
      <el-form-item label="标题" required><el-input v-model="exceptionForm.title" /></el-form-item>
      <el-form-item label="级别">
        <el-select v-model="exceptionForm.level"><el-option label="一般" :value="1" /><el-option label="重大" :value="2" /><el-option label="危急" :value="3" /></el-select>
      </el-form-item>
      <el-form-item label="描述"><el-input v-model="exceptionForm.description" type="textarea" /></el-form-item>
      <el-form-item label="原因分析"><el-input v-model="exceptionForm.causeAnalysis" type="textarea" /></el-form-item>
      <el-form-item label="纠正措施"><el-input v-model="exceptionForm.action" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="exceptionDialog = false">取消</el-button>
      <el-button type="primary" @click="submitException">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { download } from '../api/request'
import { showBatchResult } from '../utils/batch'
import AiResultDialog from '../components/AiResultDialog.vue'
import BatchImportDialog from '../components/BatchImportDialog.vue'

const tab = ref('scheme')
const schemes = ref<any[]>([])
const qcOrders = ref<any[]>([])
const qcQuery = reactive<any>({ status: undefined })
const defects = ref<any[]>([])
const exceptions = ref<any[]>([])

const schemeSelected = ref<any[]>([])
const defectSelected = ref<any[]>([])
const exceptionSelected = ref<any[]>([])
const importDialogRef = ref()
const exportScheme = () => download('/mes/qc/scheme/export', undefined, '检验方案.xlsx')

const schemeDialog = ref(false)
const schemeForm = reactive<any>({ items: [] })
const inspectDialog = ref(false)
const inspectForm = reactive<any>({})
const defectDialog = ref(false)
const defectForm = reactive<any>({})
const exceptionDialog = ref(false)
const exceptionForm = reactive<any>({})

async function loadSchemes() {
  schemes.value = (await api.pageQcScheme({ pageNum: 1, pageSize: 100 })).records as any[]
}
async function loadQcOrders() {
  qcOrders.value = (await api.pageQcOrder({ pageNum: 1, pageSize: 100, status: qcQuery.status })).records as any[]
}
async function loadDefects() {
  defects.value = (await api.pageDefect({ pageNum: 1, pageSize: 100 })).records as any[]
}
async function loadExceptions() {
  exceptions.value = (await api.pageQcException({ pageNum: 1, pageSize: 100 })).records as any[]
}

function editScheme(row?: any) {
  if (row) {
    api.getQcScheme(row.id).then((res: any) => Object.assign(schemeForm, res.scheme, { items: res.items }))
  } else {
    Object.assign(schemeForm, { id: undefined, code: '', name: '', qcType: 2, sampleRule: '', remark: '', items: [] })
  }
  schemeDialog.value = true
}
async function submitScheme() {
  await api.saveQcScheme(schemeForm)
  schemeDialog.value = false
  ElMessage.success('已保存')
  loadSchemes()
}
async function removeScheme(row: any) {
  await ElMessageBox.confirm(`确认删除方案「${row.name}」？`)
  await api.deleteQcScheme(row.id)
  loadSchemes()
}
async function removeSchemeBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${schemeSelected.value.length} 个检验方案？（被检验单引用的将跳过）`)
  showBatchResult(await api.deleteQcSchemeBatch(schemeSelected.value.map((r) => r.id)), '批量删除')
  loadSchemes()
}

function openInspect(row: any) {
  Object.assign(inspectForm, { id: row.id, okQty: 0, ngQty: 0, result: undefined, inspectData: '' })
  inspectDialog.value = true
}
async function submitInspect() {
  await api.submitQcOrder(inspectForm.id, inspectForm)
  inspectDialog.value = false
  ElMessage.success('已提交')
  loadQcOrders()
  loadDefects()
}

function openDefect(row: any) {
  Object.assign(defectForm, { id: row.id, ids: undefined, dispose: 1, disposeRemark: '' })
  defectDialog.value = true
}
/** 批量处置：同一处置方式应用到勾选记录（已处置的自动跳过） */
function openDefectBatch() {
  Object.assign(defectForm, {
    id: undefined,
    ids: defectSelected.value.filter((r) => r.status === 0).map((r) => r.id),
    dispose: 1,
    disposeRemark: ''
  })
  if (!defectForm.ids.length) {
    ElMessage.warning('勾选中没有待处置的记录')
    return
  }
  defectDialog.value = true
}
async function submitDefect() {
  if (defectForm.ids) {
    showBatchResult(await api.batchHandleDefect({
      dispose: defectForm.dispose, disposeRemark: defectForm.disposeRemark, ids: defectForm.ids
    }), '批量处置')
  } else {
    await api.handleDefect(defectForm.id, defectForm)
    ElMessage.success('已处置')
  }
  defectDialog.value = false
  loadDefects()
}

function editException(row?: any) {
  Object.assign(exceptionForm, { id: undefined, title: '', level: 1, description: '', causeAnalysis: '', action: '', status: 1 }, row || {})
  exceptionDialog.value = true
}
async function submitException() {
  await api.saveQcException(exceptionForm)
  exceptionDialog.value = false
  ElMessage.success('已保存')
  loadExceptions()
}
async function closeException(row: any) {
  await api.closeQcException(row.id)
  ElMessage.success('已关闭')
  loadExceptions()
}
async function closeExceptionBatch() {
  await ElMessageBox.confirm(`确认关闭选中的 ${exceptionSelected.value.length} 条质量异常？（已关闭的将跳过）`)
  showBatchResult(await api.batchCloseQcException(exceptionSelected.value.filter((r) => r.status < 2).map((r) => r.id)), '批量关闭')
  loadExceptions()
}

// ==================== AI 质量异常诊断（二期，AI 只出建议） ====================
const aiDiagDialog = ref()
const aiDiag = (row: any) => aiDiagDialog.value.run(`AI 质量异常诊断：${row.code}`, () => api.aiQualityDiag(row.id))

onMounted(() => {
  loadSchemes()
  loadQcOrders()
  loadDefects()
  loadExceptions()
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
</style>
