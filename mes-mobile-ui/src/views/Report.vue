<template>
  <div class="report">

    <van-cell-group inset class="r-group" title="选择工单">
      <div class="order-row">
        <van-field
          :model-value="selectedOrder?.code || ''"
          is-link
          readonly
          label="工单"
          placeholder="点击选择"
          required
          class="order-field"
          @click="orderSheetVisible = true"
        />
        <ScanCode @decoded="onScanDecoded">
          <template #default="{ scan, scanning }">
            <button type="button" class="scan-btn" :disabled="scanning" @click="scan">
              <van-icon name="scan" />
              <span>{{ scanning ? '识别中' : '扫一扫' }}</span>
            </button>
          </template>
        </ScanCode>
      </div>
      <div v-if="selectedOrder" class="order-brief">
        <span>计划 {{ selectedOrder.planQty }}</span>
        <span class="dot">·</span>
        <span>已完成 {{ selectedOrder.finishedQty ?? 0 }}</span>
        <span class="dot">·</span>
        <span>{{ ORDER_STATUS[selectedOrder.status] || '' }}</span>
      </div>
    </van-cell-group>

    <van-cell-group inset class="r-group" title="报工信息">
      <van-field v-model="form.okQty" type="number" label="合格数量" placeholder="0" required />
      <van-field v-model="form.ngQty" type="number" label="不良数量" placeholder="0" />
      <van-field
        v-if="Number(form.ngQty) > 0"
        v-model="form.defectReason"
        label="不良原因"
        placeholder="请输入不良原因"
        required
      />
    </van-cell-group>

    <div class="r-submit">
      <van-button v-permission="'mes:report:do'" type="primary" block round :loading="submitting" @click="doSubmit">
        提交报工
      </van-button>
    </div>

    <van-cell-group inset class="r-group" title="我的报工记录">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <van-cell v-for="r in records" :key="r.id" :title="r.code" :label="`合格 ${r.okQty} · 不良 ${r.ngQty}`">
          <template #value>
            <van-tag :type="REPORT_STATUS_TYPE[r.status]" size="medium">{{ REPORT_STATUS[r.status] }}</van-tag>
          </template>
        </van-cell>
      </van-list>
      <van-empty v-if="finished && !records.length" description="暂无报工记录" image-size="80" />
    </van-cell-group>

    <!-- 工单选择弹层 -->
    <van-popup v-model:show="orderSheetVisible" round position="bottom" :style="{ height: '65%' }">
      <div class="order-sheet">
        <div class="order-sheet-head">
          <span>选择工单</span>
          <van-icon name="cross" @click="orderSheetVisible = false" />
        </div>
        <van-search v-model="orderKeyword" placeholder="搜索工单号" />
        <div class="order-sheet-body">
          <van-cell
            v-for="o in filteredOrders"
            :key="o.id"
            :title="o.code"
            :label="`计划 ${o.planQty} · 已完成 ${o.finishedQty ?? 0}`"
            clickable
            @click="pickOrder(o)"
          >
            <template #value>
              <van-tag :type="o.status === 3 ? 'primary' : 'default'" size="medium">{{ ORDER_STATUS[o.status] }}</van-tag>
            </template>
          </van-cell>
          <van-empty v-if="ordersLoaded && !filteredOrders.length" description="暂无在制工单" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, reactive, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import {
  listOrders, submitReport, pageReport,
  type MesOrder, type MesReport
} from '../api/mes'
import ScanCode from '../components/ScanCode.vue'

const ORDER_STATUS: Record<number, string> = {
  0: '草稿', 1: '已提交', 2: '已下达', 3: '生产中', 4: '完工', 5: '已关闭', 6: '已取消'
}
const REPORT_STATUS: Record<number, string> = { 0: '待审核', 1: '已审核', 2: '已驳回' }
const REPORT_STATUS_TYPE: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger' }

const selectedOrder = ref<MesOrder | null>(null)
const orderSheetVisible = ref(false)
const orders = ref<MesOrder[]>([])
const ordersLoaded = ref(false)
const orderKeyword = ref('')
const form = reactive({ okQty: '', ngQty: '', defectReason: '' })
const submitting = ref(false)

// 我的报工记录
const records = ref<MesReport[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const finished = ref(false)

const filteredOrders = computed(() => {
  const kw = orderKeyword.value.trim()
  if (!kw) return orders.value
  return orders.value.filter((o) => o.code.includes(kw.toUpperCase()))
})

async function loadOrders() {
  try {
    orders.value = await listOrders()
  } catch {
    orders.value = []
  }
  ordersLoaded.value = true
}

function pickOrder(order: MesOrder) {
  selectedOrder.value = order
  orderSheetVisible.value = false
}

/** 扫码/手输结果按工单号匹配在制工单 */
function onScanDecoded(text: string) {
  const code = text.toUpperCase()
  const hit = orders.value.find((o) => o.code.toUpperCase() === code || o.code.toUpperCase().includes(code))
  if (hit) {
    pickOrder(hit)
  } else {
    showToast(`未找到在制工单 ${code}`)
  }
}

async function doSubmit() {
  if (!selectedOrder.value) {
    showToast('请先选择工单')
    return
  }
  const okQty = Number(form.okQty)
  const ngQty = Number(form.ngQty || 0)
  if (!Number.isFinite(okQty) || okQty <= 0) {
    showToast('请填写合格数量')
    return
  }
  if (ngQty > 0 && !form.defectReason.trim()) {
    showToast('有不良数量时必须填写不良原因')
    return
  }
  submitting.value = true
  try {
    await submitReport({
      orderId: selectedOrder.value.id,
      okQty,
      ngQty,
      defectReason: ngQty > 0 ? form.defectReason.trim() : undefined
    })
    showSuccessToast('报工成功，待审核')
    form.okQty = ''
    form.ngQty = ''
    form.defectReason = ''
    // 刷新工单完成数与报工记录
    await loadOrders()
    records.value = []
    page.value = 1
    total.value = 0
    finished.value = false
    loading.value = true
    onLoad()
  } finally {
    submitting.value = false
  }
}

async function onLoad() {
  try {
    const data = await pageReport({ pageNum: page.value, pageSize: 15 })
    const list = data.records || []
    records.value = page.value === 1 ? list : [...records.value, ...list]
    total.value = Number(data.total) || 0
    page.value++
    finished.value = records.value.length >= total.value || list.length < 15
  } catch {
    finished.value = true
  }
  loading.value = false
}

onActivated(() => {
  loadOrders()
})
</script>

<style scoped>
.report {
  min-height: 100%;
  padding-bottom: 24px;
}
.r-group {
  margin-top: 12px;
}
/* 工单 picker 与扫码按钮左右分离，点击互不影响 */
.order-row {
  display: flex;
  align-items: stretch;
}
.order-field {
  flex: 1;
  min-width: 0;
}
.scan-btn {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 62px;
  padding: 0;
  border: none;
  border-left: 1px solid var(--van-border-color);
  background: transparent;
  color: var(--van-primary-color);
  font-size: 11px;
}
.scan-btn .van-icon {
  font-size: 20px;
}
.order-brief {
  padding: 0 16px 10px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.dot {
  margin: 0 4px;
}
.r-submit {
  padding: 16px;
}
.order-sheet {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.order-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--van-text-color);
}
.order-sheet-body {
  flex: 1;
  overflow: auto;
}
</style>
