---
name: bpmn-flow-generator
description: 根据自然语言业务流程生成或改造本项目 Flowable 8 Headless BPM 的完整交付资产：可部署 BPMN 2.0、Portal 表单初始化 SQL、必要的本地 mock 角色映射、HTTP walkthrough 与节点说明。用于包含条件分支、策略 35/70、串并行会签、或签、超时或任务操作等复杂流程；仅在存在明确的外部业务副作用时生成 Java 监听器。
---

# Flowable 8 Headless BPM 复杂流程生成

生成可部署、可验收的复杂流程；简单流程是该规范的自然子集，不单独维护简化模板。始终遵守仓库根目录 `AGENTS.md`，并先检查已有 BPMN、Portal 接口、候选人策略、初始化脚本和测试脚本。

## 唯一标杆与边界

把下列资产作为实现参考，而不是照抄具体合同业务字段或 mock 身份：

- [复杂 BPMN](../../../script/bpmn/contract_exception_review_v1.bpmn.xml)
- [节点与代码路径说明](../../../docs/CONTRACT_EXCEPTION_REVIEW_NODE_GUIDE.md)
- [HTTP walkthrough](../../../script/shell/test_contract_exception_review_walkthrough.sh)
- [mock 表单初始化](../../../script/sql/init-mock-data.sql)

流程引擎只编排状态与 Portal 原始 String ID。不要写入或依赖本地 `system_user`、`system_role`，不要把显示名、数值 ID 或角色编码当作 Flowable 任务办理人。

## 交付物

根据需求生成其中必需的资产；未生成的项目必须在最终说明中给出理由。

| 资产 | 默认位置 | 要求 |
| --- | --- | --- |
| BPMN 2.0 | `script/bpmn/<process_key>.bpmn.xml` | 完整且可部署：全部节点、连线、条件、扩展属性、`bpmndi` 图形布局齐全。 |
| 表单初始化 | `script/sql/init-mock-data.sql` 与 `.sqlserver.sql` | 只放 mock/表单/演练数据，不混入 `init-bpm` Schema 初始化；以 `bpm_form.code` 定位，不能假设主键。 |
| 本地 mock 扩展 | `LocalBpmPortalIdentityApiMock`、`LocalPortalRoleCandidateApiMock` 及定向测试 | 仅为本地 walkthrough 所需的用户、部门、角色和 `(activityId, roleCode)` 映射增加最小条目。 |
| HTTP walkthrough | `script/shell/test_<process_key>_walkthrough.sh` | 仅 `curl`/`jq`，覆盖每个关键分支和复杂语义，并输出可审阅报告。 |
| 节点说明 | `docs/<PROCESS_KEY>_NODE_GUIDE.md` | 将业务节点、BPMN 语义、Portal 契约、运行时代码路径及验收场景逐项对应。 |
| Java 监听器 | 仅按需放在 `yudao-bpm/.../listener/<domain>/` | 不是默认产物；见“监听器决策”。 |

## 建模与 Portal 契约

### 候选人策略

- Portal 在发起时已决定具体办理人，使用 `candidateStrategy="35"`；只为这些节点在 `startUserSelectAssignees` 传入最终 String ID。
- 节点到达时才按 Portal 组织/角色关系解算，使用 `candidateStrategy="70"` 和非空 `candidateParam`（Portal 角色编码）。不要向该节点传 `startUserSelectAssignees`。
- 策略 70 先由 Portal 解算为具体 String ID，再由 Flowable 创建任务；角色本身从不是任务办理人。无候选人必须失败关闭，不回退本地用户/角色表。
- 发起接口使用 `POST /admin-api/bpm/process-instance/create`，传入 `processDefinitionId`、`variables`、`startUserSelectAssignees`。不要传 `processDefinitionKey` 或 `businessKey`。

### 复杂节点语义

- 明确每个排他网关的互斥条件与默认路径；为每条可达路径提供变量样例。
- 多实例节点按实际 Portal 解算的候选人集合创建任务。串行会签保留 Portal 传入顺序；并行全员会签使用 `${nrOfCompletedInstances == nrOfInstances}`；并行或签使用 `${nrOfCompletedInstances > 0}`。
- 需要自动跳过时，显式设置 `assignStartUserHandlerType`；不要依赖碰巧相同的用户 ID。
- 需要定时催办时使用非中断边界 timer，并验证任务在提醒后仍可办理。不要把消息投递 mock 成已验证的外部通知。
- 需要加签、委派、转办、退回或拒绝时，在 walkthrough 中分别断言其真实任务状态机语义，不能只检查最终流程结束。

### 表单与初始化

- 固定表单 code 为 `<process_key>_form`；部署、walkthrough 和文档均按 code 查询运行环境中的实际表单 ID。
- PostgreSQL 与 SQL Server 初始化脚本保持语义一致。执行或修改数据库前，使用 DBX 检查实时 `bpm` Schema；不要在文档中写死表数量。
- 若本地 mock 需要扩容，保留旧 ID、旧角色和既有 `(activityId, roleCode)` 映射；新增条目不得覆盖旧流程行为。

## 监听器决策

只有在需求明确包含流程外副作用时才创建 Java listener，例如幂等更新业务系统状态、发送受控领域事件或调用已定义的 Portal/业务系统回调。

创建前确认：触发节点、幂等键、失败/重试策略、审计责任方和外部接口契约。没有这些信息时，不生成空日志 listener、TODO listener 或虚构的领域写入。BPMN 也不得保留指向不存在 Bean 的 `delegateExpression`。

## 实施顺序

1. 提取流程 key、表单字段、每条分支条件、节点语义、选人时机、会签方式、任务操作和真实外部副作用。
2. 基于复杂标杆绘制完整 BPMN；逐一检查节点 ID、连线引用、策略 35/70、表达式、循环完成条件、边界事件和 BPMNDI。
3. 生成或更新表单初始化与必要 mock 映射；严格与 Schema 初始化解耦。
4. 仅在“监听器决策”条件满足时实现 Java listener 并绑定 BPMN；否则明确流程没有领域 listener。
5. 编写 HTTP walkthrough：部署时按表单 code 获取实际 ID，以多组变量覆盖低风险/常规/高风险/拒绝等适用路径，并检查待办办理人、任务操作与最终状态。
6. 运行与改动相称的验证：`xmllint --noout`、`bash -n`、相关 Maven 测试；涉及运行时时启动服务并执行 walkthrough。报告已验证与未验证的外部前提。

## 完成检查

- BPMN 可解析，所有流转引用闭合，且没有未绑定或无意义的 listener。
- 35 与 70 的候选人来源不混用，任务办理人始终是 Portal String ID。
- 表单初始化不污染 `init-bpm`，也不写死表单主键。
- walkthrough 覆盖每个重要业务分支和所声明的复杂能力。
- 节点说明能从业务场景追溯到 BPMN、Portal 参数、运行时代码和验收断言。
