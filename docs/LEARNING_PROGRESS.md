# Learning Progress & Project Adaptations

本文档记录针对本项目 (RuoYi-Vue-Pro / BPM-Spring-Boot-4) 的 BPM 数据库精简、基础设施表处理及运行时流转测试结论。

---

## 1. BPM 数据库精简与基础设施表备份策略

### 变更背景
为简化 `bpm` Schema 占用，移除与 BPM 业务无关的工具表及日志表，采用增加 `bak_` 前缀的安全重命名方式以便必要时恢复。

### 表结构处理分类
1. **重命名为 `bak_` 前缀的无关表**：
   - 代码生成工具表：`infra_codegen_table` -> `bak_infra_codegen_table`, `infra_codegen_column` -> `bak_infra_codegen_column`
   - 定时任务工具表：`infra_job` -> `bak_infra_job`, `infra_job_log` -> `bak_infra_job_log` (已禁用 Quartz)
   - API 日志表：`infra_api_access_log` -> `bak_infra_api_access_log`, `infra_api_error_log` -> `bak_infra_api_error_log`
   - 多数据源表：`infra_data_source_config` -> `bak_infra_data_source_config`
2. **必须保留的基础设施表**：
   - `infra_config`：框架系统参数配置。
   - `infra_file` / `infra_file_config` / `infra_file_content`：BPM 流程表单附件及流程定义文件存储。
