# BPM Portal 适配契约

> 状态：In progress（本地 Mock 已提供组织目录基础实现；生产 Portal HTTP 实现待接入）
>
> 目的：集中记录 BPM 对 Portal 的所有外部依赖。BPM 内的业务代码只能依赖本文定义的适配端口；后续接入 Portal 时替换适配器实现，而不是再次修改候选人、任务、流程实例或 Controller 业务逻辑。

## 0. 能力保留约束

解耦只替换外部依赖，不能删除 BPM 业务能力。审批、会签、或签、退回、撤回、转办、委派、加签、减签、抄送、任务监听器、流程事件、审计轨迹及其运行 API 均由 BPM/Flowable 继续负责。

Portal 适配器只负责下列边界能力：身份与权限 claims、组织目录和最终用户解算、用户展示投影、通知/抄送投递、业务回调。任何拟删除 BPM 节点、监听器、Controller、Service 或 `bpm_*` 表的变更，必须另行获得业务下线授权；不得将“Portal 接管用户或通知”理解为“删除流程语义”。

## 1. 边界与不变量

- Portal 是用户、角色、部门、岗位、权限、通知和业务单据的唯一来源；BPM 不同步这些数据。
- Flowable 的 `startUserId`、`assignee`、`owner` 和候选人均为 Portal 原始 `String` 用户 ID。
- 角色、部门、岗位只是 Portal 的选人条件；BPM 只接收最终用户 ID 集合，并据此创建单人、或签或会签任务。
- 未配置对应 Portal 适配器时必须失败关闭，禁止回退查询 `system_user`、`system_role`、`system_dept` 等本地表。
- 本地 Mock 只用于 walkthrough 和开发；生产环境由同一 SPI 的 HTTP/mTLS 实现替换。

## 2. 当前适配器与切换方式

| 适配器 | 责任 | 本地实现 | 生产实现要求 |
| --- | --- | --- | --- |
| `BpmPortalIdentityApi` | 模型管理员角色校验 | `LocalBpmPortalIdentityApiMock` | 由 Portal 返回用户及其角色编码 |
| `BpmPortalOrganizationApi` | 用户展示、启用状态、角色/岗位/部门到最终用户的解算 | `LocalBpmPortalIdentityApiMock` | HTTP/mTLS 调 Portal 组织目录 API |
| `HeadlessRemoteCandidateStrategy.PortalCandidateApi` | BPMN 策略 70 的节点级候选人解算 | `LocalPortalCandidateApiMock` | HTTP/mTLS 调 Portal 节点选人策略 API |
| `PortalPrincipal`（待实现） | 请求认证、当前用户和权限 claims | `BpmPortalAuthController` 本地 Mock 登录 + 临时 JWT 解析 | 校验 Portal JWT 或网关透传的可信身份 |
| `BpmPortalNotificationApi`（待实现） | 待办、审批结果、抄送等通知投递 | 记录事件或 no-op Mock；不影响 BPM 状态和审计 | Portal Webhook / 消息入口 |

本地 Mock 开关：

```yaml
yudao:
  bpm:
    headless-mock:
      enabled: true
      login-password: portal-local-dev
      tenant-id: 1
```

关闭该开关后，如果没有注册生产实现，`MissingBpmPortalIdentityApi` 和 `MissingBpmPortalOrganizationApi` 会抛出异常。这样可以确保生产环境不会在 Portal 故障时错误访问本地 system 数据。

### 2.1 本地 Headless 登录（仅 Mock）

当且仅当 `yudao.bpm.headless-mock.enabled=true` 时，BPM 注册下列 API；关闭 Mock 后 Controller 不存在。

| API | 用途 | 输入/输出 |
| --- | --- | --- |
| `POST /admin-api/bpm/portal-auth/login` | 以已配置的 Portal Mock 用户登录 | `userId`、固定 `password` → `accessToken`、`tenantId`、最小 `PortalUser` 投影 |
| `GET /admin-api/bpm/portal-auth/me` | 获取当前 Mock 登录用户 | Bearer token → 最小 `PortalUser` 投影 |

登录端点本身忽略租户过滤，以便浏览器在尚未持有租户上下文时建立会话；登录响应中的 `tenantId` 必须被前端写入后续 BPM 请求头。该 token 是为了复用现有 BPM API 过滤器而生成的本地开发占位 token，**不是生产认证方案**：它没有可用于生产的签名、刷新、吊销或跨服务验证语义。生产接入必须先实现 `PortalPrincipal`，验证 Portal JWT 的签名、`iss`、`aud`、有效期和租户边界，或使用受信任网关的 mTLS 身份透传；完成前不得在任何非本地环境开启 `headless-mock`。

