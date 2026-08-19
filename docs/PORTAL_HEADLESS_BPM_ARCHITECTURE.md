# 外部门户 (Portal) 对接 BPM 无头工作流中台架构设计与 API 方案

本文档针对**“外部门户/Portal 系统作为全量用户 UI 入口，BPM 平台作为无头工作流中台 (Headless BPM Engine)”**的场景，提供完整的技术架构解法与 API 对接规范。

文档索引:
- [基础架构决策: ADR-000 无头工作流中台与零用户同步架构](adr/ADR_000_HEADLESS_BPM_ZERO_USER_SYNC_ARCHITECTURE.md)
- [演进架构决策: ADR-002 Portal 角色候选人策略](adr/ADR_002_PORTAL_ROLE_CANDIDATE_STRATEGY.md)
- [安全架构决策: ADR-003 Headless BPM 对象级授权与参数篡改防护](adr/ADR_003_HEADLESS_BPM_OBJECT_AUTHORIZATION.md)
- [项目演示说明: 自研改造亮点与现场主线](HEADLESS_BPM_PROJECT_DEMO.md)
- [采购申请子流程演示: 节点、运行时与验收说明](PURCHASE_REQUISITION_SUBPROCESS_NODE_GUIDE.md)

---

## 一、 业务场景与架构定位

### 1.1 业务场景描述
- **Portal / 外部业务系统**：
  - 用户在 Portal 中填写业务表单；
  - 用户在 Portal 中**动态选择/指定审批人（用户或角色）**；
  - 用户在 Portal 中发起流程、查看我的待办/已办列表、点击【同意/拒绝】办理任务、查看流程可视化进度履历。
- **BPM 工作流中台 (RuoYi-Vue-Pro / Flowable)**：
  - 仅作为后台管理系统，由管理员在 WEB 界面在线绘制 BPMN 流程图、发布与停止流程；
  - 不做硬编码人员指派，完全通过 RESTful API 响应 Portal 的引擎计算与流转请求。

### 1.2 核心职责划分为：静态维护 + 动态中转

```text
┌──────────────────────────────────────────────────────────┐
│                     Portal 门户系统                       │
│  (负责: 界面渲染、表单展现、用户操作【点击同意/拒绝】)        │
└─────────────────────────────┬────────────────────────────┘
                              │  ① 调 API 发送【同意/拒绝】动作请求
                              ▼
┌──────────────────────────────────────────────────────────┐
│                    BPM 工作流中台                        │
│                                                          │
│  1. 静态维护：画图、发布/停止、版本控制                    │
│  2. 动态中转：基于内部 Flowable 引擎自动计算并推进节点流转   │
└──────────────────────────────────────────────────────────┘
```

---

## 二、 零用户同步架构设计 (Zero User Sync)

### 2.1 不维护与同步本地用户主数据机制
在无头中台架构下，**BPM 平台侧完全不需要同步或维护 Portal 的用户信息**：
- **Portal 是唯一数据源头 (Single Source of Truth)**：所有的用户、角色、部门维护完全在 Portal 端完成；
- **BPM 平台仅处理透明 String ID**：Flowable 引擎底层（`act_ru_task`、`act_hi_procinst`、`act_ru_variable`）按原样存储 Portal 用户 ID（如 `assignee_ = "b943f25d-4064-4f5f-8b8f-70437e4d6fd3"`），不转换为本地用户 ID；
- **免二次开发**：摒弃复杂的跨系统用户同步机制（如 ETL、MQ 同步、定时任务拉取），避免数据不一致隐患。

### 2.2 Portal 前端 UI 姓名渲染映射流程
对于审批履历、待办列表的展示，数据与 UI 的渲染职责完全交给 Portal：

