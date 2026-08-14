# 项目开发记忆与技术经验沉淀 (MEMORY.md)

本文档记录本项目在摸排、测试、调优过程中沉淀的技术经验、数据库特性分析及排障诊断记录。

> 历史记录仅用于追溯当时的环境和结论，不构成当前运行契约；当前 API、依赖版本与配置以 `README.md`、`docs/`、源码和实时 DBX 查询为准。

---

## 1. 数据库与 Flowable 引擎关键分析记忆

### 1.1 `act_ge_bytearray` 部署资源
部署 BPMN 后，Flowable 会按 `deployment_id_` 记录 BPMN 资源。是否同时存在 PNG 或其他生成资源取决于部署和模型配置；排障时必须查询实际部署资源，不能假定每次固定写入两条记录。

### 1.2 版本递增与挂起机制
- **版本累加**：重复发布同名模型时，旧版本的 `act_re_procdef` 和 `act_ge_bytearray` 记录**物理保留（不删除）**，保障运行中的旧实例完好流转。
- **挂起停用**：新版本发布时，后端自动调用 `updateProcessDefinitionSuspended()` 将旧版本的 `suspension_state_` 设置为 `2`（挂起），禁止基于旧版本发起新流程。

### 1.3 表单与业务数据边界
- **流程表单**：表单定义保存在 `bpm_form`，流程变量由 Flowable 持久化。
- **Portal 业务数据**：Headless BPM 不维护业务单据表；Portal 保存业务数据，并以流程实例 ID 建立关联。旧 `bpm_oa_leave` 示例不属于当前运行契约。

---

## 2. 踩坑与排障诊断记录

### 2.1 已废弃的上游管理端排障记录
以下 `system_menu` / `bpm:model:import` 记录属于收敛 Headless BPM 前的上游管理端问题，不适用于当前零用户同步架构，也不得对当前 `bpm` schema 执行相关 SQL。

---

## 3. 历史实例抓取记录 (2026-08-07；不构成当前契约)

下列记录发生在 Portal String-ID 迁移前，仅用于追溯，不可作为当前测试数据或 ID 设计依据：
- `act_ru_task`: 待办任务流转至节点 `lm_pass`（直属领导通过），分配给目标审批人用户 `118`。
- `act_hi_varinst`: 表单字段 `F6gqmshc4qjxabc="aaa"`, `Fxh3mshc4rviaec="rrr"`, `lm_pass_assignee=118` 成功持久化。
