<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useDevicesStore } from '../stores/devices'

const devicesStore = useDevicesStore()
const editingId = ref('')
const editingAlias = ref('')
const savingAlias = ref(false)

const devices = computed(() => devicesStore.filteredList)
const loading = computed(() => devicesStore.loading)
const filters = computed(() => devicesStore.filters)
const summary = computed(() => {
  const list = devices.value || []
  const onlineCount = list.filter((item) => item.online).length
  const offlineCount = list.length - onlineCount
  const readyCount = list.filter((item) => item.shizukuAvailable && item.overlayGranted && item.keyboardEnabled).length
  const chargingCount = list.filter((item) => item.charging).length
  return {
    total: list.length,
    online: onlineCount,
    offline: offlineCount,
    ready: readyCount,
    charging: chargingCount
  }
})

function readinessType(flag) {
  return flag ? 'success' : 'danger'
}

function readinessText(flag) {
  return flag ? '就绪' : '未就绪'
}

function batteryStatusType(value) {
  if (value >= 70) return 'success'
  if (value >= 35) return 'warning'
  return 'danger'
}

function formatHeartbeat(value) {
  if (!value) return '--'
  return new Date(value).toLocaleString()
}

function startEdit(row) {
  editingId.value = row.id
  editingAlias.value = row.alias || row.id
}

function cancelEdit() {
  editingId.value = ''
  editingAlias.value = ''
}

async function saveAlias(row) {
  const nextAlias = editingAlias.value.trim() || row.id
  savingAlias.value = true
  try {
    await devicesStore.renameDevice(row.id, nextAlias)
    row.alias = nextAlias
    row.displayName = nextAlias
    ElMessage.success('设备别名已更新')
    cancelEdit()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '设备别名更新失败')
  } finally {
    savingAlias.value = false
  }
}

onMounted(async () => {
  await devicesStore.refresh()
})
</script>

