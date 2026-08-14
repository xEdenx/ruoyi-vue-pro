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
| `BpmPortalOrganizationApi` | 用户/部门展示、启用状态、角色/岗位/部门到最终用户的解算 | `LocalBpmPortalIdentityApiMock` | HTTP/mTLS 调 Portal 组织目录 API |
| `BpmPortalConfigurationApi` | Headless 管理端固定枚举、地区树等展示配置 | `LocalBpmPortalConfigurationApi`：直接投影 BPM 枚举，地区树为空 | HTTP/mTLS 调 Portal 配置 API；直接替换该实现 |
| `PortalRoleCandidateStrategy.PortalRoleCandidateApi` | BPMN 策略 70 的节点级角色候选人解算 | `LocalPortalRoleCandidateApiMock` | HTTP/mTLS 调 Portal 角色选人 API |
| `BpmPortalPrincipal` / `BpmPortalPrincipalUtils` | 请求主体的 String ID 和权限 claims | 将现有安全上下文适配为 BPM 主体；`BpmPortalAuthController` 提供本地 Mock 登录 | 校验 Portal JWT 或网关透传的可信身份后在此适配器构造主体 |
| `BpmPortalNotificationApi` | 待办、审批结果、抄送等通知投递 | `LoggingBpmPortalNotificationApi`：只记录待投递事件，不影响 BPM 状态和审计 | HTTP/mTLS 调 Portal Webhook / 消息入口 |
| `PortalApplicationLogApi` | API 访问与异常审计 | 应用日志输出待投递事件；不写 `infra_api_*` | Portal 审计入口或 OTel 日志管道 |

本地 Mock 开关：

```yaml
yudao:
  bpm:
    headless-mock:
      enabled: true
      login-password: portal-local-dev
```

关闭该开关后，如果没有注册生产实现，`MissingBpmPortalIdentityApi` 和 `MissingBpmPortalOrganizationApi` 会抛出异常。这样可以确保生产环境不会在 Portal 故障时错误访问本地 system 数据。当前用户读取已统一经过 `BpmPortalPrincipalUtils`；Controller、Flowable Filter 和任务服务不得重新直接读取或转换框架登录 ID。

### 2.1 本地 Headless 登录（仅 Mock）

当且仅当 `yudao.bpm.headless-mock.enabled=true` 时，BPM 注册下列 API；关闭 Mock 后 Controller 不存在。

| API | 用途 | 输入/输出 |
| --- | --- | --- |
| `POST /admin-api/bpm/portal-auth/login` | 以已配置的 Portal Mock 用户登录 | `userId`、固定 `password` → `accessToken`、最小 `PortalUser` 投影 |
| `GET /admin-api/bpm/portal-auth/me` | 获取当前 Mock 登录用户 | Bearer token → 最小 `PortalUser` 投影 |

系统采用全局单租户，不接受或转发租户请求头。该 token 是为了复用现有 BPM API 过滤器而生成的本地开发占位 token，**不是生产认证方案**：它没有可用于生产的签名、刷新、吊销或跨服务验证语义。安全过滤器仅在 `yudao.security.mock-enable=true` 且 payload 含 `headlessMock=true` 时才接受这类未签名 token。生产接入必须在构造 `BpmPortalPrincipal` 前验证 Portal JWT 的签名、`iss`、`aud` 和有效期，或使用受信任网关的 mTLS 身份透传；完成前不得在任何非本地环境开启 `headless-mock` 或 `security.mock-enable`。

## 3. 组织目录 SPI

代码入口：[BpmPortalOrganizationApi.java](../yudao-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/portal/BpmPortalOrganizationApi.java)。

除 `getUser`、`getDepartment` 和候选人解算外，`listSelectableUsers`、`listSelectableDepartments` 供 Headless BPM 管理页的选人/选部门控件使用。`/bpm/portal-directory/simple-list` 仅向已认证调用方暴露其可选择范围；生产实现必须由 Portal 根据当前主体和租户做授权过滤，不能把本地 mock 的全量目录当作生产行为。

固定 BPM 枚举经 `/bpm/portal-config/dict-data/simple-list` 提供，地区树经 `/bpm/portal-config/area-tree` 提供。两者都由 `BpmPortalConfigurationApi` 统一承载：当前默认实现不依赖 `system_dict_*` 或 `infra` 表，而是直接投影 BPM 内置枚举；Portal 接管配置时直接替换该 API 实现。

### 3.1 Portal 用户投影

```java
@Getter
@AllArgsConstructor
class PortalUser {
    private final String id;
    private final String displayName;
    private final String avatar;
    private final String departmentId;
    private final String departmentName;
    private final boolean active;
    private final Set<String> roleCodes;
    private final Set<String> postCodes;
}
```

