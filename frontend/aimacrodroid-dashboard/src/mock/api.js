import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000
})

const operatorId = import.meta.env.VITE_OPERATOR_ID || 'frontend-admin'
const operatorRole = String(import.meta.env.VITE_OPERATOR_ROLE || 'ADMIN').toUpperCase()

api.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  if (!config.headers['X-Operator-Id']) {
    config.headers['X-Operator-Id'] = operatorId
  }
  if (!config.headers['X-Operator-Role']) {
    config.headers['X-Operator-Role'] = operatorRole
  }
  return config
})

const allowMockFallback = boolFlag(import.meta.env.VITE_ENABLE_API_FALLBACK)

const localScenarioList = [
  {
    scenarioId: 'sc-001',
    scenarioKey: 'daily_checkin',
    scenarioName: '通用签到',
    description: '打开应用并完成签到',
    status: 'ACTIVE',
    versionNo: 3,
    updatedAt: new Date(Date.now() - 3600_000).toISOString(),
    taskType: 'CHECKIN'
  },
  {
    scenarioId: 'sc-002',
    scenarioKey: 'video_reward',
    scenarioName: '视频领奖',
    description: '浏览视频并触发领奖',
    status: 'ACTIVE',
    versionNo: 2,
    updatedAt: new Date(Date.now() - 7200_000).toISOString(),
    taskType: 'VIDEO_REWARD'
  }
]

const localScenarioSteps = {
  daily_checkin: [
    {
      stepId: 'st-001',
      orderNo: 1,
      stepName: '打开应用',
      action: 'open_app',
      params: { pkg: 'com.ss.android.ugc.aweme' },
      timeoutMs: 5000,
      retryPolicy: { maxRetries: 1, backoffMs: 1000 },
      enabled: true
    },
    {
      stepId: 'st-002',
      orderNo: 2,
      stepName: '定位签到按钮',
      action: 'find_and_tap',
      params: { target: 'text:签到', timeout: 5000 },
      timeoutMs: 8000,
      retryPolicy: { maxRetries: 2, backoffMs: 1000 },
      enabled: true
    }
  ],
  video_reward: [
    {
      stepId: 'st-101',
      orderNo: 1,
      stepName: '打开视频页',
      action: 'open_app',
      params: { pkg: 'com.ss.android.ugc.aweme' },
      timeoutMs: 5000,
      retryPolicy: { maxRetries: 1, backoffMs: 1000 },
      enabled: true
    },
    {
      stepId: 'st-102',
      orderNo: 2,
      stepName: '滑动观看',
      action: 'swipe',
      params: { from: [500, 1500], to: [500, 300], durationMs: 500 },
      timeoutMs: 30000,
      retryPolicy: { maxRetries: 2, backoffMs: 1500 },
      enabled: true
    }
  ]
}

const localTaskMeta = new Map()
const localAlertStatus = new Map()

function unwrap(resp) {
  const payload = resp?.data || {}
  if (payload.code && payload.code !== 'OK') {
    const error = new Error(payload.message || '请求失败')
    error.response = { data: payload }
    throw error
  }
  return payload.data
}

function boolFlag(value) {
  return value === 1 || value === '1' || value === true
}

function parseTimestamp(value) {
  if (!value) return Date.now()
  if (typeof value === 'number') return value
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? Date.now() : parsed
}

