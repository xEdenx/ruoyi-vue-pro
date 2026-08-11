# ADR-002: Portal 角色候选人策略

## 状态

已采纳，替代 ADR-001。

## 背景

无头 BPM 的候选人只能由 Portal 权威解算。此前策略 70 命名为 `HEADLESS_REMOTE`，通过泛化规则参数调用 Portal；当前实际用例始终是按目标角色编码选择候选人，这使“远程”成为多余的策略维度。

## 决策

1. 使用 `ROLE(70)` 表示 Portal 角色候选人策略；删除 `HEADLESS_REMOTE` 枚举项。
2. `candidateParam` 必须是 Portal 目标角色编码，例如 `ROLE_ADMIN`。
3. BPM 仅将发起人 ID、节点 ID、角色编码和流程实例 ID 传给 `PortalRoleCandidateApi.resolveRoleAssigneeIds(...)`；Portal 基于自己的用户和部门数据返回最终 `Set<String>` 用户 ID。
4. 不查询或同步 BPM 本地用户、角色、部门或岗位表。Portal 缺失适配器、角色编码为空或未返回有效候选人时，策略失败关闭。
5. 策略值仍为 70，因此现有 BPMN 无需变更或重新发布。

## 后果

- 前端建模器仅提供“发起人自选（35）”和“按 Portal 角色（70）”。
- 将来若要按部门、岗位或业务路由解算，必须增加语义明确的独立策略与 Portal SPI，不能重新引入通用类型标记。
- 原历史编号 10 不再是可执行的角色策略；使用它的旧模型会在发布或运行期失败关闭。

## 参考

- [无头工作流架构](../PORTAL_HEADLESS_BPM_ARCHITECTURE.md)
- [Portal 适配器集成契约](../PORTAL_ADAPTER_INTEGRATION_CONTRACT.md)
- [ADR-001 历史决策](ADR_001_HEADLESS_REMOTE_CANDIDATE_STRATEGY.md)
