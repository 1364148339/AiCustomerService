<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useDevicesStore } from '../stores/devices'

const devicesStore = useDevicesStore()

const devices = computed(() => devicesStore.filteredList)
const loading = computed(() => devicesStore.loading)
const filters = computed(() => devicesStore.filters)

function readinessType(flag) {
  return flag ? 'success' : 'danger'
}

function readinessText(flag) {
  return flag ? '就绪' : '未就绪'
}

async function sendReadinessHint(deviceId) {
  try {
    await devicesStore.dispatchReadinessHint(deviceId)
    ElMessage.success(`已为 ${deviceId} 下发提示任务`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '下发提示任务失败')
  }
}

onMounted(async () => {
  await devicesStore.refresh()
  devicesStore.startPolling()
})

onBeforeUnmount(() => {
  devicesStore.stopPolling()
})
</script>

<template>
  <div class="devices-view">
    <el-card shadow="never">
      <template #header>
        <div class="section-head">
          <span>设备状态与就绪度</span>
          <el-button @click="devicesStore.refresh" :loading="loading" type="primary">
            手动刷新
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="filters" class="filter-bar">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="设备ID/机型/前台包名" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="在线状态">
          <el-select v-model="filters.online" style="width: 140px">
            <el-option label="全部" value="ALL" />
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="filters.brand" placeholder="如 Xiaomi" clearable style="width: 180px" />
        </el-form-item>
      </el-form>

      <el-table :data="devices" v-loading="loading" border>
        <el-table-column label="设备" min-width="240">
          <template #default="{ row }">
            <div class="device-id">{{ row.id }}</div>
            <div class="sub-text">
              {{ row.brand }} {{ row.model }} · Android {{ row.androidVersion }} · {{ row.resolution }}
            </div>
            <div class="sub-text">最近心跳：{{ new Date(row.lastHeartbeat).toLocaleString() }}</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="108" align="center">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'info'" effect="dark">
              {{ row.online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="运行环境" min-width="220">
          <template #default="{ row }">
            <div>前台包名：{{ row.foregroundPkg || '--' }}</div>
            <div class="sub-text">网络：{{ row.networkType }} · SSE：{{ row.sseSupported ? '支持' : '不支持' }}</div>
            <div class="sub-text">电量：{{ row.batteryPct }}% · 充电：{{ row.charging ? '是' : '否' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="就绪徽标" min-width="260">
          <template #default="{ row }">
            <div class="badge-row">
              <el-tag :type="readinessType(row.shizukuAvailable)" size="small">Shizuku {{ readinessText(row.shizukuAvailable) }}</el-tag>
              <el-tag :type="readinessType(row.overlayGranted)" size="small">悬浮窗 {{ readinessText(row.overlayGranted) }}</el-tag>
              <el-tag :type="readinessType(row.keyboardEnabled)" size="small">键盘 {{ readinessText(row.keyboardEnabled) }}</el-tag>
            </div>
            <div class="sub-text">激活方式：{{ row.lastActivationMethod }}</div>
          </template>
        </el-table-column>

        <el-table-column label="能力集" min-width="210">
          <template #default="{ row }">
            <div class="badge-row">
              <el-tag v-for="cap in row.capabilities" :key="cap" type="info" size="small">{{ cap }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="warning"
              link
              :disabled="row.shizukuAvailable && row.overlayGranted && row.keyboardEnabled"
              @click="sendReadinessHint(row.id)"
            >
              下发提示任务
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.device-id {
  font-weight: 600;
}
.filter-bar {
  margin-bottom: 10px;
}
.sub-text {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
