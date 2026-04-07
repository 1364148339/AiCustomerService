# 设备端失败步骤 Agent 决策上下文一期 Spec

## Why
当前设备端失败步骤的记录主要偏向日志与证据，虽然能够支持人工排障，但还不足以稳定支撑 Agent 对失败进行分类、可恢复性判断、补救建议生成与后续自动化处理。
本期需要在不大幅改动现有事件链路的前提下，为设备端、服务端、前端建立一套最小可用的结构化决策上下文，并同时明确任务拆分、派发方式、字段枚举标准与验收标准，降低实施成本并保证后续可演进。

## What Changes
- 新增“失败步骤 Agent 决策上下文一期”能力，围绕失败步骤定义 6 个核心决策字段
- 约定一期核心字段优先通过设备端事件 `progress` 承载，并由服务端提取到事件主记录中
- 为 `failureCategory`、`actionResult`、`pageType` 建立统一的枚举标准、中文文案和最小语义约束
- 扩展前端任务详情页，在失败事件中展示失败类别、可恢复性、动作结果、页面类型四项摘要信息
- 定义一期最小字段语义、兼容策略、职责边界与非目标，避免各端实现偏差
- 定义按端拆分的实施任务、依赖关系、派发建议与验收清单
- 明确一期能力边界：支持 Agent 分析与指导，不包含自动补救执行闭环
- **BREAKING**：无

## Impact
- Affected specs: 设备事件上报、任务详情展示、失败诊断、Agent 决策输入、联调验收
- Affected code:
  - `external/AutoGLM-For-Android/app/src/main/java/com/kevinluo/autoglm/network/AiMacrodroidService.kt`
  - `backend/aimacrodroid-server/src/main/java/com/aimacrodroid/domain/dto/EventReportReqDTO.java`
  - `backend/aimacrodroid-server/src/main/java/com/aimacrodroid/domain/entity/RunEvent.java`
  - `backend/aimacrodroid-server/src/main/java/com/aimacrodroid/service/impl/RunEventServiceImpl.java`
  - `frontend/aimacrodroid-dashboard/src/mock/api.js`
  - `frontend/aimacrodroid-dashboard/src/views/TaskDetailView.vue`

## ADDED Requirements
### Requirement: 失败步骤 Agent 决策上下文一期
系统 SHALL 为设备端失败步骤提供最小可用的结构化决策上下文，至少覆盖页面状态摘要、动作执行结果、失败分类与可恢复性判断。

#### Scenario: 设备端上报失败步骤决策字段
- **WHEN** 设备端在步骤执行过程中产生失败事件
- **THEN** 事件上报中的 `progress` SHALL 至少包含 `pageType`、`pageSignature`、`targetResolved`、`actionResult`、`recoverable`、`failureCategory` 六个核心字段
- **AND** 原有 `errorCode`、`errorMessage`、`screenshotUrl`、`elements`、`trace`、`thinking`、`progress` 其他字段 SHALL 保持兼容

#### Scenario: 核心决策字段具备最小统一语义
- **WHEN** 各端实现和消费上述六个字段
- **THEN** 字段语义 SHALL 满足以下最小约束：
  - `pageType`：表示当前页面类别，允许未知兜底值
  - `pageSignature`：表示页面摘要标识，用于粗粒度判断页面是否变化或是否处于相同页面
  - `targetResolved`：表示本步骤目标元素或目标对象是否被成功识别
  - `actionResult`：表示动作执行结果，不等价于整体任务状态
  - `recoverable`：表示当前失败是否适合进入补救或重试流程
  - `failureCategory`：表示失败归类，用于前端展示、检索与后续 Agent 决策

#### Scenario: 缺少新增字段时保持兼容
- **WHEN** 服务端收到旧版本设备端上报的失败事件，且 `progress` 中缺少新增字段
- **THEN** 服务端 SHALL 继续接受并保存事件
- **AND** 前端 SHALL 以兼容方式展示，不因为字段缺失导致详情页报错或失败事件不可见

### Requirement: 失败分类枚举标准
系统 SHALL 为 `failureCategory` 提供统一枚举标准，保证设备端生成、服务端存储、前端展示和后续 Agent 消费的一致性。

#### Scenario: failureCategory 使用统一枚举
- **WHEN** 任一端生成或消费 `failureCategory`
- **THEN** 其枚举值 SHALL 仅从以下集合中选取：`ENVIRONMENT`、`PERMISSION`、`NETWORK`、`MODEL`、`ELEMENT`、`PAGE_STATE`、`ACTION_EXECUTION`、`TIMEOUT`、`SENSITIVE_SCREEN`、`DATA`、`UNRECOVERABLE`、`UNKNOWN`
- **AND** 前端 SHALL 将这些枚举值映射为稳定的中文文案

