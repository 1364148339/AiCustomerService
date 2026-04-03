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
const flowOverview = computed(() => {
  const data = detail.value
  if (!data) return null
  const runs = data.devicesRuns || []
  const events = data.events || []
  const published = !!data.taskId && !!data.createdAt
  const allDeviceIds = runs.map((item) => item.deviceId).filter(Boolean)
  const distributedDeviceIds = runs.filter((item) => item.status !== 'PENDING').map((item) => item.deviceId)
  const pendingDeviceIds = runs.filter((item) => item.status === 'PENDING').map((item) => item.deviceId)
  const ackDeviceIds = Array.from(
    new Set(events
      .filter((item) => item.errorCode === 'DEVICE_TASK_ACKED' || item.progress?.source === 'DEVICE_TASK_ACKED')
      .map((item) => item.deviceId)
      .filter(Boolean))
  )
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
    receiveDetail: {
      total: allDeviceIds,
      distributed: distributedDeviceIds,
      pending: pendingDeviceIds,
      acked: ackDeviceIds
    },
    process: {
      status: processStatus,
      text: processStatus === 'risk' ? '执行异常' : processStatus === 'running' ? '执行中' : processStatus === 'ok' ? '执行有进展' : '暂无过程数据',
      detail: latestEvent
        ? `${latestEvent.eventTypeDesc || latestEvent.eventType || '未知事件'} · ${latestEvent.stageDesc || '无阶段描述'}`
        : '还未产生事件记录'
    },
    result: {
      status: resultStatus,
      text: data.status === 'SUCCESS' ? '执行成功' : data.status === 'FAIL' ? '执行失败' : data.status === 'CANCELED' ? '已取消' : '未完成',
      detail: data.status === 'SUCCESS' ? '全部设备执行完成' : data.status === 'FAIL' ? '存在失败设备或步骤' : '等待最终结果'
    }
  }
})

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function eventTypeTagType(eventType) {
  if (eventType === 'FAILED') return 'danger'
  if (eventType === 'COMPLETED') return 'success'
  if (eventType === 'STARTED' || eventType === 'STEP_STARTED') return 'warning'
  return 'info'
}

function statusText(status) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAIL') return '失败'
  if (status === 'RUNNING') return '执行中'
  if (status === 'PENDING') return '待执行'
  if (status === 'DISPATCHING') return '分发中'
  if (status === 'QUEUED') return '排队中'
  return status || '未知'
}

function formatDuration(ms) {
  const sec = Math.floor(ms / 1000)
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return `${h}h ${m}m ${s}s`
}
function overviewType(status) {
  if (status === true || status === 'ok') return 'success'
  if (status === false || status === 'fail' || status === 'risk') return 'danger'
  if (status === 'running') return 'warning'
  return 'info'
}

onMounted(async () => {
  await tasksStore.refreshDetail(route.params.id)
  const queryDeviceId = String(route.query.deviceId || '')
  tasksStore.setDetailDeviceFilter(queryDeviceId)
})

watch(
  () => route.params.id,
  async (value) => {
    if (!value) return
    await tasksStore.refreshDetail(value)
  }
)

watch(
  () => route.query.deviceId,
  (value) => {
    tasksStore.setDetailDeviceFilter(String(value || ''))
  }
)

function jumpToAlerts() {
  router.push(`/alerts?taskId=${route.params.id}`)
}

function refreshCurrentDetail() {
  tasksStore.refreshDetail(route.params.id)
}

function eventEvidenceList(ev) {
  const stepInstanceId = ev.stepInstanceId ?? null
  const stepNo = ev.stepNo ?? null
  return evidences.value
    .filter((item) =>
      item.deviceId === ev.deviceId &&
      (stepInstanceId !== null
        ? item.stepInstanceId === stepInstanceId
        : stepNo === null || item.stepNo === stepNo)
    )
    .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
}

function locateEventAlert(ev) {
  router.push(`/alerts?taskId=${route.params.id}&alertType=TASK_FAILURE`)
  tasksStore.setDetailDeviceFilter(ev.deviceId || '')
}

onBeforeUnmount(() => {
  tasksStore.setDetailDeviceFilter('')
})
</script>

