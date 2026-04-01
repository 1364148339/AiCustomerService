# Modify Device for AiMacrodroid Spec

## Why
当前 AutoGLM-For-Android 作为一个独立的本地手机助手运行，需要将其接入到新设计的 AiMacrodroid 后端服务中。通过设备端的改造，实现设备注册、状态上报、远程任务接收以及执行结果回传，从而支持“签到/任务领奖”等基础流程的远程控制与全程可观测，形成 Phase 1 所要求的最小闭环。

## What Changes
- **新增网络通信层**：实现与 AiMacrodroid 后端的 REST API 交互。
- **设备注册与心跳机制**：设备启动时注册并定时上报心跳（包含电量、前台应用、权限状态等）。
- **任务接收与轮询**：支持从后端拉取分配给本机的任务（原子序列或意图契约）。
- **执行状态与证据回传**：在任务执行的关键节点，上报事件状态（RUNNING/SUCCESS/FAIL）、截图、轨迹和错误码。
- **安全与鉴权**：实现 Token 管理以及事件回传时的 HMAC 签名机制。
- **UI 改造**：在设置页新增 AiMacrodroid 后端地址配置，主页展示连接状态。

## Impact
- Affected specs: 远程设备控制、自动化任务的可观测性。
- Affected code:
  - `network/*` (新增 API 客户端)
  - `service/*` (新增心跳与任务轮询后台服务)
  - `agent/PhoneAgent.kt` (适配远程任务执行与状态流转)
  - `settings/SettingsActivity.kt` (新增后端配置项)

## ADDED Requirements
### Requirement: 设备注册与心跳
系统需要能够在启动时向后端注册，并在运行期间持续发送心跳。
#### Scenario: Success case
- **WHEN** 应用启动并配置了后端地址
- **THEN** 自动调用 `/api/devices/register` 获取 Token，并每隔一定时间调用 `/api/devices/heartbeat` 保持在线状态。

### Requirement: 远程任务执行与回传
系统需要能够接收远程任务，并在执行过程中回传证据。
#### Scenario: Success case
- **WHEN** 后端下发了一个签到任务
- **THEN** 设备端解析任务，交由 `PhoneAgent` 执行，并在点击、滑动等关键步骤后调用 `/api/devices/:id/events` 上报截图与状态，最终上报成功或失败。