```text
[ BPM 平台 API ] ➔ 返回结构化 JSON 数据（保留 Portal 原始字符串 ID）：
{
  "taskId": "99c71ce6",
  "assignee": "b943f25d-4064-4f5f-8b8f-70437e4d6fd3",
  "status": "RUNNING"
}
       │
       ▼ (Portal 收到 JSON 数据)
[ Portal 前端 ] ➔ 拿着该 ID 匹配 Portal 本地用户字典 ➔ 渲染 UI："待张三审批 (研发部)"
```

---

## 三、 流程中转与 Flowable 引擎驱动原理

当 Portal 里的审批人在页面上点击【同意】或【拒绝】时，**流程节点的中转、计算与状态流转 100% 发生在 BPM 平台内部的 Flowable 引擎中**：

### 3.1 BPM 内部中转执行步骤
1. **接受动作请求**：Portal 调 BPM 的 `PUT /admin-api/bpm/task/approve` 接口（请求体传入 `id`）；
2. **引擎中转计算**：BPM 平台内部触发 Flowable 的 `taskService.complete(taskId)`：
   - **完成旧任务**：在数据库 `act_ru_task` 与 `act_hi_taskinst` 中将当前任务标志为已完成；
   - **自动计算连线**：Flowable 解析下一个连线（SequenceFlow），代入之前的参数变量计算分支表达式；
   - **生成新待办**：自动在目标节点插入新的待办任务（并将待办指派给 Portal 动态传过来的新审批人/角色 ID）。
3. **完成流转**：这一系列复杂的**“节点跳跃、状态变迁、生成新待办”**全都是 Flowable 自动搞定的，Portal 无需感知任何底层细节。

### 3.2 Portal 获取中转结果的 2 种机制
- **被动拉取 (API 轮询/页面刷新)**：
  下一个审批人登录 Portal 时，Portal 调 BPM 的 `GET /admin-api/bpm/task/todo-page` 接口，Flowable 引擎把刚才中转生成的新待办返回给 Portal 展示出来。
- **主动推送（显式配置后）**：
  为流程模型配置流程后置 HTTP 触发器后，BPM 可以回调 Portal。默认 `BpmPortalNotificationApi` 只记录待投递日志，不会发送 Webhook 或消息；生产环境必须提供实际的 HTTP/mTLS 或消息实现。

---

## 四、 动态审批人与角色指派解法

### 4.1 BPMN 流程图设计规范（零硬编码）
在 BPM 平台拖拽流程图时：
1. 若 Portal 在发起时已确定最终用户 ID，使用 **【发起人自选 (START_USER_SELECT, 35)】**，由请求中的 `startUserSelectAssignees` 传入；
2. 若节点到达时需要按 Portal 目标角色解算，使用 **【ROLE (70)】**，并在节点 `candidateParam` 中填写 Portal 角色编码；
3. 流程图节点不绑定 BPM 本地用户 ID、角色 ID 或部门 ID。

### 4.2 Portal 发起流程时的动态 JSON 数据包
当用户在 Portal 中填完表单，并为策略 35 的节点选好最终人员后，Portal 调用 BPM 发起接口。策略 70 的节点不传具体用户 ID，而是在任务实际到达时由 Portal 解算：

* **请求地址**：`POST /admin-api/bpm/process-instance/create`
* **请求体 (JSON)**：

```json
{
  "processDefinitionId": "portal_purchase_flow:3:abc",
  "variables": {
    "amount": 8000,
    "title": "采购办公电脑",
    "portal_form_id": "FORM_99812"
  },
  "startUserSelectAssignees": {
    "Activity_Node1": ["portal-manager-b3c4"]
  }
}
```

---

## 五、 Portal 与 BPM 平台的 4 大核心 API 对接规范

后端 `ruoyi-vue-pro` 已经内置了全套标准 API，Portal 只需要集成以下 4 个接口：

