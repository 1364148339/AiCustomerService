# AIMacroDroid 管理服务 Spec

## Why
面向多台 Android 设备执行“签到/任务领奖”等自动化场景，需要集中编排与风控，保证命令原子化、过程可观测、可人工干预，并尽可能降低被检测异常的风险。
同时，为适应快速变化的移动端 UI 与复杂风控环境，系统需要从“集中控制”走向“设备端副驾驶与共治”，以“意图驱动 + 原子执行”双轨并存的架构提升鲁棒性与迭代效率。

## What Changes
- 新增 Java Web 管理服务 AIMacroDroid：Spring Boot 后端 + Vue3 前端
- 设备注册与心跳，统一设备状态视图
- 命令模板与流程封装：将多步流程拆分为“一步就能执行”的原子命令，下发给 AutoGLM For Android
- 任务编排与调度：支持定时、批量、并发控制、重试与回滚
- 执行记录与审计：完整记录过程、结果与轨迹，支持检索与导出
- 人为干预：Web 端对正在运行的任务进行暂停、继续、重试、改参、终止等操作
- 风控与反检测：速率限制、随机化、人类行为特征注入、设备分组隔离、黑白名单
- 与 AutoGLM For Android 的接口契约：命令下发、结果回传、健康监控
- 不改变 AutoGLM For Android 内部执行器，仅约束命令粒度与附加元数据
- **BREAKING**：无
-
- 新增“管理端 AI 决策引擎”：在服务端使用推理能力强的模型进行任务路径选择、失败回退建议与风控策略组合
- 新增“设备端视觉理解模块”：在设备端使用图像理解能力强的模型进行页面/元素识别与场景结构化上报
- 管理端 AI 模型可在低置信度或高风险场景主动申请人工介入，并进入审批队列
-
- 引入“双轨执行模式”：确定性编排（原子命令序列）与意图驱动（意图+约束+成功标准）并存，按场景与风险自动选择
- 引入“边缘规划引擎”：设备端基于视觉理解做局部规划与路径选择，服务端提供边界与成功标准，共治协作
- 引入“非阻塞人机协作”与“调试沙箱回放”：在关键决策点推送建议选项，支持断点单步与即改即测

## Impact
- Affected specs: 设备管理、命令封装、调度、风控、审计与可视化
- Affected code: 新增后端与前端；在 AutoGLM For Android 增加轻量协议适配层

## ADDED Requirements
### Requirement: AIMacroDroid 管理服务
系统 SHALL 提供一个可管理多台 Android 设备的 Web 管理平台，完成流程封装、任务调度、执行记录与人工干预。

#### Scenario: 成功下发与执行
- WHEN 管理员在 Web 前端选择“应用签到”模板并选择设备分组，提交任务
- THEN 后端生成原子命令队列并按策略下发至各设备，设备执行后回传结果，前端显示实时状态与最终报告
-
- WHEN 管理员下发“意图契约”（意图+约束+成功标准）
- THEN 设备端副驾驶生成执行计划（步骤摘要与耗时预估），服务端校验通过后自主执行并按关键节点回传快照与证据

### Requirement: 命令原子化封装
- SHALL 将业务流程拆解为在设备端“一步就能执行完”的命令
- SHALL 定义命令模板、参数与前后置条件，支持复用与版本管理
- SHALL 校验模板，使其在目标设备/目标 App 环境可执行
-
- SHALL 与“意图契约”并存，允许同一任务按策略选择“原子序列”或“意图驱动”轨道执行

### Requirement: 任务执行记录与审计
- SHALL 记录每条命令的下发、开始、结束、耗时、结果、截图/日志快照、失败原因
- SHALL 支持按设备、任务、模板、时间范围检索与导出
-
- SHALL 对“意图驱动”任务记录计划摘要、置信度、关键节点快照与证据，确保可解释与可回溯

### Requirement: 多设备人工干预
- SHALL 前端显示正在执行的任务与设备状态
- SHALL 支持暂停/继续/重试/改参/终止，作用于单设备或设备分组
- SHALL 具备权限控制与操作审计
-
- SHALL 提供“注视与建议”非阻塞协作模式：在关键决策点推送2-3个建议选项，管理员点选后任务继续
- SHALL 提供“调试沙箱回放”：失败时加载截图流与日志，支持断点单步与改参即测，并一键更新模板/意图

