<template>
  <el-tabs v-model="tab" class="page">
    <!-- 建模 -->
    <el-tab-pane label="工厂建模" name="model">
      <div class="toolbar">
        <el-select v-model="modelQuery.type" placeholder="全部类型" clearable style="width: 140px" @change="loadModels">
          <el-option label="工厂" :value="1" /><el-option label="车间" :value="2" />
          <el-option label="产线" :value="3" /><el-option label="工位" :value="4" />
        </el-select>
        <el-button v-permission="'mes:model:save'" type="primary" @click="editModel()">新增节点</el-button>
        <el-button v-permission="'mes:model:import'" @click="openImport(importCfgs.model)">导入</el-button>
        <el-button v-permission="'mes:model:export'" @click="exportModel">导出</el-button>
        <el-button v-permission="'mes:model:delete'" type="danger" plain :disabled="!modelSelected.length" @click="removeModelBatch">批量删除</el-button>
      </div>
      <el-table :data="models" border size="small" @selection-change="(v: any[]) => (modelSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }"><el-tag size="small">{{ ['', '工厂', '车间', '产线', '工位'][row.type] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:model:save'" link type="primary" @click="editModel(row)">编辑</el-button>
            <el-button v-permission="'mes:model:delete'" link type="danger" @click="removeModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <!-- 物料 -->
    <el-tab-pane label="物料" name="material">
      <div class="toolbar">
        <el-input v-model="matQuery.keyword" placeholder="编码/名称" clearable style="width: 200px" @keyup.enter="loadMaterials" />
        <el-button type="primary" plain @click="loadMaterials">查询</el-button>
        <el-button v-permission="'mes:material:save'" type="primary" @click="editMaterial()">新增物料</el-button>
        <el-button v-permission="'mes:material:import'" @click="openImport(importCfgs.material)">导入</el-button>
        <el-button v-permission="'mes:material:export'" @click="exportMaterial">导出</el-button>
        <el-button v-permission="'mes:material:delete'" type="danger" plain :disabled="!matSelected.length" @click="removeMaterialBatch">批量删除</el-button>
      </div>
      <el-table :data="materials" border size="small" @selection-change="(v: any[]) => (matSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ ['', '原材料', '半成品', '成品'][row.type] }}</template>
        </el-table-column>
        <el-table-column label="追溯" width="90">
          <template #default="{ row }">{{ row.traceMode === 2 ? '按序列号' : '按批次' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:material:save'" link type="primary" @click="editMaterial(row)">编辑</el-button>
            <el-button v-permission="'mes:material:delete'" link type="danger" @click="removeMaterial(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="matTotal" v-model:page="matQuery.pageNum" v-model:size="matQuery.pageSize" @change="loadMaterials" />
    </el-tab-pane>

    <!-- BOM -->
    <el-tab-pane label="BOM" name="bom">
      <div class="toolbar">
        <el-button v-permission="'mes:bom:save'" type="primary" @click="editBom()">新增 BOM</el-button>
        <el-button v-permission="'mes:bom:import'" @click="openImport(importCfgs.bom)">导入</el-button>
        <el-button v-permission="'mes:bom:export'" @click="exportBom">导出</el-button>
        <el-button v-permission="'mes:bom:delete'" type="danger" plain :disabled="!bomSelected.length" @click="removeBomBatch">批量删除</el-button>
      </div>
      <el-table :data="boms" border size="small" @selection-change="(v: any[]) => (bomSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column label="父项物料" min-width="140">
          <template #default="{ row }">{{ materialName(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column prop="remark" label="备注" min-width="140" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:bom:save'" link type="primary" @click="editBom(row)">编辑</el-button>
            <el-button v-permission="'mes:bom:delete'" link type="danger" @click="removeBom(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="bomTotal" v-model:page="bomQuery.pageNum" v-model:size="bomQuery.pageSize" @change="loadBoms" />
    </el-tab-pane>

    <!-- 工艺路线 -->
    <el-tab-pane label="工艺路线" name="route">
      <div class="toolbar">
        <el-button v-permission="'mes:route:save'" type="primary" @click="editRoute()">新增路线</el-button>
        <el-button v-permission="'mes:route:import'" @click="openImport(importCfgs.route)">导入</el-button>
        <el-button v-permission="'mes:route:export'" @click="exportRoute">导出</el-button>
        <el-button v-permission="'mes:route:delete'" type="danger" plain :disabled="!routeSelected.length" @click="removeRouteBatch">批量删除</el-button>
      </div>
      <el-table :data="routes" border size="small" @selection-change="(v: any[]) => (routeSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:route:save'" link type="primary" @click="editRoute(row)">编辑</el-button>
            <el-button v-permission="'mes:route:publish'" link :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleRoute(row)">{{ row.status === 1 ? '停用' : '发布' }}</el-button>
            <el-button v-permission="'mes:route:delete'" link type="danger" @click="removeRoute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="routeTotal" v-model:page="routeQuery.pageNum" v-model:size="routeQuery.pageSize" @change="loadRoutes" />
    </el-tab-pane>

    <!-- 日历班次 -->
    <el-tab-pane label="日历班次" name="calendar">
      <div class="toolbar">
        <el-date-picker v-model="calMonth" type="month" value-format="YYYY-MM" @change="loadCalendar" />
        <el-button v-permission="'mes:calendar:save'" type="primary" @click="editCalendar()">新增班次</el-button>
        <el-button v-permission="'mes:calendar:delete'" type="danger" plain :disabled="!calSelected.length" @click="removeCalendarBatch">批量删除</el-button>
      </div>
      <el-table :data="calendars" border size="small" @selection-change="(v: any[]) => (calSelected = v)">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="workDate" label="日期" width="110" />
        <el-table-column prop="shiftCode" label="班次" width="80" />
        <el-table-column prop="shiftName" label="名称" width="100" />
        <el-table-column prop="startTime" label="开始" width="90" />
        <el-table-column prop="endTime" label="结束" width="90" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.type === 1 ? '工作' : '休息' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'mes:calendar:delete'" link type="danger" @click="removeCalendar(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <!-- 建模对话框 -->
  <el-dialog v-model="modelDialog" :title="modelForm.id ? '编辑节点' : '新增节点'" width="420px">
    <el-form :model="modelForm" label-width="70px">
      <el-form-item label="类型" required>
        <el-select v-model="modelForm.type">
          <el-option label="工厂" :value="1" /><el-option label="车间" :value="2" />
          <el-option label="产线" :value="3" /><el-option label="工位" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="上级">
        <el-select v-model="modelForm.parentId" clearable>
          <el-option v-for="m in parentOptions" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="编码" required><el-input v-model="modelForm.code" /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="modelForm.name" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="modelForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="modelDialog = false">取消</el-button>
      <el-button type="primary" @click="submitModel">保存</el-button>
    </template>
  </el-dialog>

  <!-- 物料对话框 -->
  <el-dialog v-model="matDialog" :title="matForm.id ? '编辑物料' : '新增物料'" width="460px">
    <el-form :model="matForm" label-width="80px">
      <el-form-item label="编码" required><el-input v-model="matForm.code" /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="matForm.name" /></el-form-item>
      <el-form-item label="规格"><el-input v-model="matForm.spec" /></el-form-item>
      <el-form-item label="单位"><el-input v-model="matForm.unit" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="matForm.type">
          <el-option label="原材料" :value="1" /><el-option label="半成品" :value="2" /><el-option label="成品" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="追溯粒度">
        <el-select v-model="matForm.traceMode">
          <el-option label="按批次" :value="1" /><el-option label="按序列号" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="matForm.remark" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="matDialog = false">取消</el-button>
      <el-button type="primary" @click="submitMaterial">保存</el-button>
    </template>
  </el-dialog>

  <!-- BOM 对话框 -->
  <el-dialog v-model="bomDialog" :title="bomForm.id ? '编辑 BOM' : '新增 BOM'" width="680px">
    <el-form :model="bomForm" label-width="80px" inline>
      <el-form-item label="编码" required><el-input v-model="bomForm.code" /></el-form-item>
      <el-form-item label="父项" required>
        <el-select v-model="bomForm.materialId" filterable><el-option v-for="m in allMaterials" :key="m.id" :label="m.name" :value="m.id" /></el-select>
      </el-form-item>
      <el-form-item label="版本"><el-input v-model="bomForm.version" style="width: 100px" /></el-form-item>
    </el-form>
    <el-table :data="bomForm.lines" border size="small">
      <el-table-column label="子件">
        <template #default="{ row }">
          <el-select v-model="row.materialId" filterable size="small">
            <el-option v-for="m in allMaterials" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="用量" width="130">
        <template #default="{ row }"><el-input-number v-model="row.qty" :min="0" size="small" /></template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button link type="danger" @click="bomForm.lines.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button class="mt8" size="small" @click="bomForm.lines.push({ materialId: undefined, qty: 1 })">加一行</el-button>
    <template #footer>
      <el-button @click="bomDialog = false">取消</el-button>
      <el-button type="primary" @click="submitBom">保存</el-button>
    </template>
  </el-dialog>

  <!-- 工艺路线对话框 -->
  <el-dialog v-model="routeDialog" :title="routeForm.id ? '编辑路线' : '新增路线'" width="720px">
    <el-form :model="routeForm" label-width="80px" inline>
      <el-form-item label="编码" required><el-input v-model="routeForm.code" /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="routeForm.name" /></el-form-item>
      <el-form-item label="版本"><el-input v-model="routeForm.version" style="width: 100px" /></el-form-item>
    </el-form>
    <el-table :data="routeForm.steps" border size="small">
      <el-table-column label="工序名" min-width="120">
        <template #default="{ row }"><el-input v-model="row.stepName" size="small" /></template>
      </el-table-column>
      <el-table-column label="产线" width="150">
        <template #default="{ row }">
          <el-select v-model="row.lineId" clearable size="small">
            <el-option v-for="l in lines" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="标准工时(秒)" width="130">
        <template #default="{ row }"><el-input-number v-model="row.standardTime" :min="0" size="small" /></template>
      </el-table-column>
      <el-table-column label="质检点" width="80">
        <template #default="{ row }"><el-checkbox v-model="row.qcRequired" /></template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button link type="danger" @click="routeForm.steps.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button class="mt8" size="small" @click="routeForm.steps.push({ stepName: '', lineId: undefined, standardTime: 0, qcRequired: false })">加工序</el-button>
    <template #footer>
      <el-button @click="routeDialog = false">取消</el-button>
      <el-button type="primary" @click="submitRoute">保存</el-button>
    </template>
  </el-dialog>

  <!-- 日历对话框 -->
  <el-dialog v-model="calDialog" title="新增班次" width="420px">
    <el-form :model="calForm" label-width="70px">
      <el-form-item label="日期" required><el-date-picker v-model="calForm.workDate" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="班次编码" required><el-input v-model="calForm.shiftCode" placeholder="如 D/N" /></el-form-item>
      <el-form-item label="名称" required><el-input v-model="calForm.shiftName" placeholder="如 白班" /></el-form-item>
      <el-form-item label="时间"><el-time-picker v-model="calForm.startTime" value-format="HH:mm:ss" /> - <el-time-picker v-model="calForm.endTime" value-format="HH:mm:ss" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="calForm.type"><el-option label="工作" :value="1" /><el-option label="休息" :value="2" /></el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="calDialog = false">取消</el-button>
      <el-button type="primary" @click="submitCalendar">保存</el-button>
    </template>
  </el-dialog>

  <!-- Excel 导入对话框（按 tab 切换配置） -->
  <BatchImportDialog ref="importDialogRef" :title="importCfg.title" :template-url="importCfg.templateUrl"
    :import-url="importCfg.importUrl" @imported="reloadTab" />
</template>

<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/mes'
import { download } from '../api/request'
import { showBatchResult } from '../utils/batch'
import BatchImportDialog from '../components/BatchImportDialog.vue'
import { Pagination } from 'mse-ui-shared/pc'

const tab = ref('model')
const models = ref<any[]>([])
const materials = ref<any[]>([])
const boms = ref<any[]>([])
const routes = ref<any[]>([])
const calendars = ref<any[]>([])
const calMonth = ref(new Date().toISOString().slice(0, 7))

const modelQuery = reactive({ type: undefined as number | undefined })
const matQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const matTotal = ref(0)
const bomQuery = reactive({ pageNum: 1, pageSize: 10 })
const bomTotal = ref(0)
const routeQuery = reactive({ pageNum: 1, pageSize: 10 })
const routeTotal = ref(0)

// 勾选集（各 tab 批量操作）
const modelSelected = ref<any[]>([])
const matSelected = ref<any[]>([])
const bomSelected = ref<any[]>([])
const routeSelected = ref<any[]>([])
const calSelected = ref<any[]>([])

// Excel 导入配置（一个对话框按 tab 切换）
const importCfgs = {
  model: { title: '制造建模', templateUrl: '/mes/model/import-template', importUrl: '/mes/model/import' },
  material: { title: '物料', templateUrl: '/mes/material/import-template', importUrl: '/mes/material/import' },
  bom: { title: 'BOM', templateUrl: '/mes/bom/import-template', importUrl: '/mes/bom/import' },
  route: { title: '工艺路线', templateUrl: '/mes/route/import-template', importUrl: '/mes/route/import' }
}
const importCfg = reactive({ ...importCfgs.material })
const importDialogRef = ref()
function openImport(cfg: { title: string; templateUrl: string; importUrl: string }) {
  Object.assign(importCfg, cfg)
  importDialogRef.value?.open()
}
function reloadTab() {
  if (tab.value === 'model') loadModels()
  else if (tab.value === 'material') loadMaterials()
  else if (tab.value === 'bom') loadBoms()
  else if (tab.value === 'route') loadRoutes()
  else if (tab.value === 'calendar') loadCalendar()
}

const lines = computed(() => models.value.filter((m) => m.type === 3))
const parentOptions = computed(() => models.value.filter((m) => (m.type ?? 0) < 4))

// 全量物料（BOM/路线对话框下拉与编码回显用，独立于物料 tab 的分页列表）
const allMaterials = ref<any[]>([])
async function loadAllMaterials() {
  allMaterials.value = await api.listMaterial() as any[]
}

const materialName = (id: number) => allMaterials.value.find((m) => m.id === id)?.name || id

const modelDialog = ref(false)
const modelForm = reactive<any>({})
const matDialog = ref(false)
const matForm = reactive<any>({})
const bomDialog = ref(false)
const bomForm = reactive<any>({ lines: [] })
const routeDialog = ref(false)
const routeForm = reactive<any>({ steps: [] })
const calDialog = ref(false)
const calForm = reactive<any>({})

async function loadModels() {
  models.value = await api.listModel(modelQuery.type ? { type: modelQuery.type } : undefined) as any[]
}
async function loadMaterials() {
  const res: any = await api.pageMaterial(matQuery)
  materials.value = res.records
  matTotal.value = Number(res.total)
}
async function loadBoms() {
  const res: any = await api.pageBom(bomQuery)
  boms.value = res.records
  bomTotal.value = Number(res.total)
}
async function loadRoutes() {
  const res: any = await api.pageRoute(routeQuery)
  routes.value = res.records
  routeTotal.value = Number(res.total)
}
async function loadCalendar() {
  calendars.value = await api.listCalendar(calMonth.value) as any[]
}

function editModel(row?: any) {
  Object.assign(modelForm, { id: undefined, type: 1, parentId: undefined, code: '', name: '', remark: '' }, row || {})
  modelDialog.value = true
}
async function submitModel() {
  if (modelForm.id) await api.updateModel(modelForm.id, modelForm)
  else await api.saveModel(modelForm)
  modelDialog.value = false
  ElMessage.success('已保存')
  loadModels()
}
async function removeModel(row: any) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？`)
  await api.deleteModel(row.id)
  loadModels()
}

function editMaterial(row?: any) {
  Object.assign(matForm, { id: undefined, code: '', name: '', spec: '', unit: '', type: 1, traceMode: 1, remark: '' }, row || {})
  matDialog.value = true
}
async function submitMaterial() {
  if (matForm.id) await api.updateMaterial(matForm.id, matForm)
  else await api.saveMaterial(matForm)
  matDialog.value = false
  ElMessage.success('已保存')
  loadMaterials()
}
async function removeMaterial(row: any) {
  await ElMessageBox.confirm(`确认删除物料「${row.name}」？`)
  await api.deleteMaterial(row.id)
  loadMaterials()
}

// ==================== 批量删除（部分成功：被引用行跳过并报原因） ====================
async function removeModelBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${modelSelected.value.length} 个建模节点？（有下级或被引用的节点将跳过）`)
  showBatchResult(await api.deleteModelBatch(modelSelected.value.map((r) => r.id)), '批量删除')
  loadModels()
}
async function removeMaterialBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${matSelected.value.length} 条物料？（被 BOM/工艺路线/检验方案/工单引用的将跳过）`)
  showBatchResult(await api.deleteMaterialBatch(matSelected.value.map((r) => r.id)), '批量删除')
  loadMaterials()
}
async function removeBomBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${bomSelected.value.length} 个 BOM？（被工单引用的将跳过）`)
  showBatchResult(await api.deleteBomBatch(bomSelected.value.map((r) => r.id)), '批量删除')
  loadBoms()
}
async function removeRouteBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${routeSelected.value.length} 条工艺路线？（已发布或被工单引用的将跳过）`)
  showBatchResult(await api.deleteRouteBatch(routeSelected.value.map((r) => r.id)), '批量删除')
  loadRoutes()
}
async function removeCalendarBatch() {
  await ElMessageBox.confirm(`确认删除选中的 ${calSelected.value.length} 条班次？`)
  showBatchResult(await api.deleteCalendarBatch(calSelected.value.map((r) => r.id)), '批量删除')
  loadCalendar()
}

// ==================== Excel 导出 ====================
const exportModel = () => download('/mes/model/export', undefined, '制造建模.xlsx')
const exportMaterial = () => download('/mes/material/export', { keyword: matQuery.keyword || undefined }, '物料数据.xlsx')
const exportBom = () => download('/mes/bom/export', undefined, 'BOM数据.xlsx')
const exportRoute = () => download('/mes/route/export', undefined, '工艺路线.xlsx')

function editBom(row?: any) {
  bomForm.lines = []
  if (row) {
    api.getBom(row.id).then((res: any) => {
      Object.assign(bomForm, res.bom, { lines: res.lines.map((l: any) => ({ materialId: l.materialId, qty: Number(l.qty) })) })
    })
  } else {
    Object.assign(bomForm, { id: undefined, code: '', materialId: undefined, version: 'V1.0', remark: '' })
  }
  bomDialog.value = true
}
async function submitBom() {
  await api.saveBom(bomForm)
  bomDialog.value = false
  ElMessage.success('已保存')
  loadBoms()
}
async function removeBom(row: any) {
  await ElMessageBox.confirm(`确认删除 BOM「${row.code}」？`)
  await api.deleteBom(row.id)
  loadBoms()
}

function editRoute(row?: any) {
  routeForm.steps = []
  if (row) {
    api.getRoute(row.id).then((res: any) => {
      Object.assign(routeForm, res.route, {
        steps: res.steps.map((s: any) => ({ stepName: s.stepName, lineId: s.lineId, standardTime: s.standardTime, qcRequired: s.qcRequired === 1 }))
      })
    })
  } else {
    Object.assign(routeForm, { id: undefined, code: '', name: '', version: 'V1.0', remark: '' })
  }
  routeDialog.value = true
}
async function submitRoute() {
  await api.saveRoute(routeForm)
  routeDialog.value = false
  ElMessage.success('已保存')
  loadRoutes()
}
async function toggleRoute(row: any) {
  await api.changeRouteStatus(row.id, row.status === 1 ? 0 : 1)
  loadRoutes()
}
async function removeRoute(row: any) {
  await ElMessageBox.confirm(`确认删除路线「${row.name}」？`)
  await api.deleteRoute(row.id)
  loadRoutes()
}

function editCalendar() {
  Object.assign(calForm, { workDate: '', shiftCode: '', shiftName: '', startTime: '', endTime: '', type: 1, lineId: undefined })
  calDialog.value = true
}
async function submitCalendar() {
  await api.saveCalendar([calForm])
  calDialog.value = false
  ElMessage.success('已保存')
  loadCalendar()
}
async function removeCalendar(row: any) {
  await ElMessageBox.confirm(`确认删除班次「${row.shiftName}（${row.workDate}）」？`)
  await api.deleteCalendar(row.id)
  loadCalendar()
}

onMounted(() => {
  loadModels()
  loadMaterials()
  loadBoms()
  loadRoutes()
  loadCalendar()
  loadAllMaterials()
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.pager { margin-top: 8px; justify-content: flex-end; }
.mt8 { margin-top: 8px; }
</style>
