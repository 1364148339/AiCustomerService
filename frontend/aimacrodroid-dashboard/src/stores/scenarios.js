import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createScenario, getScenarioDetail, getScenarios, saveScenarioSteps } from '../mock/api'
import { createPagePolling } from '../utils/polling'

const POLL_INTERVAL = 3000

function createDefaultStep(index) {
  return {
    stepId: `draft-${Date.now()}-${index}`,
    orderNo: index + 1,
    stepName: '',
    action: '',
    params: {},
    timeoutMs: 5000,
    retryPolicy: { maxRetries: 0, backoffMs: 1000 },
    enabled: true
  }
}

export const useScenariosStore = defineStore('scenarios', () => {
  const list = ref([])
  const loading = ref(false)
  const detail = ref(null)
  const detailLoading = ref(false)
  const stepsDraft = ref([])

  const poller = createPagePolling(refreshScenarios, POLL_INTERVAL)

  const activeCount = computed(() => list.value.filter((item) => item.status === 'ACTIVE').length)

  async function refreshScenarios() {
    loading.value = true
    try {
      list.value = await getScenarios()
    } finally {
      loading.value = false
    }
  }

  async function refreshScenarioDetail(scenarioKey) {
    detailLoading.value = true
    try {
      detail.value = await getScenarioDetail(scenarioKey)
      stepsDraft.value = (detail.value?.steps || []).map((item) => ({
        ...item,
        paramsText: JSON.stringify(item.params || {}, null, 2)
      }))
    } finally {
      detailLoading.value = false
    }
  }

  async function addScenario(payload) {
    const created = await createScenario(payload)
    await refreshScenarios()
    return created
  }

  function appendStep() {
    stepsDraft.value.push(createDefaultStep(stepsDraft.value.length))
  }

  function removeStep(index) {
    stepsDraft.value.splice(index, 1)
    stepsDraft.value = stepsDraft.value.map((item, idx) => ({ ...item, orderNo: idx + 1 }))
  }

  function moveStep(index, direction) {
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= stepsDraft.value.length) return
    const temp = stepsDraft.value[index]
    stepsDraft.value[index] = stepsDraft.value[targetIndex]
    stepsDraft.value[targetIndex] = temp
    stepsDraft.value = stepsDraft.value.map((item, idx) => ({ ...item, orderNo: idx + 1 }))
  }

  async function persistSteps() {
    if (!detail.value?.scenarioKey) return
    const normalized = stepsDraft.value.map((item, index) => {
      let params = {}
      try {
        params = JSON.parse(item.paramsText || '{}')
      } catch {
        params = {}
      }
      return {
        stepId: item.stepId,
        orderNo: index + 1,
        stepName: item.stepName,
        action: item.action,
        params,
        timeoutMs: Number(item.timeoutMs || 0),
        retryPolicy: {
          maxRetries: Number(item.retryPolicy?.maxRetries || 0),
          backoffMs: Number(item.retryPolicy?.backoffMs || 0)
        },
        enabled: !!item.enabled
      }
    })
    await saveScenarioSteps(detail.value.scenarioKey, normalized)
    await refreshScenarioDetail(detail.value.scenarioKey)
    await refreshScenarios()
  }

  function startPolling() {
    poller.start()
  }

  function stopPolling() {
    poller.stop()
  }

  return {
    list,
    loading,
    detail,
    detailLoading,
    stepsDraft,
    activeCount,
    refreshScenarios,
    refreshScenarioDetail,
    addScenario,
    appendStep,
    removeStep,
    moveStep,
    persistSteps,
    startPolling,
    stopPolling
  }
})
