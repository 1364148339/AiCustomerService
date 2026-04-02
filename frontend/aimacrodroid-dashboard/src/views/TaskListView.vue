<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useDevicesStore } from '../stores/devices'
import { useScenariosStore } from '../stores/scenarios'
import { useTasksStore } from '../stores/tasks'

const route = useRoute()
const router = useRouter()
const tasksStore = useTasksStore()
const devicesStore = useDevicesStore()
const scenariosStore = useScenariosStore()
const rows = computed(() => tasksStore.filteredList)
const loading = computed(() => tasksStore.listLoading)
const filters = computed(() => tasksStore.listFilters)
const createVisible = ref(false)
const createSubmitting = ref(false)
const createForm = ref({
  scenarioKey: '',
  devices: [],
  priority: 5,
  deadlineMs: null,
  maxRetries: null,
  snapshotLevel: '',
  logDetail: ''
})
const scenarioOptions = computed(() => scenariosStore.list)
const deviceOptions = computed(() => devicesStore.list)
const selectedScenario = computed(() =>
  scenarioOptions.value.find((item) => item.scenarioKey === createForm.value.scenarioKey) || null
)

const typeLabelMap = {
  CHECKIN: '签到',
  VIDEO_REWARD: '视频领奖'
}

function toTaskTypeLabel(type) {
  return typeLabelMap[type] || type || '--'
}

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'RUNNING') return 'warning'
  if (status === 'QUEUED' || status === 'DISPATCHING') return 'info'
  return ''
}

function progressPercent(row) {
  const total = row.progress.success + row.progress.fail + row.progress.running
  if (!total) return 0
  return Math.round((row.progress.success / total) * 100)
}

function normalizeNonNegativeInteger(value) {
  if (value === null || value === undefined || value === '') return null
  const numeric = Number(value)
  if (!Number.isInteger(numeric) || numeric < 0) return undefined
  return numeric
}

function resetCreateForm(presetScenarioKey = '') {
  createForm.value = {
    scenarioKey: presetScenarioKey || scenarioOptions.value[0]?.scenarioKey || '',
    devices: deviceOptions.value.filter((item) => item.online).map((item) => item.id).slice(0, 1),
    priority: 5,
    deadlineMs: null,
    maxRetries: null,
    snapshotLevel: '',
    logDetail: ''
  }
}