function safeJsonStringify(value) {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function toArrayCapabilities(capabilities) {
  if (!capabilities) return []
  if (Array.isArray(capabilities)) return capabilities
  if (Array.isArray(capabilities.items)) return capabilities.items
  if (typeof capabilities === 'object') return Object.keys(capabilities)
  return [String(capabilities)]
}

function toLogLevel(event) {
  if (event.status === 'FAIL') return 'ERROR'
  if (event.errorCode) return 'WARN'
  return 'INFO'
}

function toLogMessage(event) {
  if (event.errorCode) return `错误码: ${event.errorCode}`
  if (event.thinking) return event.thinking
  return `状态变更: ${event.status || 'UNKNOWN'}`
}

function resolveTaskConstraints(task) {
  return task?.constraints || task?.taskConstraints || {}
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

function mapEvent(item) {
  return {
    taskId: String(item.taskId),
    deviceId: item.deviceId || item.deviceCode || '--',
    commandId: item.commandId || String(item.stepInstanceId || '--'),
    status: item.status || item.eventStatus || 'RUNNING',
    timestamp: parseTimestamp(item.eventTimestamp || item.occurredAt || item.gmtCreate),
    thinking: item.thinking || item.thinkingText || '',
    thinkingText: item.thinkingText || item.thinking || '',
    screenshotUrl: item.screenshotUrl || '',
    traceJson: safeJsonStringify(item.traceJson || item.trace || ''),
    elementJson: safeJsonStringify(item.elementJson || item.elements || item.element || ''),
    stepNo: Number(item.stepNo || item.currentStepNo || item.stepInstanceId || 0),
    sensitiveScreenDetected: boolFlag(item.sensitiveScreenDetected ?? item.isSensitiveScreen),
    errorCode: item.errorCode || '',
    progress: item.progress || item.progressJson || {},
    durationMs: item.durationMs || 0
  }
}

function normalizeScenario(raw) {
  return {
    scenarioId: String(raw.scenarioId || raw.id || ''),
    scenarioKey: raw.scenarioKey || '',
    scenarioName: raw.scenarioName || raw.name || '',
    description: raw.description || raw.scenarioDesc || '',
    status: raw.status || 'DRAFT',
    versionNo: Number(raw.versionNo || 1),
    updatedAt: raw.updatedAt || raw.gmtModified || raw.gmtCreate || new Date().toISOString(),
    taskType: raw.taskType || 'CHECKIN'
  }
}

function normalizeStep(step, index) {
  const enabledByLegacy = step.enabled !== false && step.enabled !== 0
  const enabledByBackend = step.isEnabled === undefined ? true : boolFlag(step.isEnabled)
  return {
    stepId: step.stepId || `local-step-${index + 1}`,
    orderNo: Number(step.orderNo || step.stepNo || index + 1),
    stepName: step.stepName || '',
    action: step.action || step.actionCode || '',
    params: step.params || step.actionParams || {},
    timeoutMs: Number(step.timeoutMs || 5000),
    retryPolicy: step.retryPolicy || { maxRetries: Number(step.retryMax || 0), backoffMs: Number(step.retryBackoffMs || 1000) },
    enabled: enabledByLegacy && enabledByBackend
  }
}

function summarizeTask(task, runs) {
  const taskId = String(task.id)
  const meta = localTaskMeta.get(taskId) || {}
  const constraints = resolveTaskConstraints(task)
  const scenarioKey = constraints?.scenarioKey || task?.scenarioKey || meta.scenarioKey || ''
  const scenarioName = constraints?.scenarioName || task?.scenarioName || meta.scenarioName || ''
  const success = runs.filter((r) => resolveRunStatus(r) === 'SUCCESS').length
  const fail = runs.filter((r) => resolveRunStatus(r) === 'FAIL').length
  const running = runs.filter((r) => ['RUNNING', 'PENDING'].includes(resolveRunStatus(r))).length
  return {
    taskId,
    taskNo: task.taskNo || '',
    name: task.name || '',
    type: task.type || '',
    track: task.trackType || '',
    status: task.status || '',
    scenarioKey,
    scenarioName,
    intent: task.intent || '',
    priority: task.priority ?? 5,
    createdAt: parseTimestamp(task.gmtCreate),
    devices: runs.map((r) => r.deviceId),
    progress: { success, fail, running }
  }
}

function toAlertType(event) {
  const code = String(event.errorCode || '')
  if (code.includes('TIMEOUT')) return 'TIMEOUT'
  if (code.includes('NOT_FOUND')) return 'ELEMENT_NOT_FOUND'
  if (code.includes('SHIZUKU') || code.includes('OVERLAY') || code.includes('KEYBOARD')) return 'READINESS'
  return 'TASK_FAILURE'
}

function calcTaskMetrics(events, runs) {
  const latestProgress = {}
  events.forEach((event) => {
    latestProgress[event.deviceId] = event.progress || {}
  })
  const watchedDurationMs = Object.values(latestProgress).reduce(
    (sum, item) => sum + Number(item.watchedDurationMs || 0),
    0
  )
  const itemsCompleted = Object.values(latestProgress).reduce(
    (sum, item) => sum + Number(item.itemsCompleted || 0),
    0
  )
  const alertCount = events.filter((item) => item.status === 'FAIL' || item.errorCode).length
  const retryCount = runs.reduce((sum, item) => sum + Number(item.retryCount || 0), 0)
  return {
    watchedDurationMs,
    itemsCompleted,
    alertCount,
    retryCount
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
  const requestPayload = {
    scenarioKey: payload.scenarioKey,
    devices: Array.isArray(payload.devices) ? payload.devices : [],
    priority: Number(payload.priority ?? 5)
  }
  if (payload.constraints) requestPayload.constraints = payload.constraints
  if (payload.observability) requestPayload.observability = payload.observability
  const resp = await api.post('/api/tasks', requestPayload)
  const data = unwrap(resp) || {}
  const taskId = String(data.taskId || '')
  if (taskId) {
    localTaskMeta.set(taskId, taskMeta)
  }
  return {
    taskId,
    taskNo: data.taskNo || '',
    status: data.status || 'QUEUED',
    scenarioKey: taskMeta.scenarioKey,
    scenarioName: taskMeta.scenarioName
  }
}

export async function getTaskDetail(taskId) {
  const [detailResp, eventsResp] = await Promise.all([
    api.get(`/api/tasks/${taskId}`),
    api.get('/api/devices/events')
  ])
  const detail = unwrap(detailResp)
  if (!detail?.task) return null
  const constraints = resolveTaskConstraints(detail.task)
  const events = (unwrap(eventsResp) || [])
    .filter((event) => String(event.taskId) === String(taskId))
    .map(mapEvent)
    .sort((a, b) => b.timestamp - a.timestamp)
  const runs = detail.deviceRuns || []
  const evidences = events
    .filter((item) => item.screenshotUrl)
    .map((item) => ({
      taskId: item.taskId,
      deviceId: item.deviceId,
      stepNo: item.stepNo,
      timestamp: item.timestamp,
      screenshotUrl: item.screenshotUrl
    }))
  const elementSnapshots = events
    .filter((item) => item.elementJson)
    .map((item) => ({
      taskId: item.taskId,
      deviceId: item.deviceId,
      stepNo: item.stepNo,
      timestamp: item.timestamp,
      elementJson: item.elementJson
    }))
  return {
    taskId: String(detail.task.id),
    taskNo: detail.task.taskNo || '',
    name: detail.task.name || '',
    type: detail.task.type || '',
    track: detail.task.trackType || '',
    status: detail.task.status || '',
    scenarioKey: constraints?.scenarioKey || detail.task.scenarioKey || localTaskMeta.get(String(detail.task.id))?.scenarioKey || '',
    scenarioName:
      constraints?.scenarioName || detail.task.scenarioName || localTaskMeta.get(String(detail.task.id))?.scenarioName || '',
    priority: detail.task.priority ?? 5,
    intent: detail.task.intent || '',
    createdAt: parseTimestamp(detail.task.gmtCreate),
    constraints,
    successCriteria: detail.task.successCriteria || {},
    observability: detail.task.observability || {},
    safetyRails: detail.task.safetyRails || {},
    rhythm: detail.task.rhythm || {},
    devicesRuns: runs.map((run) => ({
      deviceId: run.deviceId,
      status: resolveRunStatus(run),
      retry: run.retryCount || 0,
      errorCode: run.errorCode || '',
      errorMessage: run.errorMessage || '',
      progress: run.progress || {}
    })),
    commands: (detail.commands || detail.stepInstances || []).map(mapStepInstance),
    events,
    evidences,
    elementSnapshots,
    metrics: calcTaskMetrics(events, runs)
  }
}

export async function getEvents() {
  const resp = await api.get('/api/devices/events')
  return (unwrap(resp) || []).map(mapEvent).sort((a, b) => b.timestamp - a.timestamp)
}

export async function getLogs({ taskId = '', deviceId = '' } = {}) {
  const eventStatus = arguments[0]?.eventStatus || ''
  const errorCode = arguments[0]?.errorCode || ''
  const startAt = Number(arguments[0]?.startAt || 0)
  const endAt = Number(arguments[0]?.endAt || 0)
  const events = await getEvents()
  return events
    .filter((item) => (!taskId || item.taskId === String(taskId)) && (!deviceId || item.deviceId === deviceId))
    .filter((item) => (!eventStatus || item.status === eventStatus) && (!errorCode || (item.errorCode || '').includes(errorCode)))
    .filter((item) => (!startAt || item.timestamp >= startAt) && (!endAt || item.timestamp <= endAt))
    .map((item) => ({
      timestamp: item.timestamp,
      taskId: item.taskId,
      deviceId: item.deviceId,
      level: toLogLevel(item),
      code: item.errorCode,
      message: toLogMessage(item),
      status: item.status,
      commandId: item.commandId,
      thinkingText: item.thinkingText || item.thinking || '',
      screenshotUrl: item.screenshotUrl,
      traceJson: item.traceJson || '',
      elementJson: item.elementJson || ''
    }))
}

function toAlertLevel(alertType) {
  if (alertType === 'TIMEOUT') return 'P2'
  if (alertType === 'READINESS') return 'P3'
  return 'P1'
}

export async function getAlerts({ taskId = '', alertLevel = '', alertType = '', alertStatus = '', startAt = 0, endAt = 0 } = {}) {
  const events = await getEvents()
  return events
    .filter((item) => item.status === 'FAIL' || item.errorCode)
    .filter((item) => !taskId || item.taskId === String(taskId))
    .map((item) => {
      const id = `${item.taskId}-${item.deviceId}-${item.timestamp}`
      const type = toAlertType(item)
      return {
      id,
      createdAt: item.timestamp,
      taskId: item.taskId,
      deviceId: item.deviceId,
      type,
      level: toAlertLevel(type),
      code: item.errorCode || 'UNKNOWN_ERROR',
      status: localAlertStatus.get(id) || 'OPEN',
      reason: item.thinking || '执行阶段异常'
      }
    })
    .filter((item) => (!alertLevel || item.level === alertLevel) && (!alertType || item.type === alertType))
    .filter((item) => !alertStatus || item.status === alertStatus)
    .filter((item) => (!startAt || item.createdAt >= startAt) && (!endAt || item.createdAt <= endAt))
}

export async function ackAlert(alertId) {
  localAlertStatus.set(alertId, 'ACK')
  return { ok: true }
}

export async function closeAlert(alertId) {
  localAlertStatus.set(alertId, 'CLOSED')
  return { ok: true }
}

export async function getScenarios() {
  try {
    const resp = await api.get('/api/scenarios')
    const data = unwrap(resp) || []
    const list = Array.isArray(data) ? data : data.records || []
    return list.map(normalizeScenario)
  } catch {
    if (!allowMockFallback) throw new Error('获取场景失败，请检查后端服务连接')
    return localScenarioList.map((item) => ({ ...item }))
  }
}

export async function createScenario(payload) {
  try {
    const resp = await api.post('/api/scenarios', {
      scenarioName: payload.scenarioName,
      scenarioKey: payload.scenarioKey,
      description: payload.description || ''
    })
    return normalizeScenario(unwrap(resp) || {})
  } catch {
    if (!allowMockFallback) throw new Error('创建场景失败，请检查后端服务连接')
    const scenario = {
      scenarioId: `sc-${Date.now()}`,
      scenarioKey: payload.scenarioKey,
      scenarioName: payload.scenarioName,
      description: payload.description || '',
      status: 'DRAFT',
      versionNo: 1,
      updatedAt: new Date().toISOString(),
      taskType: 'CHECKIN'
    }
    localScenarioList.unshift(scenario)
    localScenarioSteps[scenario.scenarioKey] = []
    return scenario
  }
}

export async function getScenarioDetail(key) {
  try {
    const resp = await api.get(`/api/scenarios/${key}`)
    const detail = unwrap(resp) || {}
    const scenario = normalizeScenario(detail.scenario || detail)
    return {
      ...scenario,
      steps: (detail.steps || []).map(normalizeStep).sort((a, b) => a.orderNo - b.orderNo)
    }
  } catch {
    if (!allowMockFallback) throw new Error('读取场景详情失败，请检查后端服务连接')
    const scenario = localScenarioList.find((item) => item.scenarioKey === key)
    if (!scenario) return null
    return {
      ...scenario,
      steps: (localScenarioSteps[key] || []).map(normalizeStep).sort((a, b) => a.orderNo - b.orderNo)
    }
  }
}

export async function saveScenarioSteps(key, steps) {
  const ordered = steps
    .map((item, index) => normalizeStep({ ...item, orderNo: index + 1 }, index))
    .sort((a, b) => a.orderNo - b.orderNo)
  try {
    const resp = await api.put(`/api/scenarios/${key}/steps`, {
      steps: ordered.map((item) => ({
        stepId: item.stepId,
        stepNo: item.orderNo,
        stepName: item.stepName,
        actionCode: item.action,
        actionParams: item.params,
        timeoutMs: item.timeoutMs,
        retryMax: Number(item.retryPolicy?.maxRetries || 0),
        retryBackoffMs: Number(item.retryPolicy?.backoffMs || 1000),
        enabled: item.enabled
      }))
    })
    unwrap(resp)
  } catch (error) {
    if (error?.response?.data?.code && error?.response?.data?.code !== 'OK') throw error
    if (!allowMockFallback) throw new Error('保存场景步骤失败，请检查后端服务连接')
    localScenarioSteps[key] = ordered
    const scenario = localScenarioList.find((item) => item.scenarioKey === key)
    if (scenario) {
      scenario.versionNo += 1
      scenario.updatedAt = new Date().toISOString()
      scenario.status = 'ACTIVE'
    }
  }
  return { ok: true }
}

export async function publishScenario(key) {
  try {
    const resp = await api.post(`/api/scenarios/${key}/publish`)
    return normalizeScenario(unwrap(resp) || {})
  } catch {
    if (!allowMockFallback) throw new Error('发布场景失败，请检查后端服务连接')
    const scenario = localScenarioList.find((item) => item.scenarioKey === key)
    if (!scenario) throw new Error('场景不存在')
    scenario.status = 'ACTIVE'
    scenario.versionNo = Number(scenario.versionNo || 0) + 1
    scenario.updatedAt = new Date().toISOString()
    return { ...scenario }
  }
}
