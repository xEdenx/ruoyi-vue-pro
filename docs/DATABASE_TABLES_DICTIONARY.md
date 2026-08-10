# Database Tables Dictionary (bpm Schema)

本文档整理了 PostgreSQL `bpm` Schema 下所有数据库表的分类、用途及状态说明。

---

## 1. 业务流程模块 (BPM Module Tables)

用于存储流程分类、流程表单、流程抄送及表达式等 BPM 核心业务数据。

| 表名 | 功能描述 | 状态 |
| --- | --- | --- |
| `bpm_category` | 流程分类表 | 核心使用 |
| `bpm_form` | 动态表单定义表 | 核心使用 |
| `bpm_process_definition_info` | 流程定义扩展信息表 | 核心使用 |
| `bpm_process_expression` | 流程表达式配置表 | 核心使用 |
| `bpm_process_instance_copy` | 流程实例抄送表 | 核心使用 |
| `bpm_process_listener` | 流程监听器配置表 | 核心使用 |

---

## 2. Flowable / Activiti 工作流引擎内核表 (Flowable Engine Tables)

Flowable 工作流引擎底层运行时（`act_ru_*`）、历史记录（`act_hi_*`）、模型部署（`act_re_*`）及通用（`act_ge_*` / `flw_*`）数据表。

| 分组 / 表前缀 | 代表性表 | 功能描述 |
| --- | --- | --- |
| `act_re_*` | `act_re_deployment`, `act_re_procdef`, `act_re_model` | 流程模型与发布定义（Repository） |
| `act_ru_*` | `act_ru_execution`, `act_ru_task`, `act_ru_variable`, `act_ru_identitylink` | 运行期流程实例、任务与变量（Runtime） |
| `act_hi_*` | `act_hi_procinst`, `act_hi_taskinst`, `act_hi_varinst`, `act_hi_comment` | 历史流程实例、任务、变量与审批意见（History） |
| `act_ge_*` | `act_ge_bytearray`, `act_ge_property` | 流程引擎通用二进制文件与配置（General） |
| `flw_*` | `flw_channel_definition`, `flw_event_definition`, `flw_ru_batch` | Flowable 事件通道及批处理运行表 |

---

## 3. 已归档的 System / Infra 表

Headless BPM 默认构建不加载 system / infra 模块，且不读取这些表。为保留审计和回滚能力，全部原地重命名为 `bak_` 前缀；不再作为运行时表使用。

| 原始表前缀 / 表 | 归档表 | 原用途 |
| --- | --- | --- |
| `system_*`（32 张） | `bak_system_*` | 本地身份、组织、权限、字典、OAuth2、通知与审计 |
| `infra_config`、`infra_file*` | `bak_infra_config`、`bak_infra_file*` | 本地参数及文件存储 |
| `bpm_user_group` | `bak_bpm_user_group` | 已下线的本地用户组候选人策略 |
| `bpm_oa_leave` | `bak_bpm_oa_leave` | 已下线的 OA 请假样例 |

---

## 4. 备份 / 已瘦身工具表 (Backup Tables with `bak_` Prefix)

已安全添加 `bak_` 前缀，与 BPM 主业务隔离：

| 原始表名 | 备份重命名表名 | 功能描述 |
| --- | --- | --- |
| `infra_codegen_table` | `bak_infra_codegen_table` | 代码生成表定义 |
| `infra_codegen_column` | `bak_infra_codegen_column` | 代码生成字段定义 |
| `infra_job` | `bak_infra_job` | Quartz 定时任务配置 |
| `infra_job_log` | `bak_infra_job_log` | Quartz 定时任务执行日志 |
| `infra_api_access_log` | `bak_infra_api_access_log` | API 访问统计日志 |
| `infra_api_error_log` | `bak_infra_api_error_log` | API 异常崩溃日志 |
| `infra_data_source_config` | `bak_infra_data_source_config` | 动态多数据源配置表 |
