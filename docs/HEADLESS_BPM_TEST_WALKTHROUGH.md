# 无头 BPM 中台 (Headless BPM Engine) 零用户同步与动态选人 API 全流程实测报告 (V2 流程)

> **测试时间**: 2026-08-07 19:07:30
> **测试环境**: RuoYi-Vue-Pro / Flowable 7 / Supabase PostgreSQL / REST API
> **流程定义**: `office_supplies_request_v2` (办公用品申请流程 V2)
> **流程定义 ID**: `11c6aadb-924f-11f1-b105-e62ab33efc67`

---

## 一、 Portal 端标准 Headless 3 步闭环架构

```text
               Portal 页面打开发起申请
                        │
 [ 1. 拉取表单 Schema ] GET /admin-api/bpm/process-definition/get?key=office_supplies_request_v2
                        │
 [ 2. 预测审批节点 ]   POST /admin-api/bpm/process-instance/get-approval-detail (带表单变量)
                        │
 [ 3. 发起与全流转 ]   POST /admin-api/bpm/process-instance/create ➔ PUT /admin-api/bpm/task/approve / reject
```

---

## 二、 步骤 1：Portal API 动态拉取流程定义与表单字段 Schema

### 1.1 HTTP 请求
- **URL**: `GET /admin-api/bpm/process-definition/get?key=office_supplies_request_v2`
- **Header**: `Authorization: Bearer <Portal_User_Token>`, `tenant-id: 1`

### 1.2 返回的数据结构
```json
{
  "code": 0,
  "data": {
    "id": "11c6aadb-924f-11f1-b105-e62ab33efc67",
    "key": "office_supplies_request_v2",
    "name": "办公用品申请流程 V2",
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