### 3.0 流程定义发布部署 API（无头 Headless 专用）
- **接口路径**: `POST /admin-api/bpm/process-definition/deploy-xml`
- **Content-Type**: `multipart/form-data`
- **认证与授权**: 该入口不额外声明 BPM 菜单/角色权限；认证通过后即可调用。更新、发布、停用和删除已有模型时，BPM 通过 `BpmPortalIdentityApi` 向 Portal 校验请求人的角色是否命中模型的 `managerRoleCodes`；不会读取本地用户、角色或部门表。
- **说明**: 这是“一键保存并发布”组合 API，不是原生 XML 文件上传 API。它按现有生命周期执行：
  `createModel`（无 `id`）或 `updateModel`（有 `id`）→ `deployModel`。
  因此会保留 BPMN 合法性、表单配置、候选人策略和模型管理人校验，并自动挂起旧版本。
- **请求参数**: `model` part 是 JSON 格式的 `BpmModelSaveReqVO`；`file` part 是 UTF-8 编码的 BPMN XML 文件。接口从上传文件读取 BPMN，不接受 `model.bpmnXml` 传入的 XML 文本；`type` 固定为 `10`（BPMN）；新建时不传 `id`，更新时传已有 Flowable model ID。
- **最小可部署示例**（先以稳定的 `bpm_form.code` 查询当前环境的 `formId`；`managerRoleCodes` 是 Portal 维护角色）：
  ```text
  key=office_supplies_request_v5
  name=办公用品申请流程 V5
  category=default
  type=10
  formType=10
  formId=<按 office_supplies_request_v5_form 查询得到的当前数据库 ID>
  visible=true
  managerRoleCodes=[ROLE_BPM_MODEL_MANAGER]
  ```
- **调用示例**:
  ```bash
  FORM_CODE='office_supplies_request_v5_form'
  FORM_ID=$(curl -sS 'http://127.0.0.1:48080/admin-api/bpm/form/list-all-simple' \
    -H 'Authorization: Bearer <Portal_JWT>' \
    | jq -er --arg formCode "${FORM_CODE}" \
      '[.data[] | select(.code == $formCode)] | if length == 1 then .[0].id else error("form code must match exactly once") end')

  curl -X POST 'http://127.0.0.1:48080/admin-api/bpm/process-definition/deploy-xml' \
    -H 'Authorization: Bearer <Portal_JWT>' \
    -F "model={\"key\":\"office_supplies_request_v5\",\"name\":\"办公用品申请流程 V5\",\"category\":\"default\",\"type\":10,\"formType\":10,\"formId\":${FORM_ID},\"visible\":true,\"managerRoleCodes\":[\"ROLE_BPM_MODEL_MANAGER\"]};type=application/json" \
    -F 'file=@script/bpmn/office_supplies_request_v5.bpmn.xml;type=application/xml'
  ```
- **响应示例**:
  ```json
  {
    "code": 0,
    "data": "office_supplies_request_v5:1:1001",
    "msg": "操作成功"
  }
  ```

### 3.1 发起流程接口
- **HTTP 方法**：`POST`
- **接口路径**：`/admin-api/bpm/process-instance/create`
- **Header 认证**：`Authorization: Bearer {token}`
- **响应示例**：
```json
{
  "code": 0,
  "data": "9987c9e1-9209-11f1-93d1-5e4a54cbb279", // 返回生成的流程实例 ID
  "msg": "成功"
}
```
Portal 将响应 `data` 中的流程实例 ID 作为 `processInstanceId` 保存到本地业务表中。当前管理端发起 API 不接收 `businessKey`；Portal 应维护自己的业务主键与该 ID 的映射。

### 3.1.1 临时 Portal JWT 授权边界

本地 walkthrough 使用 JWT payload 中的 `role` 或 `roles` 模拟 Portal 角色。无头 BPM 的这条临时链路不查询 `system_user_role`：

