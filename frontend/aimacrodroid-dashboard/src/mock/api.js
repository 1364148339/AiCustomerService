import { alerts, devices, logs, taskDetails, tasks } from './data'

function sleep(ms = 220) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function clone(data) {
  return JSON.parse(JSON.stringify(data))
}

export async function getDevices() {
  await sleep()
  return clone(devices)
}

export async function getDeviceStatus(deviceId) {
  await sleep()
  return clone(devices.find((d) => d.id === deviceId) || null)
}

export async function getTasks() {
  await sleep()
  return clone(tasks)
}

export async function createTask(payload) {
  await sleep(300)
  const newTask = {
    taskId: `t-${Math.floor(Math.random() * 9000 + 1000)}`,
    type: payload.type,
    track: payload.track,
    status: 'QUEUED',
    createdAt: Date.now(),
    devices: payload.devices,
    progress: { success: 0, fail: 0, running: 0 },
    summary: payload.track === 'ATOMIC' ? '原子任务已排队' : '意图任务已排队'
  }
  tasks.unshift(newTask)
  taskDetails[newTask.taskId] = {
    ...newTask,
    devicesRuns: payload.devices.map((id) => ({
      deviceId: id,
      status: 'PENDING',
      step: '等待下发',
      retry: 0
    })),
    stats: { success: 0, fail: 0, running: 0 },
    events: []
  }
  return clone({ taskId: newTask.taskId, status: 'QUEUED' })
}

export async function getTaskDetail(taskId) {
  await sleep()
  return clone(taskDetails[taskId] || null)
}

export async function getLogs({ taskId = '', deviceId = '' } = {}) {
  await sleep()
  const filtered = logs.filter(
    (item) => (!taskId || item.taskId === taskId) && (!deviceId || item.deviceId === deviceId)
  )
  return clone(filtered)
}

export async function getAlerts({ taskId = '' } = {}) {
  await sleep()
  const filtered = alerts.filter((item) => !taskId || item.taskId === taskId)
  return clone(filtered)
}