这是 BPM 所需的最小投影，不是 Portal 用户表的镜像。生产适配器不得把密码、手机号、证件号或其他不必要的个人数据传给 BPM。

流程模型的发起部门白名单还需要下列最小部门投影：

```java
@Getter
@AllArgsConstructor
class PortalDepartment {
    private final String id;
    private final String name;
}
```

### 3.2 用户查询与有效性

| SPI | Portal 后续能力 | BPM 使用位置 | 失败规则 |
| --- | --- | --- | --- |
| `getUser(userId)` | 按 String ID 查询单个用户 | 管理授权、任务操作主体、展示补全 | 用户不存在或不可用时拒绝需其参与的操作 |
| `getUserMap(userIds)` | 批量用户投影查询 | 待办、已办、轨迹和审批详情展示 | 允许缺失项；响应保留原始 ID 供 Portal 自行渲染 |
| `isUserActive(userId)` | 用户状态查询 | 创建任务前的候选人过滤 | 返回 `false` 时不得创建任务 |
| `getDepartment(departmentId)` / `getDepartmentMap(departmentIds)` | 按 String ID 查询部门最小投影 | 模型列表中的发起部门白名单展示 | 允许缺失项；响应仍保留原始 `startDeptIds` |

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
| `DEPT_LEADER_OF_USER` | 传入发起人 ID；解析其当前部门负责人 | `portal-requester-a1f2` |

该接口保留给 Portal 远程选人适配器使用；BPMN 不再直接配置 `USER`、`ROLE`、`POST` 或 `DEPT` 等本地策略。返回值必须是去重、非空的最终用户 String ID。BPM 根据节点的审批方式处理返回集合：随机单人选一个；或签为每人建任务且首人完成即结束；会签为每人建任务并按全员/比例条件继续。`startUserId` 和 `processInstanceId` 用于 Portal 实现“发起人所在部门”“项目成员”“动态组织快照”等策略。

流程任务只持久化 Portal 原始用户 ID。查询审批详情和 BPMN 流程图时，BPM 会批量调用组织目录，将 Portal 返回的 `displayName`、`avatar` 和部门名称投影到响应的 `assigneeUser`、`ownerUser` 字段；姓名不是 BPM 的持久化快照，Portal 更名会在后续查询中即时体现。若审计要求保留历史姓名，Portal 应另行提供不可变的审计名称字段或版本化目录查询能力。

## 4. BPMN 节点级选人 SPI

### 4.1 当前 BPMN 候选人策略边界

- `BpmTaskCandidateStrategyEnum` 保留全部历史编号，只作为 BPMN 元数据和错误诊断的稳定目录；保留枚举值不代表存在可执行实现。
- `/bpm/portal-config/dict-data/simple-list` 以 `bpm_task_candidate_strategy` 返回完整策略目录。Vue 两套建模器直接读取该目录，不维护策略白名单，也不根据 Spring 注册情况过滤选项。
- Spring 当前仅注册 `START_USER_SELECT`（35）与 Portal `ROLE`（70）两个候选人实现；这仅是后端的可执行性边界。
- 旧策略编号对应的实现已删除。包含旧编号的流程模型发布时会因找不到策略实现而失败，不能再隐式读取 `system_user`、角色、岗位或部门。
- 候选人为空时不再执行本地 `ASSIGN_EMPTY` 回退。Portal 必须在发起时提供 `startUserSelectAssignees`，或由角色策略返回有效的最终用户 String ID；否则失败关闭。

策略 70 的入口是 `PortalRoleCandidateStrategy.PortalRoleCandidateApi`：

```java
Set<String> resolveRoleAssigneeIds(
    String startUserId,
    String activityId,
    String roleCode,
    String processInstanceId
)
```