### Requirement: 风控与反检测
- SHALL 提供速率限制、随机延迟、操作顺序扰动、夜间/节假日策略
- SHALL 支持设备轮换、IP/代理管理、指纹与风险评分
- SHALL 提供黑白名单、异常自动降级与隔离
- SHALL 合规日志与开关控制，避免暴露敏感信息
-
- SHALL 建立“个体行为画像”：点击区域热力图、滑动曲率、思考时间分布、解锁习惯等，驱动个性化节奏
- SHALL 引入“群体关系模拟”：非同步轻耦合联动的群体节律，降低聚类识别风险

### Requirement: 场景支持
- SHALL 内置“常用 App 签到”与“任务领奖”模板库，支持品牌/版本差异
- SHALL 支持跨 App 的复杂任务编排
- SHALL 面向“不可被检测异常”的目标，提供策略组合与监控看板

### Requirement: 管理端 AI 决策引擎
- SHALL 集成推理能力强的模型（云/本地可插拔），对模板选择、路径切换、等待时长、重试退避、风控策略组合进行决策
- SHALL 为每个建议输出置信度、原因摘要与可解释依据（日志/截图引用），并记录到审计
- SHALL 在置信度低或风险高时，主动申请人工介入并推送到审批队列
-
- SHALL 在“意图驱动”轨道作为战略顾问与教练：跨App全局编排、策略学习与模板分流、异常仲裁与建议

### Requirement: 设备端视觉理解模块
- SHALL 在设备端集成图像理解能力强的模型，对页面进行元素/文本/结构识别
- SHALL 支持生成结构化“场景快照”（元素集合、坐标、文案、前景/弹窗标记），用于管理端比对与决策
- SHALL 支持视觉原子命令，以识别结果驱动 find_and_tap / wait_for_element / assert_screen / check_screen 的准确执行
-
- SHALL 引入“边缘规划引擎”：设备端基于快照做局部规划与路径选择，仅在低置信度或未知场景时上报管理端协作

### Requirement: 人工介入申请与审批
- SHALL 提供由管理端 AI 提交的“人工介入申请”，包含理由、风险等级、建议动作
- SHALL 在前端提供审批流（同意/驳回/改参），并将决策结果回灌至任务执行与审计
-
- SHALL 支持“注视与建议”模式与沙箱回放，降低阻塞式审批带来的延迟与风险峰值

## MODIFIED Requirements
### Requirement: AutoGLM For Android 命令契约
- SHALL 支持接收带唯一 ID 的原子命令（包含超时、重试、优先级、幂等键等元数据）
- SHALL 回传执行状态流与最终结果（运行中/成功/失败/需人工介入），附日志与截图引用
- SHALL 上报设备健康（心跳、版本、能力集、前台包名、电量、网络类型、充电状态、Shizuku 可用性）
- SHALL 在原子命令集内保留“视觉操作”能力（find_and_tap、wait_for_element、assert_screen、check_screen），设备端利用 AutoGLM 视觉理解进行动态定位
- SHALL 允许服务端覆盖规划（按下发序列执行），在失败或未下发时启用设备端本地规划作为兜底
-
- SHALL 新增“场景快照上报”契约：设备端可按请求或在关键节点上报 vision_report（含元素/文本/坐标/截图引用）
- SHALL 支持“人工介入指令”执行状态与原因回传（例如暂停等待审批/继续执行）
-
- SHALL 新增“意图契约”支持：设备端接受意图+约束+成功标准，生成并上报执行计划摘要与预估耗时，获批后执行

## 补充与细化
### 前置校验与金丝雀
- 通过“探测”命令（check_screen/assert_screen）进行入口特征比对，不匹配则走备用路径或延后执行
- 金丝雀设备需覆盖品牌/系统/App 版本等差异；金丝雀成功包含耗时、截图特征上报，服务端评估后再全量
- 备用路径触发条件明确：主路径连续失败 N 次切换备用路径；备用也失败则转人工介入

### 真实设备网络与指纹策略
- 强调使用真实设备的天然住宅/移动网络 IP，不强制代理池；风控重点在操作行为与节奏管理
- 指纹隔离天然存在（IMEI/Android ID/传感器等），避免多设备共用账号或同一 WiFi 下批量同时操作