<template>
  <div class="devices-view">
    <div class="overview-grid">
      <el-card shadow="hover" class="overview-card total-card">
        <div class="overview-label">设备总数</div>
        <div class="overview-value">{{ summary.total }}</div>
        <div class="overview-sub">当前筛选结果</div>
      </el-card>
      <el-card shadow="hover" class="overview-card online-card">
        <div class="overview-label">在线设备</div>
        <div class="overview-value">{{ summary.online }}</div>
        <div class="overview-sub">离线 {{ summary.offline }} 台</div>
      </el-card>
      <el-card shadow="hover" class="overview-card ready-card">
        <div class="overview-label">完全就绪</div>
        <div class="overview-value">{{ summary.ready }}</div>
        <div class="overview-sub">三项能力全部可用</div>
      </el-card>
      <el-card shadow="hover" class="overview-card charging-card">
        <div class="overview-label">充电中</div>
        <div class="overview-value">{{ summary.charging }}</div>
        <div class="overview-sub">便于长时任务调度</div>
      </el-card>
    </div>

    <el-card shadow="never" class="devices-panel">
      <template #header>
        <div class="section-head">
          <div>
            <div class="section-title">设备状态与就绪度</div>
            <div class="section-desc">聚合查看设备在线状态、运行环境与执行前检查结果</div>
          </div>
          <div class="header-actions">
            <el-tag type="info" effect="plain" round>展示 {{ devices.length }} 台</el-tag>
            <el-button @click="devicesStore.refresh" :loading="loading" type="primary">
              手动刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="filter-panel">
        <div class="filter-title">筛选条件</div>
        <el-form :inline="true" :model="filters" class="filter-bar">
          <el-form-item label="关键字">
            <el-input v-model="filters.keyword" placeholder="设备别名 / 设备ID / 品牌机型 / 前台包名" clearable class="filter-input filter-keyword" />
          </el-form-item>
          <el-form-item label="在线状态">
            <el-select v-model="filters.online" class="filter-select">
              <el-option label="全部" value="ALL" />
              <el-option label="在线" value="ONLINE" />
              <el-option label="离线" value="OFFLINE" />
            </el-select>
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="filters.brand" placeholder="如 Xiaomi / HUAWEI" clearable class="filter-input filter-brand" />
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="devices" v-loading="loading" border stripe class="devices-table" :header-cell-style="{ background: '#f8fafc', color: '#334155' }">
        <el-table-column label="设备信息" min-width="340">
          <template #default="{ row }">
            <div class="device-primary">
              <div class="device-title-row">
                <div class="device-name">{{ row.displayName || row.alias || row.id }}</div>
                <el-tag :type="row.online ? 'success' : 'info'" effect="light" round>
                  {{ row.online ? '在线' : '离线' }}
                </el-tag>
              </div>
              <div class="sub-text">设备ID：{{ row.id }}</div>
              <div class="sub-text device-meta">
                {{ row.brand }} {{ row.model }} · Android {{ row.androidVersion }} · {{ row.resolution }}
              </div>
              <div class="sub-text">最近心跳：{{ formatHeartbeat(row.lastHeartbeat) }}</div>
              <div v-if="editingId === row.id" class="alias-editor">
                <el-input v-model="editingAlias" maxlength="128" placeholder="请输入设备别名" />
                <div class="alias-actions">
                  <el-button size="small" @click="cancelEdit">取消</el-button>
                  <el-button size="small" type="primary" :loading="savingAlias" @click="saveAlias(row)">保存</el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="运行环境" min-width="320">
          <template #default="{ row }">
            <div class="env-grid">
              <div class="env-item">
                <span class="env-label">前台包名</span>
                <span class="env-value">{{ row.foregroundPkg || '--' }}</span>
              </div>
              <div class="env-item">
                <span class="env-label">网络类型</span>
                <span class="env-value">{{ row.networkType || '--' }}</span>
              </div>
              <div class="env-item">
                <span class="env-label">SSE 支持</span>
                <span class="env-value">{{ row.sseSupported ? '支持' : '不支持' }}</span>
              </div>
              <div class="env-item">
                <span class="env-label">激活方式</span>
                <span class="env-value">{{ row.lastActivationMethod || '--' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="电量与供电" width="180" align="center">
          <template #default="{ row }">
            <div class="power-cell">
              <el-progress :percentage="Number(row.batteryPct || 0)" :status="batteryStatusType(Number(row.batteryPct || 0))" :stroke-width="8" :show-text="false" />
              <div class="power-meta">
                <span class="power-value">{{ row.batteryPct ?? '--' }}%</span>
                <el-tag :type="row.charging ? 'warning' : 'info'" size="small" effect="light" round>
                  {{ row.charging ? '充电中' : '未充电' }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="就绪徽标" min-width="320">
          <template #default="{ row }">
            <div class="badge-row readiness-row">
              <el-tag :type="readinessType(row.shizukuAvailable)" size="small" effect="light" round>Shizuku {{ readinessText(row.shizukuAvailable) }}</el-tag>
              <el-tag :type="readinessType(row.overlayGranted)" size="small" effect="light" round>悬浮窗 {{ readinessText(row.overlayGranted) }}</el-tag>
              <el-tag :type="readinessType(row.keyboardEnabled)" size="small" effect="light" round>键盘 {{ readinessText(row.keyboardEnabled) }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="能力集" min-width="240">
          <template #default="{ row }">
            <div class="badge-row capabilities">
              <el-tag v-for="cap in row.capabilities" :key="cap" type="info" effect="plain" size="small" round>{{ cap }}</el-tag>
              <span v-if="!row.capabilities?.length" class="sub-text">--</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="快捷操作" width="188" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-group">
              <el-button type="primary" link @click="startEdit(row)">编辑别名</el-button>
              <el-button type="primary" link @click="devicesStore.dispatchReadinessHint(row.id)">发送就绪提示</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.devices-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.overview-card {
  border: none;
  border-radius: 18px;
  overflow: hidden;
}
.overview-card :deep(.el-card__body) {
  padding: 18px 20px;
}
.total-card {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
}
.online-card {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
}
.ready-card {
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
}
.charging-card {
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
}
.overview-label {
  font-size: 13px;
  color: #475569;
}
.overview-value {
  margin-top: 12px;
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}
.overview-sub {
  margin-top: 10px;
  font-size: 12px;
  color: #64748b;
}
.devices-panel {
  border-radius: 18px;
  border: 1px solid #e5e7eb;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.section-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}
.section-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.filter-panel {
  margin-bottom: 16px;
  padding: 16px 18px 4px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 16px;
}
.filter-title {
  margin-bottom: 14px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}
.filter-input,
.filter-select {
  width: 180px;
}
.filter-keyword {
  width: 320px;
}
.filter-brand {
  width: 190px;
}
.devices-table {
  border-radius: 14px;
  overflow: hidden;
}
.devices-table :deep(th.el-table__cell) {
  height: 52px;
  font-weight: 600;
}
.devices-table :deep(td) {
  padding-top: 14px;
  padding-bottom: 14px;
}
.device-primary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.device-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.device-name {
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
  word-break: break-all;
}
.sub-text {
  font-size: 12px;
  color: #64748b;
}
.device-meta {
  line-height: 1.5;
}
.alias-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.alias-actions {
  display: flex;
  gap: 8px;
}
.env-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.env-item {
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.env-label {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}
.env-value {
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}
.power-cell {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.power-meta {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.power-value {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.readiness-row {
  row-gap: 10px;
}
.capabilities {
  align-items: center;
}
.action-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
@media (max-width: 1200px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
  .env-grid {
    grid-template-columns: 1fr;
  }
  .filter-keyword,
  .filter-brand,
  .filter-input,
  .filter-select {
    width: 100%;
  }
}
</style>
