<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLogsStore } from '../stores/logs'

const route = useRoute()
const router = useRouter()
const logsStore = useLogsStore()
const autoPolling = ref(true)
const filters = computed(() => logsStore.filters)
const rows = computed(() => logsStore.rows)
const tasks = computed(() => logsStore.tasks)
const loading = computed(() => logsStore.loading)
const metrics = computed(() => ({
  total: rows.value.length,
  error: rows.value.filter((item) => item.level === 'ERROR').length,
  screenshots: rows.value.filter((item) => item.screenshotUrl).length,
  tasks: new Set(rows.value.map((item) => item.taskId).filter(Boolean)).size
}))

function levelType(level) {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}

function onAutoPollingChange(value) {
  if (value) {
    logsStore.startPolling()
    return
  }
  logsStore.stopPolling()
}

function jumpToTask(row) {
  router.push(`/tasks/${row.taskId}?deviceId=${row.deviceId}`)
}

function exportCurrentRows() {
  if (!rows.value.length) return
  const header = [
    'time',
    'taskId',
    'deviceId',
    'commandId',
    'status',
    'level',
    'code',
    'message',
    'thinkingText',
    'traceJson',
    'elementJson',
    'screenshotUrl'
  ]
  const lines = rows.value.map((item) =>
    [
      new Date(item.timestamp).toISOString(),
      item.taskId,
      item.deviceId,
      item.commandId,
      item.status,
      item.level,
      item.code || '',
      (item.message || '').replaceAll('"', '""'),
      (item.thinkingText || '').replaceAll('"', '""'),
      (item.traceJson || '').replaceAll('"', '""'),
      (item.elementJson || '').replaceAll('"', '""'),
      item.screenshotUrl || ''
    ]
      .map((col) => `"${String(col)}"`)
      .join(',')
  )
  const csv = [header.join(','), ...lines].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `logs-${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(async () => {
  filters.value.taskId = String(route.query.taskId || '')
  filters.value.deviceId = String(route.query.deviceId || '')
  await logsStore.init()
  logsStore.startPolling()
})

onBeforeUnmount(() => {
  logsStore.stopPolling()
})
</script>

<template>
  <div class="page-shell logs-view">
    <div class="metric-grid">
      <el-card shadow="hover" class="metric-card metric-card--blue">
        <div class="metric-card__label">日志总数</div>
        <div class="metric-card__value">{{ metrics.total }}</div>
        <div class="metric-card__sub">当前条件下返回的全部日志</div>
      </el-card>
      <el-card shadow="hover" class="metric-card metric-card--orange">
        <div class="metric-card__label">错误级别</div>
        <div class="metric-card__value">{{ metrics.error }}</div>
        <div class="metric-card__sub">优先关注 ERROR 日志</div>
      </el-card>
      <el-card shadow="hover" class="metric-card metric-card--green">
        <div class="metric-card__label">截图日志</div>
        <div class="metric-card__value">{{ metrics.screenshots }}</div>
        <div class="metric-card__sub">含截图链接的日志条目</div>
      </el-card>
      <el-card shadow="hover" class="metric-card metric-card--purple">
        <div class="metric-card__label">关联任务</div>
        <div class="metric-card__value">{{ metrics.tasks }}</div>
        <div class="metric-card__sub">涉及的任务数量</div>
      </el-card>
    </div>

    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div class="page-header__main">
            <div class="page-header__title">日志与截图检索</div>
            <div class="page-header__desc">按任务、设备、状态和时间范围聚合检索运行日志，并支持导出当前结果。</div>
          </div>
          <div class="page-header__actions">
            <el-switch
              v-model="autoPolling"
              inline-prompt
              active-text="轮询开"
              inactive-text="轮询关"
              @change="onAutoPollingChange"
            />
            <el-button type="primary" plain @click="logsStore.refreshLogs">手动刷新</el-button>
            <el-button @click="exportCurrentRows" :disabled="!rows.length">导出当前结果</el-button>
          </div>
        </div>
      </template>

      <div class="filter-panel">
        <div class="filter-panel__title">筛选条件</div>
        <el-form :inline="true" :model="filters" class="filter-form">
          <el-form-item label="任务">
            <el-select v-model="filters.taskId" placeholder="全部任务" clearable style="width: 220px">
              <el-option v-for="item in tasks" :key="item.taskId" :label="item.taskNo || item.taskId" :value="item.taskId" />
            </el-select>
          </el-form-item>
          <el-form-item label="设备">
            <el-input v-model="filters.deviceId" placeholder="如 dev-01" clearable style="width: 220px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.eventStatus" placeholder="全部状态" clearable style="width: 150px">
              <el-option label="RUNNING" value="RUNNING" />
              <el-option label="SUCCESS" value="SUCCESS" />
              <el-option label="FAIL" value="FAIL" />
            </el-select>
          </el-form-item>
          <el-form-item label="错误码">
            <el-input v-model="filters.errorCode" placeholder="模糊匹配" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="filters.timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="logsStore.refreshLogs">查询</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-if="rows.length" :data="rows" v-loading="loading" border class="data-table">
        <el-table-column type="expand" width="42">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="soft-panel">
                <div class="panel-title">thinkingText</div>
                <pre class="code-block">{{ row.thinkingText || '--' }}</pre>
              </div>
              <div class="soft-panel">
                <div class="panel-title">traceJson</div>
                <pre class="code-block">{{ row.traceJson || '--' }}</pre>
              </div>
              <div class="soft-panel">
                <div class="panel-title">elementJson</div>
                <pre class="code-block">{{ row.elementJson || '--' }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ new Date(row.timestamp).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务" width="120" />
        <el-table-column prop="deviceId" label="设备" width="120" />
        <el-table-column prop="commandId" label="命令" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="level" label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" effect="light" round>{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="错误码" width="170">
          <template #default="{ row }">{{ row.code || '--' }}</template>
        </el-table-column>
        <el-table-column label="截图" width="100" align="center">
          <template #default="{ row }">
            <el-link v-if="row.screenshotUrl" :href="row.screenshotUrl" target="_blank">查看</el-link>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="日志内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="thinkingText" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.thinkingText || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="jumpToTask(row)">定位</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-state">
        <div class="empty-state__title">当前筛选条件下暂无日志</div>
        <div class="empty-state__desc">可以放宽任务、设备或时间范围后重新查询，也可以保持轮询等待新的执行日志自动进入列表。</div>
        <div class="empty-state__actions">
          <el-button type="primary" plain @click="logsStore.refreshLogs">重新查询</el-button>
          <el-button @click="filters.errorCode = ''; filters.eventStatus = ''; filters.deviceId = ''; filters.taskId = ''; filters.timeRange = []">清空筛选</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.expand-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 4px;
}
</style>
