# Phase 1 设计文档（可靠与透明）

## 目标与范围

* 目标：在少量金丝雀设备上跑通“签到/任务领奖”基础流程，确保可靠执行与全程可观测，形成可回归的最小闭环。

* 范围：设备注册/心跳、任务创建与下发（原子序列或意图契约基础版）、关键步骤截图与日志回传、任务状态看板与失败告警、基础安全与权限。

## 端到端流程（概要）

* 管理员在前端创建“签到”任务（选择设备/分组、轨道：原子序列或意图契约、参数）。

* 后端校验并入队，按设备并发限制进行下发。

* 设备端接收指令（命令或意图契约），执行并按关键节点回传事件、截图与结果。

* 后端聚合状态并写入审计，前端看板轮询展示进度与证据；失败触发告警。

## 与 AutoGLM For Android 的对齐

* 系统要求与依赖

  * Android 7.0+；必须安装并激活 Shizuku（无线调试/ADB/Root 任一方式）

  * 必须授予悬浮窗权限与启用 AutoGLM Keyboard 以保证文本输入与可视化

* 设备端核心能力映射

  * 截图服务：自动隐藏悬浮窗、WebP 压缩、敏感页面检测

  * 设备操作：点击、滑动、长按、双击、文本输入、启动应用；支持人性化滑动轨迹

  * 模型通信：OpenAI 兼容 API（/chat/completions）、图片理解（image\_url），支持流式响应（SSE）

* 推荐模型配置（Phase 1）

  * Base URL: <https://open.bigmodel.cn/api/paas/v4>

  * Model: autoglm-phone

  * 备选：ModelScope ZhipuAI/AutoGLM-Phone-9B（需满足 OpenAI 兼容与图片输入）

## 后端设计

* 服务模块

  * api-rest：REST 接口层（设备/任务/日志/告警）

  * core-domain：领域模型与状态机

  * worker：调度器与下发管道（并发/重试/退避）

  * storage：持久化与对象存储适配（截图/日志引用）

