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
  <div class="logs-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>日志与截图检索</span>
          <div class="header-actions">
            <el-switch
              v-model="autoPolling"
              inline-prompt
              active-text="轮询开"
              inactive-text="轮询关"
              @change="onAutoPollingChange"
            />
            <el-button type="primary" plain @click="logsStore.refreshLogs">手动刷新</el-button>
            <el-button @click="exportCurrentRows">导出当前结果</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="filters">
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

      <el-table :data="rows" v-loading="loading" border>
        <el-table-column type="expand" width="42">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="expand-title">thinkingText</div>
              <pre class="json-block">{{ row.thinkingText || '--' }}</pre>
              <div class="expand-title">traceJson</div>
              <pre class="json-block">{{ row.traceJson || '--' }}</pre>
              <div class="expand-title">elementJson</div>
              <pre class="json-block">{{ row.elementJson || '--' }}</pre>
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
            <el-tag :type="levelType(row.level)" effect="plain">{{ row.level }}</el-tag>
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
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  gap: 12px;
}
.expand-content {
  padding: 8px 4px;
}
.expand-title {
  margin: 8px 0 4px;
  font-weight: 600;
}
.json-block {
  margin: 0;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
