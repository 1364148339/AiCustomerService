<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTasksStore } from '../stores/tasks'

const route = useRoute()
const router = useRouter()
const tasksStore = useTasksStore()

const detail = computed(() => tasksStore.detail)
const detailLoading = computed(() => tasksStore.detailLoading)
const eventLines = computed(() => tasksStore.filteredEvents || [])
const orderedEventLines = computed(() =>
  [...eventLines.value].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
)
const evidences = computed(() => detail.value?.evidences || [])
const deviceFilter = computed({
  get: () => tasksStore.detailDeviceFilter,
  set: (value) => tasksStore.setDetailDeviceFilter(value)
})
const currentTaskId = computed(() => String(route.params.id || ''))

const detailMetrics = computed(() => {
  const data = detail.value
  if (!data) {
    return { devices: 0, finished: 0, errors: 0, evidences: 0 }
  }
  return {
    devices: data.deviceRuns?.length || 0,
    finished: (data.deviceRuns || []).filter((item) => ['SUCCESS', 'FAIL', 'CANCELED'].includes(item.runStatus || item.status)).length,
    errors: (data.events || []).filter((item) => item.eventType === 'FAILED' || item.errorCode).length,
    evidences: evidences.value.length
  }
})

const flowOverview = computed(() => {
  const data = detail.value
  if (!data) return null
  const runs = data.deviceRuns || []
  const events = data.events || []
  const published = Boolean(data.taskNo || data.taskId)
  const distributedDeviceIds = [...new Set(runs.filter((item) => item.status !== 'PENDING').map((item) => item.deviceId).filter(Boolean))]
  const ackDeviceIds = [...new Set(events
    .filter((item) => item.errorCode === 'DEVICE_TASK_ACKED' || item.progress?.source === 'DEVICE_TASK_ACKED')
    .map((item) => item.deviceId)
    .filter(Boolean))]
  const distributedCount = distributedDeviceIds.length
  const ackCount = ackDeviceIds.length
  const receiveStatus = ackCount === 0 ? false : ackCount === runs.length ? true : 'running'
  const receiveText = ackCount === 0 ? '设备未接收' : ackCount === runs.length ? '设备已接收' : '部分设备已接收'
  const failedEvents = events.filter((item) => item.eventType === 'FAILED' || item.errorCode)
  const latestEvent = events.reduce((latest, item) => {
    if (!latest) return item
    return new Date(item.timestamp).getTime() > new Date(latest.timestamp).getTime() ? item : latest
  }, null)
  const executing = data.status === 'RUNNING' || data.status === 'DISPATCHING'
  const finalDone = ['SUCCESS', 'FAIL', 'CANCELED'].includes(data.status)
  const processStatus = failedEvents.length > 0 ? 'risk' : executing ? 'running' : events.length > 0 ? 'ok' : 'idle'
  const resultStatus = data.status === 'SUCCESS' ? 'ok' : data.status === 'FAIL' ? 'fail' : data.status === 'CANCELED' ? 'fail' : finalDone ? 'unknown' : 'pending'
  return {
    publish: {
      ok: published,
      text: published ? '已发布' : '未确认',
      detail: published ? `任务号 ${data.taskNo || data.taskId}` : '任务尚未创建成功'
    },
    receive: {
      ok: receiveStatus,
      text: receiveText,
      detail: `已分发 ${distributedCount}/${runs.length} 台（ACK ${ackCount} 台）`
    },
    process: {
      ok: processStatus,
      text: failedEvents.length > 0 ? '处理中存在风险' : executing ? '处理中' : events.length > 0 ? '处理轨迹已生成' : '尚无处理轨迹',
      detail: latestEvent ? `最近事件：${latestEvent.stageDesc || latestEvent.eventTypeDesc || latestEvent.eventType || '未知事件'}` : '设备尚未上报执行事件'
    },
    result: {
      ok: resultStatus,
      text: statusText(data.status),
      detail: finalDone ? `成功 ${data.metrics?.alertCount ? '伴随告警' : '无额外告警'}` : '任务仍在执行流程中'
    }
  }
})

function loadDetail(taskId) {
  if (!taskId) return
  tasksStore.refreshDetail(taskId)
}

function refreshCurrentDetail() {
  loadDetail(currentTaskId.value)
}

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL' || status === 'CANCELED') return 'danger'
  if (status === 'RUNNING' || status === 'DISPATCHING') return 'warning'
  return 'info'
}

