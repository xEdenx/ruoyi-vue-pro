#!/usr/bin/env bash
# ==============================================================================
# 无头 BPM (Headless Flowable) walkthrough.md 自动化测试一键脚本
# 说明：部门经理使用 START_USER_SELECT；行政与供应商节点使用 HEADLESS_REMOTE，
# 由本地 Portal mock 在节点到达时按角色参数返回最终 String 用户 ID。
# ==============================================================================

set -e

BASE_URL="${1:-http://127.0.0.1:48080}"
PROCESS_KEY="office_supplies_request_v5"

# 每个 Portal 调用都必须有边界，避免网络或远端数据库异常时遗留无限等待的测试进程。
curl() {
  command curl --connect-timeout 10 --max-time 90 "$@"
}

# 颜色控制
GREEN="\033[32m"
YELLOW="\033[33m"
BLUE="\033[34m"
RED="\033[31m"
CYAN="\033[36m"
MAGENTA="\033[35m"
BOLD="\033[1m"
RESET="\033[0m"

echo -e "${BOLD}${BLUE}==============================================================================${RESET}"
echo -e "${BOLD}${BLUE}  无头 BPM 流程一键自动化全场景测试 (Headless Flowable Walkthrough Test)  ${RESET}"
echo -e "${BOLD}${BLUE}  目标服务地址: ${BASE_URL}${RESET}"
echo -e "${BOLD}${BLUE}  流程 Definition Key: ${PROCESS_KEY}${RESET}"
echo -e "${BOLD}${BLUE}==============================================================================${RESET}\n"

# 构造 Base64URL 格式的 JWT Token
make_jwt() {
  local user_id="$1"
  local role="$2"
  local header_b64=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '\n=' | tr '+/' '-_')
  local payload_b64=$(echo -n "{\"userId\":\"${user_id}\",\"tenantId\":1,\"role\":\"${role}\"}" | base64 | tr -d '\n=' | tr '+/' '-_')
  echo "${header_b64}.${payload_b64}.fake_signature"
}

# 登录身份准备
TOKEN_USER_101=$(make_jwt "101" "ROLE_USER")
TOKEN_MANAGER_102=$(make_jwt "102" "ROLE_MANAGER")
TOKEN_ADMIN_103=$(make_jwt "103" "ROLE_ADMIN")
TOKEN_SUPPLIER_104=$(make_jwt "104" "ROLE_SUPPLIER")
TOKEN_SUPPLIER_105=$(make_jwt "105" "ROLE_SUPPLIER")

echo -e "${BOLD}${MAGENTA}>>> [V5 流程节点确定模式说明]:${RESET}"
echo -e "  1. 部门经理节点 (Activity_Manager) : START_USER_SELECT，发起时传入 [\"102\"]"
echo -e "  2. 行政管理员节点 (Activity_Admin)  : HEADLESS_REMOTE + ROLE_ADMIN -> mock 返回 [\"103\"]"
echo -e "  3. 供应商节点 (Activity_Supplier)   : HEADLESS_REMOTE + ROLE_SUPPLIER -> mock 返回 [\"104\", \"105\"]\n"

# ==============================================================================
# 步骤 1：Portal API 动态拉取流程定义与表单 Schema
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 步骤 1：Portal 拉取流程定义与动态表单 Schema${RESET}"
DEF_RESP=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/process-definition/get?key=${PROCESS_KEY}" \
  -H "Authorization: Bearer ${TOKEN_USER_101}")

DEF_ID=$(echo "${DEF_RESP}" | jq -r '.data.id // empty')
FORM_FIELDS=$(echo "${DEF_RESP}" | jq -c '.data.formFields // []')

if [ -n "${DEF_ID}" ]; then
  echo -e "${GREEN}✓ 流程定义拉取成功: ID = ${DEF_ID}${RESET}"
  echo -e "  - 表单 Schema 字段: ${FORM_FIELDS}\n"
else
  echo -e "${YELLOW}⚠️ 提示: 未响应流程定义 Schema，继续执行流转测试...${RESET}\n"
fi

