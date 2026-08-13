# Flowable 典型业务场景与 Headless BPM 解决方案指南

本文档用于归纳和记录常见复杂工作流业务场景在 **Flowable 官方标准机制** 下的设计思想，以及在 **`yudao-bpm` (Headless BPM 中台)** 架构下的落地实现方案。本文档随业务需求持续演进。

---

## 核心概念速查：`Assignee` vs `Owner`

| 概念 | 数据库字段 | 语义解释 | 典型场景 |
| :--- | :--- | :--- | :--- |
| **Assignee (任务办理人)** | `act_ru_task.ASSIGNEE_` | 当前**真正去点击【同意/拒绝】操作该任务的人** | 审批节点处理人、受委托人 |
| **Owner (任务所有人)** | `act_ru_task.OWNER_` | 该任务**最初归属的真正所有人** | 委派发起人、请假被代理人 |

* **正常状态**：`ASSIGNEE_ = UserA`，`OWNER_ = null`。
* **转派 (Transfer)**：`ASSIGNEE_` 变为 `UserB`，`OWNER_` 清空或变为 `UserB`（A 彻底失去任务）。
* **委派 (Delegate)**：`ASSIGNEE_` 变为 `UserB`，`OWNER_` 设为 `UserA`（任务暂时交给 B，但引擎仍记账 Owner 为 A）。

---

## 业务场景 1：用户请假 / 离岗代理 (存量交接 + 增量代理 + 双人可办 + 恢复收回)

### 1.1 业务诉求
1. **存量交接**：A 请假生效时，A 手头上所有**在办任务**交由 B 代理。
2. **增量代理**：休假期间所有**新流转到 A** 的任务，自动交由 B 代理。
3. **双人可办**：休假期间，A 和 B **均可**在待办列表中看到并办理该任务。
4. **取消收回**：A 销假/取消代理时，已被委派给 B 的任务**立即收回**，B 失去办理权限。

### 1.2 Flowable 官方标准做法
* 利用原生 `taskService.delegateTask(taskId, "UserB")` 设置 `OWNER_ = UserA`、`ASSIGNEE_ = UserB`。
* 待办查询使用 `.or().taskAssignee(userId).taskOwner(userId).endOr()` 实现 A/B 双人可见。
* 增量通过 `FlowableEventListener` 监听 `TASK_CREATED` 自动触发 `delegateTask`。
* 取消代理时调用 `setAssignee(taskId, "UserA")` + `setOwner(taskId, null)` 还原指派。

### 1.3 `yudao-bpm` 落地实现方案
* **开启代理**：
  - 存量：`BpmTaskService.delegateTask` 批量委派存量待办。
  - 增量：注册 `BpmAgentTaskEventListener` 拦截新任务。
* **取消代理**：
  - 检索 `taskOwner = A & taskAssignee = B` 的任务，将 `assignee` 改回 A，`owner` 清空，记录日志。
* **待办查询**：修改 `BpmTaskServiceImpl.getTaskTodoPage` 为 `or().taskAssignee(userId).taskOwner(userId)`。

---

## 业务场景 2：多级审批 (会签 / 或签 / 依次审批) [待扩展]

* **会签 (All Must Approve)**：BPMN 多实例节点 + `completionCondition` 设为 `nrOfCompletedInstances == nrOfInstances`。
* **或签 (Any One Approve)**：BPMN 多实例节点 + `completionCondition` 设为 `nrOfCompletedInstances > 0`（有一人同意即通过并自动消掉其余人的待办）。
* **依次审批 (Sequential Multi-instance)**：BPMN 串行多实例节点 (`isSequential=true`)。

---

## 业务场景 3：任务退回与驳回 (退回到发起人 / 上一步 / 任意指定节点) [待扩展]

* **官方做法**：使用 `runtimeService.createChangeActivityStateBuilder()` 流程加减签/节点跳转 API。
* **本项目实现**：已在 `BpmTaskServiceImpl.returnTask` 中实现基于 Flowable 节点跳转 API 的自由退回。

---

## 业务场景 4：超时催办与自动处理 (Timer Boundary Events) [待扩展]

* **官方做法**：BPMN 定时边界事件 (Timer Boundary Event) 或 Flowable Job Executor 调度。
* **本项目实现**：基于 `bpm_process_listener` 或 Quartz 结合 Flowable 定时任务。

---

## 业务场景 5：加签与减签 (前加签 / 后加签 / 并行加签) [待扩展]

* **官方做法**：通过 Flowable 6/7 动态增加多实例节点或任务跳转。
* **本项目实现**：`BpmTaskServiceImpl.createSignTask` 加签逻辑。

---

## 业务场景 6：审批人与发起人相同时的自动完成/处理策略

