# Tasks

- [x] Task 1: 设备端补充失败步骤决策字段与枚举生成逻辑
  - [x] SubTask 1.1: 在 `AiMacrodroidService.kt` 的事件上报构造逻辑中，为失败事件的 `progress` 增加 `pageType`、`pageSignature`、`targetResolved`、`actionResult`、`recoverable`、`failureCategory`
  - [x] SubTask 1.2: 在设备端新增失败分类辅助逻辑，基于 `errorCode`、`errorMessage` 和运行态信息输出 `failureCategory`
  - [x] SubTask 1.3: 在设备端新增可恢复性判断辅助逻辑，输出 `recoverable`
  - [x] SubTask 1.4: 在设备端新增动作结果判断辅助逻辑，输出 `actionResult`
  - [x] SubTask 1.5: 在设备端新增页面摘要生成逻辑，输出 `pageType` 与 `pageSignature`
  - [x] SubTask 1.6: 按一期最小枚举集合实现 `failureCategory`、`actionResult`、`pageType` 的取值控制，避免自由字符串扩散
  - [x] SubTask 1.7: 验证新增字段不会破坏原有错误码、截图、轨迹、思考、进度等事件上报能力

- [x] Task 2: 服务端提取并保存失败步骤决策字段与枚举结果
  - [x] SubTask 2.1: 保持 `EventReportReqDTO` 协议兼容，继续通过 `progress` 承载新增字段
  - [x] SubTask 2.2: 在 `RunEvent` 增加 `failureCategory`、`recoverable`、`actionResult`、`pageType`、`pageSignature` 五个可检索字段及对应持久化变更
  - [x] SubTask 2.3: 在 `RunEventServiceImpl.java` 中提取 `progress` 中的核心字段并保存到 `RunEvent`
  - [x] SubTask 2.4: 保持旧设备端缺少新增字段时的兼容处理，避免入库或查询异常
  - [x] SubTask 2.5: 调整事件查询返回结构，使前端与后续 Agent 可直接消费新增字段
  - [x] SubTask 2.6: 校验服务端对未知枚举值与缺失字段的兼容策略，避免异常值导致接口失败

- [x] Task 3: 前端展示失败步骤决策摘要与中文文案映射
  - [x] SubTask 3.1: 在 `api.js` 的事件映射逻辑中补齐 `failureCategory`、`recoverable`、`actionResult`、`pageType`、`pageSignature`
  - [x] SubTask 3.2: 在 `TaskDetailView.vue` 的失败事件区域增加失败类别、可恢复性、动作结果、页面类型四项摘要展示
  - [x] SubTask 3.3: 提供 `failureCategory`、`actionResult`、`pageType` 到中文展示文案的映射，保证展示语义清晰
  - [x] SubTask 3.4: 处理新增字段缺失时的兼容展示，避免详情页渲染异常
  - [x] SubTask 3.5: 处理未知枚举值的兼容展示，统一降级为“未知”或等效占位
  - [x] SubTask 3.6: 收紧失败决策摘要展示范围，仅在失败事件展示，避免非失败事件语义偏差

- [ ] Task 4: 联调与验收
  - [ ] SubTask 4.1: 联调元素未找到场景，验证 `failureCategory=ELEMENT`、`recoverable=true`、`actionResult=TARGET_NOT_FOUND`、`pageType` 合理
  - [ ] SubTask 4.2: 联调权限缺失场景，验证 `failureCategory=PERMISSION`、`recoverable=false`、`pageType=PERMISSION_PAGE`
  - [ ] SubTask 4.3: 联调执行超时场景，验证 `failureCategory=TIMEOUT` 与 `actionResult` 语义一致
  - [ ] SubTask 4.4: 联调敏感页命中场景，验证 `failureCategory=SENSITIVE_SCREEN`、`recoverable=false`、`pageType=SENSITIVE_PAGE`
  - [x] SubTask 4.5: 验证新增字段从设备端上报、服务端落库、接口返回到前端展示的完整链路
  - [x] SubTask 4.6: 验证旧版本设备端不带新增字段时，服务端和前端仍可兼容工作
  - [x] SubTask 4.7: 验证一期最小枚举集合、中文文案映射和未知值降级策略一致生效
  - [x] SubTask 4.8: 在规格中补充四类典型失败场景的联调样例、预期字段与最小执行步骤，便于后续验收落地
  - [x] SubTask 4.9: 在规格中补充四类失败场景的样例事件模板，作为设备端、服务端、前端联调对齐基准

- [ ] Task 5: 文档化一期能力边界、枚举标准与后续演进入口
  - [x] SubTask 5.1: 在规格评审结果中明确一期仅支持 Agent 分析与指导，不包含自动补救执行
  - [x] SubTask 5.2: 记录一期 `failureCategory`、`actionResult`、`pageType` 的最小枚举集合与中文文案标准
  - [x] SubTask 5.3: 记录二期优先扩展字段：`nextSafeActions`、`goalAchieved`、恢复动作结果、页面前后差异对比
  - [ ] SubTask 5.4: 明确一期完成后的交付物：字段协议、后端查询能力、前端展示入口、联调结论

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 1, Task 2, and Task 3
- Task 5 depends on Task 4

# 派发建议
- Task 1 派发给设备端开发
- Task 2 派发给后端开发
- Task 3 派发给前端开发
- Task 4 由设备端、后端、前端联合完成
- Task 5 由方案负责人或架构负责人完成

# 交付顺序建议
- 第一批交付：Task 1 + Task 2，先打通字段上报、枚举生成与落库链路
- 第二批交付：Task 3，补齐前端可视化展示与中文文案映射，并完成失败摘要展示语义收口
- 第三批交付：Task 4，完成典型失败场景联调、兼容性验证和未知值降级验证
- 第四批交付：Task 5，沉淀一期边界、枚举标准与后续扩展方向