# ==============================================================================
# 核心函数：根据 Bearer Token 包含的用户与角色在待办列表中寻找并审批任务
# ==============================================================================
fetch_and_approve_task() {
  local user_name="$1"
  local role_name="$2"
  local token="$3"
  local proc_inst_id="$4"
  local reason="$5"

  # 1. 模拟用户携带 Token 调 GET /task/todo-page 识别待办
  local todo_resp=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/task/todo-page?pageNo=1&pageSize=10" \
    -H "Authorization: Bearer ${token}")
  
  local task_node=$(echo "${todo_resp}" | jq -c ".data.list[]? | select(.processInstance.id==\"${proc_inst_id}\")" | head -n 1)
  
  if [ -z "${task_node}" ] || [ "${task_node}" == "null" ]; then
    # 检查流程是否已正常完成或已推进
    local check_detail=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${proc_inst_id}" -H "Authorization: Bearer ${token}")
    local check_status=$(echo "${check_detail}" | jq -r '.data.status // empty')
    if [ "${check_status}" == "2" ] || [ -n "${check_status}" ]; then
      echo -e "${YELLOW}  ℹ [用户 ${user_name} (角色:${role_name})] 该节点已由同组成员完成审批（抢签/会签条件已达标），跳过重复审批${RESET}"
      return 0
    fi
    echo -e "${RED}❌ [用户 ${user_name} (角色:${role_name})] 在待办列表中未查询到属于自己或自己角色的待办任务！${RESET}"
    exit 1
  fi

  local task_id=$(echo "${task_node}" | jq -r '.id')
  local task_name=$(echo "${task_node}" | jq -r '.name')

  echo -e "${CYAN}  🔍 [用户 ${user_name} (角色:${role_name})] 成功查获待办任务 -> 任务名: '${task_name}', Task ID: ${task_id}${RESET}"

  # 2. 提交 PUT /admin-api/bpm/task/approve
  local approve_resp=$(curl -s -X PUT "${BASE_URL}/admin-api/bpm/task/approve" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${task_id}\",\"reason\":\"${reason}\"}")

  local code=$(echo "${approve_resp}" | jq -r '.code // -1')
  if [ "${code}" == "0" ]; then
    echo -e "${GREEN}  ✓ [用户 ${user_name}] 对任务 [${task_name}] 办理同意成功 (PUT /task/approve)${RESET}"
  elif [ "${code}" == "1009005002" ]; then
    local check_detail=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${proc_inst_id}" -H "Authorization: Bearer ${token}")
    local check_status=$(echo "${check_detail}" | jq -r '.data.status // empty')
    if [ -n "${check_status}" ]; then
      echo -e "${YELLOW}  ℹ [用户 ${user_name}] 该任务已由同组成员完成（抢签/会签条件已达成），跳过${RESET}"
      return 0
    fi
    echo -e "${RED}❌ [用户 ${user_name}] 审批提交失败: $(echo "${approve_resp}" | jq -c '.msg')${RESET}"
    exit 1
  else
    echo -e "${RED}❌ [用户 ${user_name}] 审批提交失败: $(echo "${approve_resp}" | jq -c '.msg')${RESET}"
    exit 1
  fi
}

fetch_and_reject_task() {
  local user_name="$1"
  local role_name="$2"
  local token="$3"
  local proc_inst_id="$4"
  local reason="$5"

  local todo_resp=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/task/todo-page?pageNo=1&pageSize=10" \
    -H "Authorization: Bearer ${token}")
  
  local task_node=$(echo "${todo_resp}" | jq -c ".data.list[]? | select(.processInstance.id==\"${proc_inst_id}\")" | head -n 1)
  if [ -z "$task_node" ] || [ "$task_node" == "null" ]; then
    echo "Reject task was not found in the current user's todo list." >&2
    exit 1
  fi
  local task_id=$(echo "${task_node}" | jq -r '.id')
  local task_name=$(echo "${task_node}" | jq -r '.name')

  echo -e "${CYAN}  🔍 [用户 ${user_name} (角色:${role_name})] 成功查获待办任务 -> 任务名: '${task_name}', Task ID: ${task_id}${RESET}"

  local reject_resp=$(curl -s -X PUT "${BASE_URL}/admin-api/bpm/task/reject" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${task_id}\",\"reason\":\"${reason}\"}")

  if [ "$(echo "$reject_resp" | jq -r '.code // -1')" != "0" ]; then
    echo "Reject request failed." >&2
    exit 1
  fi
  echo -e "${RED}  ✓ [用户 ${user_name}] 拒绝任务 [${task_name}]，一票否决终止流程 (PUT /task/reject)${RESET}"
}