#### Scenario: failureCategory 的一期最小落地范围
- **WHEN** 团队实施一期能力
- **THEN** 至少 SHALL 支持 `ELEMENT`、`PERMISSION`、`TIMEOUT`、`SENSITIVE_SCREEN`、`PAGE_STATE`、`UNKNOWN` 六类取值
- **AND** 未在一期覆盖的值不得阻塞后续扩展或兼容展示

### Requirement: 动作结果枚举标准
系统 SHALL 为 `actionResult` 提供统一枚举标准，用于描述单步动作本身的执行结果。

#### Scenario: actionResult 使用统一枚举
- **WHEN** 任一端生成或消费 `actionResult`
- **THEN** 其枚举值 SHALL 仅从以下集合中选取：`SUCCESS`、`TARGET_NOT_FOUND`、`NO_EFFECT`、`PARTIAL`、`INTERRUPTED`、`INVALID_PARAM`、`BLOCKED`、`SKIPPED`、`UNKNOWN`
- **AND** `actionResult` SHALL 不与任务整体状态或失败分类混用

#### Scenario: actionResult 的一期最小落地范围
- **WHEN** 团队实施一期能力
- **THEN** 至少 SHALL 支持 `SUCCESS`、`TARGET_NOT_FOUND`、`NO_EFFECT`、`INTERRUPTED`、`UNKNOWN` 五类取值

### Requirement: 页面类型枚举标准
系统 SHALL 为 `pageType` 提供统一枚举标准，用于粗粒度描述失败发生时所处的页面类别。

#### Scenario: pageType 使用统一枚举
- **WHEN** 任一端生成或消费 `pageType`
- **THEN** 其枚举值 SHALL 仅从以下集合中选取：`UNKNOWN_PAGE`、`HOME_PAGE`、`DETAIL_PAGE`、`LIST_PAGE`、`SEARCH_PAGE`、`LOGIN_PAGE`、`POPUP_PAGE`、`PERMISSION_PAGE`、`LOADING_PAGE`、`RESULT_PAGE`、`SENSITIVE_PAGE`、`EXTERNAL_PAGE`
- **AND** 前端 SHALL 将这些枚举值映射为稳定的中文文案

#### Scenario: pageType 的一期最小落地范围
- **WHEN** 团队实施一期能力
- **THEN** 至少 SHALL 支持 `HOME_PAGE`、`POPUP_PAGE`、`PERMISSION_PAGE`、`SENSITIVE_PAGE`、`EXTERNAL_PAGE`、`UNKNOWN_PAGE` 六类取值

### Requirement: 可恢复性判断约束
系统 SHALL 对 `recoverable` 的语义给出最小判断约束，以降低各端理解偏差。

#### Scenario: recoverable 语义统一
- **WHEN** 任一端生成或消费 `recoverable`
- **THEN** `recoverable=true` SHALL 表示“当前失败值得进入补救或重试流程，且具备合理成功概率”
- **AND** `recoverable=false` SHALL 表示“继续重试没有意义、存在风险或当前问题需要人工/外部修复”

#### Scenario: recoverable 的一期建议规则
- **WHEN** 一期按规则实现 `recoverable`
- **THEN** 元素未找到但页面仍可继续探索、页面跑偏但仍可回退、网络偶发失败、允许重试的超时场景 SHOULD 取 `true`
- **AND** 权限缺失、环境未就绪且无法自动修复、敏感页命中、参数错误、明确不可恢复失败 SHOULD 取 `false`

### Requirement: 服务端保存并暴露失败步骤决策字段
系统 SHALL 在保留原始事件数据的同时，将可检索的核心决策字段保存到事件主记录，并通过查询接口返回。

#### Scenario: 服务端保存失败步骤决策字段
- **WHEN** 服务端接收到包含决策字段的事件上报
- **THEN** 服务端 SHALL 保留原始 `progress`
- **AND** 将 `failureCategory`、`recoverable`、`actionResult`、`pageType`、`pageSignature` 冗余保存到事件主记录，以支持查询、筛选与前端展示

#### Scenario: 查询接口返回决策字段
- **WHEN** 前端或后续 Agent 查询任务相关事件
- **THEN** 事件查询结果 SHALL 直接包含上述核心决策字段
- **AND** 调用方不应被迫每次自行解析原始 `progressJson` 才能消费这些字段

### Requirement: 前端展示失败步骤决策摘要
系统 SHALL 在任务详情中展示失败步骤的结构化决策摘要，提升人工排障效率并为后续 Agent 接入提供一致展示入口。

