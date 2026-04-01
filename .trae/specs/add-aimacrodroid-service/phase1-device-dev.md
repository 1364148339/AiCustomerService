# Phase 1 设备端开发文档

## 1. 文档目标
- 基于 `phase1-design.md` 输出设备端可直接落地的开发说明。
- 明确设备端模块职责、通信协议、步骤执行机制、容错与安全策略。
- 保证与当前业务口径一致：设备端使用 AutoGLM 内置 AI 决策能力执行，并回传可审计证据。

## 2. 范围与边界
- 包含：设备注册、心跳上报、步骤执行、事件回传、截图快照、错误码上报、签名鉴权。
- 包含：本地任务执行状态机、AI 决策执行闭环、重试与超时控制、关键日志记录。
- 包含：自然语言任务执行与“截图 -> 模型分析 -> 动作执行”循环能力。
- 不包含：多 Agent 分层研究方案的完整产品化落地，仅保留扩展接口。

## 3. 设备端架构

### 3.1 模块划分
- `device-registry`：注册与鉴权 token 管理。
- `heartbeat-agent`：周期心跳、就绪状态采集。
- `task-execution-manager`：任务生命周期控制、暂停/继续/取消。
- `phone-agent`：核心 AI 决策循环（截图、推理、动作规划、执行）。
- `model-client`：OpenAI 兼容接口调用，支持多模态与流式响应。
- `screenshot-manager`：截图采集、压缩、敏感页面处理。
- `device-action-manager`：点击、滑动、输入、启动应用等动作执行。
- `command-runtime`：步骤解析、执行调度、重试与超时。
- `event-reporter`：事件与快照上报、失败重传、幂等控制。
- `security-kit`：HMAC 计算、时间窗校验、敏感字段脱敏。
- `local-store`：本地轻量状态缓存（当前任务、当前步骤、待补偿事件）。

### 3.2 线程模型建议
- 主线程：UI 权限检查、系统事件监听。
- 执行线程池：步骤执行、超时监控。
- 上报线程池：事件批量发送与失败重传。
- 心跳线程：固定周期采集状态并发送心跳。

## 4. 运行前置条件
- Android 7.0+。
- Shizuku 可用。
- 悬浮窗权限已授予。
- 输入能力可用（如辅助键盘能力开启）。
- 网络连通，系统时间与服务端时间偏差在允许窗口内。
- 模型服务满足 OpenAI 兼容 `/chat/completions`，支持 `image_url` 输入。

## 5. 与服务端通信协议

### 5.1 注册接口
- `POST /api/devices/register`
- 请求字段：
  - deviceId
  - brand
  - model
  - androidVersion
  - resolution
  - capabilities[]
  - shizukuAvailable
  - overlayGranted
  - keyboardEnabled
- 响应字段：
  - registered
  - token

### 5.2 心跳接口
- `POST /api/devices/heartbeat`
- 请求字段：
  - deviceId
  - foregroundPkg
  - batteryPct
  - networkType
  - charging
  - shizukuAvailable
  - overlayGranted
  - keyboardEnabled
  - capabilities[]
  - sseSupported
- 响应字段：
  - ok

### 5.3 事件回传接口
- `POST /api/devices/:id/events`
- 请求字段：
  - taskId
  - stepId
  - status（RUNNING/SUCCESS/FAIL）
  - timestamp
  - durationMs
  - screenshotUrl
  - errorCode
  - trace[]
  - thinking
  - sensitiveScreenDetected
  - progress
  - hmac
- 响应字段：
  - ok

### 5.4 签名规则
- 参与签名建议字段：
  - `deviceId + taskId + stepId + timestamp + bodyDigest`
- 签名算法：`HMAC-SHA256`。
- 失败处理：
  - 401：立即刷新 token 或重新注册。
  - 5xx：指数退避重试并写入本地待重传队列。

### 5.5 模型通信要求
- 请求协议：OpenAI 兼容 Chat Completions。
- 能力要求：支持图像输入（`image_url`）并输出可执行动作意图。
- 响应模式：支持普通响应与 SSE 流式响应。
- 配置能力：支持多模型切换与连通性检测。

## 6. 步骤执行引擎

### 6.1 输入结构
- 执行输入由服务端下发并在设备端解析为本地结构：
  - taskId
  - runId
  - stepList[]
  - constraints（deadlineMs、maxRetries）

### 6.2 本地状态机
- Run 状态：`PENDING -> RUNNING -> SUCCESS/FAIL`
- Step 状态：`READY -> RUNNING -> SUCCESS/FAIL/SKIPPED`