* API 契约（核心）

  * POST /api/devices/register

    * 请求：deviceId、brand、model、androidVersion、resolution、shizukuAvailable、overlayGranted、keyboardEnabled、capabilities\[]

    * 响应：registered=true、token（用于设备鉴权）

  * POST /api/devices/heartbeat

    * 请求：deviceId、foregroundPkg、batteryPct、networkType、charging、shizukuAvailable、overlayGranted、keyboardEnabled、capabilities\[], sseSupported

    * 响应：ok=true

  * GET /api/devices/:id/status

    * 响应：在线状态、最近心跳、前台包名、能力集、就绪状态（shizuku/overlay/keyboard）

  * GET /api/devices/:id/readiness

    * 响应：shizukuRunning、overlayGranted、keyboardEnabled、lastActivationMethod（wireless/adb/root）

  * POST /api/tasks

    * 请求（原子轨道）：

      ```json
      {
        "type": "CHECKIN",
        "track": "ATOMIC",
        "devices": ["dev-01"],
        "commands": [
          {"commandId":"c1","action":"find_and_tap","params":{"target":"text:签到","timeout":5000},"retryPolicy":{"maxRetries":2,"backoffMs":1000},"idempotentKey":"checkin-2026-04-01"}
        ],
        "priority": 5
      }
      ```

    * 请求（意图轨道，基础版）：

      ```json
      {
        "type": "CHECKIN",
        "track": "INTENT",
        "devices": ["dev-01"],
        "intent": "daily_checkin",
        "constraints": {"deadlineMs":600000,"maxRetries":2},
        "successCriteria": {"uiTextContains":["签到成功","已领取"],"evidence":["screenshot","toast"]},
        "observability": {"snapshotLevel":"key-steps","logDetail":"errors_only"},
        "safetyRails": {"forbidActions":["payment"],"humanApprovalOn":["risk_high"]},
        "priority": 5
      }
      ```

    * 响应：taskId、status=QUEUED

  * GET /api/tasks/:taskId

    * 长时视频任务示例（意图轨道）：

      ```json
      {
        "type": "VIDEO_REWARD",
        "track": "INTENT",
        "devices": ["dev-01"],
        "intent": "long_video_watch",
        "constraints": {
          "deadlineMs": 10800000,
          "maxRetries": 3,
          "minBatteryPct": 50,
          "requireCharging": true
        },
        "successCriteria": {
          "watchedDurationMs": 7200000,
          "evidence": ["screenshot"]
        },
        "observability": {
          "snapshotLevel": "key-steps",
          "snapshotEvery": "5-items",
          "logDetail": "errors_only"
        },
        "safetyRails": {
          "forbidActions": ["payment", "account_switch"],
          "humanApprovalOn": ["risk_high"]
        },
        "rhythm": {
          "staySecondsMin": 20,
          "staySecondsMax": 90,
          "pauseEveryItems": 10,
          "pauseSeconds": [30, 120]
        },
        "priority": 5
      }
      ```

    * 长时视频任务示例（原子轨道）：

      ```json
      {
        "type": "VIDEO_REWARD",
        "track": "ATOMIC",
        "devices": ["dev-01"],
        "commands": [
          {"commandId":"c_open","action":"open_app","params":{"pkg":"com.ss.android.ugc.aweme"},"retryPolicy":{"maxRetries":2,"backoffMs":1000},"idempotentKey":"open-app-aweme"},
          {"commandId":"c_play","action":"play_current","params":{}},
          {"commandId":"c_wait1","action":"wait","params":{"secondsRandom":[20,90]}},
          {"commandId":"c_swipe","action":"swipe_next","params":{"path":"vertical","humanized":true}},
          {"commandId":"c_wait2","action":"wait","params":{"secondsRandom":[10,30]}},
          {"commandId":"c_snap","action":"snapshot","params":{"everyItems":5}}
        ],
        "loop": {"iterations":"untilDeadline","breakOnAlerts":true},
        "retryPolicy": {"maxRetries": 2, "backoffMs": 1000},
        "priority": 5
      }
      ```

    * 响应：任务基本信息、设备子任务进度、成功/失败统计、关键事件摘要

  * POST /api/devices/:id/events

    * 请求：事件回传（执行状态流/截图/错误码/轨迹摘要）

      ```json
      {
        "taskId":"t-123",
        "commandId":"c1",
        "status":"RUNNING",
        "timestamp":1743465600000,
        "screenshotUrl":"https://store/snap/c1-1.png",
        "errorCode":null,
        "trace":[{"tap":[320,560]},{"wait":300}],
        "thinking":"locate checkin button by text",
        "sensitiveScreenDetected":false,
        "hmac":"base64-signature"
        "progress":{"watchedDurationMs":3600000,"itemsCompleted":120,"lastAction":"swipe_next"},
      }
      ```

    * 响应：ok=true

  * GET /api/logs?taskId=...\&deviceId=...

    * 响应：事件时间线、截图/日志引用

    * 响应：事件时间线、截图/日志引用

  * GET /api/alerts?taskId=...

    * 响应：失败/超时/重试超阈值的告警列表

* 数据模型（核心表/集合）

  * Device（设备信息与能力集）

  * Task（任务主表：轨道、参数、优先级、状态）

  * TaskDeviceRun（任务-设备维度的运行态）

  * CommandInstance（仅原子轨道使用）

  * RunEvent（事件时间线：RUNNING/SUCCESS/FAIL）

  * Snapshot（截图/OCR/元素结构化引用）

  * DeviceReadiness（Shizuku/Overlay/Keyboard 就绪状态与最近变更）

* 状态机（简化）

  * Task：QUEUED → DISPATCHING → RUNNING → SUCCESS/FAIL/CANCELED

  * TaskDeviceRun：PENDING → RUNNING → SUCCESS/FAIL

  * 重试退避：指数退避且受 constraints.maxRetries 控制

* 安全与权限

  * RBAC：管理员/运营/只读

  * 设备鉴权：注册发放 token；事件回传需带 hmac（服务端验证）

  * 审计：所有人机操作与关键事件入审计流

  * 敏感页面：设备端敏感页面检测字段随事件回传，服务端仅保存引用与标记