`roleCode` 是 BPMN 中 `candidateParam` 的 Portal 目标角色编码。Portal 根据发起人、节点和自身组织数据解算用户；BPM 不解析、不映射、不查询本地角色表。

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
| BPMN 本地用户/角色/岗位/部门候选人策略 | 已删除 | `START_USER_SELECT` 或 Portal `ROLE` | 已移除 |
| 候选人禁用过滤 | `AdminUserApi.getUserMap` | `BpmPortalOrganizationApi.isUserActive` | 已迁移（候选人为空时失败关闭） |
| 待办、已办、任务明细与评论的姓名/部门补全 | `AdminUserApi`、`DeptApi` | `BpmPortalUserProjection` + `getUserMap`；Portal 可自行渲染 | 已迁移 |
| 流程实例列表、详情、打印、审批轨迹、BPMN 模型视图与下一节点预测的姓名/部门补全 | `AdminUserApi`、`DeptApi` | `BpmPortalUserProjection` + `getUserMap`；Flowable 原始 String ID 透传 | 已迁移 |
| 模型管理员校验 | 本地用户/角色已不再是权威来源 | `BpmPortalIdentityApi.hasAnyRole` | 已迁移（Mock） |
| 流程发起用户/部门白名单、子流程管理员 | `LongListTypeHandler`、`AdminUserApi`、`DeptApi` | `List<String>` + `StringListTypeHandler` + `BpmPortalOrganizationApi.getUser`；模型列表用 `getUserMap` / `getDepartmentMap` 展示 | 已迁移；旧数值白名单须由 Portal 映射后重新配置 |
| 当前登录用户与菜单权限 | 仅本地 Mock token；`@ss.hasPermission` 只识别其 BPM claims | `BpmPortalPrincipal` + 已验证 Portal claims | 已移除 system OAuth2 token 与 system 权限回退；BPM 代码中的当前用户读取已迁移到唯一适配点，生产验签与精确授权待迁移 |
| 短信、邮件、站内信 | `SmsSendApi` 等 system 能力 | `BpmPortalNotificationApi`；当前日志兜底，后续替换为 Portal Webhook；保留 BPM 通知触发时机 | 适配端口已迁移，生产投递待接入 |
| API 与操作审计 | `OperateLogCommonApi`、`Api*LogCommonApi` | `PortalApplicationLogApi`；本地只输出待投递日志 | 已迁移；Portal 审计或 OTel 接入待实现 |
| 本地部门数据权限 | `PermissionCommonApi`、部门数据权限规则 | BPM 不再创建或执行本地部门规则；Portal 在调用 BPM 前完成数据范围授权 | 已移除；不得回退读取 system 部门权限 |
| 抄送收件人、抄送查询与抄送节点 | 数值 `user_id` / `start_user_id`、`AdminUserApi` | Portal 原始 String ID + `BpmPortalOrganizationApi.getUserMap`；保留 BPM 抄送审计和查询 API | 已迁移；生产库与初始化快照均应保持 `varchar(64)` String ID 语义 |
| 转办、委派、加签、减签、退回、撤回的操作主体与目标用户 | `AdminUserApi`、`DeptApi`、Long ID | Portal 组织目录用户校验和 Flowable 原始 String ID；`DEPT_LEADER_OF_USER` 解算发起人部门负责人；保留 BPM 动作、监听器、查询 API 与审计 | 已迁移；生产 Portal 需实现 `DEPT_LEADER_OF_USER` |

`bpm_process_definition_info` 的白名单字段仍是逗号分隔文本列，因此本轮不需要数据库列类型变更。已有数值 ID 会被读取为 String，但不能自动推导为 Portal 用户/部门 ID；生产切换前必须用 Portal 的稳定 ID 重新保存每个受限模型，或清空白名单后由 Portal claims/API 统一授权。禁止猜测或通过数值转换映射到 Portal 身份。

## 6. 生产 HTTP 实现要求

生产适配器应单独位于 `framework.portal` 包下，例如 `HttpBpmPortalOrganizationApi`，并满足：

1. 使用 mTLS 或服务间签名调用 Portal；不得信任浏览器传入的用户 ID、角色或部门。
2. 为每个请求设置连接和读取超时、有限重试及熔断；组织解算与任务创建不得使用无限重试。
3. 仅传递本次操作所需的最小上下文：发起人 ID、流程实例 ID、节点 ID 和受控选择器。
4. 对角色、岗位和部门的选择器使用 Portal 原生稳定编码，禁止引入 BPM 到 Portal 的数字 ID 映射表。
5. 记录不含敏感字段的审计事件：适配器类型、选择器、返回用户数量、实例/节点 ID、耗时和失败原因。
6. Portal 不可用、返回空审批人或返回空白 ID 时失败关闭；禁止使用缓存的过期人员名单继续创建审批任务。

## 7. 每个迁移批次的验收

1. 对应 BPM 代码不再导入 `cn.iocoder.yudao.module.system.api.*`。
2. Mock 覆盖至少一个 String/UUID 风格用户 ID，以及角色、部门或岗位解算。
3. 在关闭 Mock 且未注册 HTTP 实现时，调用会清晰失败，不回退 system。
4. 使用最小 schema 或 SQL 观测验证该 API 路径没有 `system_*` 查询。
5. 更新本文件的“待迁移调用登记”和相关 walkthrough。

后端变更用户 ID、用户投影或候选人响应前，必须保持当前 Portal API 的响应形状，并通过 walkthrough 验证迁移兼容性。