<template>
  <div class="task-detail-view" v-loading="detailLoading">
    <template v-if="detail">
      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">
            <span>任务详情与进度看板 {{ detail.taskNo || detail.taskId }}</span>
            <div class="header-tags">
              <el-tag>{{ detail.scenarioName || detail.scenarioKey || '未绑定场景' }}</el-tag>
              <el-tag :type="statusTagType(detail.status)">{{ statusText(detail.status) }}</el-tag>
              <el-button @click="refreshCurrentDetail">手动刷新</el-button>
              <el-button link type="primary" @click="jumpToAlerts">查看告警</el-button>
            </div>
          </div>
        </template>
        <el-descriptions border :column="2">
          <el-descriptions-item label="任务类型">{{ detail.type }}</el-descriptions-item>
          <el-descriptions-item label="轨道">{{ detail.track }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
          <el-descriptions-item label="意图">{{ detail.intent || '--' }}</el-descriptions-item>
          <el-descriptions-item label="场景标识">{{ detail.scenarioKey || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ new Date(detail.createdAt).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="告警计数">{{ detail.metrics.alertCount }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="mb-4" v-if="flowOverview">
        <template #header>
          <div class="card-header">流程总览</div>
        </template>
        <el-timeline class="overview-flow">
          <el-timeline-item>
            <div class="overview-item">
              <div class="overview-title">
                <span>1. 任务发布</span>
                <el-tag size="small" :type="overviewType(flowOverview.publish.ok)">{{ flowOverview.publish.text }}</el-tag>
              </div>
              <div class="overview-detail">{{ flowOverview.publish.detail }}</div>
            </div>
          </el-timeline-item>
          <el-timeline-item>
            <div class="overview-item">
              <div class="overview-title">
                <span>2. 设备接收</span>
                <el-tag size="small" :type="overviewType(flowOverview.receive.ok)">{{ flowOverview.receive.text }}</el-tag>
              </div>
              <div class="overview-detail">{{ flowOverview.receive.detail }}</div>
              <div class="overview-detail">已分发设备：{{ flowOverview.receiveDetail.distributed.join('、') || '--' }}</div>
              <div class="overview-detail">未分发设备：{{ flowOverview.receiveDetail.pending.join('、') || '--' }}</div>
              <div class="overview-detail">ACK设备：{{ flowOverview.receiveDetail.acked.join('、') || '--' }}</div>
            </div>
          </el-timeline-item>
          <el-timeline-item>
            <div class="overview-item">
              <div class="overview-title">
                <span>3. 执行过程</span>
                <el-tag size="small" :type="overviewType(flowOverview.process.status)">{{ flowOverview.process.text }}</el-tag>
              </div>
              <div class="overview-detail">{{ flowOverview.process.detail }}</div>
              <div class="card-header process-filter">
                <span>按设备查看</span>
                <el-select
                  v-model="deviceFilter"
                  clearable
                  placeholder="按设备筛选"
                  style="width: 220px"
                >
                  <el-option
                    v-for="run in detail.devicesRuns"
                    :key="run.deviceId"
                    :label="run.deviceId"
                    :value="run.deviceId"
                  />
                </el-select>
              </div>
              <el-timeline v-if="orderedEventLines.length > 0" class="process-timeline">
                <el-timeline-item
                  v-for="ev in orderedEventLines"
                  :key="`${ev.timestamp}-${ev.deviceId}-${ev.commandId}`"
                  :timestamp="new Date(ev.timestamp).toLocaleString()"
                  :type="eventTypeTagType(ev.eventType)"
                >
                  <el-card shadow="hover" class="timeline-card">
                    <div class="event-title">
                      <span>{{ ev.deviceId }}</span>
                      <el-tag size="small">步骤{{ ev.stepNo || '--' }}</el-tag>
                      <el-tag size="small" :type="eventTypeTagType(ev.eventType)">{{ ev.eventTypeDesc || ev.eventType || '未知事件' }}</el-tag>
                    </div>
                    <div class="event-text">事件号：{{ ev.eventNo || '--' }}</div>
                    <div class="event-text">步骤实例ID：{{ ev.stepInstanceId || '--' }}</div>
                    <div class="event-text">{{ ev.stageDesc }}</div>
                    <div class="event-text" v-if="ev.thinking">思考过程：{{ ev.thinking }}</div>
                    <div class="event-text">错误码：{{ ev.errorCode || '--' }}</div>
                    <div class="event-text">错误信息：{{ ev.errorMessage || '--' }}</div>
                    <div class="event-text">持续时长：{{ ev.durationMs }}ms</div>
                    <div class="event-text" v-if="ev.sensitiveScreenDetected">敏感页面：已检测</div>
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
                  </el-card>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无事件记录" />
            </div>
          </el-timeline-item>
          <el-timeline-item>
            <div class="overview-item">
              <div class="overview-title">
                <span>4. 执行结果</span>
                <el-tag size="small" :type="overviewType(flowOverview.result.status)">{{ flowOverview.result.text }}</el-tag>
              </div>
              <div class="overview-detail">{{ flowOverview.result.detail }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <el-row :gutter="16" class="mb-4">
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">累计观看时长</div>
            <div class="metric-value">{{ formatDuration(detail.metrics.watchedDurationMs) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">已切换条数</div>
            <div class="metric-value">{{ detail.metrics.itemsCompleted }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">总重试次数</div>
            <div class="metric-value">{{ detail.metrics.retryCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">事件数量</div>
            <div class="metric-value">{{ detail.events.length }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="card-header">设备子任务进度</div>
        </template>
        <el-table :data="detail.devicesRuns" border>
          <el-table-column prop="deviceId" label="设备" min-width="140" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retry" label="重试次数" width="100" align="center" />
          <el-table-column label="错误码" min-width="160">
            <template #default="{ row }">{{ row.errorCode || '--' }}</template>
          </el-table-column>
          <el-table-column label="进度快照" min-width="220">
            <template #default="{ row }">
              <span>观看 {{ row.progress?.watchedDurationMs || 0 }}ms</span>
              <span class="progress-gap">|</span>
              <span>条数 {{ row.progress?.itemsCompleted || 0 }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="mb-4" v-if="detail.commands?.length">
        <template #header>
          <div class="card-header">原子命令序列</div>
        </template>
        <el-table :data="detail.commands" border>
          <el-table-column prop="orderNum" label="序号" width="80" align="center" />
          <el-table-column prop="commandId" label="commandId" width="180" />
          <el-table-column prop="action" label="action" width="180" />
          <el-table-column label="params" min-width="220">
            <template #default="{ row }">{{ JSON.stringify(row.params || {}) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

    </template>
    <el-empty v-else description="任务不存在或尚未初始化" />
  </div>
</template>

<style scoped>
.mb-4 {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}
.metric-card {
  text-align: center;
}
.metric-title {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.metric-value {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 600;
}
.progress-gap {
  margin: 0 8px;
  color: var(--el-text-color-secondary);
}
.event-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.event-text {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.evidence-links {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.evidence-link {
  font-size: 12px;
}
.overview-flow {
  padding-right: 8px;
}
.overview-item {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.process-filter {
  margin-top: 10px;
}
.process-timeline {
  margin-top: 10px;
}
.overview-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.overview-detail {
  margin-top: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
