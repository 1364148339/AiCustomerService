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
const deviceFilter = computed({
  get: () => tasksStore.detailDeviceFilter,
  set: (value) => tasksStore.setDetailDeviceFilter(value)
})

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function formatDuration(ms) {
  const sec = Math.floor(ms / 1000)
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return `${h}h ${m}m ${s}s`
}

onMounted(async () => {
  await tasksStore.refreshDetail(route.params.id)
  const queryDeviceId = String(route.query.deviceId || '')
  tasksStore.setDetailDeviceFilter(queryDeviceId)
  tasksStore.startDetailPolling(route.params.id)
})

watch(
  () => route.params.id,
  async (value) => {
    if (!value) return
    await tasksStore.refreshDetail(value)
    tasksStore.startDetailPolling(value)
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

function locateEventAlert(ev) {
  router.push(`/alerts?taskId=${route.params.id}&alertType=TASK_FAILURE`)
  tasksStore.setDetailDeviceFilter(ev.deviceId || '')
}

onBeforeUnmount(() => {
  tasksStore.stopDetailPolling()
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
              <el-tag :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
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
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
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

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>事件时间线</span>
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
        </template>
        <el-timeline v-if="eventLines.length > 0">
          <el-timeline-item
            v-for="ev in eventLines"
            :key="`${ev.timestamp}-${ev.deviceId}-${ev.commandId}`"
            :timestamp="new Date(ev.timestamp).toLocaleString()"
            :type="statusTagType(ev.status)"
          >
            <el-card shadow="hover" class="timeline-card">
              <div class="event-title">
                <span>{{ ev.deviceId }}</span>
                <el-tag size="small">{{ ev.commandId }}</el-tag>
                <el-tag size="small" :type="statusTagType(ev.status)">{{ ev.status }}</el-tag>
              </div>
              <div class="event-text" v-if="ev.thinking">thinking：{{ ev.thinking }}</div>
              <div class="event-text">错误码：{{ ev.errorCode || '--' }}</div>
              <div class="event-text">持续时长：{{ ev.durationMs }}ms</div>
              <div class="event-text" v-if="ev.sensitiveScreenDetected">敏感页面：已检测</div>
              <el-button v-if="ev.status === 'FAIL' || ev.errorCode" link type="danger" @click="locateEventAlert(ev)">
                跳转告警定位
              </el-button>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无事件记录" />
      </el-card>

      <el-row :gutter="16" class="mt-4">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">截图证据</div>
            </template>
            <el-table :data="detail.evidences || []" border>
              <el-table-column prop="deviceId" label="设备" width="120" />
              <el-table-column label="步骤" width="90" align="center">
                <template #default="{ row }">{{ row.stepNo || '--' }}</template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ new Date(row.timestamp).toLocaleString() }}</template>
              </el-table-column>
              <el-table-column label="截图" min-width="120">
                <template #default="{ row }">
                  <el-link :href="row.screenshotUrl" target="_blank">查看截图</el-link>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!(detail.evidences || []).length" description="暂无截图证据" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">元素快照</div>
            </template>
            <el-table :data="detail.elementSnapshots || []" border>
              <el-table-column prop="deviceId" label="设备" width="120" />
              <el-table-column label="步骤" width="90" align="center">
                <template #default="{ row }">{{ row.stepNo || '--' }}</template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ new Date(row.timestamp).toLocaleString() }}</template>
              </el-table-column>
              <el-table-column label="elementJson" min-width="240">
                <template #default="{ row }">
                  <pre class="json-cell">{{ row.elementJson }}</pre>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!(detail.elementSnapshots || []).length" description="暂无元素快照" />
          </el-card>
        </el-col>
      </el-row>
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
.mt-4 {
  margin-top: 16px;
}
.json-cell {
  margin: 0;
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
