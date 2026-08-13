---
name: bpmn-flow-generator
description: 根据自然语言业务流程描述，自动生成 Flowable BPMN 2.0 XML 流程图文件、Portal 表单 Schema 定义 JSON 及自动插入数据库的 SQL 语句、覆盖各节点执行逻辑的 Spring Boot Java 监听器代码 (如 OfficeSuppliesListeners)，并配置流程节点在运行中自动触发该 Java 逻辑。
---

# BPMN 流程图、表单 SQL 与 Java 执行监听器自动生成 Skill (bpmn-flow-generator)

本 Skill 用于根据用户通过**自然语言描述的业务流程**，自动化生成标准化的 BPMN 2.0 XML 流程图文件、**符合当前项目数据库方言 (PostgreSQL / MySQL / SQL Server) 的 SQL 插入脚本**、配套的 Spring Boot 流程执行监听器（JavaDelegate / ExecutionListener）代码，并完成 BPMN XML 与 Java 代码之间的绑定映射。

---

## 一、 生成的五大核心产物清单

| 产物名称 | 文件存放路径 / 输出格式 | 说明 |
| :--- | :--- | :--- |
| **1. BPMN 2.0 流程图 XML** | `script/bpmn/<process_key>.bpmn.xml` | 符合 Flowable 7 标准规范，包含图形布局 (`bpmndi`) 与监听器扩展属性 |
| **2. 表单定义 SQL 脚本** | `sql/<process_key>_form.sql` | 依据项目数据库方言 (**PostgreSQL / MySQL / SQL Server**) **仅生成 SQL 文件**，不自动在线执行 |
| **3. Portal 动态表单 Schema** | JSON Payload (`formFields` & `formConf`) | 供 Portal 前端调 API 直接渲染表单控件定义 |
| **4. Java 执行监听器组件** | `yudao-bpm/.../listener/<domain>/<ProcessName>Listeners.java` | 参照 **`OfficeSuppliesListeners`** 的范式，基于 Spring `@Component` 注入，包含流程节点到达/完成时的业务逻辑 |
| **5. Portal 对接 API JSON** | 发起流程 `POST /process-instance/create` 测试 Payload | 包含 `variables` 表单变量与 `startUserSelectAssignees` 动态选人字典 |

---

## 二、 核心遵循规范 (Architecture Rules)

1. **候选人解算边界 (Rule 3.2)**：若 Portal 在发起时已确定最终用户 ID，使用 **【发起人自选 (candidateStrategy="35")】** 并通过 `startUserSelectAssignees` 传入；若节点到达时需要按 Portal 目标角色解算，使用 **【ROLE (candidateStrategy="70")】**，并以 `candidateParam` 传入 Portal 角色编码。禁止使用 BPM 本地角色策略。
2. **零用户数据同步 (Rule 3.1)**：Java 监听器中不依赖本地 `system_users` 表，审批人 ID 均作为 Portal 原始字符串在 Flowable `act_ru_task` 中流转。
3. **Bean 表达式绑定监听器**：在 BPMN XML 的 `<extensionElements>` 中使用 `delegateExpression="${<beanName>.<methodName>}"` 或 `delegateExpression="${<beanName>}"` 进行解耦绑定。
4. **监听器编写范式**：完全采用 `OfficeSuppliesListeners` 模式，每个流程独立一个以 `<ProcessName>Listeners` 命名的组件文件，内部定义静态内部类监听器并通过 `PREFIX` 区分 Bean 名称。
5. **主流 DB 方言精准适配 (PostgreSQL / MySQL / SQL Server)**：
   - 自动检查 `application.yaml` / 数据库驱动，优先适配 **PostgreSQL** (使用 `NOW()`)、**MySQL** (使用 `NOW()`) 或 **SQL Server** (使用 `SYSDATETIME()`)。
   - 仅生成 `sql/<process_key>_form.sql` 文件，绝不在目标数据库自动执行。
6. **表单机器关联**：`bpm_form.code` 是程序化的唯一标识，推荐固定为 `<process_key>_form`；`name` 仅用于前端展示。流程定义仍保存数据库生成的 `formId`，但初始化、walkthrough 和发布脚本必须先按 `code` 查询当前环境的实际 ID，禁止写死 `formId` 或表单主键。

