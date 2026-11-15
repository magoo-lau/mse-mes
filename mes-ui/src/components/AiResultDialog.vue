<template>
  <el-dialog v-model="visible" :title="title" width="680px" destroy-on-close>
    <div v-loading="loading" element-loading-text="AI 正在分析，请稍候…" class="ai-body">
      <template v-if="!loading">
        <!-- AI 输出为纯文本/Markdown 源码，等宽流式排版；统一标注「AI 生成，仅供参考」 -->
        <div class="ai-content">{{ content }}</div>
        <div class="ai-notice">以上内容由 AI 生成，仅供参考，请人工确认后再执行相关操作。</div>
      </template>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="content" type="primary" @click="copyContent">复制内容</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '../utils/clipboard'

const visible = ref(false)
const loading = ref(false)
const content = ref('')
const title = ref('AI 分析')

/** 打开弹窗并执行 AI 生成任务（task 抛错时由请求层提示，弹窗复位） */
async function run(dialogTitle: string, task: () => Promise<string>) {
  title.value = dialogTitle
  content.value = ''
  loading.value = true
  visible.value = true
  try {
    content.value = (await task()) || ''
  } catch (error) {
    visible.value = false
    throw error
  } finally {
    loading.value = false
  }
}

function copyContent() {
  copyText(content.value).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.error('复制失败')
  )
}

defineExpose({ run })
</script>

<style scoped>
.ai-body {
  min-height: 120px;
}
.ai-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  font-size: 13px;
  max-height: 55vh;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
}
.ai-notice {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
