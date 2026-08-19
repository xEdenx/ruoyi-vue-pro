# ADR-003: Headless BPM 对象级授权与参数篡改防护

## 状态

已接受，待实施。

## 背景

Headless BPM 已将身份、组织和候选人主数据迁移到 Portal，并以 Portal 原始 String ID 在 Flowable 中保存 `startUserId`、`assignee` 与 `owner`。现有 `@PreAuthorize` 只能判定调用方能否访问一类 API；它不能判定调用方能否读取或操作请求参数所指向的某个流程实例、任务、评论、抄送或附件。

因此，不能把 Portal 前端不展示按钮、不暴露 ID 或调用方传入的用户/角色/候选人当作安全边界。调用者可能替换 `processInstanceId`、`taskId` 或发起请求中的动态候选人集合。

本 ADR 同时处理下列授权缺口：

1. 单实例及派生资源的读取越权；
2. 减签、评论、抄送等任务附属写操作未统一校验操作者；
3. 新模型发布、模型/流程定义元数据查询未按模型管理权限收紧；
4. 策略 35 的动态审批人仅做非空/启用校验，未验证业务范围内的选人资格。

HTTP 出站触发器的 SSRF/可靠投递治理，以及本地 Mock token 的生产配置治理不属于本 ADR 的实施范围，分别在后续安全与生产接入工作中处理。

## 决策

### 1. 将权限分为功能权限、对象级授权和 Portal 业务范围授权

```text
可信 Portal 身份
  -> 功能权限（@PreAuthorize）
  -> 对象级授权（BPM 事实）
  -> Portal 业务范围授权（部门、项目、业务单据等）
  -> 返回数据或执行动作
```

- 功能权限决定调用方能否访问 API 类别，不能授予任意实例的读取或操作权限。
- 对象级授权以 BPM/Flowable 持久化事实判断：发起人、当前或历史任务办理人、抄送接收人，以及显式授予的 BPM 全局管理权限。
- Portal 是部门、项目成员、业务单据归属和动态选人资格的唯一权威来源。BPM 通过失败关闭的 `BpmPortalProcessAccessApi`（待实现）向 Portal 请求判定；不得回退本地 `system_*` 数据。

### 2. 建立唯一授权裁决点

新增 `BpmProcessInstancePermissionService`，集中提供至少下列语义明确的校验：

```java
assertCanReadInstance(userId, processInstanceId);
assertCanOperateTask(userId, taskId);
assertCanComment(userId, taskId);
assertCanCopy(userId, taskId);
assertCanSelectAssignees(userId, processDefinitionId, variables, assignees);
```

- `READ_INSTANCE` 适用于实例详情、轨迹、BPMN 视图、打印、评论、任务列表、附件等派生读取。
- `OPERATE_TASK` 适用于审批、驳回、退回、委派、转办、加签和减签；可读实例不等于可办理任务。审批类操作仍以任务 `assignee`（及委派语义的 `owner`）为授权事实。
- 评论与手工抄送使用独立动作语义，避免历史参与者或仅可读主体获得无约束写权限。
- 拒绝授权时对外返回与资源不存在等价的业务错误，并记录不含表单数据的审计事件，避免枚举资源 ID。

### 3. 以切面防止 Controller 遗漏，但不在切面中编写规则

新增 `@BpmInstanceAccess`，用于声明单资源读取入口的资源参数类型（`processInstanceId` 或 `taskId`）。切面只提取资源、取得当前 Portal 主体并调用权限服务。

授权规则不能分散到 SpEL、Controller 或前端；Service、异步任务或内部 API 需要访问受保护资源时，也必须调用同一权限服务。

### 4. 列表在查询阶段限缩，字段在响应阶段最小化返回

- 我的流程、待办、已办、抄送等列表在 Flowable/持久层查询阶段按授权范围限缩，禁止先分页再在内存过滤。
- `formFieldsPermission` 仅是表单展示/编辑元数据，不构成服务端数据保护。通过对象级授权前不得返回流程变量、表单内容或附件；需要字段保密时，由 Portal 或 BPM 响应组装层按已验证策略脱敏/剔除字段。

### 5. 模型管理和动态选人使用独立授权，不复用实例读取规则

- 新建、导入、部署（含 `deploy-xml`）、更新、删除、清理流程模型必须要求 Portal 模型管理员的显式权限。新模型的 `managerRoleCodes` 不得由普通调用者通过请求参数把自己提升为管理员。
- 模型/流程定义元数据接口必须要求相应管理权限，或只返回当前主体可发起的流程定义。
- 策略 35 的 `startUserSelectAssignees` 必须由 Portal 按“当前用户、业务单据、节点、候选人集合”验证；BPM 只接受 Portal 已授权且非空的最终 String ID 集合，并在 Portal 不可用时失败关闭。

## 实施计划

1. 定义 `BpmPortalProcessAccessApi`、`BpmProcessInstancePermissionService`、错误语义和单元测试。
2. 为详情、轨迹、BPMN 视图、打印、评论、按实例任务查询和附件读取接入 `@BpmInstanceAccess` 与权限服务。
3. 将减签、评论、抄送接入 `OPERATE_TASK` / 独立动作校验；保留现有审批人校验。
4. 收紧模型创建、导入、部署及相关元数据查询的 Portal 模型管理员权限。
5. 接入 Portal 的动态选人授权，替换策略 35 当前仅做非空/启用的校验。
6. 调整列表查询与响应脱敏，并完成端到端越权回归。

## 验收标准

- 两名无交集业务范围用户互换任意实例、任务、评论、打印或附件参数，均不能读取或操作对方资源。
- 非任务办理人不能减签、评论、手工抄送、退回、委派或转办他人任务。
- 普通 Portal 用户不能创建、导入或部署流程模型，也不能借 `managerRoleCodes` 获得模型管理能力。
- 发起人不能借篡改 `startUserSelectAssignees` 指定业务范围外人员；Portal 授权不可用时发起失败关闭。
- 管理员、发起人、审批参与人、抄送人仅在约定范围内获得正确的查看与操作能力；分页 `total` 和响应字段不泄露范围外信息。

## 后果

- Controller 的功能权限与资源授权职责清晰分离；新增 API 必须选择对应的授权语义并纳入回归测试。
- Portal 需要提供稳定的实例业务范围与动态选人授权能力；这是零用户同步架构的必然边界，而不是 BPM 回退查询本地组织表的理由。
- 本 ADR 只定义授权边界和实施顺序，不改变 Flowable 的状态机、审批、会签、或签、抄送与审计语义。

## 参考

- [Portal Headless BPM 架构](../PORTAL_HEADLESS_BPM_ARCHITECTURE.md)
- [Portal 适配器集成契约](../PORTAL_ADAPTER_INTEGRATION_CONTRACT.md)
- [ADR-000 无头工作流中台与零用户同步架构](ADR_000_HEADLESS_BPM_ZERO_USER_SYNC_ARCHITECTURE.md)
- [ADR-002 Portal 角色候选人策略](ADR_002_PORTAL_ROLE_CANDIDATE_STRATEGY.md)