### 6.1 业务诉求
当流程流转到某个审批节点，算出的审批人刚好是**发起人自己**时（如：部门经理请假，部门经理节点自动算到了自己），系统需要支持灵活的处理策略（如：自动跳过通过、强制自己审批、或转交给直属上级）。

### 6.2 Flowable 官方标准做法
* **做法 A：引擎配置 `SkipExpression`（跳过表达式）**
  - 在 Flowable 配置中启用 `setSkipExpressionEnabled(true)`。
  - 在 BPMN UserTask 节点配置 `skipExpression="${initiator == assignee}"`，表达式返回 `true` 时引擎在创建 Task 时自动跳过。
* **做法 B：FlowableEventListener 监听**
  - 监听 `TASK_CREATED` 事件，校验 `task.getAssignee() == startUserId`，自动调用 `taskService.complete(taskId)`。

### 6.3 `yudao-bpm` 落地实现方案
在本项目中，已经深度实现了该功能，并参考飞书/钉钉支持了 **3 种策略**（定义在 `BpmUserTaskAssignStartUserHandlerTypeEnum`）：
1. **`START_USER_AUDIT` (由发起人对自己审批)**：正常生成待办，需发起人手动点同意。
2. **`SKIP` (自动跳过 / 自动完成)**：匹配到发起人自己时，系统自动调用 `approveTask(...)` 完成节点，生成审批日志“审批人与发起人相同，自动通过”。
3. **`TRANSFER_DEPT_LEADER` (转交给部门负责人审批)**：自动寻找发起人的直属上级审批；若无上级则自动通过。

* **退回保护机制**：在 `BpmTaskServiceImpl` 中校验如果当前任务是【被驳回/退回】到该节点的（`returnTaskFlag == true`），会强制关掉自动通过，避免退回给发起人后又被系统瞬间自动通过。

### 6.4 未配置时的默认行为与全局默认兜底方案
* **未配置时的默认行为**：若 BPMN 节点未显式配置扩展属性，`parseAssignStartUserHandlerType` 返回 `null`，系统自动兜底为 **`START_USER_AUDIT` (不自动通过，需手动审批)**。
* **全局默认兜底方案**：若希望全系统未配置时默认一律【自动跳过/自动完成】，推荐修改 `BpmnModelUtils.parseAssignStartUserHandlerType`：当解析出的扩展属性为空时，默认返回 `BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType()`。

---

## 业务场景 7：流程异常排查与管理员手动干预修补

### 7.1 业务诉求
当流程由于参数缺失、候选审批人为空、或监听器报错导致卡死或无法继续推进时，需要在哪里查看错误，以及管理员如何手动干预和修正流程。

### 7.2 Flowable 官方标准做法
* **死信任务表 (`act_ru_deadletter_job`)**：异步任务/定时器失败 3 次后进入死信表，异常堆栈存入 `act_ge_bytearray`。
* **Flowable Admin 控制台**：提供图形化界面查看死信 Job、重新触发 (`moveDeadLetterJobToExecutableJob`)、修改运行期变量 (`setVariable`)、以及强行节点跳转 (`createChangeActivityStateBuilder`)。

### 7.3 `yudao-bpm` 落地实现与管理员干预 API
* **事前防范（审批人为空策略）**：在节点上配置 `BpmUserTaskAssignEmptyHandlerTypeEnum`，当解算审批人为空时，可自动转交给流程管理员 (`ASSIGN_ADMIN`) 或指定人员 (`ASSIGN_USER`)。
* **错误查看**：
  - `infra_api_error_log` 在 Headless BPM 模式下已归档为 `bak_infra_api_error_log`。运行时异常堆栈统一通过服务器 Console / Logback 日志或 APM 监控输出。
  - 前端通过 `GET /admin-api/bpm/process-instance/get-approval-detail` 查看卡死节点。
* **管理员替代他人处理并推进到下一节点（两步法）**：
  1. **Step 1 (强制转派)**：由于 `validateTask` 会校验 `Assignee == 当前用户`，管理员需先调用 `PUT /admin-api/bpm/task/transfer`（具备 `bpm:task:update` 权限即可操作），将任务处理人强行转派给管理员自己。
  2. **Step 2 (正常审批)**：转派完成后，管理员调用 `POST /admin-api/bpm/task/approve` 完成任务审批，流程随即**顺利推进到下一个节点**。
* **其他干预手段**：
  - **强制跳转/退回**：调用 `PUT /admin-api/bpm/task/return` 利用 `createChangeActivityStateBuilder` 强行跳转离开崩溃节点。
  - **强制作废**：调用 `DELETE /admin-api/bpm/process-instance/cancel` 取消污染流程。
