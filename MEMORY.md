# 项目开发记忆与技术经验沉淀 (MEMORY.md)

本文档记录本项目在摸排、测试、调优过程中沉淀的技术经验、数据库特性分析及排障诊断记录。

---

## 1. 数据库与 Flowable 引擎关键分析记忆

### 1.1 `act_ge_bytearray` 存储机制
每次在前端发布/部署流程模型时，Flowable 引擎会在 `act_ge_bytearray` 表中用同一个 `deployment_id_` 保存 2 条核心记录：
1. **`*.bpmn`** (`generated_ = false`)：BPMN 2.0 标准 XML 语法定义文本。
2. **`*.png`** (`generated_ = true`)：引擎自动解析 XML 节点坐标渲染出来的流程矢量预览图。

### 1.2 版本递增与挂起机制
- **版本累加**：重复发布同名模型时，旧版本的 `act_re_procdef` 和 `act_ge_bytearray` 记录**物理保留（不删除）**，保障运行中的旧实例完好流转。
- **挂起停用**：新版本发布时，后端自动调用 `updateProcessDefinitionSuspended()` 将旧版本的 `suspension_state_` 设置为 `2`（挂起），禁止基于旧版本发起新流程。

### 1.3 表单类型区分
- **流程表单 (NORMAL)**：零代码低代码拖拽生成，JSON 存入 `bpm_form`，填写值存入 `act_ru_variable`。
- **业务表单 (CUSTOM)**：手写前后端代码（如 `bpm_oa_leave`），独占业务表落盘，通过 `process_instance_id` 与引擎解耦。

---

## 2. 踩坑与排障诊断记录

### 2.1 前端【导入模型】按钮隐藏问题
- **现象**：用 `admin` 账号登录前端【流程模型】页面，依然看不到【导入模型】按钮。
- **根因**：前端按钮绑定了 `v-hasPermi="[bpm:model:import]"` 指令。而后端 `system_menu` 数据库表中原本未插入 `bpm:model:import` 权限标识，导致前端权限集合未命中，指令自动从 DOM 中删除了该按钮。
- **修复 SQL**：
  ```sql
  INSERT INTO bpm.system_menu (id, name, permission, type, sort, parent_id, status, deleted) 
  VALUES (9999, "模型导入", "bpm:model:import", 3, 6, 1193, 0, 0)
  ON CONFLICT (id) DO NOTHING;
  ```

---

## 3. 实时实例抓取测试记录 (2026-08-07)

测试实例 `9987c9e1-9209-11f1-93d1-5e4a54cbb279` (模型 `Aaaa`) 的落盘数据验证：
- `act_ru_task`: 待办任务流转至节点 `lm_pass`（直属领导通过），分配给目标审批人用户 `118`。
- `act_hi_varinst`: 表单字段 `F6gqmshc4qjxabc="aaa"`, `Fxh3mshc4rviaec="rrr"`, `lm_pass_assignee=118` 成功持久化。
