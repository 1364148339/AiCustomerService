<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useDevicesStore } from '../stores/devices'
import { useScenariosStore } from '../stores/scenarios'
import { useTasksStore } from '../stores/tasks'

const route = useRoute()
const router = useRouter()
const tasksStore = useTasksStore()
const devicesStore = useDevicesStore()
const scenariosStore = useScenariosStore()
const submitting = ref(false)
const result = ref(null)
const form = ref({
  scenarioKey: '',
  devices: [],
  priority: 5,
  deadlineMs: null,
  maxRetries: null,
  snapshotLevel: '',
  logDetail: ''
})

const deviceOptions = computed(() => devicesStore.list)
const scenarioOptions = computed(() => scenariosStore.list.filter((item) => item.status === 'ACTIVE'))
const selectedScenario = computed(() =>
  scenarioOptions.value.find((item) => item.scenarioKey === form.value.scenarioKey) || null
)
const payloadPreview = computed(() => JSON.stringify(buildPayload(), null, 2))

function buildPayload() {
  const payload = {
    scenarioKey: form.value.scenarioKey,
    devices: [...form.value.devices],
    priority: Number(form.value.priority)
  }
  if (form.value.deadlineMs !== null || form.value.maxRetries !== null) {
    payload.constraints = {}
    if (form.value.deadlineMs !== null) payload.constraints.deadlineMs = form.value.deadlineMs
    if (form.value.maxRetries !== null) payload.constraints.maxRetries = form.value.maxRetries
  }
  if (form.value.snapshotLevel || form.value.logDetail) {
    payload.observability = {}
    if (form.value.snapshotLevel) payload.observability.snapshotLevel = form.value.snapshotLevel
    if (form.value.logDetail) payload.observability.logDetail = form.value.logDetail
  }
  return payload
}

function normalizeNonNegativeInteger(value) {
  if (value === null || value === undefined || value === '') return null
  const numeric = Number(value)
  if (!Number.isInteger(numeric) || numeric < 0) return undefined
  return numeric
}

async function submit() {
  if (!form.value.scenarioKey) {
    ElMessage.warning('请选择场景')
    return
  }
  if (!form.value.devices.length) {
    ElMessage.warning('请至少选择一台在线设备')
    return
  }
  const deadlineMs = normalizeNonNegativeInteger(form.value.deadlineMs)
  const maxRetries = normalizeNonNegativeInteger(form.value.maxRetries)
  if (deadlineMs === undefined || maxRetries === undefined) {
    ElMessage.warning('deadlineMs 与 maxRetries 必须为非负整数')
    return
  }
  form.value.deadlineMs = deadlineMs
  form.value.maxRetries = maxRetries
  submitting.value = true
  try {
    result.value = await tasksStore.submitTask(buildPayload())
    await tasksStore.refreshList()
    ElMessage.success(`任务已创建：${result.value.taskId}`)
    router.push(`/tasks/${result.value.taskId}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '创建任务失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([devicesStore.refresh(), scenariosStore.refreshScenarios()])
  const queryScenarioKey = String(route.query.scenarioKey || '')
  if (queryScenarioKey) {
    form.value.scenarioKey = queryScenarioKey
  } else if (scenarioOptions.value.length > 0) {
    form.value.scenarioKey = scenarioOptions.value[0].scenarioKey
  }
  form.value.devices = devicesStore.list.filter((item) => item.online).map((item) => item.id).slice(0, 1)
})
</script>

<template>
  <div class="tasks-new-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务创建</span>
          <el-button type="primary" plain @click="tasksStore.refreshList">刷新任务列表</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="14">
          <el-form :model="form" label-width="120px">
            <el-form-item label="场景">
              <el-select v-model="form.scenarioKey" style="width: 260px">
                <el-option
                  v-for="item in scenarioOptions"
                  :key="item.scenarioKey"
                  :label="`${item.scenarioName} (${item.scenarioKey})`"
                  :value="item.scenarioKey"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="1" :max="9" />
            </el-form-item>

            <el-form-item label="截止时间(ms)">
              <el-input-number v-model="form.deadlineMs" :min="0" :step="60000" />
            </el-form-item>

            <el-form-item label="最大重试">
              <el-input-number v-model="form.maxRetries" :min="0" :max="20" />
            </el-form-item>

            <el-form-item label="场景说明">
              <el-input :model-value="selectedScenario?.description || '--'" disabled />
            </el-form-item>

            <el-form-item label="快照级别">
              <el-select v-model="form.snapshotLevel" clearable style="width: 220px">
                <el-option label="关键步骤" value="key-steps" />
                <el-option label="全部步骤" value="all-steps" />
              </el-select>
            </el-form-item>

            <el-form-item label="日志级别">
              <el-select v-model="form.logDetail" clearable style="width: 220px">
                <el-option label="仅错误" value="errors_only" />
                <el-option label="全部日志" value="all" />
              </el-select>
            </el-form-item>

            <el-form-item label="选择设备">
              <el-checkbox-group v-model="form.devices" class="device-list">
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

            <el-form-item>
              <el-button @click="router.push('/scenarios')">场景管理</el-button>
              <el-button @click="router.push('/tasks')">任务列表</el-button>
              <el-button type="primary" :loading="submitting" @click="submit">提交任务</el-button>
            </el-form-item>
          </el-form>
        </el-col>

        <el-col :span="10">
          <el-card shadow="never" class="preview-card">
            <template #header>请求预览</template>
            <pre class="payload">{{ payloadPreview }}</pre>
          </el-card>
        </el-col>
      </el-row>

      <el-alert
        v-if="result"
        :title="`已创建任务 ${result.taskNo || result.taskId}，状态 ${result.status}`"
        type="success"
        show-icon
        :closable="false"
      />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.device-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.preview-card {
  height: 100%;
}
.payload {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
}
</style>