function statusText(status) {
  switch (status) {
    case 'QUEUED':
      return '排队中'
    case 'DISPATCHING':
      return '派发中'
    case 'RUNNING':
      return '执行中'
    case 'SUCCESS':
      return '执行成功'
    case 'FAIL':
      return '执行失败'
    case 'CANCELED':
      return '已取消'
    default:
      return status || '未知状态'
  }
}

function eventTypeTagType(type) {
  if (type === 'FAILED') return 'danger'
  if (type === 'COMPLETED') return 'success'
  if (type === 'STEP_STARTED' || type === 'ACTION_EXECUTED') return 'warning'
  return 'info'
}

function flowTagType(ok) {
  if (ok === true || ok === 'ok') return 'success'
  if (ok === 'running') return 'warning'
  if (ok === 'risk' || ok === false || ok === 'fail') return 'danger'
  return 'info'
}

function runStatusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL' || status === 'CANCELED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function eventEvidenceList(ev) {
  return evidences.value.filter((item) => item.deviceId === ev.deviceId && item.stepNo === ev.stepNo)
}

function formatBooleanText(value, trueText, falseText, emptyText = '--') {
  if (value === true) return trueText
  if (value === false) return falseText
  return emptyText
}

function isFailureEvent(ev) {
  return ev.eventType === 'FAILED' || Boolean(ev.errorCode)
}

function showDecisionSummary(ev) {
  if (!isFailureEvent(ev)) return false
  return Boolean(
    ev.failureCategory ||
    ev.actionResult ||
    ev.pageType ||
    ev.pageSignature ||
    ev.recoverable !== null ||
    ev.targetResolved !== null
  )
}

function locateEventAlert(ev) {
  const taskId = detail.value?.taskId || currentTaskId.value
  router.push({
    path: '/alerts',
    query: {
      taskId,
      deviceId: ev.deviceId || '',
      code: ev.errorCode || ev.eventType || ''
    }
  })
}

function jumpToAlerts() {
  const taskId = detail.value?.taskId || currentTaskId.value
  router.push({ path: '/alerts', query: { taskId } })
}

function pickDevice(deviceId) {
  tasksStore.setDetailDeviceFilter(deviceId)
}

function clearDeviceFilter() {
  tasksStore.setDetailDeviceFilter('')
}

watch(currentTaskId, (value) => {
  loadDetail(value)
}, { immediate: true })

onMounted(() => {
  if (currentTaskId.value) {
    loadDetail(currentTaskId.value)
  }
})

onBeforeUnmount(() => {
  tasksStore.setDetailDeviceFilter('')
})
</script>

