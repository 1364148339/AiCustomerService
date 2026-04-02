# Phase 1 前端开发文档

## 1. 文档目标
- 基于 `phase1-design.md` 输出前端可直接落地的开发说明。
- 明确页面职责、接口契约、状态管理、组件划分、交互规则与验收标准。
- 统一术语口径：场景、步骤、任务、运行实例、事件、快照、告警。

## 2. 范围与边界
- 包含：设备页、场景管理页、任务创建页、任务详情页、日志页、告警页。
- 包含：列表轮询、详情轮询、失败定位、截图查看、基础权限控制。
- 不包含：管理端 AI 决策、自动审批流、复杂可视化编排器。

## 3. 技术方案

### 3.1 技术栈
- Vue 3 + TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- 组件库：按项目已有组件库接入

### 3.2 前端目录建议
```text
src/
  api/
    device.ts
    scenario.ts
    task.ts
    log.ts
    alert.ts
  stores/
    device.store.ts
    scenario.store.ts
    task.store.ts
    log.store.ts
    alert.store.ts
  views/
    devices/
    scenarios/
    tasks/
    logs/
    alerts/
  components/
    common/
    scenario/
    task/
    log/
    alert/
  router/
    index.ts
  types/
    api.ts
    domain.ts
```

### 3.3 路由规划
- `/devices`：设备列表与就绪状态
- `/scenarios`：场景列表与步骤编排
- `/tasks/new`：任务创建
- `/tasks/:id`：任务详情与运行时间线
- `/logs`：日志与快照检索
- `/alerts`：告警聚合与定位

## 4. 页面开发说明

### 4.1 设备页 `/devices`
- 展示字段：
  - deviceCode、brand、model、deviceStatus
  - foregroundPkg、batteryPct、networkType
  - isShizukuAvailable、isOverlayGranted、isKeyboardEnabled
  - heartbeatAt
- 交互：
  - 支持按在线状态、品牌、关键字过滤
  - 每 3 秒自动刷新，支持手动刷新
- 重点组件：
  - DeviceTable
  - DeviceReadinessBadge
  - DeviceFilterBar

### 4.2 场景页 `/scenarios`
- 展示字段：
  - scenarioKey、scenarioName、status、versionNo、gmtModified
- 交互：
  - 新增场景仅输入 `scenarioName` 与 `scenarioKey`
  - 进入详情后人工编排步骤（stepNo、stepName、actionCode、actionParams、timeoutMs、retryMax）
  - 支持步骤增删、上下移动、启用/禁用、保存
- 重点组件：
  - ScenarioListTable
  - ScenarioCreateDialog
  - StepEditorTable
  - StepParamsJsonEditor

### 4.3 任务创建页 `/tasks/new`
- 表单字段：
  - scenarioKey（必填）
  - devices[]（必填）
  - priority（必填，默认 5）
  - constraints（可选：deadlineMs、maxRetries）
  - observability（可选：snapshotLevel、logDetail）
- 校验规则：
  - scenarioKey 必选
  - devices 至少 1 台
  - deadlineMs、maxRetries 为非负整数
- 提交成功：
  - 返回 taskId 后跳转 `/tasks/:id`

### 4.4 任务详情页 `/tasks/:id`
- 展示区域：
  - 任务基础信息：taskNo、status、scenarioKey、scenarioName、priority、创建时间
  - 设备运行卡片：每台设备 runStatus、currentStepNo、retryCount、errorCode
  - 时间线：事件状态、耗时、错误、思考摘要
  - 证据区：截图列表、元素快照
- 交互：
  - 默认 3 秒轮询任务与运行状态
  - 支持按设备过滤事件
  - 点击失败事件可跳转告警详情

### 4.5 日志页 `/logs`
- 查询条件：
  - taskId、deviceId、时间范围、eventStatus、errorCode
- 展示内容：
  - 事件列表、traceJson、thinkingText、screenshotUrl
  - 快照元素信息（elementJson）
- 能力：
  - 支持导出当前筛选结果

### 4.6 告警页 `/alerts`
- 查询条件：
  - taskId、alertLevel、alertType、alertStatus、时间范围
- 展示字段：
  - alertNo、taskId、runId、stepInstanceId、alertLevel、alertType、errorCode、firstOccurAt、lastOccurAt
- 交互：
  - 支持 ACK / CLOSED
  - 支持跳转至任务详情失败节点

## 5. 接口契约（前端消费）

### 5.1 场景接口
- `GET /api/scenarios`
  - 响应字段：scenarioId、scenarioKey、scenarioName、status、versionNo、updatedAt
