# Headless BPM 解耦路线图

> 状态：Proposed
>
> 范围：将当前 RuoYi-Vue-Pro BPM 服务收敛为 Portal 驱动的无头工作流服务。
> 本文不删除任何代码或表；每一阶段必须通过本阶段验收后才可进入下一阶段。

## 1. 目标与边界

Portal 是用户、组织、角色、业务单据和文件的唯一数据源。BPM 服务负责：

- 维护流程模型、发布版本和表单 Schema；
- 按 `businessKey`、有限流程变量和动态审批人推进 Flowable；
- 提供待办、审批、拒绝与审批轨迹 API；
- 保存 Flowable 必需的运行态与历史审计数据。

`bpm_form` 是流程设计期的参数契约，必须保留。Portal 在发起前从 BPM 读取已发布流程定义及其表单 Schema，渲染、校验并收集业务参数。实际业务单据仍归 Portal 所有。

```text
流程设计 / 发布
  └─ bpm_form + bpm_process_definition_info + Flowable Repository

Portal 发起
  └─ businessKey + 路由变量 + 最终审批人 String ID

Flowable 运行
  └─ ACT_RU_* / ACT_HI_*（待办、变量、轨迹与审批意见）
```

为避免 BPM 成为业务数据副本，`variables` 只应包含网关判断、节点标题等流程必需字段，以及业务引用；完整业务表单由 Portal 通过 `businessKey` 查询。

## 2. 最终模块边界

```text
Portal（身份、权限、组织、业务数据、文件）
             │ 已签名 Token / mTLS + BPM API
             ▼
Headless BPM Server
  ├─ BPM API 与表单/流程定义管理
  ├─ PortalPrincipal 安全适配
  ├─ Flowable Process Engine
  └─ PostgreSQL（Flowable 表 + 最小 BPM 配置表）
```

目标运行时保留：`yudao-server`、`yudao-module-bpm`、必要的 `yudao-framework` starter、`yudao-dependencies` 和 PostgreSQL 驱动。

目标运行时移除：`yudao-module-system`、`yudao-module-infra`，以及未启用的业务模块。

## 3. 阶段 0：冻结 API 与数据契约

**目标：** 在删除依赖前明确 Portal 与 BPM 的可信边界。

- [ ] 定义 `PortalPrincipal`：`userId: String`、可选 `tenantId: String`、`authorities`。（本地 Mock 登录与 `/me` 已完成；生产 Principal 尚未实现）
- [ ] 由 BPM 网关或服务端验证 Portal JWT / mTLS；不得从普通请求头读取用户 ID。（本地 Mock token 仅用于 walkthrough，不能作为验收）
- [ ] 将运行 API 收敛为：流程定义/表单读取、创建流程、待办/已办、同意/拒绝、审批轨迹；流程图维护 API 仅开放给定义管理员。
- [ ] 定义流程发起契约：`processDefinitionId`（或解析后的 key/version）、`businessKey`、路由变量、`startUserSelectAssignees`。
- [ ] 约定 `startUserSelectAssignees` 中只允许最终用户 String ID；Portal 在调用前完成角色到用户的展开。

**验收：** 以 UUID 用户 ID 完成“读取表单 → 发起 → 待办 → 同意/拒绝 → 查询轨迹”闭环。

## 4. 阶段 1：解耦 system 身份、权限和组织

**目标：** BPM 不再读取本地用户、部门、岗位、角色或菜单权限。

Portal 适配接口、Mock 与后续 HTTP 实现的集中约定见 [PORTAL_ADAPTER_INTEGRATION_CONTRACT.md](PORTAL_ADAPTER_INTEGRATION_CONTRACT.md)。任何 system 依赖的替换必须先登记在该契约中，避免将 Portal 对接细节分散进候选人、Controller 和 Service。

### 4.1 身份与权限替换

- [ ] 用 `PortalPrincipal` 替换 `getLoginUserId()` 及其 Long 类型调用链。
- [ ] 用签名 claims 的 `hasAuthority(...)` 或 API 网关策略替换 `@ss.hasPermission(...)`。
- [ ] 定义管理员权限，例如 `bpm:definition:manage`；运行用户仅能操作其 String ID 对应的任务。
- [ ] 流程发起人、任务 owner/assignee、历史返回字段统一为 String。

### 4.2 删除本地组织候选人能力

- [ ] 保留 `START_USER_SELECT`；按需保留读取表单内最终 String 用户 ID 的策略。
- [ ] 删除用户、角色、岗位、部门、部门负责人、用户组等本地候选人策略及其校验。
- [ ] 删除 `AdminUserApi`、`DeptApi`、`RoleApi`、`PostApi`、`PermissionApi` 注入。
- [ ] 响应中返回原始 ID，不再由 BPM 补全昵称、部门名称或本地用户信息。

