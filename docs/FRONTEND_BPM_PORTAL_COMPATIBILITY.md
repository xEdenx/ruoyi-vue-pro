# BPM 前端与 Portal 兼容性约定

> 状态：In progress
>
> 本文规定 Headless BPM 解耦期间的前端兼容策略。它与 [Portal 适配契约](PORTAL_ADAPTER_INTEGRATION_CONTRACT.md) 配套使用：前者定义 BPM 与 Portal 服务间的边界，本文定义现有 Vue 管理端和未来 Portal 前端的边界。

## 1. 当前事实

仓库中的 `yudao-ui-admin-vue3` 是 RuoYi 管理端，不是最终业务 Portal。它仍包含部分本地用户选择页面（转办、委派、加签等），但流程建模已经收敛：

- 审批节点仅可选择“发起人自选”或“Portal 远程候选人”；不再显示本地用户、角色、岗位、部门、用户组、表达式或审批人自选策略；
- “Portal 远程候选人”只编辑 Portal 规则编码/选择器，不请求任何 system 组织接口；
- 转办、委派、加签等页面必须通过 Portal 用户选择器提供目标用户；后端已仅接受 Portal String ID；
- 模型的 `startUserIds`、`startDeptIds`、`managerUserIds` 已改为 Portal String ID；
- 直接显示 BPM 响应中的 `startUser`、`assigneeUser`、`ownerUser`、`candidateUsers` 的昵称、头像和部门名称；
- 通过前端权限 `bpm:*` 控制管理菜单和按钮。

因此不能在后端改用 Portal String ID 后直接删除这些字段或让页面改读 Portal 私有 API，否则当前管理端会出现空审批人、选择器失效或 TypeScript 类型错误。

## 2. 双轨兼容策略

| 调用方 | 身份/组织来源 | 用户 ID | 用户展示 | 当前处理 |
| --- | --- | --- | --- | --- |
| 旧 RuoYi 管理端页面 | 原 system 用户选择器 | 数值 ID（历史兼容） | 仅可作为前端迁移参考 | 当前 headless server 已移除 system 登录与组织 API，页面不可作为可用运行路径 |
| 外部 Portal | Portal 用户目录 | 原始 String ID | Portal 自己的用户字典；BPM 可选返回最小投影 | Headless 主路径 |

前端 API 类型允许 `string | number` 只是一项过渡措施，保证旧管理端不会因类型改变立即中断。新的 Portal 集成必须只使用 String ID，且不得依赖将 ID 转为数字。

## 3. 稳定的 BPM 响应形状

在迁移过程中，下列对象继续保留，字段语义不变：

```ts
type BpmUser = {
  id: string | number
  nickname: string
  avatar?: string
  deptId?: string | number
  deptName?: string
}
```

涉及对象：

- `processInstance.startUser`
- `processInstance.tasks[].assigneeUser`
- `task.assigneeUser`、`task.ownerUser`
- 审批详情 `activityNodes[].tasks[]` 的 `assigneeUser` / `ownerUser`
- 审批详情 `activityNodes[].candidateUsers`

在 Portal 用户资料暂时无法读取时，BPM 至少应保留 Flowable 原始的 `startUserId`、`assignee` 或 `owner`。现有页面必须将用户投影视为可空，展示 `nickname ?? id`，而不是假定 `nickname` 必定存在。

## 4. 前端改造顺序

1. **类型兼容**：所有 BPM 用户 ID 类型改为 `string | number`；发起请求的 `startUserSelectAssignees` 接受该联合类型。
2. **展示兜底**：流程列表、详情时间线、任务表格使用 `nickname || id`，部门、头像可空。
3. **选择器抽象**：把 `UserSelectForm` 的调用封装为 BPM 用户选择接口。RuoYi 管理端实现调用本地选择器；Portal 实现调用 Portal 用户选择器。
4. **权限边界**：页面按钮权限从对本地菜单 `bpm:*` 的唯一依赖，逐步改为可信 Portal claims；当前本地 Headless Mock 只用于开发，生产由 Portal claims 替换。
5. **遗留页面迁移**：Portal 页面完成发起、动态选人、待办、同意/拒绝、轨迹展示；旧管理端对应功能要么迁移到 Portal，要么从前端构建中下线。

## 5. 本轮已做的兼容调整

