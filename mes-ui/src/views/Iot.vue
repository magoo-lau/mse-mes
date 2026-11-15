<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="实时监控" name="realtime">
        <div class="toolbar">
          <el-button type="primary" plain @click="loadRealtime">刷新</el-button>
          <el-button v-permission="'mes:iot:collect:list'" @click="openSimulate">模拟上报</el-button>
          <span class="sub">实时源：窗口内有数采的设备显示实时状态（iot），否则回退台账状态（manual）</span>
        </div>
        <el-table :data="realtime" border size="small" v-loading="loading">
          <el-table-column prop="equipmentCode" label="设备编码" width="120" />
          <el-table-column prop="equipmentName" label="设备名称" min-width="120" />
          <el-table-column label="在线" width="80">
            <template #default="{ row }">
              <el-tag :type="row.online ? 'success' : 'info'" size="small">{{ row.online ? '在线' : '离线' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="STATUS_TYPE[row.status] || 'info'">{{ EQUIP_STATUS[row.status] || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态源" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.source === 'iot' ? 'success' : 'info'">{{ row.source }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastCollectTime" label="最近上报" width="165" />
          <el-table-column prop="todayOutput" label="今日产量增量" width="110" />
          <el-table-column prop="ratedCapacity" label="额定产能/时" width="100" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="OEE 统计" name="oee">
        <div class="toolbar">
          <el-select v-model="oeeDays" style="width: 120px" @change="loadOee">
            <el-option label="近 7 天" :value="7" />
            <el-option label="近 14 天" :value="14" />
            <el-option label="近 30 天" :value="30" />
          </el-select>
          <span class="sub">OEE = 可用率 × 性能 × 质量（数采状态时长折算 + 报工合格率；设备级质量暂按全厂报工口径）</span>
        </div>
        <el-table :data="oee" border size="small" v-loading="oeeLoading">
          <el-table-column prop="equipmentCode" label="设备编码" width="120" />
          <el-table-column prop="equipmentName" label="设备名称" min-width="110" />
          <el-table-column label="可用率" width="90">
            <template #default="{ row }">{{ pct(row.availability) }}</template>
          </el-table-column>
          <el-table-column label="性能" width="90">
            <template #default="{ row }">
              {{ pct(row.performance) }}
              <el-tooltip v-if="row.performanceNote" :content="row.performanceNote">
                <el-tag size="small" type="info">估算</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="质量" width="90">
            <template #default="{ row }">{{ pct(row.quality) }}</template>
          </el-table-column>
          <el-table-column label="OEE" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.oee >= 0.65 ? 'success' : row.oee >= 0.4 ? 'warning' : 'danger'">{{ pct(row.oee) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="outputQty" label="产量增量" width="100" />
          <el-table-column prop="downMinutes" label="维修/停机(分)" width="110" />
          <el-table-column prop="samplePoints" label="数采点数" width="90" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="数采明细" name="collect">
        <div class="toolbar">
          <el-select v-model="collectQuery.equipmentId" placeholder="设备" clearable filterable style="width: 180px">
            <el-option v-for="e in equipments" :key="e.id" :label="`${e.code} ${e.name}`" :value="e.id" />
          </el-select>
          <el-button type="primary" plain @click="loadCollects">查询</el-button>
        </div>
        <el-table :data="collects" border size="small" v-loading="collectLoading">
          <el-table-column prop="equipmentCode" label="设备编码" width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="STATUS_TYPE[row.status] || 'info'">{{ EQUIP_STATUS[row.status] || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="outputQty" label="累计计数" width="110" />
          <el-table-column prop="outputDelta" label="增量" width="90" />
          <el-table-column prop="collectTime" label="上报时间" width="165" />
        </el-table>
        <Pagination :total="collectTotal" v-model:page="collectQuery.pageNum" v-model:size="collectQuery.pageSize" @change="loadCollects" />
      </el-tab-pane>

      <el-tab-pane label="设备配置" name="config">
        <el-table :data="configs" border size="small" v-loading="configLoading">
          <el-table-column prop="equipmentCode" label="设备编码" width="120" />
          <el-table-column prop="equipmentName" label="设备名称" min-width="120" />
          <el-table-column label="额定小时产量" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.draftCapacity" :min="0" size="small" controls-position="right" style="width: 120px" />
            </template>
          </el-table-column>
          <el-table-column label="启用监控" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.draftEnabled" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-permission="'mes:iot:collect:list'" link type="primary" @click="saveConfig(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 模拟上报（IoT 网关同通道；演示/验证用） -->
    <el-dialog v-model="simulateDialog" title="模拟数采上报" width="440px">
      <el-form :model="simulateForm" label-width="90px">
        <el-form-item label="设备" required>
          <el-select v-model="simulateForm.equipmentCode" filterable>
            <el-option v-for="e in equipments" :key="e.id" :label="`${e.code} ${e.name}`" :value="e.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="simulateForm.status">
            <el-option v-for="(label, key) in EQUIP_STATUS" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="累计产量">
          <el-input-number v-model="simulateForm.outputQty" :min="0" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="simulateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSimulate">上报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api/mes'
import { Pagination } from 'mse-ui-shared/pc'

const EQUIP_STATUS: Record<number, string> = { 1: '运行', 2: '待机', 3: '维修', 4: '停机' }
const STATUS_TYPE: Record<number, string> = { 1: 'success', 2: '', 3: 'warning', 4: 'danger' }

const tab = ref('realtime')
const loading = ref(false)
const realtime = ref<any[]>([])
const oee = ref<any[]>([])
const oeeDays = ref(7)
const oeeLoading = ref(false)
const collects = ref<any[]>([])
const collectTotal = ref(0)
const collectLoading = ref(false)
const collectQuery = reactive<any>({ pageNum: 1, pageSize: 10, equipmentId: undefined })
const configs = ref<any[]>([])
const configLoading = ref(false)
const equipments = ref<any[]>([])

const simulateDialog = ref(false)
const simulateForm = reactive<any>({ equipmentCode: undefined, status: 1, outputQty: 0 })

const pct = (v: number | null) => (v == null ? '-' : (v * 100).toFixed(1) + '%')

async function loadEquipments() {
  equipments.value = (await api.pageEquipment({ pageNum: 1, pageSize: 200 }))?.records || []
}

async function loadRealtime() {
  loading.value = true
  try {
    realtime.value = await api.iotRealtime() as any[]
  } finally {
    loading.value = false
  }
}

async function loadOee() {
  oeeLoading.value = true
  try {
    oee.value = await api.iotOee(oeeDays.value) as any[]
  } finally {
    oeeLoading.value = false
  }
}

async function loadCollects() {
  collectLoading.value = true
  try {
    const page = await api.pageIotCollect({ ...collectQuery }) as any
    collects.value = page.records || []
    collectTotal.value = page.total
  } finally {
    collectLoading.value = false
  }
}

async function loadConfigs() {
  configLoading.value = true
  try {
    configs.value = ((await api.iotConfigList()) as any[]).map((row) => ({
      ...row,
      draftCapacity: row.ratedCapacity ?? undefined,
      draftEnabled: row.enabled
    }))
  } finally {
    configLoading.value = false
  }
}

async function saveConfig(row: any) {
  await api.saveIotConfig({ equipmentId: row.equipmentId, ratedCapacity: row.draftCapacity || null, enabled: row.draftEnabled })
  ElMessage.success('已保存')
  loadConfigs()
}

function openSimulate() {
  simulateDialog.value = true
}

async function submitSimulate() {
  if (!simulateForm.equipmentCode) {
    ElMessage.warning('请选择设备')
    return
  }
  const result = await api.iotCollect({ ...simulateForm })
  ElMessage.success(`已上报（产量增量 ${result.outputDelta}）`)
  simulateDialog.value = false
  loadRealtime()
}

onMounted(() => {
  loadEquipments()
  loadRealtime()
  loadOee()
  loadConfigs()
})
</script>

<style scoped>
.page { padding: 12px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.pager { margin-top: 8px; justify-content: flex-end; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
