<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>能力状态</span>
          <el-tag :type="capability?.available ? 'success' : 'info'" size="small">
            {{ capability?.available ? '可用' : '不可用' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions v-if="capability" :column="3" border size="small">
        <el-descriptions-item label="能力模式（sys_config 参数 mes.ai.mode）">
          <el-tag size="small" effect="plain">{{ modeLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI 服务通道">
          <el-tag :type="capability.platformAvailable ? 'success' : 'info'" size="small">
            {{ capability.platformAvailable ? '可用' : '未接入' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="直连模型通道（spring.ai.*）">
          <el-tag :type="capability.directAvailable ? 'success' : 'info'" size="small">
            {{ capability.directAvailable ? '可用' : '未配置' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="capability?.message" :title="capability.message" type="warning" :closable="false" show-icon class="tip" />
      <el-alert
        title="开启方式：管理员在「参数设置」把参数 mes.ai.mode 设为 platform / auto / direct（本租户可自建同名参数覆盖默认行 id=53200）。"
        type="info" :closable="false" show-icon class="tip" />
    </el-card>

    <el-card shadow="never" class="mt">
      <template #header>
        <div class="card-header">
          <span>场景 → 智能体映射</span>
          <span class="sub">下拉数据源为 AI 服务已发布智能体（服务未接入时展示内置默认项）；清空=恢复内置默认，保存即生效</span>
        </div>
      </template>
      <el-alert
        v-if="capability && !platformAvailable" type="warning" :closable="false" show-icon class="mb"
        title="未检测到 AI 服务：下拉暂展示内置默认项，服务接入后此处将列出全部已发布智能体" />
      <div class="scenes" v-loading="loading">
        <div v-for="scene in SCENES" :key="scene.key" class="scene-card">
          <div class="scene-head">
            <span class="scene-name">{{ scene.name }}</span>
            <span class="scene-desc">{{ scene.desc }}</span>
          </div>
          <el-select v-model="form[scene.key]" filterable clearable placeholder="使用内置默认" class="scene-select" :loading="loading">
            <el-option-group v-for="group in optionGroups" :key="group.label" :label="group.label">
              <el-option v-for="o in group.options" :key="o.agentCode" :value="o.agentCode" :label="`${o.name}（${o.agentCode}）`">
                <div class="option-row">
                  <span>{{ o.name }}（{{ o.agentCode }}）</span>
                  <span class="option-desc">{{ o.description }}</span>
                </div>
              </el-option>
            </el-option-group>
          </el-select>
          <div class="scene-effective">
            <span class="eff-label">当前生效：</span>
            <el-tag size="small" type="info" class="eff-tag">{{ effective(scene.key) }}</el-tag>
          </div>
        </div>
      </div>
      <div class="tip sub">
        映射仅作用于智能体链路（direct 直连模式不走智能体）；AI 输出一律为草稿/建议，写操作须人工确认。
        各场景默认智能体（mes_report_writer / mes_quality_diag / mes_equip_diag / mes_data_analyst /
        mes_schedule_advisor / mes_kb_qa / mes_andon_summary / mes_spc_interpret）由 MES 模块随服务启动
        自动注册到 AI 服务-智能体管理（默认已发布），无需手工创建；如需调整提示词或绑定模型/知识库，
        请到 AI 服务「智能体管理」编辑本租户副本。
      </div>
      <div class="save-bar">
        <el-button v-permission="'mes:ai:config'" type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api/mes'

interface AgentOption {
  agentCode: string
  name: string
  description?: string
  scope?: string
}

interface SceneMapRow {
  scene: string
  configuredCode: string | null
  effectiveCode: string
  defaultCode: string
}

const SCENES = [
  { key: 'report', name: 'AI 生产日报/周报', desc: '看板报表页：一键生成生产日报/周报' },
  { key: 'quality', name: 'AI 质量异常诊断', desc: '质量管理页：质量异常归因与处置建议' },
  { key: 'equip', name: 'AI 设备故障诊断', desc: '设备管理页：故障原因与维修建议' },
  { key: 'data_qa', name: 'AI 智能问数', desc: '看板报表页：自然语言查数（依赖 mes_query 反向工具）' },
  { key: 'schedule', name: 'AI 排产建议', desc: '生产计划页：排产与调整建议' },
  { key: 'kb', name: 'AI 工艺/SOP 知识问答', desc: '报工工作台：工艺/SOP 知识检索问答' },
  { key: 'andon', name: 'AI 安灯异常摘要', desc: '安灯看板页：安灯异常事件摘要' },
  { key: 'spc', name: 'AI SPC 判读', desc: '质量 SPC 页：控制图异常判读' }
]

const capability = ref<Awaited<ReturnType<typeof api.aiCapability>> | null>(null)
const platformAvailable = ref(true)
const options = ref<AgentOption[]>([])
const defaults = reactive<Record<string, string>>({})
const form = reactive<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)

const modeLabel = computed(() => {
  const map: Record<string, string> = {
    off: '关闭（off）',
    auto: 'AI 服务优先失败回退直连（auto）',
    platform: '仅 AI 服务（platform）',
    direct: '仅直连（direct）'
  }
  return map[capability.value?.mode || ''] || capability.value?.mode
})

const optionGroups = computed(() => [
  { label: '公共智能体', options: options.value.filter((o) => o.scope !== 'tenant') },
  { label: '本租户自有', options: options.value.filter((o) => o.scope === 'tenant') }
])

function effective(scene: string) {
  const code = form[scene] || defaults[scene] || ''
  const opt = options.value.find((o) => o.agentCode === code)
  return opt ? `${opt.name}（${code}）` : `${code}（内置默认）`
}

async function load() {
  loading.value = true
  try {
    capability.value = await api.aiCapability()
    const opts = await api.aiAgentOptions()
    options.value = opts.options || []
    platformAvailable.value = opts.platformAvailable !== false
    for (const row of (await api.aiAgentMap()) as SceneMapRow[]) {
      defaults[row.scene] = row.defaultCode
      form[row.scene] = row.configuredCode || ''
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    for (const scene of SCENES) {
      await api.saveAiAgentMap(scene.key, form[scene.key] || '')
    }
    ElMessage.success('已保存，保存即生效')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  padding: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-header .sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
}
.mt {
  margin-top: 12px;
}
.mb {
  margin-bottom: 10px;
}
.tip {
  margin-top: 10px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.scenes {
  margin-top: 4px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}
.scene-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px;
  background: var(--el-bg-color);
}
.scene-head {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.scene-name {
  font-size: 13px;
  font-weight: 500;
}
.scene-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.scene-select {
  width: 100%;
}
.scene-effective {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.eff-label {
  flex: none;
  white-space: nowrap;
}
.eff-tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.option-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.option-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.save-bar {
  margin-top: 12px;
}
</style>
