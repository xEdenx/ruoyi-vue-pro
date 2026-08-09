# 外部门户 (Portal) 对接 BPM 无头工作流中台架构设计与 API 方案

本文档针对**“外部门户/Portal 系统作为全量用户 UI 入口，BPM 平台作为无头工作流中台 (Headless BPM Engine)”**的场景，提供完整的技术架构解法与 API 对接规范。

文档索引:
- [基础架构决策: ADR-000 无头工作流中台与零用户同步架构](file:///Users/eden/Documents/coding/ruoyi-vue-pro/docs/adr/ADR_000_HEADLESS_BPM_ZERO_USER_SYNC_ARCHITECTURE.md)
- [演进架构决策: ADR-001 远程候选人解算策略](file:///Users/eden/Documents/coding/ruoyi-vue-pro/docs/adr/ADR_001_HEADLESS_REMOTE_CANDIDATE_STRATEGY.md)

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

### 2.1 不维护与同步 `system_users` 机制
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
1. **接受动作请求**：Portal 调 BPM 的 `POST /admin-api/bpm/task/approve` 接口（传入 `taskId`）；
2. **引擎中转计算**：BPM 平台内部触发 Flowable 的 `taskService.complete(taskId)`：
   - **完成旧任务**：在数据库 `act_ru_task` 与 `act_hi_taskinst` 中将当前任务标志为已完成；
   - **自动计算连线**：Flowable 解析下一个连线（SequenceFlow），代入之前的参数变量计算分支表达式；
   - **生成新待办**：自动在目标节点插入新的待办任务（并将待办指派给 Portal 动态传过来的新审批人/角色 ID）。
3. **完成流转**：这一系列复杂的**“节点跳跃、状态变迁、生成新待办”**全都是 Flowable 自动搞定的，Portal 无需感知任何底层细节。

### 3.2 Portal 获取中转结果的 2 种机制
- **被动拉取 (API 轮询/页面刷新)**：
  下一个审批人登录 Portal 时，Portal 调 BPM 的 `GET /admin-api/bpm/task/todo-page` 接口，Flowable 引擎把刚才中转生成的新待办返回给 Portal 展示出来。
- **主动推送 (Webhook HTTP 回调 / 消息队列 MQ)**：
  在 BPM 平台的 Flowable 引擎完成中转（节点切换或流程结束）的瞬间，BPM 平台可以通过 **Webhook** 主动推一条消息给 Portal（如：`"单据 PORTAL_ORDER_001 已由部门经理审批通过，当前流转至 HR 节点！"`）。

---

## 四、 动态审批人与角色指派解法

### 4.1 BPMN 流程图设计规范（零硬编码）
在 BPM 平台拖拽流程图时：
1. 若 Portal 在发起时已确定最终用户 ID，使用 **【发起人自选 (START_USER_SELECT, 35)】**，由请求中的 `startUserSelectAssignees` 传入；
2. 若节点到达时才需依据 Portal 当前角色、部门或业务规则解算，使用 **【HEADLESS_REMOTE (70)】**，并在节点 `candidateParam` 中填写 Portal 原生规则 Key；
3. 流程图节点不绑定 BPM 本地用户 ID、角色 ID 或部门 ID。

### 4.2 Portal 发起流程时的动态 JSON 数据包
当用户在 Portal 中填完表单，并为策略 35 的节点选好最终人员后，Portal 调用 BPM 发起接口。策略 70 的节点不传具体用户 ID，而是在任务实际到达时由 Portal 解算：

* **请求地址**：`POST /admin-api/bpm/process-instance/create`
* **请求体 (JSON)**：

```json
{
  "processDefinitionKey": "portal_purchase_flow",
  "businessKey": "portal_order_20260807_001",
  "variables": {
    "amount": 8000,
    "title": "采购办公电脑",
    "portal_form_id": "FORM_99812"
  },
  "startUserSelectAssignees": {
    "Activity_Node1": ["102"]
  }
}
```

---

## 五、 Portal 与 BPM 平台的 4 大核心 API 对接规范

后端 `ruoyi-vue-pro` 已经内置了全套标准 API，Portal 只需要集成以下 4 个接口：

### 3.0 流程定义发布部署 API（无头 Headless 专用）
- **接口路径**: `POST /admin-api/bpm/process-definition/deploy-xml`
- **Content-Type**: `application/json`
- **认证与授权**: 该入口不额外声明 BPM 菜单/角色权限；认证通过后即可调用。为保持与既有模型 API 一致，更新或发布已有模型仍会校验请求人的 `systemUserId` 是否在 `managerUserIds` 中，禁止伪造固定维护用户。
- **说明**: 这是“一键保存并发布”组合 API，不是原生 XML 文件上传 API。它按现有生命周期执行：
  `createModel`（无 `id`）或 `updateModel`（有 `id`）→ `deployModel`。
  因此会保留 BPMN 合法性、表单配置、候选人策略和模型管理人校验，并自动挂起旧版本。
- **请求参数**: 请求体与模型保存 API 完全一致，为 `BpmModelSaveReqVO`。`bpmnXml` 是 BPMN XML 文本；`type` 固定为 `10`（BPMN）；新建时不传 `id`，更新时传已有 Flowable model ID。
- **最小可部署示例**（`formId` 必须是已存在的 `bpm_form` 主键，且 `managerUserIds` 包含当前认证用户的兼容本地 ID）：
  ```json
  {
    "key": "office_supplies_request_v5",
    "name": "办公用品申请流程 V5",
    "category": "无",
    "type": 10,
    "formType": 10,
    "formId": 1,
    "visible": true,
    "managerUserIds": [102],
    "bpmnXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions>...</definitions>"
  }
  ```
- **调用示例**:
  ```bash
  curl -X POST 'http://127.0.0.1:48080/admin-api/bpm/process-definition/deploy-xml' \
    -H 'Authorization: Bearer <Portal_JWT>' \
    -H 'Content-Type: application/json' \
    --data @deploy-office-supplies-v3.json
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
Portal 将返回的 `processInstanceId` 保存到本地业务表中。

### 3.1.1 临时 Portal JWT 授权边界

本地 walkthrough 使用 JWT payload 中的 `role` 或 `roles` 模拟 Portal 角色。无头 BPM 的这条临时链路不查询 `system_user_role`：

- JWT 必须包含非空角色声明；无角色声明直接拒绝。
- 具备角色声明的 Portal JWT 仅可访问流程发起/详情和待办查询/办理所需的 BPM 权限：`bpm:process-instance:query`、`bpm:task:query`、`bpm:task:update`。
- 它不能访问 `system:*` 或其他非 BPM 管理权限。

真实 Portal 接入时，应替换为已验证 JWT 及 Portal 自己的权限策略；不得再绑定本地 `system_user`、`system_role` 或 `system_user_role`。

本地无头配置还启用 `yudao.bpm.headless.enabled=true`：流程完成、拒绝、任务分配和超时不会调用本地 `system` 短信服务。Portal 负责接收状态变化并自行通知业务用户。

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
- **HTTP 方法**：`POST`
- **接口路径**：`/admin-api/bpm/task/approve`
- **请求体 (JSON)**：
```json
{
  "id": "99c71ce6-9209-11f1-93d1-5e4a54cbb279", // taskId
  "reason": "同意办理，符合要求"
}
```

#### 3.2 点击【拒绝 / 终止】
- **HTTP 方法**：`POST`
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
        "tasks": [{ "assignee": 1, "endTime": 1786070535000 }]
      },
      {
        "id": "lm_pass",
        "name": "部门经理审批",
        "status": 1, // 1代表进行中
        "tasks": [{ "assignee": 118 }]
      }
    ]
  }
}
```
Portal 前端拿此 JSON 匹配本地用户字典后，可直接渲染出带有用户姓名和部门的流程步骤条（Steps）。

---

## 六、 方案总结与最佳实践

1. **解耦性极强**：Portal 拥有 100% 的 UI 自由度与业务控制权，BPM 平台只做流程维护与底层 Flowable 状态机运算。
2. **零用户同步**：无需维护 `system_users`，Portal 独占用户数据源，BPM 仅按数字 ID 透明中转。
3. **免二次开发**：利用 BPM 框架内置的 `startUserSelectAssignees` 机制，无需手写后端监听器，直接在 API 发起时传入动态选人字典。

---

## 七、 后续解耦路线图

`system` / `infra` 的解耦目标、阶段门槛和数据库清理顺序见 [HEADLESS_BPM_DECOUPLING_ROADMAP.md](HEADLESS_BPM_DECOUPLING_ROADMAP.md)。在完成认证与本地组织依赖替换前，不得直接删除相关模块或数据库表。