<template>
  <div class="page-shell task-detail-view" v-loading="detailLoading">
    <template v-if="detail">
      <div class="metric-grid">
        <el-card shadow="hover" class="metric-card metric-card--blue">
          <div class="metric-card__label">目标设备</div>
          <div class="metric-card__value">{{ detailMetrics.devices }}</div>
          <div class="metric-card__sub">参与本次任务的设备总数</div>
        </el-card>
        <el-card shadow="hover" class="metric-card metric-card--green">
          <div class="metric-card__label">已结束设备</div>
          <div class="metric-card__value">{{ detailMetrics.finished }}</div>
          <div class="metric-card__sub">成功、失败或取消均计入</div>
        </el-card>
        <el-card shadow="hover" class="metric-card metric-card--orange">
          <div class="metric-card__label">异常事件</div>
          <div class="metric-card__value">{{ detailMetrics.errors }}</div>
          <div class="metric-card__sub">含失败事件和错误码事件</div>
        </el-card>
        <el-card shadow="hover" class="metric-card metric-card--purple">
          <div class="metric-card__label">截图证据</div>
          <div class="metric-card__value">{{ detailMetrics.evidences }}</div>
          <div class="metric-card__sub">辅助定位失败原因</div>
        </el-card>
      </div>

      <el-card shadow="never" class="page-card">
        <template #header>
          <div class="page-header">
            <div class="page-header__main">
              <div class="page-header__title">任务详情与进度看板 {{ detail.taskNo || detail.taskId }}</div>
              <div class="page-header__desc">聚合展示任务状态、设备执行情况、时间线事件与失败决策摘要。</div>
            </div>
            <div class="page-header__actions">
              <el-tag effect="plain" round>{{ detail.scenarioName || detail.scenarioKey || '未绑定场景' }}</el-tag>
              <el-tag :type="statusTagType(detail.status)" effect="light" round>{{ statusText(detail.status) }}</el-tag>
              <el-button @click="refreshCurrentDetail">手动刷新</el-button>
              <el-button link type="primary" @click="jumpToAlerts">查看告警</el-button>
            </div>
          </div>
        </template>

        <el-descriptions border :column="2" class="detail-descriptions">
          <el-descriptions-item label="任务类型">{{ detail.type }}</el-descriptions-item>
          <el-descriptions-item label="轨道">{{ detail.track }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
          <el-descriptions-item label="意图">{{ detail.intent || '--' }}</el-descriptions-item>
          <el-descriptions-item label="场景标识">{{ detail.scenarioKey || '--' }}</el-descriptions-item>
          <el-descriptions-item label="场景名称">{{ detail.scenarioName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ new Date(detail.createdAt).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusText || statusText(detail.status) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <div class="metric-grid" v-if="flowOverview">
        <el-card v-for="(item, key) in flowOverview" :key="key" shadow="never" class="metric-card flow-card">
          <div class="flow-card__title">
            <span>{{ key === 'publish' ? '发布' : key === 'receive' ? '接收' : key === 'process' ? '处理' : '结果' }}</span>
            <el-tag size="small" :type="flowTagType(item.ok)" effect="light" round>{{ item.text }}</el-tag>
          </div>
          <div class="metric-card__sub flow-card__detail">{{ item.detail }}</div>
        </el-card>
      </div>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-card shadow="never" class="page-card device-panel">
            <template #header>
              <div class="page-header">
                <div class="page-header__main">
                  <div class="page-header__title">设备执行情况</div>
                  <div class="page-header__desc">按设备筛选时间线，快速聚焦异常节点。</div>
                </div>
                <div class="page-header__actions">
                  <el-button link @click="clearDeviceFilter" v-if="deviceFilter">清空筛选</el-button>
                </div>
              </div>
            </template>
            <div v-if="detail.deviceRuns?.length" class="device-run-list">
              <div
                v-for="run in detail.deviceRuns"
                :key="run.deviceId"
                class="device-run-card is-clickable"
                :class="{ active: deviceFilter === run.deviceId }"
                @click="pickDevice(run.deviceId)"
              >
                <div class="device-run-card__header">
                  <span class="device-run-card__id">{{ run.deviceId }}</span>
                  <el-tag size="small" :type="runStatusType(run.runStatus || run.status)" effect="light" round>
                    {{ run.statusText || statusText(run.runStatus || run.status) }}
                  </el-tag>
                </div>
                <div class="device-run-card__meta">当前步骤：{{ run.currentStepNo || '--' }}</div>
                <div class="device-run-card__meta">重试次数：{{ run.retryCount || 0 }}</div>
                <div class="device-run-card__meta" v-if="run.errorCode">错误码：{{ run.errorCode }}</div>
                <div class="device-run-card__meta" v-if="run.errorMessage">错误信息：{{ run.errorMessage }}</div>
              </div>
            </div>
            <div v-else class="empty-state empty-state--compact">
              <div class="empty-state__title">设备侧暂未回传执行记录</div>
              <div class="empty-state__desc">任务可能仍在派发阶段，或设备还未开始执行。可以稍后刷新，继续观察 ACK 和事件流转。</div>
              <div class="empty-state__actions">
                <el-button type="primary" plain @click="refreshCurrentDetail">刷新详情</el-button>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card shadow="never" class="page-card">
            <template #header>
              <div class="page-header">
                <div class="page-header__main">
                  <div class="page-header__title">执行时间线</div>
                  <div class="page-header__desc">查看设备执行轨迹、失败原因与关联截图证据。</div>
                </div>
                <div class="page-header__actions">
                  <el-tag type="info" effect="plain" round>{{ orderedEventLines.length }} 条事件</el-tag>
                </div>
              </div>
            </template>
            <div class="timeline-wrapper">
              <el-timeline v-if="orderedEventLines.length">
                <el-timeline-item
                  v-for="ev in orderedEventLines"
                  :key="`${ev.eventNo}-${ev.deviceId}-${ev.timestamp}`"
                  :timestamp="new Date(ev.timestamp).toLocaleString()"
                  :type="ev.eventType === 'FAILED' || ev.errorCode ? 'danger' : ev.eventType === 'COMPLETED' ? 'success' : 'primary'"
                >
                  <div class="soft-panel event-card">
                    <div class="event-card__header">
                      <span class="event-card__device">{{ ev.deviceId }}</span>
                      <el-tag size="small" effect="light" round>步骤{{ ev.stepNo || '--' }}</el-tag>
                      <el-tag size="small" :type="eventTypeTagType(ev.eventType)" effect="light" round>
                        {{ ev.eventTypeDesc || ev.eventType || '未知事件' }}
                      </el-tag>
                    </div>
                    <div class="event-text">事件号：{{ ev.eventNo || '--' }}</div>
                    <div class="event-text">步骤实例ID：{{ ev.stepInstanceId || '--' }}</div>
                    <div class="event-text">{{ ev.stageDesc }}</div>
                    <div class="event-text" v-if="ev.thinking">思考过程：{{ ev.thinking }}</div>
                    <div class="event-text">错误码：{{ ev.errorCode || '--' }}</div>
                    <div class="event-text">错误信息：{{ ev.errorMessage || '--' }}</div>
                    <div class="event-text">持续时长：{{ ev.durationMs }}ms</div>
                    <div class="event-text" v-if="ev.sensitiveScreenDetected">敏感页面：已检测</div>
                    <div class="soft-panel decision-summary" v-if="showDecisionSummary(ev)">
                      <div class="panel-title decision-summary__title">失败决策摘要</div>
                      <div class="event-text">失败类别：{{ ev.failureCategoryText || '未知' }}</div>
                      <div class="event-text">动作结果：{{ ev.actionResultText || '未知' }}</div>
                      <div class="event-text">页面类型：{{ ev.pageTypeText || '未知页面' }}</div>
                      <div class="event-text">可恢复：{{ formatBooleanText(ev.recoverable, '可恢复', '不可恢复', '--') }}</div>
                      <div class="event-text">目标识别：{{ formatBooleanText(ev.targetResolved, '已识别', '未识别', '--') }}</div>
                      <div class="event-text">页面签名：{{ ev.pageSignature || '--' }}</div>
                    </div>
                    <div class="evidence-links" v-if="eventEvidenceList(ev).length > 0">
                      <el-link
                        v-for="(item, idx) in eventEvidenceList(ev)"
                        :key="`${item.timestamp}-${item.deviceId}-${item.stepInstanceId || item.stepNo || 'na'}-${idx}`"
                        :href="item.screenshotUrl"
                        target="_blank"
                        class="evidence-link"
                      >
                        截图证据（{{ new Date(item.timestamp).toLocaleString() }}）
                      </el-link>
                    </div>
                    <el-button v-if="ev.eventType === 'FAILED' || ev.errorCode" link type="danger" @click="locateEventAlert(ev)">
                      跳转告警定位
                    </el-button>
                  </div>
                </el-timeline-item>
              </el-timeline>
              <div v-else class="empty-state">
                <div class="empty-state__title">还没有可展示的执行时间线</div>
                <div class="empty-state__desc">设备还未上报事件，或当前设备筛选条件下没有匹配记录。你可以清空筛选后重新刷新查看。</div>
                <div class="empty-state__actions">
                  <el-button type="primary" plain @click="refreshCurrentDetail">刷新详情</el-button>
                  <el-button v-if="deviceFilter" @click="clearDeviceFilter">清空设备筛选</el-button>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
    <div v-else-if="!detailLoading" class="empty-state">
      <div class="empty-state__title">未找到任务详情</div>
      <div class="empty-state__desc">可能是任务编号不存在，或者详情数据尚未成功返回。可以回到任务列表重新进入，或稍后手动刷新。</div>
      <div class="empty-state__actions">
        <el-button type="primary" @click="router.push('/tasks')">返回任务列表</el-button>
        <el-button @click="refreshCurrentDetail">重新加载</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-descriptions :deep(.el-descriptions__label) {
  width: 120px;
}

.flow-card {
  min-height: 120px;
}

.flow-card__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.flow-card__detail {
  line-height: 1.6;
}

.device-panel {
  height: 100%;
}

.device-run-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.device-run-card {
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  background: var(--surface-2);
}

.device-run-card:hover {
  border-color: rgba(96, 165, 250, 0.34);
  background: linear-gradient(180deg, #f8fbff 0%, #f1f5f9 100%);
}

.device-run-card.active {
  border-color: var(--brand-2);
  background: var(--surface-3);
  box-shadow: var(--shadow-soft);
}

.device-run-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.device-run-card__id {
  font-weight: 700;
  color: var(--text-1);
}

.device-run-card__meta {
  color: var(--text-3);
  line-height: 1.8;
}

.timeline-wrapper {
  max-height: 70vh;
  overflow: auto;
}

.event-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.event-card__device {
  font-weight: 700;
  color: var(--text-1);
}

.event-text {
  line-height: 1.8;
  color: var(--text-2);
}

.decision-summary {
  margin-top: 8px;
}

.decision-summary__title {
  margin-bottom: 6px;
}

.evidence-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}

.evidence-link {
  width: fit-content;
}
</style>
