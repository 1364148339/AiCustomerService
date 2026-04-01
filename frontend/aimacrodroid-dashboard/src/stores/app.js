import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const lastRefreshAt = ref('')
  const deviceOnlineCount = ref(0)
  const deviceTotalCount = ref(0)
  const taskRunningCount = ref(0)
  const alertOpenCount = ref(0)

  const summaryText = computed(
    () =>
      `在线设备 ${deviceOnlineCount.value}/${deviceTotalCount.value} · 运行中任务 ${taskRunningCount.value} · 开放告警 ${alertOpenCount.value}`
  )

  function setOverview({ onlineCount, totalCount, runningCount, openAlertCount }) {
    deviceOnlineCount.value = onlineCount
    deviceTotalCount.value = totalCount
    taskRunningCount.value = runningCount
    alertOpenCount.value = openAlertCount
    lastRefreshAt.value = new Date().toLocaleTimeString()
  }

  return {
    lastRefreshAt,
    deviceOnlineCount,
    deviceTotalCount,
    taskRunningCount,
    alertOpenCount,
    summaryText,
    setOverview
  }
})
