import axios from 'axios'

function randomUUID() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

const api = axios.create({
  baseURL: '/'
})

const localScenarioList = []
const localTaskMeta = new Map()

function unwrap(resp) {
  return resp?.data?.data ?? resp?.data ?? null
}

function getListData(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.records)) return value.records
  if (Array.isArray(value?.list)) return value.list
  return []
}

function boolFlag(value) {
  if (typeof value === 'boolean') return value
  if (value === 1 || value === '1' || value === 'true') return true
  return false
}

function toArrayCapabilities(value) {
  if (Array.isArray(value)) return value
  if (!value) return []
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return value.split(',').map((item) => item.trim()).filter(Boolean)
    }
  }
  if (Array.isArray(value?.items)) {
    return value.items
  }
  return []
}

function safeJsonStringify(value) {
  if (!value) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function parseTimestamp(value) {
  if (!value) return new Date().toISOString()
  if (typeof value === 'number') return new Date(value).toISOString()
  return value
}

function resolveTaskConstraints(task) {
  if (task?.constraints) return task.constraints
  if (typeof task?.constraintJson === 'string') {
    try {
      return JSON.parse(task.constraintJson)
    } catch {
      return null
    }
  }
  return task?.constraintJson || null
}

function resolveTaskStatusText(status) {
  switch (status) {
    case 'QUEUED':
      return '排队中'
    case 'DISPATCHING':
      return '派发中'
    case 'RUNNING':
      return '执行中'
    case 'SUCCESS':
      return '执行成功'
    case 'FAIL':
      return '执行失败'
    case 'CANCELED':
      return '已取消'
    default:
      return status || '未知状态'
  }
}

function resolveRunStatus(run) {
  return run?.status || run?.runStatus || 'PENDING'
}

function mapStepInstance(step) {
  return {
    orderNum: Number(step.stepNo || 0),
    commandId: step.commandId || step.stepId || String(step.id || ''),
    action: step.action || step.actionCode || '',
    params: step.params || step.actionParams || {}
  }
}

function readEventProgress(item) {
  return item.progress || item.progressJson || {}
}

function resolveFailureCategoryText(value) {
  const map = {
    ENVIRONMENT: '环境异常',
    PERMISSION: '权限问题',
    NETWORK: '网络异常',
    MODEL: '模型异常',
    ELEMENT: '元素问题',
    PAGE_STATE: '页面状态异常',
    ACTION_EXECUTION: '动作执行异常',
    SYSTEM: '系统异常',
    UNKNOWN: '未知异常'
  }
  return map[value] || value || '未知异常'
}

function mapEvent(item) {
  const progress = readEventProgress(item)
  const screenshotUrl = item.screenshotUrl || progress.screenshotUrl || ''
  return {
    id: String(item.id || randomUUID()),
    taskId: String(item.taskId || ''),
    runId: String(item.runId || ''),
    deviceId: item.deviceId || progress.deviceId || '--',
    stepNo: Number(progress.stepNo || item.stepNo || 0),
    stepInstanceId: String(item.stepInstanceId || progress.stepInstanceId || ''),
    eventType: item.eventType || item.type || 'RUN_EVENT',
    stageDesc: item.stageDesc || progress.stageDesc || item.eventStatus || '--',
    status: item.eventStatus || item.status || 'RUNNING',
    errorCode: item.errorCode || progress.errorCode || '',
    errorMessage: item.errorMessage || progress.errorMessage || '',
    failureCategory: resolveFailureCategoryText(item.failureCategory || progress.failureCategory),
    latencyMs: Number(progress.latencyMs || item.latencyMs || 0),
    modelTokens: Number(progress.modelTokens || item.modelTokens || 0),
    screenshotUrl,
    timestamp: parseTimestamp(item.occurredAt || item.createdAt || item.gmtCreate),
    raw: item
  }
}

function calcTaskMetrics(events, runs) {
  const successCount = events.filter((item) => item.status === 'SUCCESS').length
  const failCount = events.filter((item) => item.status === 'FAIL').length
  const latencyAvg = events.length
    ? Math.round(events.reduce((sum, item) => sum + Number(item.latencyMs || 0), 0) / events.length)
    : 0
  const tokens = events.reduce((sum, item) => sum + Number(item.modelTokens || 0), 0)
  return {
    runCount: runs.length,
    successCount,
    failCount,
    avgLatencyMs: latencyAvg,
    totalModelTokens: tokens
  }
}

function normalizeScenario(item) {
  return {
    id: String(item.id || item.scenarioId || item.scenarioKey || ''),
    scenarioKey: item.scenarioKey || '',
    scenarioName: item.scenarioName || item.name || '',
    description: item.description || '',
    status: item.status || 'DRAFT',
    latestVersion: item.latestVersion ?? item.version ?? 1,
    updatedAt: parseTimestamp(item.gmtModified || item.updatedAt || item.gmtCreate)
  }
}

function normalizeStep(item) {
  return {
    id: String(item.id || item.stepId || randomUUID()),
    stepNo: Number(item.stepNo || item.orderNum || 0),
    stepName: item.stepName || item.name || '',
    actionCode: item.actionCode || item.action || '',
    timeoutMs: Number(item.timeoutMs || 0),
    retryMax: Number(item.retryMax || 0),
    retryBackoffMs: Number(item.retryBackoffMs || 0),
    actionParams: item.actionParams || item.params || {}
  }
}

function summarizeTask(task, runs) {
  const constraints = resolveTaskConstraints(task)
  const scenarioKey = constraints?.scenarioKey || task.scenarioKey || ''
  const scenarioName = constraints?.scenarioName || task.scenarioName || localTaskMeta.get(String(task.id || ''))?.scenarioName || ''
  return {
    id: String(task.id || ''),
    taskNo: task.taskNo || '',
    name: task.name || task.taskNo || '',
    scenarioKey,
    scenarioName,
    status: task.status || 'QUEUED',
    statusText: resolveTaskStatusText(task.status || 'QUEUED'),
    track: task.trackType || '',
    priority: task.priority ?? 5,
    intent: task.intent || '',
    targetDeviceIds: runs.map((item) => String(item.deviceCode || item.deviceId || item.id || '')),
    createdAt: parseTimestamp(task.gmtCreate),
    updatedAt: parseTimestamp(task.gmtModified),
    deviceRuns: runs,
    constraints,
    observability: task.observability || task.observabilityJson || {}
  }
}

export async function getDevices() {
  const [devicesResp, tasksResp] = await Promise.all([api.get('/api/devices'), api.get('/api/tasks')])
  const devices = unwrap(devicesResp) || []
  const tasks = unwrap(tasksResp) || []
  const runningTaskCount = tasks.filter((item) =>
    ['RUNNING', 'DISPATCHING', 'QUEUED'].includes(item.status)
  ).length
  const list = await Promise.all(
    devices.map(async (item) => {
      const deviceId = String(item.deviceId || item.deviceCode || item.id || '')
      const alias = String(item.alias || deviceId)
      const status = String(item.status || item.deviceStatus || '').toUpperCase()
      let readiness = null
      try {
        if (deviceId) {
          const readinessResp = await api.get(`/api/devices/${deviceId}/readiness`)
          readiness = unwrap(readinessResp)
        }
      } catch {
        readiness = null
      }
      return {
        id: deviceId,
        alias,
        displayName: alias || deviceId,
        brand: item.brand || '',
        model: item.model || '',
        androidVersion: item.androidVersion || '--',
        resolution: item.resolution || '--',
        online: status === 'ONLINE',
        lastHeartbeat: parseTimestamp(item.lastHeartbeatTime || item.lastSeenAt || item.gmtModified),
        foregroundPkg: item.foregroundPkg || '',
        batteryPct: item.batteryPct ?? 0,
        charging: boolFlag(item.isCharging ?? item.charging),
        networkType: item.networkType || '--',
        shizukuAvailable: boolFlag(readiness?.shizukuRunning ?? item.shizukuAvailable),
        overlayGranted: boolFlag(readiness?.overlayGranted ?? item.overlayGranted),
        keyboardEnabled: boolFlag(readiness?.keyboardEnabled ?? item.keyboardEnabled),
        lastActivationMethod: readiness?.lastActivationMethod || '--',
        capabilities: toArrayCapabilities(item.capabilities || item.capabilityJson),
        sseSupported: boolFlag(item.sseSupported)
      }
    })
  )
  return {
    devices: list,
    overview: {
      onlineCount: list.filter((item) => item.online).length,
      runningCount: runningTaskCount
    }
  }
}

export async function updateDeviceAlias(deviceId, alias) {
  await api.put(`/api/devices/${deviceId}/alias`, { alias })
  return true
}

export async function getTasks() {
  const resp = await api.get('/api/tasks')
  const taskList = unwrap(resp) || []
  const result = await Promise.all(
    taskList.map(async (task) => {
      try {
        const detailResp = await api.get(`/api/tasks/${task.id}`)
        const detail = unwrap(detailResp)
        return summarizeTask(task, detail?.deviceRuns || [])
      } catch {
        return summarizeTask(task, [])
      }
    })
  )
  return result.sort((a, b) => b.createdAt - a.createdAt)
}

export async function createTask(payload) {
  const taskMeta = {
    scenarioKey: payload.scenarioKey || '',
    scenarioName: localScenarioList.find((item) => item.scenarioKey === payload.scenarioKey)?.scenarioName || ''
  }
  const req = {
    name: payload.name,
    type: payload.type,
    trackType: payload.track,
    intent: payload.intent,
    targetDeviceIds: payload.targetDeviceIds || payload.devices || [],
    priority: payload.priority,
    constraints: payload.constraints,
    commandTemplate: payload.commandTemplate
  }
  const resp = await api.post('/api/tasks', req)
  const data = unwrap(resp) || {}
  const taskId = String(data.taskId || '')
  if (taskId) {
    localTaskMeta.set(taskId, taskMeta)
  }
  return {
    taskId,
    taskNo: data.taskNo || '',
    status: data.status || 'QUEUED',
    statusText: resolveTaskStatusText(data.status || 'QUEUED')
  }
}

export async function cancelTask(taskId) {
  await api.post(`/api/tasks/${taskId}/cancel`)
  return true
}

export async function getTaskDetail(taskId) {
  const [detailResp, eventResp] = await Promise.all([
    api.get(`/api/tasks/${taskId}`),
    api.get('/api/events', { params: { taskId } })
  ])
  const detail = unwrap(detailResp) || {}
  const eventsRaw = unwrap(eventResp) || []
  const runs = (detail.deviceRuns || []).map((run) => ({
    ...run,
    statusText: resolveTaskStatusText(resolveRunStatus(run))
  }))
  const events = eventsRaw.map(mapEvent)
  const stepInstances = (detail.steps || detail.stepInstances || []).map(mapStepInstance)
  const evidences = events
    .filter((item) => item.screenshotUrl)
    .map((item) => ({
      timestamp: item.timestamp,
      deviceId: item.deviceId,
      stepInstanceId: item.stepInstanceId,
      stepNo: item.stepNo,
      screenshotUrl: item.screenshotUrl
    }))
  const alerts = events
    .filter((event) => event.status === 'FAIL' || event.errorCode)
    .map((event) => ({
      id: randomUUID(),
      taskId: String(taskId),
      deviceId: event.deviceId,
      type: toAlertType(event),
      severity: 'HIGH',
      code: event.errorCode || event.eventType,
      message: event.errorMessage || event.stageDesc,
      createdAt: event.timestamp
    }))
  return {
    taskId: String(detail.id || taskId),
    taskNo: detail.taskNo || '',
    name: detail.name || '',
    type: detail.type || '',
    track: detail.trackType || '',
    status: detail.status || 'QUEUED',
    statusText: resolveTaskStatusText(detail.status || 'QUEUED'),
    scenarioKey: resolveTaskConstraints(detail)?.scenarioKey || detail.scenarioKey || '',
    scenarioName: resolveTaskConstraints(detail)?.scenarioName || detail.scenarioName || localTaskMeta.get(String(taskId))?.scenarioName || '',
    priority: detail.priority ?? 5,
    intent: detail.intent || '',
    createdAt: parseTimestamp(detail.gmtCreate),
    stepInstances,
    deviceRuns: runs,
    events,
    evidences,
    alerts,
    metrics: calcTaskMetrics(events, runs)
  }
}

export async function getTaskAlerts(taskId) {
  const detail = await getTaskDetail(taskId)
  return detail.alerts
}

export async function getScenarios() {
  const resp = await api.get('/api/scenarios')
  const list = getListData(unwrap(resp)).map(normalizeScenario)
  localScenarioList.splice(0, localScenarioList.length, ...list)
  return list
}

export async function getScenarioDetail(scenarioId) {
  const resp = await api.get(`/api/scenarios/${scenarioId}`)
  const raw = unwrap(resp) || {}
  const scenario = raw.scenario || raw
  return {
    ...normalizeScenario(scenario),
    steps: (raw.steps || scenario.steps || []).map(normalizeStep)
  }
}

export async function createScenario(payload) {
  const resp = await api.post('/api/scenarios', payload)
  return normalizeScenario(unwrap(resp) || {})
}

export async function saveScenarioSteps(scenarioKey, steps) {
  await api.put(`/api/scenarios/${scenarioKey}/steps`, { steps })
  return true
}

export async function publishScenario(scenarioKey) {
  const resp = await api.post(`/api/scenarios/${scenarioKey}/publish`)
  return normalizeScenario(unwrap(resp) || {})
}

function normalizeAlert(item) {
  return {
    id: String(item.id || item.alertId || randomUUID()),
    taskId: String(item.taskId || ''),
    deviceId: item.deviceId || '--',
    level: item.level || item.severity || 'P2',
    type: item.type || 'TASK_FAILURE',
    code: item.code || item.errorCode || '',
    reason: item.reason || item.message || item.summary || '',
    status: item.status || 'OPEN',
    createdAt: parseTimestamp(item.createdAt || item.gmtCreate)
  }
}

export async function getAlerts(filters = {}) {
  const resp = await api.get('/api/alerts', {
    params: {
      taskId: filters.taskId || undefined
    }
  })
  let list = getListData(unwrap(resp)).map(normalizeAlert)
  if (filters.alertLevel) {
    list = list.filter((item) => item.level === filters.alertLevel)
  }
  if (filters.alertType) {
    list = list.filter((item) => item.type === filters.alertType)
  }
  if (filters.status) {
    list = list.filter((item) => item.status === filters.status)
  }
  return list.sort((a, b) => b.createdAt - a.createdAt)
}

export async function ackAlert(alertId) {
  await api.post(`/api/alerts/${alertId}/ack`)
  return true
}

export async function closeAlert(alertId, reason = '') {
  await api.post(`/api/alerts/${alertId}/close`, null, {
    params: {
      reason: reason || undefined
    }
  })
  return true
}

function toAlertType(event) {
  switch (event.failureCategory) {
    case '权限问题':
      return 'PERMISSION'
    case '网络异常':
      return 'NETWORK'
    case '环境异常':
      return 'ENVIRONMENT'
    default:
      return 'TASK_FAILURE'
  }
}

export async function getLogs(filters = {}) {
  const resp = await api.get('/api/devices/events', {
    params: {
      taskId: filters.taskId || undefined,
      deviceId: filters.deviceId || undefined
    }
  })
  const list = getListData(unwrap(resp)).map(mapEvent)
  return list.sort((a, b) => b.timestamp - a.timestamp)
}
