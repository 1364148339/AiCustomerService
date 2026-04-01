# Phase 1 后端开发文档

## 1. 文档目标

- 基于 `phase1-design.md` 输出后端可直接落地的实现文档。
- 明确模块职责、领域模型、接口契约、调度机制、安全策略与测试方案。
- 保证与当前业务口径一致：场景 + 人工步骤编排 + 任务步骤快照执行。

## 2. 范围与边界

- 包含：设备注册与心跳、场景与步骤管理、任务创建与调度、事件回传、日志检索、告警聚合、审计与权限。
- 包含：PostgreSQL 持久化、任务队列消费、并发与重试控制、HMAC 验签。
- 不包含：管理端 AI 决策引擎、自动审批、复杂策略学习。

## 3. 技术基线

### 3.1 建议技术栈

- Java 17
- Spring Boot 3.x
- Spring Web + Validation
- Spring Security（RBAC）
- MyBatis-Plus
- PostgreSQL 15+
- Redis（队列与幂等键，可选）
- 对象存储（截图与日志引用）

### 3.3 MyBatis-Plus 落地约定

- Mapper 命名：`*Mapper.java`，XML 命名：`*Mapper.xml`。
- 通用字段自动填充：`creator`、`modifier`、`gmt_create`、`gmt_modified`。
- 逻辑删除字段：`is_deleted`，统一使用 MyBatis-Plus 逻辑删除配置。
- 分页查询统一使用 MyBatis-Plus `Page`，避免手写分页 SQL。
- 写操作优先使用 Wrapper 条件更新，避免全表更新风险。

### 3.2 模块划分

- `api-rest`：控制器、DTO、参数校验、统一异常处理。
- `core-domain`：领域对象、状态机、业务规则、聚合逻辑。
- `worker`：任务出队、分发、超时检测、重试退避、告警触发。
- `storage`：Repository、SQL 映射、对象存储客户端。
- `security`：鉴权、权限、签名、审计切面。

## 4. 领域模型与状态机

### 4.1 核心实体

- ScenarioDefinition：场景主数据。
- ScenarioStep：场景步骤定义（人工编排）。
- Task：任务主实体。
- TaskDeviceRun：任务在设备维度的运行实体。
- StepInstance：任务创建时生成的步骤快照。
- RunEvent：设备回传事件流。
- Snapshot：截图与结构化快照。
- Alert：异常告警实体。
- Device / DeviceReadiness：设备基础信息与就绪状态。

### 4.2 状态机

- Task：`QUEUED -> DISPATCHING -> RUNNING -> SUCCESS/FAIL/CANCELED`
- TaskDeviceRun：`PENDING -> RUNNING -> SUCCESS/FAIL`
- Alert：`OPEN -> ACK -> CLOSED`

### 4.3 关键业务规则

- 创建任务必须绑定 `ACTIVE` 场景，且场景至少包含 1 条启用步骤。
- 创建任务时将场景步骤复制为 `StepInstance`，后续执行只依赖快照。
- 任务是否成功以设备运行结果聚合判定：全部成功为 SUCCESS，存在失败为 FAIL。
- 告警由事件聚合器触发，支持重复告警合并更新 `last_occur_at`。

## 5. API 设计（后端实现口径）

### 5.1 设备相关

- `POST /api/devices/register`
  - 入参：deviceId、brand、model、androidVersion、resolution、capabilities\[]、shizukuAvailable、overlayGranted、keyboardEnabled
  - 出参：registered、token
  - 规则：deviceId 幂等注册，重复注册返回同设备记录并更新基础信息
- `POST /api/devices/heartbeat`
  - 入参：deviceId、foregroundPkg、batteryPct、networkType、charging、sseSupported
  - 出参：ok
  - 规则：刷新 `t_device.last_seen_at` 与 `t_device_readiness` 快照
- `GET /api/devices/:id/status`
- `GET /api/devices/:id/readiness`

### 5.2 场景与步骤

- `GET /api/scenarios`
  - 返回场景分页列表
- `POST /api/scenarios`
  - 入参：scenarioName、scenarioKey、description
  - 规则：scenarioKey 唯一；默认状态 DRAFT
- `GET /api/scenarios/:key`
  - 返回场景详情 + 步骤列表
- `PUT /api/scenarios/:key/steps`
  - 入参：steps\[]
  - 规则：stepNo 连续正整数；actionCode 非空；启用步骤至少 1 条

### 5.3 任务与执行

- `POST /api/tasks`
  - 入参：scenarioKey、devices\[]、priority、constraints、observability
  - 处理：
    - 场景校验
    - 生成 taskNo
    - 写入 Task
    - 生成 TaskDeviceRun
    - 复制步骤到 StepInstance
    - 入队
  - 出参：taskId、status、scenarioKey、scenarioName
- `GET /api/tasks/:taskId`
  - 出参：任务主信息、设备运行列表、聚合统计、步骤执行进度

### 5.4 事件、日志、告警

- `POST /api/devices/:id/events`
  - 入参：taskId、stepId、status、timestamp、durationMs、errorCode、trace、thinking、progress、screenshotUrl、hmac
  - 规则：
    - 验签 + 时效校验
    - eventNo 幂等
    - 落库 RunEvent / Snapshot
    - 驱动 TaskDeviceRun 与 Task 聚合状态更新