- `src/api/bpm/processInstance/index.ts` 的 BPM 用户 ID 改为 `BpmUserId = string | number`，并补齐可选部门字段。
- 请假发起页的 `startUserSelectAssignees` 改为接收 String 或数值用户 ID；后端已使用 `Map<String, List<String>>` 接收该字段。
- 取消流程实例 API 的实例 ID 类型更正为 Flowable 实际使用的 `string`。
- 登录页只保留“Headless BPM Portal 登录”，调用 BPM 的 `/portal-auth/login` 和 `/portal-auth/me`；普通账号、短信、二维码、注册、找回密码、SSO、社交登录及其路由均不再注册。管理端不调用 `/system/auth/*`，也不加载 system 字典。
- 登录成功后进入 `/bpm/task/todo`。路由启动时只注册登录与错误页，登录后由 Portal 角色动态注入“工作流程”菜单：运行用户获得“审批中心”，带 `ROLE_BPM_MODEL_MANAGER` 的 Mock 用户获得完整 BPM 管理菜单（流程管理、审批中心）。本地 Mock 中该角色可调用 `bpm:*` API；生产必须由 Portal claims 精确替换，不得沿用此宽泛开发权限。当前 server 已移除 system 认证与动态菜单 API。
- 前端对同一 Mock 角色投影 `*:*:*`，仅用于让既有 BPM 页面中的 `v-hasPermi` 按钮与后端的 `bpm:*` Mock 授权一致；它不会放行 backend 的 system API，也不能作为生产权限模型。
- Headless 本地登录不挂载顶部的 system 站内信铃铛，也不轮询 `/system/notify-message/*`。该入口属于已移除的 system 通知能力；未来由 Portal 的通知中心或 Portal Webhook 投递能力替换，不能为兼容该管理端而在 BPM 服务中恢复旧 API。
- Headless 本地登录不挂载租户切换控件，也不请求 `/system/tenant/*`；租户边界由登录时的 Portal claims 与 BPM 的 `PortalTenantApi` 校验。静态工作流程菜单排除已下线的 OA 请假示例和本地用户分组，避免进入仍依赖 system 数据的遗留页面。
- Headless 字典存储从 `/bpm/portal-config/dict-data/simple-list` 获取 BPM 状态和建模所需的固定枚举，禁止状态标签或表单控件回退请求 `/system/dict-data/*`。候选人策略选择器始终直接从该 BPM 配置 API 读取完整策略目录，前端不维护策略白名单，也不依据 Spring 当前是否注册策略进行过滤；策略是否可执行由后端发布、运行时校验。默认实现投影 BPM 自己的枚举，不读取或同步 system 字典；未来由 Portal 配置时，直接替换后端 `BpmPortalConfigurationApi` 实现，前端无需改动。
- BPM 建模、流程管理、审批详情和动态选人控件统一从 `/bpm/portal-directory/simple-list` 读取可选择用户/部门；地区打印选项从 `/bpm/portal-config/area-tree` 读取。前端不保留 system 回退路径；生产 Portal 只需替换组织目录/配置适配器并按调用方权限过滤结果。
- RuoYi 全局 `UserVO` 仍保留数值 `id`/`deptId`，避免影响大量非 BPM 页面；Headless 模式中该全局投影只用于展示名称，Portal 原始 String ID 始终保留在 Bearer token 与 BPM API 链路中。未来 Portal 页面不能依赖此管理端全局 `UserVO`。
- 模型管理页面的字段名不变，但 `startUserIds`、`startDeptIds`、`managerUserIds` 的元素已稳定为 String。现有管理端在编辑这些字段前需将其 TypeScript 类型改为 `BpmUserId`/`string`，并使用 Portal 选择器；不得再把它们转换为数值。模型列表仍返回 `startUsers`、`startDepts` 展示投影，目录缺失时页面应回退显示原始 ID。

## 6. 联调验收

- 使用 `portal-requester-a1f2`、`portal-supplier-e7f8` 等非数值 ID 跑 V5 walkthrough。
- 页面不因用户 ID 为 String 而报 TypeScript 或渲染错误。
- 一个会签节点返回多个 Portal 用户时，时间线正确展示多个候选人或任务。
- 新建或重新发布的流程可从后端返回的完整候选策略目录中选择；若所选策略没有后端实现，发布校验必须拒绝并明确提示，前端不做实现状态推断或静态过滤。