* 配置项（示例）

  * 并发限制：每设备并发=1；分组并发=可配

  * 超时：命令默认超时、任务整体截止 deadlineMs

  * 存储：截图与日志对象存储前缀，保留期

## 前端设计

* 页面与路由

  * /devices：设备列表与状态（在线/心跳/前台包名/能力集）

  * /tasks/new：创建任务（选择轨道、设备/分组、参数/模板）

  * /tasks/:id：任务详情与进度看板（设备子任务卡片、时间线、证据）

  * /logs：日志与截图检索（按任务/设备过滤）

  * /alerts：失败与超时告警列表

* 组件与状态

  * 设备表格、分组选择器、任务创建表单（轨道切换）、事件时间线、截图查看器

  * 状态管理：Pinia；接口调用：Axios；路由：Vue Router

* 调用约定

  * 列表与详情默认 3s 轮询；支持手动刷新

  * 截图查看器低频加载，支持懒加载与占位

* 错误与告警

  * 前端 toast 展示失败原因摘要；告警页聚合重试超阈、超时、元素未找到等错误码

  * 链接到任务详情的失败节点，便于定位

* 就绪状态展示：设备行显示 Shizuku/悬浮窗/键盘就绪徽标；未就绪允许一键下发提示任务

* 思考与敏感提示：在事件时间线中展示简短“thinking”摘要与敏感页面标记

* 长时视频看板字段：累计观看时长、已切换条数、最近快照时间、当前前台包名、充电与网络状态、告警计数

## 设备端交互契约（Phase 1）

* 心跳结构

  * deviceId、foregroundPkg、batteryPct、networkType、charging、shizukuAvailable、overlayGranted、keyboardEnabled、capabilities\[], sseSupported

* 结果与事件回传

  * taskId、commandId、status（RUNNING/SUCCESS/FAIL）、durationMs、screenshotUrl、errorCode、trace\[]、thinking、sensitiveScreenDetected、progress{watchedDurationMs,itemsCompleted,lastAction}、hmac

* 快照结构（key-steps）

  * elements\[{text,bounds,confidence,role}]、foregroundPkg、screenshotUrl、timestamp

* 安全

  * 设备持有 token；所有事件带 hmac；后端校验签名与时效

* 错误码建议（与设备端常见问题对齐）

  * SHIZUKU\_NOT\_RUNNING、OVERLAY\_NOT\_GRANTED、KEYBOARD\_NOT\_ENABLED、SENSITIVE\_SCREEN\_BLACKOUT、API\_CONNECTION\_FAILED、MODEL\_TIMEOUT

## 成功标准与指标

* 成功率≥既定阈值（如签到场景≥90%）

* 事件完整率：关键节点均有事件与证据

* 告警准确率：失败/超时/重试超阈能被正确识别与聚合

* 金丝雀设备试跑通过：覆盖品牌/版本差异的代表设备

* 就绪率：Shizuku/Overlay/Keyboard 就绪率≥既定阈值；就绪异常可追溯

* 长时任务指标：watchedDurationMs、itemsCompleted、平均停留秒数、滑动次数、告警数、心跳稳定度

## 开发分工与验收

* 后端

  * 实现上述 API、状态机、调度与存储；完成 RBAC 与 HMAC 校验

  * 验收：接口联调用例、集成测试（成功/超时/鉴权失败）

* 前端

  * 实现页面与轮询、时间线与截图浏览、告警聚合

  * 验收：手工用例与基本自动化测试（列表/详情/创建/失败定位）

* 设备端

  * 实现事件回传与快照上报、原子命令执行器或意图契约基础版

  * 验收：对齐契约字段，联调 2 个场景（签到、简单领奖）

## 里程碑

* M1：设备注册/心跳与状态看板

* M2：任务创建与原子轨道下发、事件时间线与截图

* M3：意图契约基础版与关键节点快照

* M4：失败告警与验收通过，试跑报告输出