- `POST /api/scenarios`
  - 请求字段：scenarioName、scenarioKey、description
  - 响应字段：scenarioId、scenarioKey、scenarioName、status
- `GET /api/scenarios/:key`
  - 响应字段：场景信息 + steps[]
- `PUT /api/scenarios/:key/steps`
  - 请求字段：steps[{stepId?,orderNo,stepName,action,params,timeoutMs,retryPolicy,enabled}]
  - 响应字段：ok=true

### 5.2 任务接口
- `POST /api/tasks`
  - 请求字段：
    - scenarioKey
    - devices[]
    - priority
    - constraints{deadlineMs,maxRetries}
    - observability{snapshotLevel,logDetail}
  - 响应字段：taskId、status、scenarioKey、scenarioName
- `GET /api/tasks/:taskId`
  - 响应字段：
    - task 基础信息
    - taskDeviceRuns[]
    - aggregates{total,success,fail,running,pending}
    - stepSummary{totalSteps,currentStep}

### 5.3 设备接口
- `GET /api/devices/:id/status`
- `GET /api/devices/:id/readiness`

### 5.4 日志与告警接口
- `GET /api/logs?taskId=...&deviceId=...`
- `GET /api/alerts?taskId=...`

## 6. 前端类型定义建议
```ts
export interface Scenario {
  scenarioId: string
  scenarioKey: string
  scenarioName: string
  description?: string
  status: 'DRAFT' | 'ACTIVE' | 'DEPRECATED'
  versionNo: number
  updatedAt: string
}

export interface ScenarioStep {
  stepId?: string
  scenarioKey: string
  orderNo: number
  stepName: string
  action: string
  params: Record<string, unknown>
  timeoutMs: number
  retryPolicy: { maxRetries: number; backoffMs: number }
  enabled: boolean
}

export interface TaskCreatePayload {
  scenarioKey: string
  devices: string[]
  priority: number
  constraints?: { deadlineMs?: number; maxRetries?: number }
  observability?: { snapshotLevel?: string; logDetail?: string }
}
```

## 7. 状态管理设计（Pinia）
- `useScenarioStore`
  - state：scenarioList、scenarioDetail、stepsDraft、loading、error
  - actions：fetchScenarios、createScenario、fetchScenarioDetail、saveSteps
- `useTaskStore`
  - state：taskDetail、taskRuns、taskPolling、createSubmitting
  - actions：createTask、fetchTaskDetail、startTaskPolling、stopTaskPolling
- `useDeviceStore`
  - state：deviceList、filter、polling
  - actions：fetchDevices、startPolling、stopPolling
- `useLogStore` / `useAlertStore`
  - state：query、rows、pagination、loading
  - actions：search、ackAlert、closeAlert

## 8. 轮询与性能策略
- 默认轮询周期：3 秒。
- 页面不可见时暂停轮询，恢复可见后立即拉取一次。
- 日志与截图按需加载，列表分页 + 图片懒加载。
- 大列表统一虚拟滚动或分页，避免一次渲染过多节点。

## 9. 错误处理与可观测性
- 错误分层：
  - 网络错误（超时/断网）
  - 业务错误（参数非法、状态冲突）
  - 权限错误（401/403）
- 交互规范：
  - 全局错误提示使用统一 Toast
  - 表单错误定位到字段
  - 重试按钮仅在幂等查询场景提供
- 前端日志：
  - 关键操作埋点：创建场景、保存步骤、创建任务、筛选日志、处理告警

## 10. 权限与安全
- 页面级权限：
  - 管理员：全量操作
  - 运营：场景与任务相关操作
  - 只读：仅查看
- 安全要求：
  - 不在前端存储明文敏感信息
  - 不打印 token、签名、隐私字段到控制台
  - 所有写操作携带鉴权头

## 11. 开发里程碑（前端）
- FE-M1：完成 `/devices` 与 `/scenarios` 基础列表。
- FE-M2：完成场景创建与步骤编排。
- FE-M3：完成 `/tasks/new` 与 `/tasks/:id`。
- FE-M4：完成 `/logs` 与 `/alerts`，打通失败定位链路。
- FE-M5：完成联调、回归与文档补齐。

## 12. 验收清单
- 可以创建场景（仅 name/key）并完成步骤编排保存。
- 可以按场景+设备创建任务并跳转详情。
- 任务详情可看到设备运行状态、事件时间线、截图证据。
- 日志与告警页可检索、过滤、跳转定位。
- 列表/详情轮询稳定，无明显内存泄漏与重复请求。