### 模板与可视化增强
- 提供模板录制功能：人工一次完整操作生成 JSON DSL 模板，支持编辑与版本管理
- 可视化调试：截图流低频刷新、支持断点单步执行与参数在线调整
- 成功率热力图：按设备/品牌/系统/网络等维度汇总模板兼容性
-
- 提供“自然语言生成模板”：管理员以自然语言描述流程，管理端模型生成初始 DSL，并由运营做微调

### 安全与权限
- 设备与业务账号绑定关系强约束：一机一号，避免切换导致风控关联
- 命令下发采用 HMAC 签名，设备端校验签名与时效
- 高风险操作二次确认：服务端推送告警，管理员确认后继续

### 通信层与集成路径
- 与设备端采用 WebSocket 或 MQTT 双向通信，HTTP 轮询作为降级
- 在 AutoGLM For Android 内新增“远程命令监听器”，复用截图、操作、权限模块
- 设备心跳扩展上报“支持的原子命令能力集”以便服务端兼容

### 接口契约示例
命令下发示例：

```json
{
  "commandId": "cmd-123",
  "idempotentKey": "daily-checkin-2026-04-01",
  "action": "find_and_tap",
  "params": {
    "target": "text:签到",
    "timeout": 5000,
    "fallback": "backup_click"
  },
  "retryPolicy": {"maxRetries": 2, "backoffMs": 1000},
  "priority": 5,
  "hmac": "base64-signature"
}
```

结果回传示例：

```json
{
  "commandId": "cmd-123",
  "status": "SUCCESS",
  "durationMs": 820,
  "screenshotUrl": "https://store/snap/cmd-123-1.png",
  "errorCode": null,
  "trace": [{"tap": [320, 560]}, {"wait": 300}],
  "device": {"id": "dev-01", "foregroundPkg": "com.example.app"}
}
```

场景快照上报示例：

```json
{
  "deviceId": "dev-01",
  "snapshotId": "snap-789",
  "timestamp": 1743465600000,
  "elements": [
    {"text": "签到", "bounds": [300, 520, 380, 560], "confidence": 0.94, "role": "button"},
    {"text": "任务", "bounds": [40, 160, 120, 200], "confidence": 0.88, "role": "tab"}
  ],
  "foregroundPkg": "com.example.app",
  "screenshotUrl": "https://store/snap/dev-01-1743465600.png"
}
```

管理端 AI 决策建议示例：

```json
{
  "taskId": "task-456",
  "decision": {
    "action": "switch_path",
    "targetPath": "backup_path_1",
    "reason": "entrance not matched; modal detected",
    "confidence": 0.87
  },
  "requiresHuman": true,
  "riskLevel": "HIGH"
}
```

意图契约下发示例：

```json
{
  "intent": "daily_checkin",
  "constraints": {
    "deadlineMs": 600000,
    "maxRetries": 2,
    "noBackgroundNetworkChange": true
  },
  "successCriteria": {
    "uiTextContains": ["签到成功", "已领取"],
    "evidence": ["screenshot", "toast"]
  },
  "observability": {
    "snapshotLevel": "key-steps",
    "logDetail": "errors_only"
  },
  "safetyRails": {
    "forbidActions": ["payment", "account_switch"],
    "humanApprovalOn": ["risk_high"]
  },
  "hmac": "base64-signature"
}
```

设备端执行计划摘要示例：

```json
{
  "intent": "daily_checkin",
  "plan": [
    {"step": "open_app", "estimateMs": 2000},
    {"step": "navigate_tab_mine", "estimateMs": 1500},
    {"step": "find_and_tap_checkin", "estimateMs": 1200}
  ],
  "totalEstimateMs": 4700,
  "confidence": 0.85
}
```

## MVP 演进路径
- 第一阶段：可靠与透明。意图契约基础版、设备端自主执行、关键截图回传与失败告警、少量设备试跑签到。
- 第二阶段：协作与学习。场景快照上报、管理端 AI 协作建议、非阻塞人机协作、设备行为画像基础参数。
- 第三阶段：生态与智能编排。自然语言生成模板、成功率热力图与画像驱动节奏、跨 App 工作流与群体关系模拟。

## REMOVED Requirements
无