---

## 三、 自动化生成 5 步标准 Workflow

### 步骤 1：解析自然语言流程描述
从用户输入的自然语言中提取以下要素：
- **流程标识与名称**：`process_key` (如 `reimbursement_request`), `process_name` (如 `差旅报销流程`)
- **表单字段定义**：如 `name` (报销说明, input), `totalAmount` (报销金额, inputNumber)
- **网关条件与限额**：如总金额 `totalAmount`，网关限额（如 `<= 2000` 直通，`> 2000` 需总监审批）
- **审批节点与机制**：
  - 单人审批节点（如 部门经理 `Activity_Manager`）
  - 角色或签/抢办节点（如 财务角色 `Activity_Finance`）
  - 会签/多实例节点（如 供应商全员签收 `Activity_Supplier`）
- **解算时机**：发起时确定的节点使用策略 35；需在任务到达时读取 Portal 当前组织关系的角色/会签节点使用策略 70，并提供非空的 `candidateParam`。

---

### 步骤 2：生成表单 SQL 脚本 (`sql/<process_key>_form.sql`)

依据项目中 3 大主流数据库方言之一生成 SQL 文本：

#### 示例 1: PostgreSQL 方言 (本工程默认 Supabase 环境)
```sql
INSERT INTO bpm_form (
    code, name, status, conf, fields, remark,
    creator, create_time, updater, update_time, deleted, tenant_id
) VALUES (
    '<process_key>_form',
    '<process_name>表单',
    0,
    '{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}',
    '[
        "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
        "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
    ]',
    '系统自动生成的表单 Schema',
    '1', NOW(), '1', NOW(), 0, 1
) RETURNING id;
```

#### 示例 2: MySQL 方言
```sql
INSERT INTO `bpm_form` (
    `code`, `name`, `status`, `conf`, `fields`, `remark`,
    `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
) VALUES (
    '<process_key>_form',
    '<process_name>表单',
    0,
    '{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}',
    '[
        "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
        "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
    ]',
    '系统自动生成的表单 Schema',
    '1', NOW(), '1', NOW(), 0, 1
);
```

#### 示例 3: SQL Server 方言
```sql
INSERT INTO [bpm_form] (
    [code], [name], [status], [conf], [fields], [remark],
    [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]
)
OUTPUT INSERTED.[id]
VALUES (
    N'<process_key>_form',
    N'<process_name>表单',
    0,
    N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}',
    N'[
        "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
        "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
    ]',
    N'系统自动生成的表单 Schema',
    N'1', SYSDATETIME(), N'1', SYSDATETIME(), 0, 1
);
```

上述 SQL 均由数据库生成主键。后续发布时使用 `<process_key>_form` 查询 `bpm_form.code` 取得实际 `id`，不要假设插入顺序或主键数值。

---

### 步骤 3：生成 BPMN 2.0 XML 文件 (`script/bpmn/<process_key>.bpmn.xml`)

生成的 XML 结构模板：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://www.flowable.org/processdef">

  <process id="<process_key>" name="<process_name>" isExecutable="true">
    
    <!-- 开始节点 -->
    <startEvent id="StartEvent_1" name="发起申请" />

    <!-- 排他网关 (按金额/条件分支) -->
    <exclusiveGateway id="ExclusiveGateway_1" name="条件网关" />

    <!-- 审批节点 1 (绑定 Java 监听器，参考 OfficeSuppliesListeners 格式) -->
    <userTask id="Activity_Manager" name="部门经理审批" flowable:candidateStrategy="35">
      <extensionElements>
        <flowable:executionListener event="end" delegateExpression="${<process_key>Listeners_deptManagerApproval}" />
      </extensionElements>
    </userTask>

    <!-- 审批节点 2（Portal 在节点到达时按角色实时解算的多实例会签节点） -->
    <userTask id="Activity_Supplier" name="供应商全员签收"
              flowable:candidateStrategy="70" flowable:candidateParam="ROLE_SUPPLIER">
      <extensionElements>
        <flowable:executionListener event="end" delegateExpression="${<process_key>Listeners_supplierApproval}" />
        <flowable:candidateStrategy>70</flowable:candidateStrategy>
        <flowable:candidateParam>ROLE_SUPPLIER</flowable:candidateParam>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false">
        <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <!-- 流转连线与分支表达式 -->
    <sequenceFlow id="Flow_start_to_gw" sourceRef="StartEvent_1" targetRef="ExclusiveGateway_1" />
    <sequenceFlow id="Flow_low_amount" sourceRef="ExclusiveGateway_1" targetRef="Activity_Admin">
      <conditionExpression xsi:type="tLanguage">${totalAmount &lt;= 1000}</conditionExpression>
    </sequenceFlow>

    <!-- 结束节点 -->
    <endEvent id="EndEvent_1" name="流程结束" />

  </process>

  <!-- 图形布局定义 (用于前端 bpmn-js 渲染) -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_<process_key>">
    <bpmndi:BPMNPlane id="BPMNPlane_<process_key>" bpmnElement="<process_key>">
      <!-- 渲染 BPMNShape 与 BPMNEdge ... -->
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>
```

