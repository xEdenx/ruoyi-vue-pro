# Learning Progress & Project Adaptations

本文档记录针对本项目 (RuoYi-Vue-Pro / BPM-Spring-Boot-4) 的架构调优、环境解耦、高版本 JDK 适配及数据库精简等关键技术点与变更经验。

---

## 1. 配置与安全解耦 (Environment Variable Security)

### 变更背景
避免在代码库（Git）中硬编码或暴露云端数据库主机连接串。

### 实践规范
- **敏感 Host 脱敏**：[application-local.yaml](file:///Users/eden/Documents/coding/ruoyi-vue-pro/yudao-server/src/main/resources/application-local.yaml#L75) 中的数据库 URL 不再带有默认地址，统一通过环境变量获取：
  ```yaml
  datasource:
    master:
      url: jdbc:postgresql://${SUPABASE_DB_HOST}/postgres?currentSchema=bpm&sslmode=require
    slave:
      url: jdbc:postgresql://${SUPABASE_DB_HOST}/postgres?currentSchema=bpm&sslmode=require
  ```
- **运行配置**：本地开发或启动时在环境变量中指定 `SUPABASE_DB_HOST=db.vtkxfvguokhvxztrbpvw.supabase.co:5432` 和 `SUPABASE_DB_PASSWORD`。

---

## 2. JDK 21 / 25 高版本兼容与 PR 提交策略

### 变更背景
在 JDK 21/25 下运行服务时，`mybatis-plus-join` (MPJ 1.5.7) 会产生 `WARNING: Final field interceptors in class org.apache.ibatis.plugin.InterceptorChain has been mutated reflectively` 警告。

### 实践规范
- **依赖升级**：将 [yudao-dependencies/pom.xml](file:///Users/eden/Documents/coding/ruoyi-vue-pro/yudao-dependencies/pom.xml#L28) 中的 `<mybatis-plus-join.version>` 升级为 `1.5.9`，消除了反射修改 final 属性的问题。
- **独立 PR 分支管理**：
  为了将该修补提交给上游仓库（`YunaiV/ruoyi-vue-pro` 的 `master-jdk25` 分支）：
  1. 基于 `origin/master-jdk25` 切出全新独立分支 `fix/upgrade-mybatis-plus-join-jdk25`。
  2. 使用 `cherry-pick` 仅提取该升级 Commit (`9535b56eb1`)。
  3. 保持 PR 分支单一独立，不包含本地业务与定制配置。

---

## 3. BPM 数据库精简与基础设施表备份策略

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