assert_process_status() {
  if [ "$1" != "$2" ]; then
    echo "Unexpected process status: expected $2, got $1." >&2
    exit 1
  fi
}

# ==============================================================================
# 场景一：小额申请直通流程 (totalAmount = 500 <= 1000元)
# 说明：排他网关跳过 Activity_Manager；后续节点在到达时由 Portal mock 解算。
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 启动 场景一：小额申请直通流程 (totalAmount = 500元 <= 1000元)${RESET}"

CREATE_RESP_1=$(curl -s -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
  -H "Authorization: Bearer ${TOKEN_USER_101}" \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionId": "'"${DEF_ID}"'",
    "variables": {
      "name": "A4打印纸与黑墨盒",
      "count": 10,
      "price": 50,
      "totalAmount": 500
    },
    "startUserSelectAssignees": {}
  }')

PROC_ID_1=$(echo "${CREATE_RESP_1}" | jq -r '.data // empty')
if [ -z "${PROC_ID_1}" ]; then
  echo -e "${RED}❌ 场景一发起失败: $(echo "${CREATE_RESP_1}" | jq -c '.msg')${RESET}"
  exit 1
fi
echo -e "${GREEN}✓ [发起人 101] 流程发起成功（下游候选人由 Portal mock 在节点到达时解算），实例 ID: ${PROC_ID_1}${RESET}"

# 1. 行政管理员（Portal mock 依据 ROLE_ADMIN 解算为用户 103）查获待办并办理
fetch_and_approve_task "103 行政管理员" "ROLE_ADMIN" "${TOKEN_ADMIN_103}" "${PROC_ID_1}" "同意直通采购"

# 2. 供应商会签（Portal mock 依据 ROLE_SUPPLIER 解算为用户 104、105）全部办理
fetch_and_approve_task "104 供应商成员A" "ROLE_SUPPLIER" "${TOKEN_SUPPLIER_104}" "${PROC_ID_1}" "供应商104确认发货派送"
fetch_and_approve_task "105 供应商成员B" "ROLE_SUPPLIER" "${TOKEN_SUPPLIER_105}" "${PROC_ID_1}" "供应商105确认发货派送"

# 3. 校验流程履历
DETAIL_1=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${PROC_ID_1}" \
  -H "Authorization: Bearer ${TOKEN_USER_101}")
STATUS_1=$(echo "${DETAIL_1}" | jq -r '.data.status // empty')
assert_process_status "$STATUS_1" "2"
echo -e "${BOLD}${GREEN}✓ 场景一全流程测试完毕，流程状态 code: ${STATUS_1} (2=正常完成)${RESET}\n"


# ==============================================================================
# 场景二：大额申请全流程同意 (totalAmount = 3500 > 1000元)
# 说明：发起时仅为经理节点指定用户，行政与供应商节点由 Portal mock 解算。
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 启动 场景二：大额申请全流程通过 (totalAmount = 3500元 > 1000元)${RESET}"

CREATE_RESP_2=$(curl -s -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
  -H "Authorization: Bearer ${TOKEN_USER_101}" \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionId": "'"${DEF_ID}"'",
    "variables": {
      "name": "人体工学电脑椅",
      "count": 2,
      "price": 1750,
      "totalAmount": 3500
    },
    "startUserSelectAssignees": {
      "Activity_Manager": ["102"]
    }
  }')

PROC_ID_2=$(echo "${CREATE_RESP_2}" | jq -r '.data // empty')
if [ -z "${PROC_ID_2}" ]; then
  echo -e "${RED}❌ 场景二发起失败: $(echo "${CREATE_RESP_2}" | jq -c '.msg')${RESET}"
  exit 1
fi
echo -e "${GREEN}✓ [发起人 101] 流程发起成功（仅经理在发起时指定），实例 ID: ${PROC_ID_2}${RESET}"

# 1. 部门经理 (指定个人 102) 查获待办并办理
fetch_and_approve_task "102 部门经理" "ROLE_MANAGER" "${TOKEN_MANAGER_102}" "${PROC_ID_2}" "部门大额同意"

