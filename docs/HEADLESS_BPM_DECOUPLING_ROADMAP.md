# Headless BPM 解耦路线图

> 状态：In progress
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

### 1.1 范围锁定：替换依赖，不删除 BPM 业务能力

本路线图的“移除”仅指移除 BPM 对本地 `system` / `infra` 的数据读取、认证、通知投递和组织解算实现；**不授权删除已有 BPM 业务语义、Flowable 节点或运行 API**。下列能力必须保留，并通过 Portal 适配器完成外部协作：

| BPM 能力 | BPM 必须保留的职责 | Portal 替换的职责 |
| --- | --- | --- |
| 审批、会签、或签、依次审批、拒绝、回退、撤回 | Flowable 状态机、任务创建/取消、历史轨迹与权限时序 | 最终审批人解算、用户展示与身份授权 |
| 抄送节点 | 节点触发时机、流程上下文、抄送事件与查询 API | 收件人解算、消息投递、Portal 侧收件箱/展示 |
| 转办、委派、加签、减签 | 任务状态变更、审计记录、并行/串行规则 | 目标用户检索、可用性与权限校验 |
| 任务创建、完成、超时、流程结束等监听器 | 监听器执行时机及流程变量/状态回调 | 通知、业务回调和外部消息投递 |

任何删除 BPM 节点、监听器、Controller、Service 或 `bpm_*` 表的提案，都必须单独说明被替代的能力、Portal 接口、数据迁移、回归用例和明确审批；不得以“解耦 system”为理由直接删除。

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

目标运行时移除：`yudao-module-system`、`yudao-module-infra`，以及未启用的业务模块。当前 `yudao-server` 已仅依赖 BPM；安全 starter 不再调用 system OAuth2 或权限 API。

## 3. 阶段 0：冻结 API 与数据契约

**目标：** 在删除依赖前明确 Portal 与 BPM 的可信边界。

- [x] 定义 `BpmPortalPrincipal`：`userId: String`、可选 `tenantId: String`、`authorities`；Controller 与 Flowable Filter 经 `BpmPortalPrincipalUtils` 读取当前主体。（本地安全上下文适配已完成；生产可信 Principal 尚未实现）
- [ ] 由 BPM 网关或服务端验证 Portal JWT / mTLS，并提供 `PortalTenantApi`；不得从普通请求头读取用户 ID。（本地 Mock token 和固定租户仅用于 walkthrough，不能作为验收）
- [ ] 将运行 API 收敛为：流程定义/表单读取、创建流程、待办/已办、同意/拒绝、审批轨迹；流程图维护 API 仅开放给定义管理员。
- [ ] 定义流程发起契约：`processDefinitionId`（或解析后的 key/version）、`businessKey`、路由变量、`startUserSelectAssignees`。
- [ ] 约定 `startUserSelectAssignees` 中只允许最终用户 String ID；Portal 在调用前完成角色到用户的展开。

**验收：** 以 UUID 用户 ID 完成“读取表单 → 发起 → 待办 → 同意/拒绝 → 查询轨迹”闭环。

## 4. 阶段 1：解耦 system 身份、权限和组织

**目标：** BPM 不再读取本地用户、部门、岗位、角色或菜单权限。

Portal 适配接口、Mock 与后续 HTTP 实现的集中约定见 [PORTAL_ADAPTER_INTEGRATION_CONTRACT.md](PORTAL_ADAPTER_INTEGRATION_CONTRACT.md)。任何 system 依赖的替换必须先登记在该契约中，避免将 Portal 对接细节分散进候选人、Controller 和 Service。

### 4.1 身份与权限替换

- [x] 用 `BpmPortalPrincipalUtils` 替换 BPM Controller、Flowable Filter 中的 `getLoginUserId()` 及其 Long 类型调用链；模型维护与任务管理页不再传递 Long 登录 ID。生产可信 Principal 的构造仍待阶段 0 验签完成。
- [ ] 用签名 claims 的 `hasAuthority(...)` 或 API 网关策略替换 `@ss.hasPermission(...)`。
- [ ] 定义管理员权限，例如 `bpm:definition:manage`；运行用户仅能操作其 String ID 对应的任务。
- [x] 流程发起人、任务 owner/assignee、历史返回字段统一为 String。（任务运行接口、流程实例、抄送、用户投影、转办、委派、加签、减签、退回、撤回及定义白名单均已迁移；身份 claims 仍待迁移）

### 4.2 删除本地组织候选人能力

- [x] 仅保留 `START_USER_SELECT` 与 `HEADLESS_REMOTE`；策略枚举保留历史编号，但不再保留旧编号的实现。
- [x] 删除用户、角色、岗位、部门、部门负责人、用户组、表达式和本地空审批人等候选人策略及其校验；旧模型不再兼容。
- [x] 删除 BPM 引擎路径中的 `AdminUserApi`、`DeptApi`、`RoleApi`、`PostApi`、`PermissionApi` 注入。（候选人调用器、任务运行/变更、流程实例、抄送、模型列表、流程定义和转换器均改为 Portal 端口；旧 OA 请假样例已下线）
- [x] 响应中保留原始 ID，昵称、部门名称和头像只由 Portal 最小投影补全；目录缺失时展示对象可为空，调用方回退显示原始 ID。

