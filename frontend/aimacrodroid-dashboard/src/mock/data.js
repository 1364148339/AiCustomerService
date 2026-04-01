export const devices = [
  {
    id: 'dev-01',
    brand: 'Xiaomi',
    model: '13',
    androidVersion: '14',
    online: true,
    lastHeartbeat: Date.now() - 8_000,
    foregroundPkg: 'com.ss.android.ugc.aweme',
    batteryPct: 78,
    networkType: 'wifi',
    charging: true,
    shizukuAvailable: true,
    overlayGranted: true,
    keyboardEnabled: true,
    capabilities: ['tap', 'swipe', 'input', 'screenshot', 'sse']
  },
  {
    id: 'dev-02',
    brand: 'HONOR',
    model: 'Magic5',
    androidVersion: '13',
    online: true,
    lastHeartbeat: Date.now() - 14_000,
    foregroundPkg: 'com.xingin.xhs',
    batteryPct: 43,
    networkType: '5g',
    charging: false,
    shizukuAvailable: true,
    overlayGranted: true,
    keyboardEnabled: false,
    capabilities: ['tap', 'swipe', 'input', 'screenshot']
  },
  {
    id: 'dev-03',
    brand: 'vivo',
    model: 'X100',
    androidVersion: '14',
    online: false,
    lastHeartbeat: Date.now() - 210_000,
    foregroundPkg: '',
    batteryPct: 66,
    networkType: 'wifi',
    charging: false,
    shizukuAvailable: false,
    overlayGranted: false,
    keyboardEnabled: false,
    capabilities: ['tap', 'swipe', 'screenshot']
  }
]

export const tasks = [
  {
    taskId: 't-1001',
    type: 'CHECKIN',
    track: 'ATOMIC',
    status: 'RUNNING',
    createdAt: Date.now() - 480_000,
    devices: ['dev-01'],
    progress: { success: 0, fail: 0, running: 1 },
    summary: '签到任务进行中'
  },
  {
    taskId: 't-1002',
    type: 'VIDEO_REWARD',
    track: 'INTENT',
    status: 'SUCCESS',
    createdAt: Date.now() - 3_600_000,
    devices: ['dev-02'],
    progress: { success: 1, fail: 0, running: 0 },
    summary: '累计观看 7200s，奖励已到账'
  },
  {
    taskId: 't-1003',
    type: 'CHECKIN',
    track: 'INTENT',
    status: 'FAIL',
    createdAt: Date.now() - 1_200_000,
    devices: ['dev-03'],
    progress: { success: 0, fail: 1, running: 0 },
    summary: '设备未就绪，任务失败'
  }
]

export const taskDetails = {
  't-1001': {
    taskId: 't-1001',
    type: 'CHECKIN',
    track: 'ATOMIC',
    status: 'RUNNING',
    devicesRuns: [
      { deviceId: 'dev-01', status: 'RUNNING', step: 'find_and_tap 签到', retry: 0 }
    ],
    stats: { success: 0, fail: 0, running: 1 },
    events: [
      {
        timestamp: Date.now() - 120_000,
        deviceId: 'dev-01',
        status: 'RUNNING',
        commandId: 'c_open',
        thinking: '打开目标应用',
        sensitiveScreenDetected: false,
        screenshotUrl: 'https://dummyimage.com/240x520/1f2937/fff&text=open_app'
      },
      {
        timestamp: Date.now() - 40_000,
        deviceId: 'dev-01',
        status: 'RUNNING',
        commandId: 'c1',
        thinking: '定位签到按钮并准备点击',
        sensitiveScreenDetected: false,
        screenshotUrl: 'https://dummyimage.com/240x520/0f766e/fff&text=checkin'
      }
    ]
  },
  't-1002': {
    taskId: 't-1002',
    type: 'VIDEO_REWARD',
    track: 'INTENT',
    status: 'SUCCESS',
    devicesRuns: [{ deviceId: 'dev-02', status: 'SUCCESS', step: '任务完成', retry: 0 }],
    stats: { success: 1, fail: 0, running: 0 },
    events: [
      {
        timestamp: Date.now() - 3_200_000,
        deviceId: 'dev-02',
        status: 'RUNNING',
        commandId: 'intent',
        thinking: '执行 long_video_watch，按节奏滑动',
        sensitiveScreenDetected: false,
        screenshotUrl: 'https://dummyimage.com/240x520/4c1d95/fff&text=video_loop'
      },
      {
        timestamp: Date.now() - 2_700_000,
        deviceId: 'dev-02',
        status: 'SUCCESS',
        commandId: 'intent',
        thinking: '达成 watchedDurationMs 目标',
        sensitiveScreenDetected: false,
        screenshotUrl: 'https://dummyimage.com/240x520/166534/fff&text=reward_done'
      }
    ]
  },
  't-1003': {
    taskId: 't-1003',
    type: 'CHECKIN',
    track: 'INTENT',
    status: 'FAIL',
    devicesRuns: [{ deviceId: 'dev-03', status: 'FAIL', step: '设备校验失败', retry: 2 }],
    stats: { success: 0, fail: 1, running: 0 },
    events: [
      {
        timestamp: Date.now() - 1_120_000,
        deviceId: 'dev-03',
        status: 'FAIL',
        commandId: 'intent',
        thinking: 'Shizuku 未运行，无法继续',
        sensitiveScreenDetected: false,
        screenshotUrl: 'https://dummyimage.com/240x520/7f1d1d/fff&text=not_ready'
      }
    ]
  }
}

export const logs = [
  {
    id: 'l-1',
    taskId: 't-1001',
    deviceId: 'dev-01',
    level: 'INFO',
    timestamp: Date.now() - 39_000,
    message: '执行 find_and_tap'
  },
  {
    id: 'l-2',
    taskId: 't-1003',
    deviceId: 'dev-03',
    level: 'ERROR',
    timestamp: Date.now() - 1_100_000,
    message: 'SHIZUKU_NOT_RUNNING'
  },
  {
    id: 'l-3',
    taskId: 't-1002',
    deviceId: 'dev-02',
    level: 'INFO',
    timestamp: Date.now() - 2_700_000,
    message: '任务完成，reward=ok'
  }
]

export const alerts = [
  {
    id: 'a-1',
    taskId: 't-1003',
    deviceId: 'dev-03',
    type: 'READINESS',
    code: 'SHIZUKU_NOT_RUNNING',
    createdAt: Date.now() - 1_100_000,
    status: 'OPEN'
  },
  {
    id: 'a-2',
    taskId: 't-1001',
    deviceId: 'dev-01',
    type: 'RETRY_THRESHOLD',
    code: 'ELEMENT_NOT_FOUND',
    createdAt: Date.now() - 200_000,
    status: 'OPEN'
  }
]
