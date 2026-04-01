<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { useAlertsStore } from '../stores/alerts'

const route = useRoute()
const router = useRouter()
const alertsStore = useAlertsStore()
const autoPolling = ref(true)
const filters = computed(() => alertsStore.filters)
const rows = computed(() => alertsStore.rows)
const loading = computed(() => alertsStore.loading)

function alertTypeTag(type) {
  if (type === 'TIMEOUT') return 'warning'
  if (type === 'READINESS') return 'info'
  if (type === 'ELEMENT_NOT_FOUND') return 'warning'
  return 'danger'
}

function onAutoPollingChange(value) {
  if (value) {
    alertsStore.startPolling()
    return
  }
  alertsStore.stopPolling()
}

async function ack(row) {
  await alertsStore.ack(row.id)
}

async function close(row) {
  await alertsStore.close(row.id)
}

function toTask(row) {
  router.push(`/tasks/${row.taskId}?deviceId=${row.deviceId}`)
}

onMounted(async () => {
  filters.value.taskId = String(route.query.taskId || '')
  filters.value.alertType = String(route.query.alertType || '')
  await alertsStore.refreshAlerts()
  alertsStore.startPolling()
})

onBeforeUnmount(() => {
  alertsStore.stopPolling()
})
</script>

<template>
  <div class="alerts-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>失败与超时告警</span>
          <div class="header-actions">
            <el-switch
              v-model="autoPolling"
              inline-prompt
              active-text="轮询开"
              inactive-text="轮询关"
              @change="onAutoPollingChange"
            />
            <el-button type="primary" plain @click="alertsStore.refreshAlerts">手动刷新</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true">
        <el-form-item label="任务">
          <el-input v-model="filters.taskId" placeholder="按 taskId 过滤" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.alertLevel" clearable placeholder="全部" style="width: 120px">
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.alertType" clearable placeholder="全部" style="width: 180px">
            <el-option label="TIMEOUT" value="TIMEOUT" />
            <el-option label="READINESS" value="READINESS" />
            <el-option label="ELEMENT_NOT_FOUND" value="ELEMENT_NOT_FOUND" />
            <el-option label="TASK_FAILURE" value="TASK_FAILURE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.alertStatus" clearable placeholder="全部" style="width: 140px">
            <el-option label="OPEN" value="OPEN" />
            <el-option label="ACK" value="ACK" />
            <el-option label="CLOSED" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 330px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="alertsStore.refreshAlerts">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务" width="120" />
        <el-table-column prop="deviceId" label="设备" width="120" />
        <el-table-column prop="level" label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 'P1' ? 'danger' : row.level === 'P2' ? 'warning' : 'info'" effect="plain">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="告警类型" width="160">
          <template #default="{ row }">
            <el-tag :type="alertTypeTag(row.type)" effect="plain">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="错误码" width="200" />
        <el-table-column prop="reason" label="摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="toTask(row)">定位任务</el-button>
            <el-button v-if="row.status === 'OPEN'" link type="warning" @click="ack(row)">ACK</el-button>
            <el-button v-if="row.status !== 'CLOSED'" link type="danger" @click="close(row)">CLOSED</el-button>
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
</style>