- JWT 必须包含非空角色声明；无角色声明直接拒绝。
- 具备角色声明的 Portal JWT 可取得流程发起、查询和待办办理所需的 BPM **功能权限**：`bpm:process-instance:query`、`bpm:task:query`、`bpm:task:update`。这不应被理解为可读取任意流程实例；当前读取入口的实例级授权尚待按 3.1.2 实施。
- 它不能访问 `system:*` 或其他非 BPM 管理权限。

真实 Portal 接入时，应替换为已验证 JWT 及 Portal 自己的权限策略；不得再绑定本地 `system_user`、`system_role` 或 `system_user_role`。

本地 mock 由 `yudao.bpm.headless-mock.enabled=true` 控制。流程通知当前默认只记录待投递日志；生产环境必须以 `BpmPortalNotificationApi` 的 HTTP/mTLS 或消息实现替换它，才能向 Portal 实际投递状态变化。

### 3.1.2 实例级数据访问授权（防参数篡改）

`@PreAuthorize` 校验的是“调用者能否使用一类 BPM API”的**功能权限**，不是“调用者能否查看某一个流程实例”的**数据权限**。因此不能以隐藏 Portal 按钮、前端不暴露实例 ID，或仅信任 Portal 调用方作为安全边界；调用者替换 `processInstanceId`、`taskId`、评论或附件关联 ID 后，BPM 仍必须在服务端拒绝越权访问。

目标调用链如下：

```text
可信 Portal 身份
  -> @PreAuthorize（功能权限）
  -> @BpmInstanceAccess（Controller 切面，防止漏接入）
  -> BpmProcessInstancePermissionService（唯一授权裁决点）
  -> Flowable 实例/历史任务/抄送事实 + Portal 业务数据范围判定
  -> 构造并返回响应
```

- **切面只作入口覆盖，不承载规则**：读取单个资源的 API 标注 `@BpmInstanceAccess`，由切面提取 `processInstanceId` 或先由 `taskId` 反查实例后调用权限服务。授权规则不得分散到 SpEL、Controller 或前端；Service/异步调用也必须显式调用同一权限服务。
- **授权规则由权限服务统一实现**：普通用户是否可读由流程发起人、当前/历史审批参与人、抄送接收人等 Flowable/BPM 持久化事实决定；跨部门、项目成员、业务单据状态等业务范围通过 Portal 的失败关闭授权适配器判定。全局查看/管理必须是独立、最小授予的 Portal claim，不能因为“拥有任一 Portal 角色”自动获得。
- **读、办分离**：`approve`、`reject`、退回、转办等仍必须校验任务 `assignee` 与当前 Portal 原始 String ID 一致；“可以查看实例”绝不等于“可以办理任务”。
- **覆盖所有派生资源**：实例详情、审批轨迹、BPMN 视图、打印数据、任务/评论列表、附件下载等，只要可由外部参数定位到某个实例或任务，均先进行实例级授权。拒绝访问时返回与资源不存在等价的业务错误，并记录不含表单内容的审计事件，避免通过 ID 探测实例是否存在。
- **分页必须在查询阶段限缩**：我的流程、待办、已办、抄送等列表通过 Flowable/持久层条件构造授权范围；不得先取一页全量数据再在内存过滤，否则 `total`、分页空洞和耗时仍会泄露数据。
- **字段权限不是数据访问授权**：BPMN 中的 `formFieldsPermission` 当前是表单展示/编辑元数据。实例级授权通过前不得返回流程变量、表单内容或附件；若同一实例对不同主体还需字段级保密，Portal 或 BPM 响应组装层必须按已验证的字段策略脱敏/剔除字段。

当前代码已对待办办理使用 `assignee` 校验，但实例详情、审批轨迹、BPMN 视图、打印数据和按实例查询任务等读取入口尚未统一接入上述 `BpmProcessInstancePermissionService`。本节是待实施的安全设计，不应将现状视为已完成的数据权限能力。

### 3.1.3 Portal 适配器替换点

