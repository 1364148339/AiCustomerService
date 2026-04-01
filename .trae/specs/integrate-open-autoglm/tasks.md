# Tasks
- [x] Task 1: 梳理现有模型调用链路与扩展点，确定 Open-AutoGLM 接入边界与改造文件。
  - [x] SubTask 1.1: 定位模型客户端、路由层、配置加载入口
  - [x] SubTask 1.2: 明确请求/响应字段映射与统一接口契约
- [x] Task 2: 实现 Open-AutoGLM 适配器与配置项接入，支持基础对话调用。
  - [x] SubTask 2.1: 接入 Open-AutoGLM Java SDK 并完成供应方客户端与鉴权封装
  - [x] SubTask 2.2: 增加配置读取、校验与默认值策略
  - [x] SubTask 2.3: 将 Open-AutoGLM 注册到模型路由并可按配置启用
- [x] Task 3: 完善可靠性能力，包括错误标准化、超时重试与降级处理。
  - [x] SubTask 3.1: 统一上游错误映射与日志字段
  - [x] SubTask 3.2: 增加超时与重试控制
  - [x] SubTask 3.3: 实现失败时降级策略并覆盖关键分支
- [ ] Task 4: 增加测试与验证，确保功能可回归。
  - [x] SubTask 4.1: 编写单元测试覆盖适配器与路由选择
  - [x] SubTask 4.2: 编写集成测试覆盖成功、超时、鉴权失败场景
  - [ ] SubTask 4.3: 运行现有测试与新增测试并修复回归问题

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 2 and Task 3
