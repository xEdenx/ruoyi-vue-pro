# 项目全局规则与 AI 助理记忆指南 (AGENTS.md)

本文档为 Antigravity AI 助理的长期项目记忆文件。每当在新对话中打开本工程时，AI 助理会自动加载本文档，继承项目的全量架构上下文、开发原则与技术规范。

---

## 1. 项目与环境概览 (Project & Environment Setup)

- **后端工程 (Java / Spring Boot 4 / Flowable 7)**:
  - 绝对路径: `/Users/John Doe/Documents/coding/ruoyi-vue-pro`
  - 关键模块: `yudao-module-bpm` (工作流模块), `yudao-server` (应用容器)
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
- **透明 ID 中转**：BPM 平台底层（`act_ru_task` 等）仅按纯数字/字符串 ID (`userId`/`roleId`) 进行流转与存储。Portal 调 API 收到数字 ID 后，在 Portal 前端匹配本地字典渲染姓名与部门。

### 3.2 动态审批人指派 (Dynamic Assignees)
- **零硬编码画图**：在 BPM 平台绘制 BPMN 图时，所有审批节点候选人策略统一配置为 **【发起人自选 (START_USER_SELECT)】**。
- **发起时动态注入**：Portal 发起流程时，通过 API 中的 `startUserSelectAssignees` 字典（如 `{"Node1": [102], "Node2": [5]}`）动态指定用户 ID 数组或角色 ID 数组。

### 3.3 只读研读代码规范 (Read-Only Code Research)
- 进行代码与逻辑剖析时，遵循只读原则，不修改非必要的业务代码。

---

## 4. Portal 对接 BPM 的 4 大 REST API 规范

1. **发起流程 API**: `POST /admin-api/bpm/process-instance/create`
   - 参数: `processDefinitionKey`, `variables` (表单变量), `startUserSelectAssignees` (动态选人/选角色字典).
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
- [docs/DATABASE_TABLES_DICTIONARY.md](file:///Users/John Doe/Documents/coding/ruoyi-vue-pro/docs/DATABASE_TABLES_DICTIONARY.md): 数据库 97 张全量表字典
