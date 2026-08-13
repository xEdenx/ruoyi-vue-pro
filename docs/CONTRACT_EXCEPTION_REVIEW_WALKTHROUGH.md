# 合同例外评审复杂流程 Walkthrough

流程资产为 [`script/bpmn/contract_exception_review_v1.bpmn.xml`](../script/bpmn/contract_exception_review_v1.bpmn.xml)，可执行验收脚本为 [`script/shell/test_contract_exception_review_walkthrough.sh`](../script/shell/test_contract_exception_review_walkthrough.sh)。节点、业务场景与引擎处理逻辑见[节点说明](CONTRACT_EXCEPTION_REVIEW_NODE_GUIDE.md)。它是用于验证 Headless BPM 能力边界的 mock 场景，不是特定合同系统的生产模板。

先在可丢弃的本地 `bpm` schema 执行结构脚本，再执行对应数据库版本的 `init-mock-data`。后者只写默认分类与两个 walkthrough 表单，不写流程实例或运行期数据。

```bash
bash script/shell/test_contract_exception_review_walkthrough.sh http://127.0.0.1:48080
```

默认会重新部署 BPMN，并将结果写到 `output/walkthrough/`。本地 mock 必须启用；生产 Portal 应以自己的组织目录、鉴权与 `PortalRoleCandidateApi` 适配器替换 mock。

| 场景 | 验收的能力 |
| --- | --- |
| 常规例外 | 发起人同人 `SKIP`、委派归还、前加签、30 秒非中断催办、退回、串行多实例、并行全员会签 |
| 高风险例外 | 转办、并行或签（首个同意者结束节点）、最终授权 |
| 驳回 | 任意审批节点拒绝后流程终止 |
| 低风险例外 | 到达节点时按 `ROLE_BUSINESS_OWNER` 与 `ROLE_ADMIN` 解算 Portal String ID |

新加入的本地身份包含业务归口、两名法务、两名风控委员与最终授权人。流程中的 70 策略将角色解析留在 Portal 侧；35 策略仅在确实需要发起时动态指定的节点使用。定时器验证的是“任务仍可办理”的非中断语义；提醒的最终投递由运行环境的消息适配器负责。
