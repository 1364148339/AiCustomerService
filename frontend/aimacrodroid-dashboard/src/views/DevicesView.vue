<script setup>
import { computed, onMounted } from 'vue'
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

onMounted(async () => {
  await devicesStore.refresh()
})
</script>

<template>
  <div class="devices-view">
    <el-card shadow="never">
      <template #header>
        <div class="section-head">
          <div>
            <div class="section-title">设备状态与就绪度</div>
            <div class="section-desc">已关闭自动刷新，可手动刷新获取最新状态</div>
          </div>
          <div class="header-actions">
            <el-tag type="info" effect="plain">共 {{ devices.length }} 台</el-tag>
            <el-button @click="devicesStore.refresh" :loading="loading" type="primary" plain>
              手动刷新
            </el-button>
          </div>
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

      <el-table :data="devices" v-loading="loading" border stripe class="devices-table" :header-cell-style="{ background: '#f8fafc' }">
        <el-table-column label="设备" min-width="240">
          <template #default="{ row }">
            <div class="device-id">{{ row.id }}</div>
            <div class="sub-text device-meta">
              {{ row.brand }} {{ row.model }} · Android {{ row.androidVersion }} · {{ row.resolution }}
            </div>
            <div class="sub-text">最近心跳：{{ new Date(row.lastHeartbeat).toLocaleString() }}</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'info'" effect="plain" round>
              {{ row.online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="运行环境" min-width="280">
          <template #default="{ row }">
            <div class="env-grid">
              <span class="env-label">前台包名</span>
              <span class="env-value">{{ row.foregroundPkg || '--' }}</span>
              <span class="env-label">网络</span>
              <span class="env-value">{{ row.networkType }}</span>
              <span class="env-label">SSE</span>
              <span class="env-value">{{ row.sseSupported ? '支持' : '不支持' }}</span>
              <span class="env-label">电量</span>
              <span class="env-value">{{ row.batteryPct }}% · {{ row.charging ? '充电中' : '未充电' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="就绪徽标" min-width="300">
          <template #default="{ row }">
            <div class="badge-row">
              <el-tag :type="readinessType(row.shizukuAvailable)" size="small" effect="light" round>Shizuku {{ readinessText(row.shizukuAvailable) }}</el-tag>
              <el-tag :type="readinessType(row.overlayGranted)" size="small" effect="light" round>悬浮窗 {{ readinessText(row.overlayGranted) }}</el-tag>
              <el-tag :type="readinessType(row.keyboardEnabled)" size="small" effect="light" round>键盘 {{ readinessText(row.keyboardEnabled) }}</el-tag>
            </div>
            <div class="sub-text">激活方式：{{ row.lastActivationMethod }}</div>
          </template>
        </el-table-column>

        <el-table-column label="能力集" min-width="210">
          <template #default="{ row }">
            <div class="badge-row capabilities">
              <el-tag v-for="cap in row.capabilities" :key="cap" type="info" size="small">{{ cap }}</el-tag>
              <span v-if="!row.capabilities?.length" class="sub-text">--</span>
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
  gap: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.section-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.device-id {
  font-weight: 600;
  color: #111827;
  line-height: 1.5;
  word-break: break-all;
}
.filter-bar {
  margin-bottom: 14px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
}
.devices-table :deep(td) {
  padding-top: 12px;
  padding-bottom: 12px;
}
.sub-text {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.device-meta {
  line-height: 1.45;
}
.env-grid {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 6px 8px;
}
.env-label {
  color: #6b7280;
  font-size: 12px;
}
.env-value {
  color: #374151;
  font-size: 12px;
  word-break: break-all;
}
.badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.capabilities {
  align-items: center;
}
</style>
