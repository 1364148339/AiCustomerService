import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLogs, getTasks } from '../mock/api'
import { createPagePolling } from '../utils/polling'

const POLL_INTERVAL = 3000

export const useLogsStore = defineStore('logs', () => {
  const filters = ref({
    taskId: '',
    deviceId: '',
    eventStatus: '',
    errorCode: '',
    timeRange: []
  })
  const rows = ref([])
  const tasks = ref([])
  const loading = ref(false)
  const poller = createPagePolling(refreshLogs, POLL_INTERVAL)

  async function refreshTasks() {
    tasks.value = await getTasks()
  }

  async function refreshLogs() {
    loading.value = true
    try {
      const [startAt, endAt] = filters.value.timeRange || []
      rows.value = await getLogs({
        taskId: filters.value.taskId,
        deviceId: filters.value.deviceId,
        eventStatus: filters.value.eventStatus,
        errorCode: filters.value.errorCode,
        startAt: startAt ? new Date(startAt).getTime() : 0,
        endAt: endAt ? new Date(endAt).getTime() : 0
      })
    } finally {
      loading.value = false
    }
  }

  async function init() {
    await Promise.all([refreshTasks(), refreshLogs()])
  }

  function startPolling() {
    poller.start()
  }

  function stopPolling() {
    poller.stop()
  }

  return {
    filters,
    rows,
    tasks,
    loading,
    init,
    refreshLogs,
    startPolling,
    stopPolling
  }
})
