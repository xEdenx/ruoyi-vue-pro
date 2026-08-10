# Portal 业务接入 Headless BPM 模板

> 用途：新业务接入流程时的最小模板。业务数据归 Portal 所有，BPM 只负责流程状态机、任务、轨迹和审计。

## 1. 责任边界

| Portal | BPM |
| --- | --- |
| 业务单据、表单页面、用户/组织、业务状态展示 | BPMN 定义、实例、任务、会签/或签、审批轨迹、任务审计 |
| 选择审批人并展开为最终用户 String ID | 验证并按节点规则创建任务 |
| 收到状态回调后更新单据 | 在流程状态改变时发出事件/回调 |

Portal 不得把用户 ID 转为数字；BPM 不得创建业务单据表或查询 Portal 业务数据库。

## 2. 发起流程

Portal 先持久化自己的业务单据，再调用 `POST /admin-api/bpm/process-instance/create`：

```json
{
  "processDefinitionId": "purchase_request:3:abc",
  "businessKey": "purchase-request-2026-00042",
  "variables": {
    "amount": 1200,
    "requestTitle": "采购显示器"
  },
  "startUserSelectAssignees": {
    "Activity_Manager": ["portal-manager-b3c4"]
  }
}
```

保存返回的 `processInstanceId` 到 Portal 单据。若 BPM 调用失败，Portal 必须回滚或标记单据为“流程发起失败”；不得伪造流程状态。

## 3. 办理与展示

- Portal 以当前登录用户的可信 Bearer token 查询待办、已办和审批轨迹；不在请求参数中伪造操作人。
- 同意、拒绝、退回、撤回、转办、委派、加签、减签均调用 BPM 对应 API；传入的目标用户 ID 必须是 Portal 原始 String ID。
- Portal 用自身目录渲染姓名、部门和头像。BPM 返回的最小用户投影可作为兜底，缺失时显示原始 ID。

## 4. 状态回调

为流程定义配置流程后置通知或由 Portal 消费 BPM 状态事件。回调至少包含：`processInstanceId`、`businessKey`、流程状态、原因和发生时间。Portal 按 `businessKey` 幂等更新自己的单据；重复回调不得重复写入业务审计。

## 5. 上线检查

1. BPMN 仅使用 `START_USER_SELECT` 或 `HEADLESS_REMOTE` 候选人策略。
2. 使用非数值 Portal 用户 ID 跑通发起、待办、审批、轨迹和回调。
3. Portal 为组织目录、通知和权限 claims 提供生产适配器；本地 mock 不得进入生产。
4. 业务单据表不放入 `bpm` Schema；BPM 表不作为 Portal 的业务主数据来源。