### 6.3 执行流程
1. 接收运行请求并写入本地 `RUNNING`。
2. 若是步骤任务，按 `stepNo` 升序执行；若是自然语言任务，进入 AI 自主循环。
3. 进入 `截图 -> 模型分析 -> 动作执行 -> 状态判断` 闭环。
4. 每步或关键动作前后上报 `RUNNING` 事件与必要证据。
5. 成功则进入下一步，失败按重试策略重试。
6. 重试耗尽上报 `FAIL`，结束该设备运行。
7. 全部步骤成功上报最终 `SUCCESS`。

### 6.4 超时与重试
- 步骤超时：`timeoutMs`。
- 步骤重试：`retryMax + retryBackoffMs`。
- 任务截止：`constraints.deadlineMs`，超时后立即失败上报。

## 7. 执行动作适配层

### 7.1 动作类型建议
- `open_app`
- `tap`
- `swipe`
- `long_press`
- `input_text`
- `wait`
- `snapshot`
- `assert_condition`

### 7.2 动作适配规范
- 每个动作统一返回：
  - success
  - durationMs
  - traceItem
  - errorCode
  - errorMessage
- 每个动作执行后可按观测策略决定是否抓取快照。
- 动作执行必须可中断，支持暂停/继续/取消。

## 8. 快照与证据链

### 8.1 快照触发时机
- 步骤开始前（可选）。
- 步骤失败时（必须）。
- 关键节点步骤成功后（按观测配置）。

### 8.2 快照内容
- screenshotUrl
- foregroundPkg
- elements[{text,bounds,confidence,role}]
- timestamp

### 8.3 敏感页面处理
- 命中敏感页面时：
  - `sensitiveScreenDetected=true`
  - 图像可降采样或遮罩
  - 仅上报必要证据字段

## 9. 错误码与故障处理

### 9.1 标准错误码
- `SHIZUKU_NOT_RUNNING`
- `OVERLAY_NOT_GRANTED`
- `KEYBOARD_NOT_ENABLED`
- `SENSITIVE_SCREEN_BLACKOUT`
- `API_CONNECTION_FAILED`
- `MODEL_TIMEOUT`
- `STEP_ACTION_UNSUPPORTED`
- `STEP_PARAM_INVALID`

### 9.2 故障恢复策略
- 网络故障：事件落本地队列，恢复后补传。
- 执行故障：按步骤重试策略重试，不跨步骤回滚。
- 进程重启：恢复最近运行上下文，从当前步骤继续或标记失败上报。

## 10. 性能与资源控制
- 心跳默认周期：3 秒。
- 事件上报：关键事件立即上报，非关键日志可批量上报。
- 截图压缩：优先 WebP，控制单图大小。
- 本地队列上限：超限触发淘汰策略并记录告警日志。
- 模型请求超时：可配置并与步骤超时协同控制。

## 11. 安全与合规
- token 仅保存在安全存储。
- HMAC 密钥不写日志、不明文输出。
- 敏感参数脱敏后写日志。
- 所有上报链路强制 HTTPS。

## 12. 设备端日志规范
- 日志等级：DEBUG / INFO / WARN / ERROR。
- 必打日志：
  - 注册结果
  - 心跳发送结果
  - 每步开始/结束
  - 失败原因与错误码
  - 事件上报结果与重试次数
  - 模型请求摘要与响应耗时
- 日志关联键：
  - taskId、runId、stepId、eventId

## 13. 配置项建议
- `heartbeat.interval.ms=3000`
- `event.retry.max=5`
- `event.retry.backoff.ms=1000`
- `step.default.timeout.ms=5000`
- `snapshot.quality=0.7`
- `local.queue.max.size=1000`
- `security.hmac.window.seconds=300`

## 14. 测试与验收

### 14.1 单元测试
- 步骤解析与参数校验。
- 超时与重试策略。
- HMAC 计算与校验。
- 本地队列补偿逻辑。

### 14.2 集成测试
- 注册 -> 心跳 -> 状态查询链路。
- 执行步骤 -> 事件回传 -> 状态聚合链路。
- 网络中断 -> 补传恢复链路。
- 权限缺失错误码链路。

### 14.3 现场验收清单
- 设备能稳定注册并持续心跳。
- 步骤按顺序执行且失败可重试。
- 关键节点有事件与截图证据。
- 故障场景可正确上报错误码并可追溯。

## 15. 交付里程碑（设备端）
- DEV-M1：注册与心跳能力完成。
- DEV-M2：步骤执行引擎与动作适配完成。
- DEV-M3：事件回传与快照能力完成。
- DEV-M4：安全签名、错误恢复与日志链路完成。
- DEV-M5：联调、压测与试运行验收完成。
