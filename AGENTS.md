# 项目全局规则与 AI 助理记忆指南 (AGENTS.md)

本文档为 Antigravity AI 助理的长期项目记忆文件。每当在新对话中打开本工程时，AI 助理会自动加载本文档，继承项目的全量架构上下文、开发原则与技术规范。

---

## 1. 项目与环境概览 (Project & Environment Setup)

- **后端工程 (Java / Spring Boot 4 / Flowable 7)**:
  - 绝对路径: `/Users/John Doe/Documents/coding/ruoyi-vue-pro`
  - 关键模块: `yudao-bpm`（无头 BPM 服务，包含应用容器与工作流模块）
- **前端工程 (Vue3 / Vite / Element Plus / bpmn-js)**:
  - 绝对路径: `/Users/John Doe/Documents/coding/yudao-ui-admin-vue3`
- **云端数据库 (Supabase PostgreSQL)**:
  - Schema: `bpm` (包含 97 张精简后的核心业务与工作流表)
  - 环境变量: `SUPABASE_DB_HOST`, `SUPABASE_DB_PASSWORD`

---

## 2. 无头工作流中台架构目标 (Headless BPM Architecture Goals)

本项目定位为 **“无头工作流中台 (Headless / API-Driven BPM Engine)”**：

1. **Portal 门户系统 (外部前端与 UI 入口)**:
   - 负责全量用户交互：业务表单填写、**动态指定审批人/审批角色**、发起流程、查看我的待办/已办列表、点击【同意/拒绝】办理任务、渲染流程图进度树。
2. **BPM 平台 (RuoYi-Vue-Pro / Flowable 7)**:
   - 仅作为幕后中台，提供在线画图、发布/停止流程图等静态维护功能；
   - 通过 REST API 响应 Portal 请求，基于内部 Flowable 状态机驱动节点跳转、条件计算与待办生成。

---

## 3. 核心设计原则 (Design Principles)

### 3.1 零用户数据同步 (Zero User Sync)
- **不维护 `system_users` / `system_role`**：Portal 是唯一的数据源头 (Single Source of Truth)，所有的用户、角色、部门增删改查全在 Portal 完成。
- **透明 ID 中转**：BPM 平台底层（`act_ru_task` 等）只按 Portal 原始字符串 ID (`userId`/`roleId`) 进行流转与存储。Portal 自行用本地字典渲染姓名与部门。

### 3.2 动态审批人指派 (Dynamic Assignees)
- **零硬编码画图**：在 BPM 平台绘制 BPMN 图时，所有审批节点候选人策略统一配置为 **【发起人自选 (START_USER_SELECT)】**。
- **发起时动态注入**：Portal 发起流程时，通过 API 中的 `startUserSelectAssignees` 字典（如 `{"Node1": [102], "Node2": [5]}`）动态指定用户 ID 数组或角色 ID 数组。

### 3.3 只读研读代码规范 (Read-Only Code Research)
- 进行代码与逻辑剖析时，遵循只读原则，不修改非必要的业务代码。

### 3.4 存量项目最小影响改动 (Minimal-Impact Changes)
- 本项目基于既有 RuoYi-Vue-Pro 与 Flowable 代码演进；任何改动都应优先复用现有模块边界、接口和流程行为。
- 先定位根因与最小修复点，再实施改动；不得以重构、清理或“顺手优化”为由扩大修改范围。
- 修改应限制在实现目标所必需的文件和调用链内；无关模块、前端和配置保持不动。
- 变更后使用与风险相称的定向或模块级回归验证，并明确说明未覆盖的运行时前提。

---

## 4. Portal 对接 BPM 的 4 大 REST API 规范

1. **发起流程 API**: `POST /admin-api/bpm/process-instance/create`
   - 参数: `processDefinitionKey`, `variables` (表单变量), `startUserSelectAssignees` (动态选人/选角色字典，ID 为字符串).
2. **查询待办列表 API**: `GET /admin-api/bpm/task/todo-page?pageNo=1&pageSize=10`
   - 根据 Header Token 自动识别 Portal 当前用户，返回待办列表及 `taskId`.
3. **办理任务 (同意/拒绝) API**:
   - 同意: `POST /admin-api/bpm/task/approve` (传入 `taskId`, `reason`)
   - 拒绝: `POST /admin-api/bpm/task/reject` (传入 `taskId`, `reason`)
4. **流程进度与轨迹 API**: `GET /admin-api/bpm/process-instance/get-approval-detail?processInstanceId={id}`
   - 返回结构化节点履历树，供 Portal 渲染步骤条与高亮轨迹。

---

## 5. 项目核心文档索引 (Project Docs Index)

- [docs/PORTAL_HEADLESS_BPM_ARCHITECTURE.md](file:///Users/John Doe/Documents/coding/ruoyi-vue-pro/docs/PORTAL_HEADLESS_BPM_ARCHITECTURE.md): Portal 对接无头 BPM 架构设计与 API 规范
- [script/sql/init-bpm.sql](file:///Users/eden/Documents/coding/ruoyi-vue-pro/script/sql/init-bpm.sql): `bpm` Schema 初始化 SQL；执行前确认目标 Schema 与现有数据范围
