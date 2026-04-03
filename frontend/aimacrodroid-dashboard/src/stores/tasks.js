import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createTask, getTaskDetail, getTasks } from '../mock/api'
import { useAppStore } from './app'

export const useTasksStore = defineStore('tasks', () => {
  const appStore = useAppStore()
  const list = ref([])
  const listLoading = ref(false)
  const listFilters = ref({ keyword: '', status: '' })
  const detail = ref(null)
  const detailLoading = ref(false)
  const detailDeviceFilter = ref('')

  const filteredList = computed(() =>
    list.value.filter((item) => {
      const keyword = listFilters.value.keyword.trim().toLowerCase()
      const status = listFilters.value.status
      const matchesKeyword =
        !keyword ||
        item.taskId.toLowerCase().includes(keyword) ||
        (item.taskNo || '').toLowerCase().includes(keyword) ||
        (item.scenarioName || '').toLowerCase().includes(keyword) ||
        (item.scenarioKey || '').toLowerCase().includes(keyword)
      const matchesStatus = !status || item.status === status
      return matchesKeyword && matchesStatus
    })
  )
  const filteredEvents = computed(() => {
    if (!detail.value) return []
    if (!detailDeviceFilter.value) return detail.value.events || []
    return (detail.value.events || []).filter((item) => item.deviceId === detailDeviceFilter.value)
  })

  async function refreshList() {
    listLoading.value = true
    try {
      const tasks = await getTasks()
      list.value = tasks
      appStore.setOverview({
        onlineCount: appStore.deviceOnlineCount,
        totalCount: appStore.deviceTotalCount,
        runningCount: tasks.filter((item) =>
          ['RUNNING', 'DISPATCHING', 'QUEUED'].includes(item.status)
        ).length,
        openAlertCount: appStore.alertOpenCount
      })
    } finally {
      listLoading.value = false
    }
  }

  async function refreshDetail(taskId) {
    if (!taskId) return
    detailLoading.value = true
    try {
      detail.value = await getTaskDetail(taskId)
    } finally {
      detailLoading.value = false
    }
  }

  async function submitTask(payload) {
    return createTask(payload)
  }

  function setDetailDeviceFilter(deviceId) {
    detailDeviceFilter.value = deviceId || ''
  }

  return {
    list,
    filteredList,
    listFilters,
    listLoading,
    detail,
    filteredEvents,
    detailDeviceFilter,
    detailLoading,
    refreshList,
    refreshDetail,
    submitTask,
    setDetailDeviceFilter
  }
})
