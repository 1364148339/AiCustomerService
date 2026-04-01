# Tasks

- [x] Task 1: 基础通信层与模型定义
  - [x] SubTask 1.1: 在 `network` 包下定义后端 REST API 接口契约 (Retrofit/OkHttp)。
  - [x] SubTask 1.2: 定义相关的数据模型 (DeviceRegisterReq, HeartbeatReq, TaskDto, EventReq 等)。
  - [x] SubTask 1.3: 实现 Token 管理与 HMAC 签名工具类。

- [x] Task 2: 设备注册与心跳服务
  - [x] SubTask 2.1: 实现 `DeviceManager` 用于收集设备信息 (型号、版本、权限状态、电量等)。
  - [x] SubTask 2.2: 创建后台 Service 或 Worker 负责应用启动时的设备注册。
  - [x] SubTask 2.3: 实现定时心跳上报逻辑，维护设备的在线状态。

- [x] Task 3: 任务轮询与解析机制
  - [x] SubTask 3.1: 实现任务拉取机制 (长轮询或定时拉取 `GET /api/tasks`)。
  - [x] SubTask 3.2: 解析任务 JSON (支持 Phase 1 的原子轨道与意图轨道基础版)。
  - [x] SubTask 3.3: 将远程任务转换为本地 `PhoneAgent` 可执行的指令序列。

- [x] Task 4: 执行引擎适配与事件回传
  - [x] SubTask 4.1: 改造 `PhoneAgent`，在执行任务的关键节点 (开始、截图、点击、结束) 触发回调。
  - [x] SubTask 4.2: 实现事件回传逻辑 (`POST /api/devices/:id/events`)，包含截图上传、执行轨迹与状态。
  - [x] SubTask 4.3: 统一错误码映射 (如 `SHIZUKU_NOT_RUNNING`, `MODEL_TIMEOUT`) 并上报。

- [ ] Task 5: UI 配置与状态展示
  - [ ] SubTask 5.1: 在设置页新增 AiMacrodroid 后端 Base URL 与 Secret Key 配置项。
  - [ ] SubTask 5.2: 在主页或悬浮窗展示当前设备在线状态与当前执行的远程任务信息。
  - [ ] SubTask 5.3: 提供手动重连与解除绑定的功能。

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 3
- Task 5 depends on Task 2 and Task 3
