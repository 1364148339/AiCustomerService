import axios from 'axios'
import { randomUUID } from './utils'

const api = axios.create({
  baseURL: '/'
})

const localScenarioList = []
const localTaskMeta = new Map()

function unwrap(resp) {
  return resp?.data?.data ?? resp?.data ?? null
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
    TIMEOUT: '执行超时',
    SENSITIVE_SCREEN: '敏感页面拦截',
    DATA: '数据问题',
    UNRECOVERABLE: '不可恢复异常',
    UNKNOWN: '未知'
  }
  return map[value] || '未知'
}

function resolveActionResultText(value) {
  const map = {
    SUCCESS: '成功',
    TARGET_NOT_FOUND: '目标未找到',
    NO_EFFECT: '执行无效',
    PARTIAL: '部分完成',
    INTERRUPTED: '执行中断',
    INVALID_PARAM: '参数无效',
    BLOCKED: '被阻塞',
    SKIPPED: '已跳过',
    UNKNOWN: '未知'
  }
  return map[value] || '未知'
}

function resolvePageTypeText(value) {
  const map = {
    UNKNOWN_PAGE: '未知页面',
    HOME_PAGE: '首页',
    DETAIL_PAGE: '详情页',
    LIST_PAGE: '列表页',
    SEARCH_PAGE: '搜索页',
    LOGIN_PAGE: '登录页',
    POPUP_PAGE: '弹窗页',
    PERMISSION_PAGE: '权限页',
    LOADING_PAGE: '加载页',
    RESULT_PAGE: '结果页',
    SENSITIVE_PAGE: '敏感页',
    EXTERNAL_PAGE: '外部页面'
  }
  return map[value] || '未知页面'
}

function mapEvent(item) {
  const progress = readEventProgress(item)
  const stepNo = Number(item.stepNo || item.currentStepNo || progress.stepNo || 0)
  const eventType = item.eventType || ''
  const eventTypeDesc = item.eventTypeDesc || ''
  const status = item.status || item.eventStatus || 'RUNNING'
  const resultDesc = item.resultDesc || (status === 'SUCCESS'
    ? '执行成功'
    : status === 'FAIL'
      ? '执行失败'
      : '执行中')
  const stepName = item.stepName || ''
  const stageDesc = item.stageDesc || `${stepNo > 0 ? `第${stepNo}步` : '步骤信息缺失'}${stepName ? `（${stepName}）` : ''}：${resultDesc}`
  const failureCategory = item.failureCategory || progress.failureCategory || ''
  const recoverableRaw = item.recoverable ?? progress.recoverable
  const actionResult = item.actionResult || progress.actionResult || ''
  const pageType = item.pageType || progress.pageType || ''
  const pageSignature = item.pageSignature || progress.pageSignature || ''
  const targetResolvedRaw = item.targetResolved ?? progress.targetResolved
  return {
    eventNo: item.eventNo || '',
    taskId: String(item.taskId),
    deviceId: item.deviceId || item.deviceCode || '--',
    stepInstanceId: item.stepInstanceId || null,
    commandId: item.commandId || String(item.stepInstanceId || '--'),
    status,
    eventStatus: status,
    eventType,
    eventTypeDesc: eventTypeDesc || (eventType ? eventType.replaceAll('_', ' ') : '未知事件'),
    timestamp: parseTimestamp(item.eventTimestamp || item.occurredAt || item.gmtCreate),
    thinking: item.thinking || item.thinkingText || '',
    thinkingText: item.thinkingText || item.thinking || '',
    screenshotUrl: item.screenshotUrl || '',
    traceJson: safeJsonStringify(item.traceJson || item.trace || ''),
    elementJson: safeJsonStringify(item.elementJson || item.elements || item.element || ''),
    stepNo,
    stepName,
    resultDesc,
    stageDesc,
    sensitiveScreenDetected: boolFlag(item.sensitiveScreenDetected ?? item.isSensitiveScreen),
    errorCode: item.errorCode || '',
    errorMessage: item.errorMessage || '',
    progress,
    durationMs: item.durationMs || 0,
    failureCategory,
    failureCategoryText: resolveFailureCategoryText(failureCategory),
    recoverable: recoverableRaw === null || recoverableRaw === undefined ? null : boolFlag(recoverableRaw),
    actionResult,
    actionResultText: resolveActionResultText(actionResult),
    pageType,
    pageTypeText: resolvePageTypeText(pageType),
    pageSignature,
    targetResolved: targetResolvedRaw === null || targetResolvedRaw === undefined ? null : boolFlag(targetResolvedRaw)
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
  const req = {
    name: payload.name,
    type: payload.type,
    trackType: payload.track,
    intent: payload.intent,
    targetDeviceIds: payload.targetDeviceIds,
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
  const list = (unwrap(resp) || []).map(normalizeScenario)
  localScenarioList.splice(0, localScenarioList.length, ...list)
  return list
}

export async function getScenarioDetail(scenarioId) {
  const resp = await api.get(`/api/scenarios/${scenarioId}`)
  const raw = unwrap(resp) || {}
  return {
    ...normalizeScenario(raw),
    steps: (raw.steps || []).map(normalizeStep)
  }
}