为使接入真实 Portal 时不改动 Flowable 流转逻辑，BPM 通过身份、组织目录、固定配置、角色候选人、请求主体、通知和应用日志等适配端口与 Portal 协作。所有流程身份 ID 均为原始 `String`，接口中不应重新引入本地用户、角色或部门主数据。完整端口清单和切换条件以《Portal 适配契约》为准；其中最直接影响流程流转的是：

| 场景 | SPI 方法 | Portal 返回值 |
| --- | --- | --- |
| `ROLE` 节点到达时的审批人解算 | `PortalRoleCandidateStrategy.PortalRoleCandidateApi.resolveRoleAssigneeIds(startUserId, activityId, roleCode, processInstanceId)` | `Set<String>` 审批人 ID |
| 流程模型的管理人信息与角色校验 | `BpmPortalIdentityApi.getUser(userId)`；框架默认调用 `hasAnyRole(...)` | `PortalUser(id, displayName, departmentId, roleCodes)` |

生产接入应关闭 `yudao.bpm.headless-mock.enabled`，并提供可信请求主体、身份、组织目录和角色候选人实现；需要外部回调时还应替换通知实现。调用方和 BPMN 图均无需修改。若候选人适配器缺失、Portal 返回空审批人，或身份/组织实现未配置，服务会失败关闭，不会回退查询本地用户、角色或部门表。

真实 HTTP 实现应使用 BPM 与 Portal 约定的服务间凭证或已验证的用户委托凭证；不要把客户端随意传入的用户 ID 当作 Portal 身份事实。

---

