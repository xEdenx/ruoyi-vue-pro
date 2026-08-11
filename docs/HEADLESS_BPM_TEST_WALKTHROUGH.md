# 无头 BPM 中台 (Headless BPM Engine) 零用户同步与动态选人 API 全流程实测报告 (V5 流程)

> **测试时间**: 2026-08-07 19:07:30
> **测试环境**: RuoYi-Vue-Pro / Flowable 7 / Supabase PostgreSQL / REST API
> **流程定义**: `office_supplies_request_v5` (办公用品申请流程 V5)
> **流程定义 ID**: 每次通过 `/deploy-xml` 发布后由服务返回；不得复用历史 ID。

---

## 当前可执行演练配置（Portal ROLE）

下方 2026-08-07 的履历是旧的实测记录。当前 V5 流程图配置为：

- Activity_Manager：START_USER_SELECT，发起请求传入 `portal-manager-b3c4`。
- Activity_Admin：ROLE（70），Portal mock 按 ROLE_ADMIN 返回 `portal-admin-d5e6`。
- Activity_Supplier：ROLE（70），Portal mock 按 ROLE_SUPPLIER 返回 `portal-supplier-e7f8`、`portal-supplier-f9a0`，并行会签。

本地环境通过 `yudao.bpm.headless-mock.enabled=true` 注册 mock。执行 `script/shell/test_headless_bpm_walkthrough.sh` 时，脚本会先查询 `office_supplies_request_v5` 是否存在可发起的最新发布版本：已发布时输出提示并继续；未发布时会按表单 code `office_supplies_request_v5_form` 查询 `bpm_form` 并解析当前环境的 ID，随后上传 `docs/office_supplies_request_v5.bpmn.xml`，复用同 key 的草稿模型（若有）或新建模型后通过 `POST /admin-api/bpm/process-definition/deploy-xml` 一键保存并发布。`code` 是机器关联字段，`name` 保持用于前端显示；code 必须唯一，可通过 `WALKTHROUGH_FORM_CODE` 覆盖；可通过 `WALKTHROUGH_BPMN_FILE` 指定 BPMN 文件。

脚本的 JWT 以 `role` 声明模拟 Portal 角色。该临时无头授权不读取本地 `system_user_role`；没有 `role`/`roles` 的 JWT 必须被拒绝。

模型维护接口的人员与角色也通过 `BpmPortalIdentityApi` 获取：新模型使用 `managerRoleCodes`，例如 `["ROLE_BPM_MODEL_MANAGER"]`。本地 mock 为 `portal-manager-b3c4` 和 `portal-admin-d5e6` 配置了该角色；生产环境必须提供 Portal HTTP 适配器。未配置时，更新、发布、停用和删除已有模型会失败关闭，绝不回退查询本地用户或角色表。

---

## 一、 Portal 端标准 Headless 3 步闭环架构

```text
               Portal 页面打开发起申请
                        │
 [ 1. 拉取表单 Schema ] GET /admin-api/bpm/process-definition/get?key=office_supplies_request_v5
                        │
 [ 2. 预测审批节点 ]   POST /admin-api/bpm/process-instance/get-approval-detail (带表单变量)
                        │
 [ 3. 发起与全流转 ]   POST /admin-api/bpm/process-instance/create ➔ PUT /admin-api/bpm/task/approve / reject
```

---

## 二、 步骤 1：Portal API 动态拉取流程定义与表单字段 Schema

### 1.1 HTTP 请求
- **URL**: `GET /admin-api/bpm/process-definition/get?key=office_supplies_request_v5`
- **Header**: `Authorization: Bearer <Portal_User_Token>`, `tenant-id: 1`

### 1.2 返回的数据结构
```json
{
  "code": 0,
  "data": {
    "id": "11c6aadb-924f-11f1-b105-e62ab33efc67",
    "key": "office_supplies_request_v5",
    "name": "办公用品申请流程 V5",
    "formFields": [
      "{\"type\":\"input\",\"field\":\"name\",\"title\":\"物品名称\",\"$required\":true}",
      "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"物品数量\",\"$required\":true}",
      "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
      "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总价\",\"$required\":true}"
    ]
  }
}
```

---

## 三、 实测分支场景一：小额申请直通流程 (`totalAmount` <= 1000元)

- **测试流程实例 ID**: `64186dac-924f-11f1-b105-e62ab33efc67`
- **表单变量**: `{"name": "A4打印纸与黑墨盒", "count": 10, "price": 50.0, "totalAmount": 500.0}`

### 流转履历
1. **排他网关计算**: `${totalAmount <= 1000}` 为 true，自动跳过部门经理节点 `Activity_Manager`。
2. **节点 1 (`Activity_Admin` - 办公室管理员审批)**: 调 `PUT /admin-api/bpm/task/approve` 完成审批。
3. **节点 2 (`Activity_Supplier` - 供应商收单派送)**: 调 `PUT /admin-api/bpm/task/approve` 完成履约。
4. **归档状态**: `proc_inst_id_ = 64186dac-924f-11f1-b105-e62ab33efc67` 于 `19:03:32` 正常结束并存档。

---

## 四、 实测分支场景二：大额申请全流程通过 (`totalAmount` > 1000元)

- **测试流程实例 ID**: `ab124950-924f-11f1-b105-e62ab33efc67`
- **表单变量**: `{"name": "人体工学电脑椅", "count": 2, "price": 1750.0, "totalAmount": 3500.0}`

### 流转履历
1. **排他网关计算**: `${totalAmount > 1000}` 为 true，进入部门经理节点 `Activity_Manager`。
2. **节点 1 (`Activity_Manager` - 部门经理审批)**: 调 `PUT /admin-api/bpm/task/approve`（任务 ID `ab127072-924f-11f1-b105-e62ab33efc67`）同意大额采购申请。
3. **节点 2 (`Activity_Admin` - 办公室管理员审批)**: 调 `PUT /admin-api/bpm/task/approve`（任务 ID `be3015eb-924f-11f1-b105-e62ab33efc67`）抢办通过。
4. **节点 3 (`Activity_Supplier` - 供应商收单与派送)**: 调 `PUT /admin-api/bpm/task/approve`（任务 ID `d1b1382d-924f-11f1-b105-e62ab33efc67`）确认收单与派送。
5. **归档状态**: `proc_inst_id_ = ab124950-924f-11f1-b105-e62ab33efc67` 于 `19:05:36` 正常结束并存档。

---

## 五、 实测分支场景三：供应商一票否决/拒单终止流程 (`PUT /task/reject`)

- **测试流程实例 ID**: `f207e026-924f-11f1-b105-e62ab33efc67`
- **表单变量**: `{"name": "定制办公茶水柜", "count": 1, "price": 2500.0, "totalAmount": 2500.0}`

### 流转履历
1. **节点 1 (`Activity_Manager`) & 节点 2 (`Activity_Admin`)**: 顺利审批通过。
2. **节点 3 (`Activity_Supplier` - 供应商收单)**: 供应商在办理任务（任务 ID `195892d3-9250-11f1-b105-e62ab33efc67`）时，发现货源断货损毁，调用 `PUT /admin-api/bpm/task/reject`：
   ```json
   {
     "id": "195892d3-9250-11f1-b105-e62ab33efc67",
     "reason": "【供应商拒单】商品断货且物流受阻，无法完成派送"
   }
   ```
3. **流程终止结果**: Flowable 状态机拦截并立即调用 `moveTaskToEnd()` 终止整个流程实例，`f207e026-924f-11f1-b105-e62ab33efc67` 于 `19:07:24` 终止归档。
