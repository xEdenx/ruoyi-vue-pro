# ADR-000: 无头工作流中台与零用户同步基础架构设计 (Headless BPM & Zero User Sync Architecture)

* **状态 (Status)**: 已接受；候选人策略部分已由 ADR-002 演进
* **日期 (Date)**: 2026-08-07
* **决策者 (Deciders)**: Project Architecture Team
* **标签 (Tags)**: Headless BPM, Architecture, Zero User Sync, Dynamic Assignees, JWT, ADR

---

## 1. 上下文与背景 (Context & Problem Statement)

原 RuoYi-Vue-Pro / Flowable 模块默认强依赖本地的 `system_users`（用户表）、`system_role`（角色表）与 `system_oauth2_access_token`（Token 鉴权表）。

在多端系统 / Portal 门户接入场景下：
1. **Portal 是唯一的数据源头 (Single Source of Truth)**：所有的用户账号、组织架构与角色全量归属于 Portal 门户系统；
2. **拒绝双向同步与数据冗余**：如果在 BPM 平台维护一套用户与角色镜像表，会导致频繁的变更同步开销、状态不一致漏洞以及高昂运维成本；
3. **需要 REST API 驱动的无头引擎**：Portal 前端作为唯一的交互界面，BPM 平台仅作为幕后工作流中台。

---

## 2. 决策方案 (Decision Outcome)

我们将 BPM 模块重构定位为 **“无头工作流中台 (Headless / API-Driven BPM Engine)”**，并制定以下核心决策：

### 2.1 零用户数据同步原则 (Zero User Sync)
* **不维护 `system_users` 与 `system_role`**：Portal 负责所有的用户与角色增删改查。
* **透明 ID 中转 (Transparent ID Delegation)**：BPM 平台底层（如 Flowable `act_ru_task` 表）仅按 Portal 原始字符串 ID (`userId`/`roleId`) 进行流转与存储。BPM 响应 REST API 时返回 ID，由 Portal 前端自行匹配本地字典渲染姓名与部门。

### 2.2 JWT Token Payload 无库解包鉴权 (Bearer JWT Authentication)
* **重构 `TokenAuthenticationFilter`**：直接 Base64URL 解码 HTTP Header 中 Bearer JWT Token 的 Payload 声明；
* **提取 Claim 自动组装 `LoginUser`**：提取 `userId` 与 `role` 声明直接填充 Spring Security 的 `LoginUser`，跳过 BPM 数据库中 `system_oauth2_access_token` 的查库逻辑。

### 2.3 动态审批人指派机制 (Dynamic Assignees)
* **静态画图解耦**：审批节点使用 **【发起人自选 (START_USER_SELECT, 35)】** 或 ADR-002 定义的 **【Portal 角色 (ROLE, 70)】**；不绑定 BPM 本地用户、角色或部门。
* **发起时动态注入**：Portal 仅为策略 35 通过 `startUserSelectAssignees` 传入最终用户 String ID（如 `{"Activity_Manager": ["portal-manager-b3c4"]}`）；策略 70 在节点抵达时由 Portal 根据角色编码解算。

### 2.4 4 大标准 REST API 对接规范
1. **发起流程 API**: `POST /admin-api/bpm/process-instance/create`
2. **查询我的待办 API**: `GET /admin-api/bpm/task/todo-page`
3. **办理任务 (同意/拒绝) API**:
   - 同意: `PUT /admin-api/bpm/task/approve`
   - 拒绝: `PUT /admin-api/bpm/task/reject`
4. **流程履历与轨迹 API**: `GET /admin-api/bpm/process-instance/get-approval-detail`

---

## 3. 架构影响 (Consequences)

1. **彻底解耦**：BPM 平台可以剥离绝大部分用户、角色与权限维护表，精简数据库字典；
2. **性能与高并发**：JWT 鉴权全在内存完成，每次 API 请求 0 鉴权数据库查询；
3. **多端接入极简**：任何外部 Portal 系统只需要携带符合约定的 JWT Token 即可无缝调用 BPM 无头中台。

---

## 4. 相关参考 (References)

* **主架构文档**: [Portal Headless BPM 架构](../PORTAL_HEADLESS_BPM_ARCHITECTURE.md)
* **后续演进决策**: [ADR-002 Portal 角色候选人策略](ADR_002_PORTAL_ROLE_CANDIDATE_STRATEGY.md)