- `GET /api/logs?taskId=...&deviceId=...`
- `GET /api/alerts?taskId=...`

## 6. 数据落库映射（对应第 8 章）

- `t_scenario` ↔ ScenarioDefinition
- `t_scenario_step` ↔ ScenarioStep
- `t_device` / `t_device_readiness` ↔ Device / DeviceReadiness
- `t_task` ↔ Task
- `t_task_device_run` ↔ TaskDeviceRun
- `t_step_instance` ↔ StepInstance
- `t_run_event` ↔ RunEvent
- `t_snapshot` ↔ Snapshot
- `t_alert` ↔ Alert
- `t_audit_log` ↔ AuditLog

## 7. 调度与执行设计

### 7.1 入队与消费

- 任务创建成功后写入队列（Redis Stream / DB Outbox 均可）。
- worker 按 `priority desc, gmt_create asc` 拉取任务。
- 单设备并发限制为 1，避免同设备任务抢占冲突。

### 7.2 步骤分发策略

- 从 `t_step_instance` 按 `step_no` 升序读取。
- 每步执行前记录 RUNNING 事件。
- 成功继续下一步，失败按 `retry_max/retry_backoff_ms` 重试。
- 重试耗尽则该 run 标记 FAIL，触发告警。

### 7.3 超时控制

- 单步超时：`timeout_ms`。
- 任务超时：`constraints.deadlineMs`。
- 定时任务扫描 RUNNING 超时 run 并补发 FAIL 事件。

## 8. 安全与权限

### 8.1 RBAC

- 管理员：全部资源读写。
- 运营：场景、任务、日志、告警操作。
- 只读：只允许查询。

### 8.2 设备鉴权与签名

- 设备注册后下发 token，服务端仅存 hash。
- 事件回传必须带 HMAC，签名字段建议：
  - `deviceId + taskId + stepId + timestamp + bodyDigest`
- 签名校验失败返回 401，并写审计日志。

### 8.3 审计

- 所有写操作写入 `t_audit_log`。
- 审计字段包含 operatorId、actionCode、bizType、bizId、resultCode。

## 9. 异常码与错误处理

### 9.1 建议错误码

- `INVALID_PARAM`
- `SCENARIO_NOT_FOUND`
- `SCENARIO_NOT_ACTIVE`
- `SCENARIO_STEPS_EMPTY`
- `DEVICE_NOT_FOUND`
- `TASK_NOT_FOUND`
- `RUN_NOT_FOUND`
- `EVENT_DUPLICATE`
- `SIGNATURE_INVALID`
- `SIGNATURE_EXPIRED`
- `STEP_EXEC_TIMEOUT`
- `MAX_RETRY_EXCEEDED`

### 9.2 返回结构

```json
{
  "code": "SCENARIO_NOT_ACTIVE",
  "message": "场景未发布，无法创建任务",
  "requestId": "req-20260401-0001",
  "data": null
}
```

## 10. 配置项建议

- `task.dispatch.device-concurrency=1`
- `task.dispatch.batch-size=100`
- `task.dispatch.poll-interval-ms=1000`
- `task.step.default-timeout-ms=5000`
- `task.step.max-retry-default=1`
- `security.hmac.window-seconds=300`
- `storage.snapshot-url-expire-seconds=3600`

## 11. 可观测性

- 指标：
  - 任务创建成功率
  - 任务完成率
  - 步骤平均耗时
  - 事件入库 TPS
  - 告警触发率
- 日志：
  - API 访问日志
  - 调度日志
  - 验签失败日志
- Trace：
  - 统一 requestId / traceId 贯穿 API、worker、存储层

## 12. 测试与验收

### 12.1 单元测试

- 场景创建与唯一 key 校验
- 步骤编排合法性校验（stepNo 连续、actionCode 非空）
- 任务状态机迁移测试
- 告警聚合策略测试

### 12.2 集成测试

- 设备注册 -> 心跳 -> 状态查询
- 场景创建 -> 步骤保存 -> 任务创建 -> 入队
- 事件回传 -> 状态聚合 -> 告警生成
- 验签失败与权限拒绝路径

### 12.3 回归测试清单

- 任务并发下设备互斥生效
- 任务步骤快照不受场景后续编辑影响
- 日志/告警可按 taskId 追踪全链路

## 13. 交付里程碑（后端）

- BE-M1：设备与场景基础 API
- BE-M2：步骤编排与任务创建链路
- BE-M3：worker 调度与事件回传聚合
- BE-M4：日志与告警能力
- BE-M5：安全、审计、联调与压测

## 14. PostgreSQL 初始化建表脚本

- 脚本路径：`add-aimacrodroid-service/phase1-pg-init.sql`
- 脚本目标：
  - 初始化 Phase 1 全量表结构
  - 不使用外键约束
  - 包含审计字段与中文注释
  - 包含唯一约束、检查约束、索引
- 执行建议：
  - 在全新数据库执行一次
  - 使用具备建表权限的账号执行