### 2. 查询 Portal 登录用户的待办任务列表接口
- **HTTP 方法**：`GET`
- **接口路径**：`/admin-api/bpm/task/todo-page?pageNo=1&pageSize=10`
- **Header 认证**：`Authorization: Bearer {token}`（系统根据 Token 自动识别当前登录 Portal 用户）
- **响应示例**：
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "id": "99c71ce6-9209-11f1-93d1-5e4a54cbb279", // 任务 ID (taskId)
        "name": "部门经理审批",
        "processInstanceId": "9987c9e1-9209-11f1-93d1-5e4a54cbb279",
        "createTime": 1786070535000
      }
    ],
    "total": 1
  }
}
```

---

### 3. 办理审批（同意 / 拒绝）接口

#### 3.1 点击【同意 / 通过】
- **HTTP 方法**：`PUT`
- **接口路径**：`/admin-api/bpm/task/approve`
- **请求体 (JSON)**：
```json
{
  "id": "99c71ce6-9209-11f1-93d1-5e4a54cbb279", // taskId
  "reason": "同意办理，符合要求"
}
```

#### 3.2 点击【拒绝 / 终止】
- **HTTP 方法**：`PUT`
- **接口路径**：`/admin-api/bpm/task/reject`
- **请求体 (JSON)**：
```json
{
  "id": "99c71ce6-9209-11f1-93d1-5e4a54cbb279", // taskId
  "reason": "预算超出，予以拒绝"
}
```

---

### 4. 查看流程流转进度与轨迹树接口
Portal 需要展示流程跑到了哪个节点、谁审批过了、审批意见是什么：
- **HTTP 方法**：`GET`
- **接口路径**：`/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=9987c9e1-9209-11f1-93d1-5e4a54cbb279`
- **响应示例**：
```json
{
  "code": 0,
  "data": {
    "status": 1, // 1:审批中, 2:已通过, 3:已拒绝
    "activityNodes": [
      {
        "id": "startNode",
        "name": "发起流程",
        "status": 2, // 2代表已完成
        "tasks": [{ "assignee": "portal-requester-a1f2", "endTime": "2026-08-14T10:20:30" }]
      },
      {
        "id": "lm_pass",
        "name": "部门经理审批",
        "status": 1, // 1代表进行中
        "tasks": [{ "assignee": "portal-manager-b3c4" }]
      }
    ]
  }
}
```
Portal 前端拿此 JSON 匹配本地用户字典后，可直接渲染出带有用户姓名和部门的流程步骤条（Steps）。

---

## 六、 方案总结与最佳实践

1. **解耦性极强**：Portal 拥有 100% 的 UI 自由度与业务控制权，BPM 平台只做流程维护与底层 Flowable 状态机运算。
2. **零用户同步**：无需维护 `system_users`，Portal 独占用户数据源，BPM 仅按 Portal 原始字符串 ID 透明中转。
3. **免二次开发**：利用 BPM 框架内置的 `startUserSelectAssignees` 机制，无需手写后端监听器，直接在 API 发起时传入动态选人字典。

---

## 七、 后续解耦路线图

`system` / `infra` 的替代边界以 Portal 适配契约、实现和 walkthrough 为准；删除相关模块或数据库表前必须确认替代能力和回归验证均已完成。

截至 2026-08-19，路线图**未全部完成**。已完成的是 BPM 内部从本地 `system_*` 用户、角色、部门和数据范围依赖中退出，并建立可本地演练的适配端口；尚未完成的是接入真实 Portal 的生产实现及实例级数据授权。不得将本地 Mock walkthrough 通过视为生产 Portal 已解耦。

| 范围 | BPM 当前状态 | 生产完成条件 | 状态 |
| --- | --- | --- | --- |
| Portal 原始 String 用户 ID、发起人/任务办理人链路 | 已完成；Flowable 的 `startUserId`、`assignee`、`owner` 使用 Portal String ID | 保持 String ID 端到端，真实身份源通过验证 | 已完成（BPM 内部） |
| 本地用户、角色、部门、岗位选人依赖 | 已移除；仅保留策略 35 和 Portal 角色策略 70，缺少适配器时失败关闭 | Portal 提供组织目录及节点级角色候选人 HTTP/mTLS 实现 | 待接入生产 Portal |
| BPM 管理授权与请求主体 | 已有 `BpmPortalPrincipal`、Portal claims 适配及本地 Mock 登录 | 验证 Portal JWT 的签名、`iss`、`aud`、有效期，或采用受信任网关/mTLS 身份透传；按最小 claim 精确授权 | 待接入生产 Portal |
| 用户/部门展示与 Headless 配置投影 | 已有组织/配置适配端口和本地实现；关闭 Mock 时组织访问失败关闭 | Portal 提供受当前主体和租户约束的目录/配置实现 | 待接入生产 Portal |
| 角色候选人到最终审批人 | 已有 `PortalRoleCandidateApi` 和本地 Mock；空候选人失败关闭 | Portal 在节点到达时返回稳定、已授权的最终用户 ID 快照 | 待接入生产 Portal |
| 通知、抄送投递与应用审计 | BPM 保留事件触发；通知默认只写待投递日志 | HTTP/mTLS Webhook 或消息投递、失败策略和 Portal 审计/OTel 管道均已验证 | 待接入生产 Portal |
| 本地部门数据权限 | BPM 已不再读取本地部门权限 | Portal 的业务数据范围判定与 BPM 实例级授权服务/读取入口覆盖完成 | 待实施 |
| 参数篡改下的实例、任务、评论、附件读取 | 任务办理已有 `assignee` 校验；单实例读取尚无统一对象级授权 | 实现本章 3.1.2 的权限服务、切面、查询限缩、Portal 授权适配器与越权回归测试 | 待实施 |

完成判定：关闭 `yudao.bpm.headless-mock.enabled` 后，必须注册真实 Portal 适配器；身份、组织解算、通知和实例访问授权均失败关闭；使用至少两个无交集业务范围的 Portal 用户验证“替换任意实例/任务/附件参数均无法读取或办理他人资源”。在此之前，平台处于“Headless BPM 代码边界已就绪、本地 Mock 可验证”的阶段，而非生产 Portal 解耦完成。