## 3. 组织目录 SPI

代码入口：[BpmPortalOrganizationApi.java](../yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/portal/BpmPortalOrganizationApi.java)。

### 3.1 Portal 用户投影

```java
record PortalUser(
    String id,
    String displayName,
    String avatar,
    String departmentId,
    String departmentName,
    boolean active,
    Set<String> roleCodes,
    Set<String> postCodes
) {}
```

这是 BPM 所需的最小投影，不是 Portal 用户表的镜像。生产适配器不得把密码、手机号、证件号或其他不必要的个人数据传给 BPM。

### 3.2 用户查询与有效性

| SPI | Portal 后续能力 | BPM 使用位置 | 失败规则 |
| --- | --- | --- | --- |
| `getUser(userId)` | 按 String ID 查询单个用户 | 管理授权、任务操作主体、展示补全 | 用户不存在或不可用时拒绝需其参与的操作 |
| `getUserMap(userIds)` | 批量用户投影查询 | 待办、已办、轨迹和审批详情展示 | 允许缺失项；响应保留原始 ID 供 Portal 自行渲染 |
| `isUserActive(userId)` | 用户状态查询 | 创建任务前的候选人过滤 | 返回 `false` 时不得创建任务 |

### 3.3 组织条件到最终用户 ID 的解算

```java
Set<String> resolveUserIds(
    String selectorType,
    Collection<String> selectorValues,
    String startUserId,
    String processInstanceId
)
```

| `selectorType` | `selectorValues` 语义 | 示例 |
| --- | --- | --- |
| `USER` | Portal 用户 ID | `portal-manager-b3c4` |
| `ROLE` | Portal 角色编码 | `ROLE_FINANCE` |
| `POST` | Portal 岗位编码 | `POST_PROCUREMENT` |
| `DEPT` | Portal 部门 ID | `portal-dept-admin` |

该接口保留给 Portal 远程选人适配器使用；BPMN 不再直接配置 `USER`、`ROLE`、`POST` 或 `DEPT` 等本地策略。返回值必须是去重、非空的最终用户 String ID。BPM 根据节点的审批方式处理返回集合：随机单人选一个；或签为每人建任务且首人完成即结束；会签为每人建任务并按全员/比例条件继续。`startUserId` 和 `processInstanceId` 用于 Portal 实现“发起人所在部门”“项目成员”“动态组织快照”等策略。

流程任务只持久化 Portal 原始用户 ID。查询审批详情和 BPMN 流程图时，BPM 会批量调用组织目录，将 Portal 返回的 `displayName`、`avatar` 和部门名称投影到响应的 `assigneeUser`、`ownerUser` 字段；姓名不是 BPM 的持久化快照，Portal 更名会在后续查询中即时体现。若审计要求保留历史姓名，Portal 应另行提供不可变的审计名称字段或版本化目录查询能力。

## 4. BPMN 节点级选人 SPI

### 4.1 当前 BPMN 候选人策略边界

- `BpmTaskCandidateStrategyEnum` 保留全部历史编号，只作为 BPMN 元数据和错误诊断的稳定目录；保留枚举值不代表存在可执行实现。
- Spring 仅注册 `START_USER_SELECT`（35）与 `HEADLESS_REMOTE`（70）两个候选人实现。Vue 两套建模器也只提供这两个选项。
- 旧策略编号对应的实现已删除。包含旧编号的流程模型发布时会因找不到策略实现而失败，不能再隐式读取 `system_user`、角色、岗位或部门。
- 候选人为空时不再执行本地 `ASSIGN_EMPTY` 回退。Portal 必须在发起时提供 `startUserSelectAssignees`，或由远程策略返回有效的最终用户 String ID；否则失败关闭。

策略 70 的入口是 `HeadlessRemoteCandidateStrategy.PortalCandidateApi`：

```java
Set<String> resolveAssigneeIds(
    String startUserId,
    String activityId,
    String roleParam,
    String processInstanceId
)
```

`roleParam` 是 BPMN 中 `candidateParam` 的原样值，由 Portal 自定义解释。例如可为单角色编码、组合角色策略编码，或受控 JSON 选择器。BPM 不解析、不映射、不查询本地角色表。

