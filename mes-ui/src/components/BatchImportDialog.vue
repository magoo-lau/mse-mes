<template>
  <el-dialog v-model="visible" :title="title" width="500px">
    <div class="import-tip">
      第一步：<el-link type="primary" @click="downloadTemplate">下载导入模板</el-link>，按模板列填写（* 为必填，类型等枚举列填中文）
    </div>
    <div class="import-tip">第二步：上传填写好的 Excel 文件（.xlsx / .xls）</div>
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :limit="1"
      accept=".xlsx,.xls"
      :on-change="onFileChange"
      :on-remove="onRemove"
      style="margin-top: 8px"
    >
      <div class="upload-text">拖拽文件到此处，或<em>点击选择文件</em></div>
    </el-upload>
    <el-checkbox v-model="updateSupport" style="margin-top: 10px">更新已存在的数据（按编码覆盖）</el-checkbox>
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" style="margin-top: 12px" />
    <div v-if="result" class="import-result">
      <el-alert
        :type="result.failCount > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="`导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`"
      />
      <div v-if="result.errors.length" class="import-errors">
        <div v-for="(line, i) in result.errors" :key="i">{{ line }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="importing" :disabled="!file" @click="doImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import request, { download } from '../api/request'
import type { BatchOpResult } from '../utils/batch'

const props = defineProps<{ title: string; templateUrl: string; importUrl: string }>()
const emit = defineEmits<{ (e: 'imported'): void }>()

const visible = ref(false)
const updateSupport = ref(false)
const importing = ref(false)
const file = ref<File | null>(null)
const result = ref<BatchOpResult | null>(null)
const loadError = ref('')
const uploadRef = ref()

/** 打开导入对话框（重置上次选择与结果） */
function open() {
  visible.value = true
  updateSupport.value = false
  file.value = null
  result.value = null
  loadError.value = ''
  uploadRef.value?.clearFiles()
}
defineExpose({ open })

function onFileChange(uploadFile: { raw?: File }) {
  file.value = uploadFile.raw || null
}
function onRemove() {
  file.value = null
}
function downloadTemplate() {
  download(props.templateUrl, undefined, `${props.title}模板.xlsx`)
}
async function doImport() {
  if (!file.value) return
  importing.value = true
  result.value = null
  loadError.value = ''
  try {
    const form = new FormData()
    form.append('file', file.value)
    result.value = (await request.post(`${props.importUrl}?updateSupport=${updateSupport.value}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })) as BatchOpResult
    emit('imported')
  } catch (e: any) {
    // 业务校验失败（code≠0）等：错误信息回显到对话框内，不再只靠 toast
    loadError.value = e?.message || '导入失败，请重试'
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.import-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 6px; }
.upload-text { font-size: 12px; color: var(--el-text-color-secondary); padding: 16px 0; }
.upload-text em { color: var(--el-color-primary); font-style: normal; }
.import-result { margin-top: 12px; }
.import-errors { max-height: 160px; overflow-y: auto; margin-top: 8px; font-size: 12px; color: var(--el-color-danger); line-height: 1.8; }
</style>
