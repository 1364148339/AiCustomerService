<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAlerts, getDevices, getTasks } from '../mock/api'
import { useAppStore } from '../stores/app'

const router = useRouter()
const appStore = useAppStore()
const loading = ref(false)
const deviceCount = ref(0)
const onlineCount = ref(0)
const runningTasks = ref(0)
const openAlerts = ref(0)
const readyDevices = ref(0)
const latestTasks = ref([])
const latestAlerts = ref([])

const metrics = computed(() => [
  {
    label: '设备总数',
    value: deviceCount.value,
    sub: `完全就绪 ${readyDevices.value} 台`,
    className: 'metric-card--blue'
  },
  {
    label: '在线设备',
    value: onlineCount.value,
    sub: `在线率 ${deviceCount.value ? Math.round((onlineCount.value / deviceCount.value) * 100) : 0}%`,
    className: 'metric-card--green'
  },
  {
    label: '运行中任务',
    value: runningTasks.value,
    sub: '关注调度和执行进度',
    className: 'metric-card--purple'
  },
  {
    label: '开放告警',
    value: openAlerts.value,
    sub: '优先处理失败与超时',
    className: 'metric-card--orange'
  }
])

async function loadOverview() {
  loading.value = true
  try {
    const [devicesResp, tasks, alerts] = await Promise.all([getDevices(), getTasks(), getAlerts()])
    const devices = Array.isArray(devicesResp) ? devicesResp : devicesResp?.devices || []
    deviceCount.value = devices.length
    onlineCount.value = devices.filter((d) => d.online).length
    readyDevices.value = devices.filter((d) => d.shizukuAvailable && d.overlayGranted && d.keyboardEnabled).length
    runningTasks.value = tasks.filter((t) => ['RUNNING', 'DISPATCHING', 'QUEUED'].includes(t.status)).length
    openAlerts.value = alerts.filter((a) => a.status === 'OPEN').length
    latestTasks.value = [...tasks].slice(0, 5)
    latestAlerts.value = [...alerts].slice(0, 5)
    appStore.setOverview({
      totalCount: deviceCount.value,
      onlineCount: onlineCount.value,
      runningCount: runningTasks.value,
      openAlertCount: openAlerts.value
    })
  } finally {
    loading.value = false
  }
}

function taskStatusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'RUNNING' || status === 'DISPATCHING') return 'warning'
  return 'info'
}

function alertType(level) {
  if (level === 'P1') return 'danger'
  if (level === 'P2') return 'warning'
  return 'info'
}

onMounted(loadOverview)
</script>

<template>
  <div class="page-shell home-view" v-loading="loading">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div class="page-header__main">
            <div class="page-header__title">运行概览</div>
            <div class="page-header__desc">聚合查看设备、任务与告警状态，快速进入高频工作流。</div>
          </div>
          <div class="page-header__actions">
            <el-button @click="loadOverview">刷新概览</el-button>
            <el-button type="primary" @click="router.push('/tasks/new')">创建任务</el-button>
            <el-button @click="router.push('/devices')">查看设备</el-button>
          </div>
        </div>
      </template>

      <div class="metric-grid">
        <el-card v-for="item in metrics" :key="item.label" shadow="hover" class="metric-card" :class="item.className">
          <div class="metric-card__label">{{ item.label }}</div>
          <div class="metric-card__value">{{ item.value }}</div>
          <div class="metric-card__sub">{{ item.sub }}</div>
        </el-card>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never" class="page-card">
          <template #header>
            <div class="page-header">
              <div class="page-header__main">
                <div class="page-header__title">最新任务</div>
                <div class="page-header__desc">快速回看最近任务的执行情况。</div>
              </div>
              <div class="page-header__actions">
                <el-button link type="primary" @click="router.push('/tasks')">查看全部</el-button>
              </div>
            </div>
          </template>
          <el-table v-if="latestTasks.length" :data="latestTasks" border class="data-table">
            <el-table-column label="任务编号" min-width="160">
              <template #default="{ row }">{{ row.taskNo || row.taskId }}</template>
            </el-table-column>
            <el-table-column label="场景" min-width="180">
              <template #default="{ row }">{{ row.scenarioName || row.scenarioKey || '--' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="taskStatusType(row.status)" effect="light" round>{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(`/tasks/${row.taskId}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-state empty-state--compact">
            <div class="empty-state__title">暂无任务动态</div>
            <div class="empty-state__desc">当前还没有最近任务记录，可以直接发起一个新任务来验证设备链路和场景配置。</div>
            <div class="empty-state__actions">
              <el-button type="primary" @click="router.push('/tasks/new')">立即创建</el-button>
              <el-button @click="router.push('/tasks')">查看任务列表</el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" class="page-card">
          <template #header>
            <div class="page-header">
              <div class="page-header__main">
                <div class="page-header__title">最新告警</div>
                <div class="page-header__desc">优先关注高优先级失败告警。</div>
              </div>
              <div class="page-header__actions">
                <el-button link type="primary" @click="router.push('/alerts')">告警中心</el-button>
              </div>
            </div>
          </template>
          <div v-if="latestAlerts.length" class="alert-list">
            <div v-for="item in latestAlerts" :key="item.id" class="alert-item is-clickable" @click="router.push('/alerts')">
              <div class="alert-item__header">
                <el-tag :type="alertType(item.level)" effect="light" round>{{ item.level }}</el-tag>
                <span class="alert-item__type">{{ item.type }}</span>
              </div>
              <div class="alert-item__reason">{{ item.reason || '--' }}</div>
              <div class="alert-item__meta">{{ item.deviceId }} · {{ new Date(item.createdAt).toLocaleString() }}</div>
            </div>
          </div>
          <div v-else class="empty-state empty-state--compact">
            <div class="empty-state__title">当前没有待处理告警</div>
            <div class="empty-state__desc">系统状态较稳定，后续出现失败、超时或异常回传时会在这里第一时间聚合展示。</div>
            <div class="empty-state__actions">
              <el-button @click="loadOverview">重新检查</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  background: var(--surface-2);
}

.alert-item:hover {
  border-color: rgba(96, 165, 250, 0.34);
  background: linear-gradient(180deg, #f8fbff 0%, #f1f5f9 100%);
}

.alert-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.alert-item__type {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}

.alert-item__reason {
  margin-top: 10px;
  line-height: 1.6;
  color: var(--text-2);
}

.alert-item__meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
}
</style>
