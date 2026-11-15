<template>
  <div class="check">

    <van-tabs v-model:active="activeTab" sticky offset-top="46">
      <van-tab title="点检计划" name="plans" />
      <van-tab title="我的点检记录" name="records" />
    </van-tabs>

    <!-- 点检计划列表（执行入口） -->
    <template v-if="activeTab === 'plans'">
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoadPlans">
          <div v-for="plan in plans" :key="plan.id" class="plan-card" @click="openExecute(plan)">
            <div class="plan-head">
              <span class="plan-name">{{ plan.name }}</span>
              <van-tag :type="plan.type === 1 ? 'primary' : 'warning'" size="medium">{{ plan.type === 1 ? '点检' : '保养' }}</van-tag>
            </div>
            <div class="plan-meta">
              <span>{{ CYCLE_LABELS[plan.cycle] || '' }}检</span>
              <span class="dot">·</span>
              <span :class="{ overdue: isOverdue(plan) }">下次 {{ plan.nextDate || '-' }}</span>
            </div>
            <div v-if="plan.content" class="plan-content">{{ plan.content }}</div>
          </div>
        </van-list>
        <van-empty v-if="finished && !plans.length" description="暂无点检计划" />
      </van-pull-refresh>
    </template>

    <!-- 我的点检记录 -->
    <template v-else>
      <van-list v-model:loading="recordLoading" :finished="recordFinished" finished-text="没有更多了" @load="onLoadRecords">
        <van-cell-group inset class="record-group">
          <van-cell v-for="r in records" :key="r.id" :title="`${CYCLE_LABELS['1']}检 ${r.checkDate}`" :label="r.remark || ''">
            <template #value>
              <van-tag :type="r.result === 1 ? 'success' : 'danger'" size="medium">
                {{ r.result === 1 ? '正常' : '异常' }}
              </van-tag>
            </template>
          </van-cell>
        </van-cell-group>
      </van-list>
      <van-empty v-if="recordFinished && !records.length" description="暂无点检记录" />
    </template>

    <!-- 点检执行弹层 -->
    <van-popup v-model:show="executeVisible" round position="bottom" class="execute-popup">
      <div class="execute">
        <div class="execute-title">{{ executingPlan?.name }}</div>
        <div class="execute-sub">{{ executingPlan?.content || '按计划内容执行点检' }}</div>
        <van-cell-group inset>
          <van-field label="执行结果" required>
            <template #input>
              <van-radio-group v-model="executeForm.result" direction="horizontal">
                <van-radio :name="1">正常</van-radio>
                <van-radio :name="2">异常</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-if="executeForm.result === 2"
            v-model="executeForm.remark"
            rows="2"
            autosize
            type="textarea"
            label="异常描述"
            placeholder="描述异常情况（提交后自动生成维修工单）"
            required
          />
        </van-cell-group>
        <div class="execute-actions">
          <van-button size="small" @click="executeVisible = false">取消</van-button>
          <van-button
            v-permission="'mes:check:submit'"
            size="small"
            type="primary"
            :loading="submitting"
            @click="doSubmitCheck"
          >
            提交
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { onActivated, reactive, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import {
  pageCheckPlans, pageCheckRecords, submitCheck,
  type MesCheckPlan, type MesCheckRecord
} from '../api/mes'

const CYCLE_LABELS: Record<number, string> = { 1: '日', 2: '周', 3: '月' }

const activeTab = ref<'plans' | 'records'>('plans')

// 计划列表
const plans = ref<MesCheckPlan[]>([])
const planPage = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

// 记录列表
const records = ref<MesCheckRecord[]>([])
const recordPage = ref(1)
const recordLoading = ref(false)
const recordFinished = ref(false)

// 执行弹层
const executeVisible = ref(false)
const executingPlan = ref<MesCheckPlan | null>(null)
const executeForm = reactive({ result: 1 as 1 | 2, remark: '' })
const submitting = ref(false)

function isOverdue(plan: MesCheckPlan): boolean {
  if (!plan.nextDate) return false
  return plan.nextDate < new Date().toISOString().slice(0, 10)
}

async function onLoadPlans() {
  try {
    const data = await pageCheckPlans({ pageNum: planPage.value, pageSize: 15 })
    const list = (data.records || []).filter((p) => p.status === 1)
    plans.value = planPage.value === 1 ? list : [...plans.value, ...list]
    planPage.value++
    finished.value = plans.value.length >= Number(data.total) || list.length < 15
  } catch {
    finished.value = true
  }
  loading.value = false
  refreshing.value = false
}

function onRefresh() {
  plans.value = []
  planPage.value = 1
  finished.value = false
  loading.value = true
  onLoadPlans()
}

async function onLoadRecords() {
  try {
    const data = await pageCheckRecords({ pageNum: recordPage.value, pageSize: 15 })
    const list = data.records || []
    records.value = recordPage.value === 1 ? list : [...records.value, ...list]
    recordPage.value++
    recordFinished.value = records.value.length >= Number(data.total) || list.length < 15
  } catch {
    recordFinished.value = true
  }
  recordLoading.value = false
}

function openExecute(plan: MesCheckPlan) {
  executingPlan.value = plan
  executeForm.result = 1
  executeForm.remark = ''
  executeVisible.value = true
}

async function doSubmitCheck() {
  if (!executingPlan.value) return
  if (executeForm.result === 2 && !executeForm.remark.trim()) {
    showToast('异常时必须填写异常描述')
    return
  }
  submitting.value = true
  try {
    await submitCheck({
      planId: executingPlan.value.id,
      equipmentId: executingPlan.value.equipmentId,
      result: executeForm.result,
      remark: executeForm.result === 2 ? executeForm.remark.trim() : undefined
    })
    showSuccessToast(executeForm.result === 1 ? '点检完成' : '已提交，异常已生成维修工单')
    executeVisible.value = false
    // 刷新记录与计划
    records.value = []
    recordPage.value = 1
    recordFinished.value = false
    recordLoading.value = true
    onLoadRecords()
  } finally {
    submitting.value = false
  }
}

onActivated(() => {
  // 保活切回刷新（其他人在 PC 侧新增计划/审核结果）
  onRefresh()
})
</script>

<style scoped>
.check {
  min-height: 100%;
  padding-bottom: 24px;
}
.plan-card {
  margin: 10px 12px 0;
  padding: 12px;
  border-radius: 8px;
  background: var(--van-background-2);
}
.plan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.plan-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--van-text-color);
}
.plan-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.plan-meta .overdue {
  color: var(--van-danger-color);
}
.dot {
  margin: 0 4px;
}
.plan-content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--van-text-color-2);
  background: var(--van-fill-color-light);
  border-radius: 4px;
  padding: 6px 8px;
}
.record-group {
  margin-top: 10px;
}
.execute-popup {
  width: 88%;
}
.execute {
  padding: 16px 0 10px;
}
.execute-title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--van-text-color);
}
.execute-sub {
  margin: 6px 16px 12px;
  font-size: 12px;
  color: var(--van-text-color-2);
  text-align: center;
}
.execute-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px 4px;
}
</style>