### 4.3 处理 system 绑定的可选功能

- [ ] 本地短信、邮件、站内信改为 Portal Webhook，或先禁用。
- [ ] 流程管理员、发起人白名单、部门白名单改为 Portal 授权，或移入独立 BPM 配置。
- [ ] 自动审批、撤回、转办、委派等能力的主体比较全部支持 String ID。

**验收：** 删除/屏蔽 `system_*` 数据后，服务不发出本地用户、组织、角色、菜单 SQL；核心 walkthrough 仍通过。

## 5. 阶段 2：收敛 BPM 功能与 BPM 扩展表

**保留：**

| 表 | 原因 |
| --- | --- |
| `bpm_form` | 已发布流程的表单 Schema；Portal 发起前必须读取。 |
| `bpm_process_definition_info` | 流程定义的扩展配置、表单关联及运行期控制项。 |
| `bpm_category` | 短期保留流程分类的后台维护能力。 |
| `bpm_process_expression` | 短期保留流程表达式的后台配置能力。 |
| `bpm_process_listener` | 短期保留流程监听器的后台配置能力。 |
| `ACT_RE_*`、`ACT_RU_*`、`ACT_HI_*`、`ACT_GE_*` | Flowable 的模型、部署、运行、变量和历史审计。 |

**先移除代码，再删除表：**

| 表 | 前提 |
| --- | --- |
| `bpm_oa_leave` | 删除 OA 示例 Controller、Service、Mapper。 |
| `bpm_process_instance_copy` | Portal 负责抄送/通知，删除抄送监听器和 API。 |
| `bpm_user_group` | 已完成阶段 1 的本地用户组策略移除。 |

`bpm_process_definition_info` 后续可以裁剪本地用户/部门/管理员相关字段，但必须在替代行为上线后再迁移。

**验收：** 已下线 API 对应的 Mapper、Service、Controller 与数据库表均不存在；流程定义读取仍能返回 `bpm_form` Schema。

## 6. 阶段 3：解耦 infra

当前 BPM 源码没有直接依赖 `infra`；它经由 `system` 和 `yudao-server` 引入。因此必须在阶段 1 完成后再处理。

| Infra 能力 | Headless 替代 | 后续表 |
| --- | --- | --- |
| 参数配置 | YAML、环境变量、密钥服务 | `infra_config` |
| 文件与附件 | Portal 文件服务；BPM 只保存业务引用 | `infra_file*` |
| Quartz 定时任务 | Flowable 自身 `ACT_RU_TIMER_JOB` 等能力 | `infra_job*`、`qrtz_*` |
| API 审计日志 | 应用日志、OTel 或外部日志平台 | `infra_api_*` |
| 代码生成、动态数据源 | 不提供 | `infra_codegen_*`、`infra_data_source_config` |

**验收：** 从 `yudao-server` 删除 `yudao-module-infra` 后，核心 API 可启动并通过 walkthrough；无 `infra_*` SQL。

## 7. 阶段 4：POM、依赖与数据库收尾

按以下顺序执行，禁止反向操作：

1. 移除 `yudao-module-bpm` 对 `yudao-module-system` 的依赖，并完成构建与 walkthrough。
2. 从 `yudao-server` 移除 `system`、`infra` 依赖；再从根 `pom.xml` 移除对应 Reactor 模块。
3. 清理 system/infra 配置、自动装配、测试夹具和无效 REST API。
4. 在新建的最小 PostgreSQL schema 上验证启动，确认仅创建保留表。
5. 备份生产数据后，以可回滚迁移删除候选表；不要以手工 `DROP TABLE` 替代迁移。

Flowable starter 当前可能自动引入 IDM 与 Event Registry。`ACT_ID_*`、`FLW_EVENT_*` 只能在精简依赖并验证相关引擎未启动后再清理，不能与 system 表一起直接删除。

## 8. 总体验收门槛

- [ ] 服务编译、打包成功。
- [ ] 最小 schema 中不存在 `system_*`、`infra_*`，核心 API 仍可启动。
- [ ] Portal 能读取流程定义与 `bpm_form` Schema。
- [ ] UUID 发起人和 UUID 审批人可完成发起、待办、审批、拒绝、轨迹查询。
- [ ] BPM 不查询 Portal 的用户、角色、部门数据，也不持有其副本。
- [ ] 删除表均由版本化迁移执行，且已有备份与回滚方案。
