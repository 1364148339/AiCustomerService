import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const lastRefreshAt = ref('')
  const deviceOnlineCount = ref(0)
  const taskRunningCount = ref(0)

  const summaryText = computed(
    () => `在线设备 ${deviceOnlineCount.value} / 运行中任务 ${taskRunningCount.value}`
  )

  function setOverview({ onlineCount, runningCount }) {
    deviceOnlineCount.value = onlineCount
    taskRunningCount.value = runningCount
    lastRefreshAt.value = new Date().toLocaleTimeString()
  }

  return {
    lastRefreshAt,
    deviceOnlineCount,
    taskRunningCount,
    summaryText,
    setOverview
  }
})