#### Scenario: 前端展示失败事件决策摘要
- **WHEN** 用户在任务详情查看失败事件
- **THEN** 前端 SHALL 展示失败类别、可恢复性、动作结果、页面类型四项摘要信息
- **AND** 保留原有截图、错误码、轨迹、思考、证据等诊断信息

#### Scenario: 前端展示语义清晰
- **WHEN** 前端渲染决策字段
- **THEN** 枚举值 SHALL 映射为明确的中文展示文案
- **AND** 字段缺失时 SHALL 使用兼容占位，而不是展示异常或中断渲染

### Requirement: 一期任务拆分与派发
系统 SHALL 为该变更提供按端拆分的实施任务，明确设备端、服务端、前端和联调验收的职责边界、依赖关系和交付物。

#### Scenario: 任务可直接派发给开发
- **WHEN** 开发负责人查看任务列表
- **THEN** 能够按设备端、服务端、前端、联调四类直接分配任务
- **AND** 能识别每项任务的输入、输出、依赖关系与验收方式

### Requirement: 一期开发结果验收
系统 SHALL 为该变更提供可执行的验收清单，覆盖字段上报、落库、查询返回、前端展示、典型失败场景联调和枚举值一致性校验。

#### Scenario: 典型失败场景验收通过
- **WHEN** 联调元素未找到、权限缺失、执行超时、敏感页命中等典型失败场景
- **THEN** 验收结果 SHALL 能确认字段链路完整、语义稳定、页面展示正确，并可作为后续 Agent 的输入

#### Scenario: 元素未找到场景联调样例
- **GIVEN** 设备端执行点击、输入或选择类步骤时，目标元素不存在、定位失败或页面内容未出现目标控件
- **WHEN** 任务详情查询该失败事件
- **THEN** 至少应观察到 `failureCategory=ELEMENT`
- **AND** `recoverable=true`
- **AND** `actionResult=TARGET_NOT_FOUND`
- **AND** `pageType` 为实际页面类型或 `UNKNOWN_PAGE`
- **AND** 前端失败决策摘要展示“失败类别=元素问题”“动作结果=目标未找到”

#### Scenario: 权限缺失场景联调样例
- **GIVEN** 设备端因悬浮窗、无障碍、Shizuku、输入法或其他权限未就绪而失败
- **WHEN** 任务详情查询该失败事件
- **THEN** 至少应观察到 `failureCategory=PERMISSION`
- **AND** `recoverable=false`
- **AND** `pageType=PERMISSION_PAGE` 或与当前权限引导页语义一致的页面类型
- **AND** 前端失败决策摘要展示“失败类别=权限问题”“可恢复=不可恢复”

#### Scenario: 执行超时场景联调样例
- **GIVEN** 设备端在步骤执行、模型等待或页面加载过程中超时
- **WHEN** 任务详情查询该失败事件
- **THEN** 至少应观察到 `failureCategory=TIMEOUT`
- **AND** `actionResult=INTERRUPTED` 或与超时语义一致的动作结果
- **AND** `recoverable` 与超时是否允许重试的策略保持一致
- **AND** 前端失败决策摘要展示“失败类别=执行超时”

#### Scenario: 敏感页命中场景联调样例
- **GIVEN** 设备端检测到隐私页、敏感页或受保护页面而停止继续执行
- **WHEN** 任务详情查询该失败事件
- **THEN** 至少应观察到 `failureCategory=SENSITIVE_SCREEN`
- **AND** `recoverable=false`
- **AND** `pageType=SENSITIVE_PAGE`
- **AND** 前端同时可见原有“敏感页面：已检测”和失败决策摘要中的对应语义

#### Scenario: 联调执行步骤最小清单
- **WHEN** 团队执行一期联调验收
- **THEN** SHOULD 按以下步骤完成：
  1. 触发对应失败场景并记录任务 ID、设备 ID、步骤号
  2. 在服务端确认事件主记录和原始 `progress` 中均可观察到目标字段
  3. 通过事件查询接口确认核心字段已直接返回
  4. 在前端任务详情页确认失败决策摘要、中文文案与原始错误信息一致
  5. 将结果回填到 `checklist.md` 和验收记录中

#### Scenario: 联调操作清单与样例事件模板
- **WHEN** 团队需要执行最小闭环联调
- **THEN** SHOULD 以同一类事件模板作为设备端、服务端、前端三方对齐基准
- **AND** 样例事件模板至少包含 `taskId`、`stepNo`、`errorCode`、`errorMessage`、`failureCategory`、`recoverable`、`actionResult`、`pageType`、`pageSignature`
- **AND** 每个模板都应能映射到 `checklist.md` 中的一条未完成验收项