function openCreateDialog(presetScenarioKey = '') {
  resetCreateForm(presetScenarioKey)
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.value.scenarioKey) {
    ElMessage.warning('请选择场景')
    return
  }
  if (!createForm.value.devices.length) {
    ElMessage.warning('请至少选择一台设备')
    return
  }
  const deadlineMs = normalizeNonNegativeInteger(createForm.value.deadlineMs)
  const maxRetries = normalizeNonNegativeInteger(createForm.value.maxRetries)
  if (deadlineMs === undefined || maxRetries === undefined) {
    ElMessage.warning('截止时间与最大重试必须为非负整数')
    return
  }
  const payload = {
    scenarioKey: createForm.value.scenarioKey,
    devices: [...createForm.value.devices],
    priority: Number(createForm.value.priority)
  }
  if (deadlineMs !== null || maxRetries !== null) {
    payload.constraints = {}
    if (deadlineMs !== null) payload.constraints.deadlineMs = deadlineMs
    if (maxRetries !== null) payload.constraints.maxRetries = maxRetries
  }
  if (createForm.value.snapshotLevel || createForm.value.logDetail) {
    payload.observability = {}
    if (createForm.value.snapshotLevel) payload.observability.snapshotLevel = createForm.value.snapshotLevel
    if (createForm.value.logDetail) payload.observability.logDetail = createForm.value.logDetail
  }
  createSubmitting.value = true
  try {
    const result = await tasksStore.submitTask(payload)
    await tasksStore.refreshList()
    createVisible.value = false
    ElMessage.success(`任务已创建：${result.taskId}`)
    router.push(`/tasks/${result.taskId}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '创建任务失败')
  } finally {
    createSubmitting.value = false
  }
}

function syncCreateByQuery() {
  const shouldOpen = String(route.query.create || '') === '1'
  if (!shouldOpen) return
  const presetScenarioKey = String(route.query.scenarioKey || '')
  openCreateDialog(presetScenarioKey)
}

onMounted(async () => {
  await Promise.all([tasksStore.refreshList(), devicesStore.refresh(), scenariosStore.refreshScenarios()])
  tasksStore.startListPolling()
  syncCreateByQuery()
})

watch(
  () => `${route.query.create || ''}|${route.query.scenarioKey || ''}`,
  () => syncCreateByQuery()
)

onBeforeUnmount(() => {
  tasksStore.stopListPolling()
})
</script>

<template>
  <div class="task-list-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <div class="header-actions">
            <el-button @click="tasksStore.refreshList">手动刷新</el-button>
            <el-button type="primary" @click="openCreateDialog()">新建任务</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="filters" class="filter-bar">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="taskId/任务编号/场景" style="width: 280px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 160px">
            <el-option label="QUEUED" value="QUEUED" />
            <el-option label="DISPATCHING" value="DISPATCHING" />
            <el-option label="RUNNING" value="RUNNING" />
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAIL" value="FAIL" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="rows" border v-loading="loading">
        <el-table-column label="任务编号" min-width="160">
          <template #default="{ row }">
            {{ row.taskNo || row.taskId }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ toTaskTypeLabel(row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="track" label="轨道" width="120" />
        <el-table-column label="场景" min-width="200">
          <template #default="{ row }">
            {{ row.scenarioName || row.scenarioKey || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度看板" min-width="300">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="progressPercent(row)" :stroke-width="14" />
              <span class="progress-text">
                成功 {{ row.progress.success }} / 运行中 {{ row.progress.running }} / 失败 {{ row.progress.fail }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/tasks/${row.taskId}`)">
              任务详情
            </el-button>
            <el-button link @click="router.push(`/logs?taskId=${row.taskId}`)">日志</el-button>
            <el-button link @click="router.push(`/alerts?taskId=${row.taskId}`)">告警</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" width="680px" title="新建任务" destroy-on-close>
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="场景" required>
          <el-select v-model="createForm.scenarioKey" placeholder="请选择场景" style="width: 100%">
            <el-option
              v-for="item in scenarioOptions"
              :key="item.scenarioKey"
              :label="`${item.scenarioName} (${item.scenarioKey})`"
              :value="item.scenarioKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="场景说明">
          <el-input :model-value="selectedScenario?.description || '--'" disabled />
        </el-form-item>
        <el-form-item label="设备" required>
          <el-checkbox-group v-model="createForm.devices" class="device-list">
            <el-checkbox-button
              v-for="item in deviceOptions"
              :key="item.id"
              :value="item.id"
              :disabled="!item.online"
            >
              {{ item.id }}（{{ item.online ? '在线' : '离线' }}）
            </el-checkbox-button>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="优先级" required>
          <el-input-number v-model="createForm.priority" :min="1" :max="9" />
        </el-form-item>
        <el-form-item label="截止时间(ms)">
          <el-input-number v-model="createForm.deadlineMs" :min="0" :step="60000" />
        </el-form-item>
        <el-form-item label="最大重试">
          <el-input-number v-model="createForm.maxRetries" :min="0" :max="20" />
        </el-form-item>
        <el-form-item label="快照级别">
          <el-select v-model="createForm.snapshotLevel" clearable placeholder="可选" style="width: 100%">
            <el-option label="关键步骤" value="key-steps" />
            <el-option label="全部步骤" value="all-steps" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="createForm.logDetail" clearable placeholder="可选" style="width: 100%">
            <el-option label="仅错误" value="errors_only" />
            <el-option label="全部日志" value="all" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">提交任务</el-button>
      </template>
    </el-dialog>
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
  gap: 8px;
}
.filter-bar {
  margin-bottom: 10px;
}
.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.progress-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.device-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