---

### 步骤 4：生成 Spring Boot Java 监听器代码（完全参照 `OfficeSuppliesListeners` 模式）

创建路径：`yudao-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/listener/<domain>/<ProcessName>Listeners.java`

Java 代码生成标准模板（与 `OfficeSuppliesListeners` 结构完全一致）：

```java
package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.<domain>;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * <process_name> 流程节点执行监听器集合
 */
@Slf4j
@Component("<process_key>Listeners")
public class <ProcessName>Listeners {

    public static final String PREFIX = "<process_key>Listeners_";

    /**
     * 部门经理审批通过监听器
     */
    @Component(PREFIX + "deptManagerApproval")
    @Slf4j
    public static class DeptManagerApprovalListener implements JavaDelegate {
        @Override
        public void execute(DelegateExecution execution) {
            String processInstanceId = execution.getProcessInstanceId();
            Map<String, Object> variables = execution.getVariables();
            log.info("[<process_name>][部门经理审批节点完成] processInstanceId: {}, variables: {}",
                    processInstanceId, variables);
            // TODO: 在此处编写触发的业务逻辑 (如: 更新业务系统采购单状态为 已审批)
        }
    }

    /**
     * 供应商多实例履约监听器
     */
    @Component(PREFIX + "supplierApproval")
    @Slf4j
    public static class SupplierApprovalListener implements JavaDelegate {
        @Override
        public void execute(DelegateExecution execution) {
            String processInstanceId = execution.getProcessInstanceId();
            log.info("[<process_name>][供应商签收履约完成] processInstanceId: {}", processInstanceId);
        }
    }
}
```

---

### 步骤 5：编译验证与生成 REST API 入参 Json

1. **编译检查**：自动运行 `mvn compile -pl yudao-bpm -am -DskipTests` 校验 Java 代码语法无误。
2. **提供 Portal REST API 接口测试 Json**：

```json
{
  "processDefinitionKey": "<process_key>",
  "variables": {
    "name": "测试申请项",
    "totalAmount": 1500.0
  },
  "startUserSelectAssignees": {
    "Activity_Manager": ["portal-user-102"]
  }
}
```

策略 70 的节点不放入 `startUserSelectAssignees`；由 `PortalRoleCandidateApi` 在任务到达时按 `candidateParam` 中的角色编码返回最终 String 用户 ID 集合。

---

## 四、 标杆案例示范：办公用品申请流程 V5 (`office_supplies_request_v5`)

- **BPMN XML**：[script/bpmn/office_supplies_request_v5.bpmn.xml](file:///Users/John%20Doe/Documents/coding/ruoyi-vue-pro/script/bpmn/office_supplies_request_v5.bpmn.xml)
- **Java 监听器**：[OfficeSuppliesListeners.java](file:///Users/John%20Doe/Documents/coding/ruoyi-vue-pro/yudao-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/listener/office/OfficeSuppliesListeners.java)
- **测试 Walkthrough**：[docs/HEADLESS_BPM_TEST_WALKTHROUGH.md](file:///Users/John%20Doe/Documents/coding/ruoyi-vue-pro/docs/HEADLESS_BPM_TEST_WALKTHROUGH.md)