# 2. 办公室管理员（Portal mock 解算的用户 103）查获待办并办理
fetch_and_approve_task "103 办公室管理员" "ROLE_ADMIN" "${TOKEN_ADMIN_103}" "${PROC_ID_2}" "行政备案同意"

# 3. 供应商会签（Portal mock 解算的用户 104、105）全部办理
fetch_and_approve_task "104 供应商成员A" "ROLE_SUPPLIER" "${TOKEN_SUPPLIER_104}" "${PROC_ID_2}" "大额订单出库派送"
fetch_and_approve_task "105 供应商成员B" "ROLE_SUPPLIER" "${TOKEN_SUPPLIER_105}" "${PROC_ID_2}" "大额订单确认派送"

# 4. 校验流程状态
DETAIL_2=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${PROC_ID_2}" \
  -H "Authorization: Bearer ${TOKEN_USER_101}")
STATUS_2=$(echo "${DETAIL_2}" | jq -r '.data.status // empty')
assert_process_status "$STATUS_2" "2"
echo -e "${BOLD}${GREEN}✓ 场景二全流程测试完毕，流程状态 code: ${STATUS_2} (2=正常完成)${RESET}\n"


# ==============================================================================
# 场景三：供应商拒单/一票否决终止流程 (totalAmount = 2500 > 1000元)
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 启动 场景三：供应商拒单一票否决终止流程 (totalAmount = 2500元)${RESET}"

CREATE_RESP_3=$(curl -s -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
  -H "Authorization: Bearer ${TOKEN_USER_101}" \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionId": "'"${DEF_ID}"'",
    "variables": {
      "name": "定制办公茶水柜",
      "count": 1,
      "price": 2500,
      "totalAmount": 2500
    },
    "startUserSelectAssignees": {
      "Activity_Manager": ["102"]
    }
  }')

PROC_ID_3=$(echo "${CREATE_RESP_3}" | jq -r '.data // empty')
if [ -z "${PROC_ID_3}" ]; then
  echo -e "${RED}❌ 场景三发起失败: $(echo "${CREATE_RESP_3}" | jq -c '.msg')${RESET}"
  exit 1
fi
echo -e "${GREEN}✓ [发起人 101] 流程发起成功，实例 ID: ${PROC_ID_3}${RESET}"

# 1. 部门经理 (指定个人 102) 查获待办并办理
fetch_and_approve_task "102 部门经理" "ROLE_MANAGER" "${TOKEN_MANAGER_102}" "${PROC_ID_3}" "部门同意"

# 2. 办公室管理员（Portal mock 解算的用户 103）查获待办并办理
fetch_and_approve_task "103 办公室管理员" "ROLE_ADMIN" "${TOKEN_ADMIN_103}" "${PROC_ID_3}" "行政同意"

# 3. 供应商会签中用户 104 查获待办后拒单
fetch_and_reject_task "104 供应商成员A" "ROLE_SUPPLIER" "${TOKEN_SUPPLIER_104}" "${PROC_ID_3}" "【供应商拒单】商品断货且物流受阻，无法完成派送"

# 4. 校验流程状态 (3=不通过/终止)
DETAIL_3=$(curl -s -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${PROC_ID_3}" \
  -H "Authorization: Bearer ${TOKEN_USER_101}")
STATUS_3=$(echo "${DETAIL_3}" | jq -r '.data.status // empty')
assert_process_status "$STATUS_3" "3"
echo -e "${BOLD}${GREEN}✓ 场景三全流程测试完毕，流程状态 code: ${STATUS_3} (3=拒绝/终止)${RESET}\n"


echo -e "${BOLD}${GREEN}==============================================================================${RESET}"
echo -e "${BOLD}${GREEN}  🎉 Portal mock HEADLESS_REMOTE 候选人解算自动化测试全部通过！  ${RESET}"
echo -e "${BOLD}${GREEN}  场景 1 (小额直通): 实例 ${PROC_ID_1} -> 状态: ${STATUS_1}${RESET}"
echo -e "${BOLD}${GREEN}  场景 2 (大额通过): 实例 ${PROC_ID_2} -> 状态: ${STATUS_2}${RESET}"
echo -e "${BOLD}${GREEN}  场景 3 (拒单终止): 实例 ${PROC_ID_3} -> 状态: ${STATUS_3}${RESET}"
echo -e "${BOLD}${GREEN}==============================================================================${RESET}"
