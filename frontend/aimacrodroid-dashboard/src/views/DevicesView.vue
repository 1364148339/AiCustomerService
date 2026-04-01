<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getDevices, getTasks } from '../mock/api'
import { useAppStore } from '../stores/app'

const appStore = useAppStore()
const devices = ref([])
const loading = ref(false)
let timer = null

async function loadDevices() {
  loading.value = true
  try {
    const [deviceList, taskList] = await Promise.all([getDevices(), getTasks()])
    devices.value = deviceList
    appStore.setOverview({
      onlineCount: deviceList.filter((item) => item.online).length,
      runningCount: taskList.filter((item) => item.status === 'RUNNING').length
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDevices()
  timer = setInterval(loadDevices, 3000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<template>
  <div class="devices-view">
    <el-card shadow="never">
      <template #header>
        <div class="section-head">
          <span>设备状态</span>
          <el-button @click="loadDevices" :loading="loading" type="primary" plain>
            手动刷新
          </el-button>
        </div>
      </template>

      <el-table :data="devices" v-loading="loading" border style="width: 100%">
        <el-table-column label="设备" min-width="180">
          <template #default="{ row }">
            <div>{{ row.id }}</div>
            <div class="text-sm text-secondary">{{ row.brand }} {{ row.model }}</div>
          </template>
        </el-table-column>
        
        <el-table-column label="在线" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'danger'" effect="dark">
              {{ row.online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="foregroundPkg" label="前台包名" min-width="200">
          <template #default="{ row }">
            {{ row.foregroundPkg || '--' }}
          </template>
        </el-table-column>

        <el-table-column label="电量/网络" width="150">
          <template #default="{ row }">
            <div>{{ row.batteryPct }}% <el-icon v-if="row.charging"><i-ep-lightning /></el-icon></div>
            <div class="text-sm text-secondary">{{ row.networkType }}</div>
          </template>
        </el-table-column>

        <el-table-column label="就绪状态" min-width="220">
          <template #default="{ row }">
            <el-tag :type="row.shizukuAvailable ? 'success' : 'warning'" size="small" class="mr-2 mb-1">
              Shizuku
            </el-tag>
            <el-tag :type="row.overlayGranted ? 'success' : 'warning'" size="small" class="mr-2 mb-1">
              悬浮窗
            </el-tag>
            <el-tag :type="row.keyboardEnabled ? 'success' : 'warning'" size="small" class="mb-1">
              键盘
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="能力集" min-width="200">
          <template #default="{ row }">
            <div class="flex flex-wrap gap-1">
              <el-tag v-for="cap in row.capabilities" :key="cap" type="info" size="small">
                {{ cap }}
              </el-tag>
            </div>
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
.text-sm {
  font-size: 12px;
}
.text-secondary {
  color: var(--el-text-color-secondary);
}
.mr-2 {
  margin-right: 8px;
}
.mb-1 {
  margin-bottom: 4px;
}
.flex {
  display: flex;
}
.flex-wrap {
  flex-wrap: wrap;
}
.gap-1 {
  gap: 4px;
}
</style>