当前本地示例：

| 节点 | 参数 | Mock 返回 |
| --- | --- | --- |
| `Activity_Admin` | `ROLE_ADMIN` | `portal-admin-d5e6` |
| `Activity_Supplier` | `ROLE_SUPPLIER` | `portal-supplier-e7f8`、`portal-supplier-f9a0` |

生产 Portal 应在单次请求中完成组织解算，返回固定快照。流程已经到达节点后，不应因为 Portal 组织变化而重新分配已创建任务。

## 5. 待迁移调用登记

以下登记是后续替换的唯一工作清单；每完成一项，必须同步本表、路线图和测试。

| 范围 | 当前本地依赖 | Portal 替代 | 目标状态 |
| --- | --- | --- | --- |
| BPMN 本地用户/角色/岗位/部门候选人策略 | 已删除 | `START_USER_SELECT` 或 `HEADLESS_REMOTE` | 已移除 |
| 候选人禁用过滤 | `AdminUserApi.getUserMap` | `BpmPortalOrganizationApi.isUserActive` | 已迁移（候选人为空时失败关闭） |
| 待办、已办、任务明细与评论的姓名/部门补全 | `AdminUserApi`、`DeptApi` | `BpmPortalUserProjection` + `getUserMap`；Portal 可自行渲染 | 已迁移 |
| 模型、流程实例详情/打印、审批轨迹的姓名/部门补全 | `AdminUserApi`、`DeptApi` | `getUser` / `getUserMap`；Portal 可自行渲染 | 待迁移 |
| 模型管理员校验 | 本地用户/角色已不再是权威来源 | `BpmPortalIdentityApi.hasAnyRole` | 已迁移（Mock） |
| 发起权限、部门白名单 | 本地用户/部门判断 | Portal claims 或 Portal 授权接口 | 待迁移 |
| 当前登录用户与菜单权限 | `SecurityFrameworkUtils`、`@ss.hasPermission` | `PortalPrincipal` + Portal claims | 本地 Mock 已提供登录与 `/me`；生产待迁移 |
| 短信、邮件、站内信 | `SmsSendApi` 等 system 能力 | `BpmPortalNotificationApi` / Portal Webhook；保留 BPM 通知触发时机 | 待迁移 |
| 抄送、转办、委派、加签目标用户校验 | `AdminUserApi` | Portal 组织目录用户校验；保留 BPM 动作、监听器、查询 API 与审计 | 待迁移 |

## 6. 生产 HTTP 实现要求

生产适配器应单独位于 `framework.portal` 包下，例如 `HttpBpmPortalOrganizationApi`，并满足：

1. 使用 mTLS 或服务间签名调用 Portal；不得信任浏览器传入的用户 ID、角色或部门。
2. 为每个请求设置连接和读取超时、有限重试及熔断；组织解算与任务创建不得使用无限重试。
3. 仅传递本次操作所需的最小上下文：发起人 ID、流程实例 ID、节点 ID、受控选择器和租户边界。
4. 对角色、岗位和部门的选择器使用 Portal 原生稳定编码，禁止引入 BPM 到 Portal 的数字 ID 映射表。
5. 记录不含敏感字段的审计事件：适配器类型、选择器、返回用户数量、实例/节点 ID、耗时和失败原因。
6. Portal 不可用、返回空审批人或返回空白 ID 时失败关闭；禁止使用缓存的过期人员名单继续创建审批任务。

## 7. 每个迁移批次的验收

1. 对应 BPM 代码不再导入 `cn.iocoder.yudao.module.system.api.*`。
2. Mock 覆盖至少一个 String/UUID 风格用户 ID，以及角色、部门或岗位解算。
3. 在关闭 Mock 且未注册 HTTP 实现时，调用会清晰失败，不回退 system。
4. 使用最小 schema 或 SQL 观测验证该 API 路径没有 `system_*` 查询。
5. 更新本文件的“待迁移调用登记”、[解耦路线图](HEADLESS_BPM_DECOUPLING_ROADMAP.md)和相关 walkthrough。

现有 Vue 管理端和未来 Portal 前端的兼容边界见 [FRONTEND_BPM_PORTAL_COMPATIBILITY.md](FRONTEND_BPM_PORTAL_COMPATIBILITY.md)。后端变更用户 ID、用户投影或候选人响应前，必须先检查该文档中的稳定响应形状与迁移顺序。
