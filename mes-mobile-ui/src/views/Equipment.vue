<template>
  <div class="equipment">

    <!-- 状态统计 chips -->
    <div class="status-chips">
      <div v-for="(label, key) in EQUIP_STATUS" :key="key" class="status-chip" :class="`chip-${key}`">
        <div class="chip-count">{{ counts[key] ?? 0 }}</div>
        <div class="chip-label">{{ label }}</div>
      </div>
    </div>

    <van-search v-model="keyword" placeholder="搜索设备名称/编号" />

    <van-cell-group inset>
      <van-cell v-for="e in filteredList" :key="e.id" :title="e.name" :label="`${e.code}${e.model ? ' · ' + e.model : ''}`">
        <template #value>
          <van-tag :type="statusTagType(e.status)" size="medium">{{ EQUIP_STATUS[e.status] || '未知' }}</van-tag>
        </template>
      </van-cell>
    </van-cell-group>
    <van-empty v-if="loaded && !filteredList.length" description="暂无设备" />
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, ref } from 'vue'
import { equipmentStatus, type MesEquipment } from '../api/mes'

const EQUIP_STATUS: Record<number, string> = { 1: '运行', 2: '待机', 3: '维修', 4: '停机' }

const list = ref<MesEquipment[]>([])
const counts = ref<Record<string, number>>({})
const keyword = ref('')
const loaded = ref(false)

const filteredList = computed(() => {
  const kw = keyword.value.trim().toUpperCase()
  if (!kw) return list.value
  return list.value.filter((e) => e.name.includes(keyword.value) || e.code.toUpperCase().includes(kw))
})

function statusTagType(status: number): string {
  return ['success', 'primary', 'warning', 'default'][status - 1] || 'default'
}

async function load() {
  try {
    const data = await equipmentStatus()
    list.value = data.list || []
    counts.value = data.counts || {}
  } catch {
    list.value = []
    counts.value = {}
  }
  loaded.value = true
}

onActivated(load)
</script>

<style scoped>
.equipment {
  min-height: 100%;
  padding-bottom: 24px;
}
.status-chips {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 12px;
}
.status-chip {
  border-radius: 8px;
  background: var(--van-background-2);
  padding: 10px 0;
  text-align: center;
}
.chip-count {
  font-size: 20px;
  font-weight: 600;
  color: var(--van-text-color);
}
.chip-1 .chip-count { color: var(--van-success-color); }
.chip-2 .chip-count { color: var(--van-primary-color); }
.chip-3 .chip-count { color: var(--van-warning-color); }
.chip-4 .chip-count { color: var(--van-text-color-2); }
.chip-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
</style>