### 4.3 处理 system 绑定的可选功能

- [ ] 保留任务创建、完成、超时、流程结束等通知触发语义；本地短信、邮件、站内信投递改为 `BpmPortalNotificationApi` / Portal Webhook。Portal 暂未接入时可 no-op 或记录待投递事件，但不得删除 BPM 事件或状态变更。
- [x] 流程管理员、发起人白名单、部门白名单改为 Portal String ID/组织目录解算；旧数值白名单须在生产切换前重新配置。请求级 Portal claims 授权仍属于阶段 1.1 未完成项。
- [x] 自动审批、撤回、转办、委派、加签、减签等能力的流程语义和审计记录保留；其主体比较、目标用户校验全部支持 String ID 并委托 Portal。发起人部门负责人转交改由 Portal `DEPT_LEADER_OF_USER` 解算。
- [x] 抄送节点、`BpmCopyTaskDelegate` 和抄送查询 API 保留；收件人使用 Portal String ID，查询展示由 Portal 组织目录投影。生产库需执行迁移脚本后上线。

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

**仅在业务能力被明确下线后，才可移除代码和表：**

| 表 | 前提 |
| --- | --- |
| `bpm_oa_leave` | 已下线旧 OA 请假示例，已归档为 `bak_bpm_oa_leave`，不直接删除以保留回滚数据。新业务按 [Portal 业务接入模板](PORTAL_BUSINESS_BPM_INTEGRATION_TEMPLATE.md) 实现。 |
| `bpm_process_instance_copy` | 当前不在删除范围。先保留抄送节点、监听器、查询 API 和审计记录；仅将收件人解算、投递和 Portal 收件箱替换为 Portal 适配器。未来若业务明确下线 BPM 抄送审计，再单独设计迁移和删除。 |
| `bpm_user_group` | 已完成阶段 1 的本地用户组策略移除，已归档为 `bak_bpm_user_group`。 |

`bpm_process_definition_info` 已将用户、部门和子流程管理员字段改为 Portal String ID；逗号分隔列的物理类型无需变化。旧数值白名单不允许自动映射，必须在 Portal 侧确认后重新保存。

**验收：** 已下线 API 对应的 Mapper、Service、Controller 与数据库表均不存在；流程定义读取仍能返回 `bpm_form` Schema。

## 6. 阶段 3：解耦 infra

当前 BPM 源码没有直接依赖 `infra`。`yudao-module-bpm` 对 `yudao-module-system` 的直接 Maven 依赖已移除，因而不再由 BPM 模块传递引入 `infra`；`yudao-server` 仍暂时保留两者，必须在生产认证与后台定义维护入口完成迁移后再处理。

| Infra 能力 | Headless 替代 | 后续表 |
| --- | --- | --- |
| 参数配置 | YAML、环境变量、密钥服务 | `infra_config` |
| 文件与附件 | Portal 文件服务；BPM 只保存业务引用 | `infra_file*` |
| Quartz 定时任务 | Flowable 自身 `ACT_RU_TIMER_JOB` 等能力 | `infra_job*`、`qrtz_*` |
| API 审计日志 | 应用日志、OTel 或外部日志平台；当前 Web starter 经 `PortalApplicationLogApi` 输出待投递事件，不写本地表 | `infra_api_*` |
| 部门数据权限 | Portal 在调用 BPM 前完成数据范围授权；BPM 不执行本地部门规则 | `system_dept`、本地部门权限关系 |
| 代码生成、动态数据源 | 不提供 | `infra_codegen_*`、`infra_data_source_config` |

**验收：** 从 `yudao-server` 删除 `yudao-module-infra` 后，核心 API 可启动并通过 walkthrough；无 `infra_*` SQL。

## 7. 阶段 4：POM、依赖与数据库收尾

按以下顺序执行，禁止反向操作：

1. [x] 移除 `yudao-module-bpm` 对 `yudao-module-system` 的依赖，并完成构建与 walkthrough。
2. [x] 从 `yudao-server` 和根 `pom.xml` 移除 `system`、`infra` 依赖与 Reactor 模块；源码目录暂保留在仓库中，不参与 headless 默认构建。
3. 清理 system/infra 配置、自动装配、测试夹具和无效 REST API。
4. 在新建的最小 PostgreSQL schema 上验证启动，确认仅创建保留表。
5. 备份生产数据后，以可回滚迁移将候选表改名为 `bak_` 前缀；不要以手工 `DROP TABLE` 替代迁移。

Flowable starter 当前可能自动引入 IDM 与 Event Registry。`ACT_ID_*`、`FLW_EVENT_*` 只能在精简依赖并验证相关引擎未启动后再清理，不能与 system 表一起直接删除。

## 8. 总体验收门槛

- [ ] 服务编译、打包成功。
- [ ] 最小 schema 中不存在 `system_*`、`infra_*`，核心 API 仍可启动。
- [ ] Portal 能读取流程定义与 `bpm_form` Schema。
- [ ] UUID 发起人和 UUID 审批人可完成发起、待办、审批、拒绝、轨迹查询。
- [ ] BPM 不查询 Portal 的用户、角色、部门数据，也不持有其副本。
- [ ] 删除表均由版本化迁移执行，且已有备份与回滚方案。
