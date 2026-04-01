# 集成 Open-AutoGLM 规范

## Why
当前系统定位已从客服场景调整为手机任务控制 agent，需要稳定的模型能力支撑任务理解与步骤规划。通过接入 Open-AutoGLM Java SDK，可在保留统一路由能力的同时降低调用层维护成本。

## What Changes
- 新增基于 Open-AutoGLM Java SDK 的适配层，支持统一调用与参数映射。
- 新增 Open-AutoGLM 配置项（API 地址、模型名、密钥、超时、重试）。
- 在现有模型路由中加入 Open-AutoGLM 供应方选择逻辑。
- 统一错误处理与降级策略，避免调用失败导致主流程中断。
- 新增最小可观测能力（调用成功率、延迟、错误类型统计）。
- 补充自动化测试与集成验证用例。
- 更新项目定位信息为手机任务控制 agent。

## Impact
- Affected specs: 模型供应方管理、任务规划流程、系统配置管理、可观测性
- Affected code: 模型客户端层、配置加载模块、任务控制服务、测试目录与 CI 校验脚本

## ADDED Requirements
### Requirement: Open-AutoGLM 供应方接入
系统 SHALL 提供 Open-AutoGLM 供应方接入能力，并通过统一接口暴露给上层业务。

#### Scenario: 成功调用 Open-AutoGLM
- **WHEN** 用户请求到达并选择 Open-AutoGLM 模型
- **THEN** 系统通过 Open-AutoGLM Java SDK 发起调用并成功返回模型回复

#### Scenario: 配置缺失阻断启动
- **WHEN** Open-AutoGLM 被启用但关键配置（如密钥或模型名）缺失
- **THEN** 系统在启动或配置校验阶段给出明确错误并阻止不安全运行

### Requirement: 调用失败降级
系统 SHALL 在 Open-AutoGLM 调用失败时执行可配置降级策略，保障业务连续性。

#### Scenario: 上游超时触发降级
- **WHEN** Open-AutoGLM 在超时阈值内未返回结果
- **THEN** 系统记录超时错误并按配置降级到默认供应方或返回标准化失败响应

## MODIFIED Requirements
### Requirement: 模型路由策略
现有模型路由 SHALL 支持在多个供应方之间根据配置或请求参数进行选择，并包含 Open-AutoGLM 作为合法目标。

## REMOVED Requirements
### Requirement: 单一供应方假设
**Reason**: 原有流程默认单一供应方，无法满足新增 Open-AutoGLM 的并存需求。  
**Migration**: 将旧的硬编码供应方逻辑迁移为可配置路由；默认值保持与旧行为一致以降低升级风险。
