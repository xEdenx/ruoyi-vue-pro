# Headless BPM Platform

这是一个面向 Portal 的无头工作流中台。它以 Flowable 作为流程状态机，通过 REST API 为外部门户提供流程定义维护、发起、待办、办理和审批轨迹能力；业务表单、用户目录、角色目录与最终 UI 均由 Portal 管理。

本仓库已从上游 RuoYi-Vue-Pro 的完整业务平台收敛为 `yudao-bpm` 服务。不要将其中遗留的上游模块或文档理解为本服务仍提供的产品能力。

## 核心边界

- **Portal 是用户与组织数据的唯一来源。** Flowable 任务仅保存 Portal 原始字符串 ID；BPM 不维护本地用户、角色或部门主数据。
- **Portal 在发起时动态指定审批人。** BPMN 审批节点使用 `START_USER_SELECT` 或 Portal 角色候选人策略；候选人由 Portal 适配器解算，不能回退到本地组织表。
- **BPM 负责状态机，不持有业务数据。** 服务管理流程定义、任务、意见、变量和轨迹；业务表单及业务状态由 Portal 负责，并通过 API/回调对接。
- **生产身份必须可信。** 本地 walkthrough 使用的 mock token 仅用于开发验证；生产环境必须接入已验签的 Portal 身份与精确 claims 授权。

详细架构与接入约束见 [Portal 适配契约](docs/PORTAL_ADAPTER_INTEGRATION_CONTRACT.md) 和 [Portal 业务接入模板](docs/PORTAL_BUSINESS_BPM_INTEGRATION_TEMPLATE.md)。

## 技术栈与模块

- Java 21、Spring Boot 4、Flowable 7
- PostgreSQL（当前目标 schema：`bpm`）、Redis、MyBatis-Plus
- Maven 多模块工程；可运行服务为 [`yudao-bpm`](yudao-bpm/)
- 外部 Portal 前端位于独立仓库，不在本仓库维护

## 本地运行

先准备本地 profile 所需的 PostgreSQL、Redis 和 Portal Mock 配置。连接信息与密码只放在本地环境配置或环境变量中，不提交到仓库。

```bash
# 在仓库根目录执行
mvn -pl yudao-bpm -am test
mvn -pl yudao-bpm -am spring-boot:run
```

默认 profile 为 `local`。启动后可通过 `/swagger-ui` 查看 API；实际端口以本地 profile 配置为准。

## 验证 Headless 流程

V5 办公用品流程是可重复执行的集成 walkthrough：脚本会查询或部署流程定义，并验证非数值 Portal ID 的发起、待办、审批/拒绝和轨迹闭环。

```bash
bash script/shell/test_headless_bpm_walkthrough.sh http://127.0.0.1:48080
```

脚本依赖 `curl` 和 `jq`，结果写入 `output/walkthrough/`。它构造的 token 仅适用于启用本地 Headless Mock 的开发环境，绝不能用于生产。

## 文档与资产

| 内容 | 用途 |
| --- | --- |
| [Portal 架构总览](docs/PORTAL_HEADLESS_BPM_ARCHITECTURE.md) | 无头 BPM 的职责边界与 REST API 概览 |
| [Portal 适配契约](docs/PORTAL_ADAPTER_INTEGRATION_CONTRACT.md) | 身份、组织目录、候选人、通知与审计的适配端口 |
| [Portal 业务接入模板](docs/PORTAL_BUSINESS_BPM_INTEGRATION_TEMPLATE.md) | 新业务接入流程的最小实施模板 |
| [架构决策记录](docs/adr/) | 已采纳与已替代决策的历史依据 |
| [流程演练说明](docs/HEADLESS_BPM_TEST_WALKTHROUGH.md) | V5 流程的 API 验收背景与场景 |
| [`script/bpmn/office_supplies_request_v5.bpmn.xml`](script/bpmn/office_supplies_request_v5.bpmn.xml) | walkthrough 会直接部署的 BPMN 示例资产 |
| [`script/sql/`](script/sql/) | `bpm` 初始化 SQL；包含 destructive bootstrap DDL，执行前必须确认目标 schema |
| [`.agents/skills/bpmn-flow-generator/`](.agents/skills/bpmn-flow-generator/) | 新 BPMN、表单、监听器与接入资产的生成规范 |

历史文档已在 Git 提交 `0aaa5cdde4` 中完整归档；当前运行约定以实现、测试、本文档和上表列出的当前契约为准。

## API 范围

Portal 运行期主要使用以下 API：

- `POST /admin-api/bpm/process-instance/create`：发起流程，并传入 `startUserSelectAssignees`。
- `GET /admin-api/bpm/task/todo-page`：查询当前 Portal 主体的待办。
- `POST /admin-api/bpm/task/approve`、`POST /admin-api/bpm/task/reject`：办理任务。
- `GET /admin-api/bpm/process-instance/get-approval-detail`：查询审批轨迹。

完整请求/响应、流程定义维护接口和权限边界以 Swagger 与 Portal 适配契约为准。

## 数据库注意事项

`script/sql/init-bpm.sql` 与其 SQL Server 版本是受控的初始化资产，不是日常增量迁移。它会重建其覆盖范围内的表；执行前必须备份、明确选中 `bpm` schema，并验证不在其范围内的 Flowable 表和现有业务数据不会受影响。
