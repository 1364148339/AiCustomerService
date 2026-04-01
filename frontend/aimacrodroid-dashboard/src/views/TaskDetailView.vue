<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getTaskDetail, getTasks } from '../mock/api'
import { useAppStore } from '../stores/app'

const route = useRoute()
const appStore = useAppStore()
const detail = ref(null)
let timer = null

const eventLines = computed(() =>
  (detail.value?.events || []).slice().sort((a, b) => b.timestamp - a.timestamp)
)

async function loadDetail() {
  detail.value = await getTaskDetail(route.params.id)
  const tasks = await getTasks()
  appStore.setOverview({
    onlineCount: appStore.deviceOnlineCount,
    runningCount: tasks.filter((item) => item.status === 'RUNNING').length
  })
}

onMounted(() => {
  loadDetail()
  timer = setInterval(loadDetail, 3000)
})

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="task-detail-view" v-if="detail">
    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="card-header">
          <span>任务详情 {{ detail.taskId }}</span>
          <el-tag :type="detail.status === 'FAIL' ? 'danger' : detail.status === 'SUCCESS' ? 'success' : 'warning'">
            {{ detail.status }}
          </el-tag>
        </div>
      </template>

      <el-descriptions border :column="2">
        <el-descriptions-item label="任务类型">{{ detail.type }}</el-descriptions-item>
        <el-descriptions-item label="轨道">{{ detail.track }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ new Date(detail.createdAt).toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ detail.priority || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="card-header">设备子任务</div>
      </template>
      <el-table :data="detail.devicesRuns" border style="width: 100%">
        <el-table-column prop="deviceId" label="设备" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAIL' ? 'danger' : 'primary'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="step" label="当前步骤" />
        <el-table-column prop="retry" label="重试次数" width="100" align="center" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">事件时间线</div>
      </template>
      
      <el-timeline v-if="eventLines.length > 0">
        <el-timeline-item
          v-for="ev in eventLines"
          :key="`${ev.timestamp}-${ev.commandId}`"
          :timestamp="new Date(ev.timestamp).toLocaleString()"
          :type="ev.status === 'SUCCESS' ? 'success' : ev.status === 'FAIL' ? 'danger' : 'primary'"
        >
          <el-card shadow="hover" class="timeline-card">
            <div class="event-meta">
              <span class="font-bold">{{ ev.deviceId }}</span>
              <el-tag size="small">{{ ev.commandId }}</el-tag>
              <el-tag size="small" type="info">{{ ev.status }}</el-tag>
            </div>
            
            <div class="event-details mt-2">
              <div v-if="ev.thinking" class="thinking-box">
                <i-ep-chat-line-round /> 思考：{{ ev.thinking }}
              </div>
              <div v-if="ev.sensitiveScreenDetected" class="sensitive-alert">
                <i-ep-warning /> 检测到敏感页面
              </div>
            </div>

            <div v-if="ev.screenshotUrl" class="mt-2">
              <el-image 
                :src="ev.screenshotUrl" 
                :preview-src-list="[ev.screenshotUrl]"
                fit="cover"
                class="screenshot-img"
              />
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无事件记录" />
    </el-card>
  </div>
  <el-empty v-else description="加载中或任务不存在..." />
</template>

<style scoped>
.mb-4 {
  margin-bottom: 16px;
}
.mt-2 {
  margin-top: 8px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}
.font-bold {
  font-weight: bold;
  margin-right: 8px;
}
.event-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.thinking-box {
  background-color: var(--el-color-info-light-9);
  padding: 8px 12px;
  border-radius: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  margin-bottom: 8px;
}
.sensitive-alert {
  color: var(--el-color-danger);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.screenshot-img {
  width: 200px;
  height: auto;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}
.timeline-card {
  --el-card-padding: 12px;
}
</style>
