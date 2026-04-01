<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAlerts, getDevices, getTasks } from '../mock/api'
import { useAppStore } from '../stores/app'

const router = useRouter()
const appStore = useAppStore()
const deviceCount = ref(0)
const onlineCount = ref(0)
const runningTasks = ref(0)
const openAlerts = ref(0)

async function loadOverview() {
  const [devices, tasks, alerts] = await Promise.all([getDevices(), getTasks(), getAlerts()])
  deviceCount.value = devices.length
  onlineCount.value = devices.filter((d) => d.online).length
  runningTasks.value = tasks.filter((t) => t.status === 'RUNNING').length
  openAlerts.value = alerts.filter((a) => a.status === 'OPEN').length
  appStore.setOverview({ onlineCount: onlineCount.value, runningCount: runningTasks.value })
}

onMounted(loadOverview)
</script>

<template>
  <div class="home-view">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>运行概览</span>
          <div>
            <el-button type="primary" @click="router.push('/tasks/new')">创建任务</el-button>
            <el-button @click="router.push('/devices')">查看设备</el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-title">设备总数</div>
            <div class="stat-value">{{ deviceCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-title">在线设备</div>
            <div class="stat-value">{{ onlineCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-title">运行中任务</div>
            <div class="stat-value">{{ runningTasks }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-title">开放告警</div>
            <div class="stat-value text-danger">{{ openAlerts }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-card {
  text-align: center;
  padding: 20px 0;
}
.stat-title {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--el-text-color-primary);
}
.text-danger {
  color: var(--el-color-danger);
}
</style>