#### Scenario: 元素未找到样例事件模板
- **GIVEN** 设备端在点击或输入动作前无法定位目标控件
- **THEN** 可使用如下最小样例作为联调基准：
  ```json
  {
    "status": "FAIL",
    "errorCode": "ELEMENT_NOT_FOUND",
    "errorMessage": "target element not found",
    "progress": {
      "stepNo": 3,
      "pageType": "DETAIL_PAGE",
      "pageSignature": "DETAIL_PAGE|step-3|target-element-not-found",
      "targetResolved": false,
      "actionResult": "TARGET_NOT_FOUND",
      "recoverable": true,
      "failureCategory": "ELEMENT"
    }
  }
  ```

#### Scenario: 权限缺失样例事件模板
- **GIVEN** 设备端因权限能力未准备好而中断执行
- **THEN** 可使用如下最小样例作为联调基准：
  ```json
  {
    "status": "FAIL",
    "errorCode": "OVERLAY_NOT_GRANTED",
    "errorMessage": "overlay permission missing",
    "progress": {
      "stepNo": 1,
      "pageType": "PERMISSION_PAGE",
      "pageSignature": "PERMISSION_PAGE|step-1|overlay-permission-missing",
      "targetResolved": false,
      "actionResult": "BLOCKED",
      "recoverable": false,
      "failureCategory": "PERMISSION"
    }
  }
  ```

#### Scenario: 执行超时样例事件模板
- **GIVEN** 设备端在等待页面、模型响应或步骤执行结果时超时
- **THEN** 可使用如下最小样例作为联调基准：
  ```json
  {
    "status": "FAIL",
    "errorCode": "MODEL_TIMEOUT",
    "errorMessage": "step timeout exceeded",
    "progress": {
      "stepNo": 5,
      "pageType": "LOADING_PAGE",
      "pageSignature": "LOADING_PAGE|step-5|step-timeout-exceeded",
      "targetResolved": false,
      "actionResult": "INTERRUPTED",
      "recoverable": true,
      "failureCategory": "TIMEOUT"
    }
  }
  ```

#### Scenario: 敏感页命中样例事件模板
- **GIVEN** 设备端检测到敏感页后停止继续执行
- **THEN** 可使用如下最小样例作为联调基准：
  ```json
  {
    "status": "FAIL",
    "errorCode": "SENSITIVE_SCREEN_BLACKOUT",
    "errorMessage": "sensitive screen detected",
    "progress": {
      "stepNo": 2,
      "pageType": "SENSITIVE_PAGE",
      "pageSignature": "SENSITIVE_PAGE|step-2|sensitive",
      "targetResolved": false,
      "actionResult": "BLOCKED",
      "recoverable": false,
      "failureCategory": "SENSITIVE_SCREEN"
    }
  }
  ```

#### Scenario: 一期能力边界被清晰约束
- **WHEN** 团队依据本期规格实施
- **THEN** 默认目标 SHALL 为“支持 Agent 分析与指导”
- **AND** 不将自动补救执行、恢复动作闭环、页面前后差异建模、`nextSafeActions` 等能力纳入一期必交范围

## MODIFIED Requirements
### Requirement: 设备事件上报与失败诊断
系统现有的事件上报能力从“日志与证据记录”扩展为“日志、证据与决策上下文并存”。
- SHALL 继续保留错误码、错误信息、截图、元素、轨迹、思考、进度等原有数据
- SHALL 在不破坏现有协议兼容性的前提下，为失败事件增加结构化决策字段
- SHALL 允许前端与后续 Agent 直接消费这些决策字段，而不必每次完全依赖截图或原始轨迹推理
- SHALL 优先复用现有 `progress` 扩展位承载一期新增字段，降低协议与实现改造成本
- SHALL 在一期内优先落地枚举标准的最小取值集合，并为后续扩展保留兼容空间

### Requirement: 一期实现边界
系统一期实现以最小可用闭环为目标。
- SHALL 优先实现六个核心字段的上报、落库、查询返回和前端展示
- SHALL 优先实现 `failureCategory`、`actionResult`、`pageType` 的最小枚举集合、中文文案映射与兼容展示
- SHALL 不要求本期完成自动恢复动作执行、审批流、策略学习、页面前后差异持久化等二期能力
- SHALL 为后续扩展 `goalAchieved`、`nextSafeActions`、恢复动作结果等字段保留兼容空间

## REMOVED Requirements
### Requirement: 无
**Reason**: 本变更为增量增强，不移除现有能力。
**Migration**: 无需迁移；旧事件在缺少新增字段时按兼容逻辑处理。
