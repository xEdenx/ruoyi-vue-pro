# Database Tables Dictionary (bpm Schema)

本文档整理了 PostgreSQL `bpm` Schema 下所有数据库表的分类、用途及状态说明。

---

## 1. 业务流程模块 (BPM Module Tables)

用于存储流程分类、流程表单、请假示例、流程抄送及表达式等 BPM 核心业务数据。

| 表名 | 功能描述 | 状态 |
| --- | --- | --- |
| `bpm_category` | 流程分类表 | 核心使用 |
| `bpm_form` | 动态表单定义表 | 核心使用 |
| `bpm_oa_leave` | OA 请假申请示例表 | 示例数据 |
| `bpm_process_definition_info` | 流程定义扩展信息表 | 核心使用 |
| `bpm_process_expression` | 流程表达式配置表 | 核心使用 |
| `bpm_process_instance_copy` | 流程实例抄送表 | 核心使用 |
| `bpm_process_listener` | 流程监听器配置表 | 核心使用 |
| `bpm_user_group` | 流程用户组配置表 | 核心使用 |

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

## 3. 基础设施模块表 (Infra Module Tables)

用于系统参数配置及文件存储等基础功能。

| 表名 | 功能描述 | 状态 |
| --- | --- | --- |
| `infra_config` | 系统参数配置表 | 核心保留 |
| `infra_file` | 文件上传记录表 | 核心保留 (流程附件/流程图) |
| `infra_file_config` | 文件存储配置表 | 核心保留 |
| `infra_file_content` | 数据库存储文件内容表 | 核心保留 |

---

## 4. 系统管理模块表 (System Module Tables)

支撑用户、角色、部门、字典、菜单及鉴权等系统基础能力。

| 分类 | 代表性表 | 功能描述 |
| --- | --- | --- |
| **组织架构** | `system_users`, `system_dept`, `system_post`, `system_user_post` | 用户、部门、岗位及关联关系 |
| **权限与角色** | `system_role`, `system_menu`, `system_role_menu`, `system_user_role` | 角色、菜单权限与授权 |
| **字典与公告** | `system_dict_type`, `system_dict_data`, `system_notice` | 系统数据字典与公告消息 |
| **认证与 OAuth2** | `system_oauth2_client`, `system_oauth2_access_token`, `system_oauth2_refresh_token` | OAuth2 客户端与 Token 鉴权 |
| **租户与日志** | `system_tenant`, `system_tenant_package`, `system_login_log`, `system_operate_log` | 多租户管理与审计日志 |
| **消息通知** | `system_notify_template`, `system_notify_message`, `system_sms_*`, `system_mail_*` | 站内信、短信与邮件模板及日志 |

---

## 5. 备份 / 已瘦身工具表 (Backup Tables with `bak_` Prefix)

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
