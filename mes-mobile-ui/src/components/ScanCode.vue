<template>
  <!-- 扫码按钮：拍照后本地解码（zxing），HTTP 内网环境可用（无需 secure context 摄像头流）；
       HTTPS 部署时可升级 BarcodeDetector 实时扫码，本组件接口不变 -->
  <slot :scan="scan" :scanning="scanning" />
  <input
    ref="fileInputRef"
    type="file"
    accept="image/*"
    capture="environment"
    style="display: none"
    @change="onFileChange"
  />
  <van-dialog
    v-model:show="manualVisible"
    title="扫码失败，手工输入"
    show-cancel-button
    :before-close="onManualClose"
  >
    <van-field v-model="manualCode" label="编码" placeholder="输入工单号/编码" class="manual-field" />
  </van-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  (e: 'decoded', text: string): void
}>()

const fileInputRef = ref<HTMLInputElement>()
const scanning = ref(false)
const manualVisible = ref(false)
const manualCode = ref('')

function scan() {
  fileInputRef.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  scanning.value = true
  try {
    const text = await decodeImage(file)
    if (text) {
      emit('decoded', text.trim())
    } else {
      manualVisible.value = true
    }
  } catch {
    manualVisible.value = true
  } finally {
    scanning.value = false
  }
}

/** 拍照图片解码：动态引入 @zxing/library（避免阻塞首屏），失败返回 null */
async function decodeImage(file: File): Promise<string | null> {
  const { BrowserMultiFormatReader } = await import('@zxing/library')
  const reader = new BrowserMultiFormatReader()
  const url = URL.createObjectURL(file)
  try {
    const result = await reader.decodeFromImageUrl(url)
    return result.getText()
  } catch {
    return null
  } finally {
    URL.revokeObjectURL(url)
    reader.reset()
  }
}

function onManualClose(action: string) {
  if (action !== 'confirm') return true
  const text = manualCode.value.trim()
  if (!text) return false
  emit('decoded', text)
  manualCode.value = ''
  return true
}
</script>

<style scoped>
.manual-field {
  margin: 8px 0;
}
</style>
