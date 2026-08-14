# 项目全局规则与 AI 助理记忆指南 (AGENTS.md)

本文档为本项目 AI 助理的长期工作约定，记录架构上下文、开发原则与技术规范。

---

## 1. 项目与环境概览 (Project & Environment Setup)

- **后端工程 (Java 21 / Spring Boot 4 / Flowable 8.0.0)**:
  - 绝对路径: `/Users/eden/Documents/coding/ruoyi-vue-pro`
  - 关键模块: `yudao-bpm`（无头 BPM 服务，包含应用容器与工作流模块）
- **前端工程 (Vue3 / Vite / Element Plus / bpmn-js)**:
  - 绝对路径: `/Users/eden/Documents/coding/yudao-ui-admin-vue3`
- **云端数据库 (Supabase PostgreSQL)**:
  - Schema: `bpm`（表结构和数量以 DBX 的实时查询为准；不要在文档中写死表数）
  - 环境变量: `SUPABASE_DB_HOST`, `SUPABASE_DB_PASSWORD`

---

## 2. 无头工作流中台架构目标 (Headless BPM Architecture Goals)

本项目定位为 **“无头工作流中台 (Headless / API-Driven BPM Engine)”**：

1. **Portal 门户系统 (外部前端与 UI 入口)**:
   - 负责全量用户交互：业务表单填写、**动态指定审批人/审批角色**、发起流程、查看我的待办/已办列表、点击【同意/拒绝】办理任务、渲染流程图进度树。
2. **BPM 平台 (RuoYi-Vue-Pro / Flowable 8)**:
   - 仅作为幕后中台，提供在线画图、发布/停止流程图等静态维护功能；
   - 通过 REST API 响应 Portal 请求，基于内部 Flowable 状态机驱动节点跳转、条件计算与待办生成。

---

## 3. 核心设计原则 (Design Principles)

### 3.1 零用户数据同步 (Zero User Sync)
- **不维护本地 `system_user` / `system_role` 主数据**：Portal 是唯一的数据源头 (Single Source of Truth)，所有的用户、角色、部门增删改查全在 Portal 完成。
- **透明 ID 中转**：BPM 平台底层（`act_ru_task` 等）只按 Portal 原始字符串 ID (`userId`/`roleId`) 进行流转与存储。Portal 自行用本地字典渲染姓名与部门。

### 3.2 动态审批人指派 (Dynamic Assignees)
- **零本地组织硬编码画图**：审批节点仅使用 **【发起人自选 (START_USER_SELECT, 35)】** 或 **【Portal 角色 (ROLE, 70)】**。70 的 `candidateParam` 是 Portal 角色编码。
- **发起时动态注入**：Portal 仅为策略 35 通过 `startUserSelectAssignees` 传入最终用户 String ID，例如 `{"Activity_Manager": ["portal-manager-b3c4"]}`；策略 70 在节点到达时由 Portal 解算。

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
   - 参数: `processDefinitionId`, `variables`（表单变量）、`startUserSelectAssignees`（仅策略 35 的最终用户 String ID 字典）。该管理端 API 不接收 `processDefinitionKey` 或 `businessKey`。
2. **查询待办列表 API**: `GET /admin-api/bpm/task/todo-page?pageNo=1&pageSize=10`
   - 根据 Header Token 自动识别 Portal 当前用户，返回待办列表及 `taskId`.
3. **办理任务 (同意/拒绝) API**:
   - 同意: `PUT /admin-api/bpm/task/approve`（传入 `id`, `reason`）
   - 拒绝: `PUT /admin-api/bpm/task/reject`（传入 `id`, `reason`）
4. **流程进度与轨迹 API**: `GET /admin-api/bpm/process-instance/get-approval-detail?processInstanceId={id}`
   - 返回结构化节点履历树，供 Portal 渲染步骤条与高亮轨迹。

---

## 5. 项目核心文档索引 (Project Docs Index)

- [docs/PORTAL_HEADLESS_BPM_ARCHITECTURE.md](docs/PORTAL_HEADLESS_BPM_ARCHITECTURE.md): Portal 对接无头 BPM 架构设计与 API 规范
- [script/sql/init-bpm.sql](script/sql/init-bpm.sql): `bpm` Schema 初始化 SQL；执行前确认目标 Schema 与现有数据范围
