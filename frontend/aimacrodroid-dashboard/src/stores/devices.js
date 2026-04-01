import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createTask, getDevices } from '../mock/api'
import { useAppStore } from './app'
import { createPagePolling } from '../utils/polling'

const POLL_INTERVAL = 3000

export const useDevicesStore = defineStore('devices', () => {
  const appStore = useAppStore()
  const list = ref([])
  const loading = ref(false)
  const filters = ref({
    keyword: '',
    online: 'ALL',
    brand: ''
  })
  const poller = createPagePolling(refresh, POLL_INTERVAL)

  const filteredList = computed(() =>
    list.value.filter((item) => {
      const keyword = filters.value.keyword.trim().toLowerCase()
      const brand = filters.value.brand.trim().toLowerCase()
      const matchesKeyword =
        !keyword ||
        item.id.toLowerCase().includes(keyword) ||
        `${item.brand} ${item.model}`.toLowerCase().includes(keyword) ||
        (item.foregroundPkg || '').toLowerCase().includes(keyword)
      const matchesOnline =
        filters.value.online === 'ALL' ||
        (filters.value.online === 'ONLINE' && item.online) ||
        (filters.value.online === 'OFFLINE' && !item.online)
      const matchesBrand = !brand || (item.brand || '').toLowerCase().includes(brand)
      return matchesKeyword && matchesOnline && matchesBrand
    })
  )

  async function refresh() {
    loading.value = true
    try {
      const response = await getDevices()
      list.value = response.devices
      appStore.setOverview({
        onlineCount: response.overview.onlineCount,
        totalCount: response.devices.length,
        runningCount: appStore.taskRunningCount,
        openAlertCount: appStore.alertOpenCount
      })
    } finally {
      loading.value = false
    }
  }

  async function dispatchReadinessHint(deviceId) {
    await createTask({
      type: 'READINESS_HINT',
      track: 'INTENT',
      devices: [deviceId],
      intent: 'readiness_recover',
      constraints: { deadlineMs: 300000, maxRetries: 1 },
      successCriteria: { uiTextContains: ['Shizuku', '悬浮窗', '键盘'] },
      observability: { snapshotLevel: 'key-steps', logDetail: 'errors_only' },
      safetyRails: { forbidActions: ['payment'], humanApprovalOn: ['risk_high'] },
      priority: 3
    })
  }

  function startPolling() {
    poller.start()
  }

  function stopPolling() {
    poller.stop()
  }

  return {
    list,
    filteredList,
    loading,
    filters,
    refresh,
    startPolling,
    stopPolling,
    dispatchReadinessHint
  }
})
