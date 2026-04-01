import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ackAlert, closeAlert, getAlerts } from '../mock/api'
import { useAppStore } from './app'
import { createPagePolling } from '../utils/polling'

const POLL_INTERVAL = 3000

export const useAlertsStore = defineStore('alerts', () => {
  const appStore = useAppStore()
  const filters = ref({
    taskId: '',
    alertLevel: '',
    alertType: '',
    alertStatus: '',
    timeRange: []
  })
  const rows = ref([])
  const loading = ref(false)
  const poller = createPagePolling(refreshAlerts, POLL_INTERVAL)

  async function refreshAlerts() {
    loading.value = true
    try {
      const [startAt, endAt] = filters.value.timeRange || []
      const list = await getAlerts({
        taskId: filters.value.taskId,
        alertLevel: filters.value.alertLevel,
        alertType: filters.value.alertType,
        alertStatus: filters.value.alertStatus,
        startAt: startAt ? new Date(startAt).getTime() : 0,
        endAt: endAt ? new Date(endAt).getTime() : 0
      })
      rows.value = list
      appStore.setOverview({
        onlineCount: appStore.deviceOnlineCount,
        totalCount: appStore.deviceTotalCount,
        runningCount: appStore.taskRunningCount,
        openAlertCount: list.filter((item) => item.status === 'OPEN').length
      })
    } finally {
      loading.value = false
    }
  }

  async function ack(alertId) {
    await ackAlert(alertId)
    await refreshAlerts()
  }

  async function close(alertId) {
    await closeAlert(alertId)
    await refreshAlerts()
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
    loading,
    refreshAlerts,
    ack,
    close,
    startPolling,
    stopPolling
  }
})
