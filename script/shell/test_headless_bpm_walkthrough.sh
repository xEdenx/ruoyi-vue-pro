#!/usr/bin/env bash
# ==============================================================================
# 无头 BPM (Headless Flowable) walkthrough.md 自动化测试一键脚本
# 说明：部门经理使用 START_USER_SELECT；行政与供应商节点使用 ROLE（70），
# 由本地 Portal mock 在节点到达时按目标角色返回最终 String 用户 ID。
# 用法：bash script/shell/test_headless_bpm_walkthrough.sh [BASE_URL]
# 可选环境变量：TODO_PAGE_SIZE、TASK_POLL_ATTEMPTS、TASK_POLL_INTERVAL_SECONDS、
# RESULT_DIR、RUN_ID、WALKTHROUGH_FORM_CODE、WALKTHROUGH_BPMN_FILE。脚本只依赖目标环境
# 公开的 HTTP API 与 curl/jq，不调用 MCP。
# ==============================================================================

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:48080}"
PROCESS_KEY="office_supplies_request_v5"
PROCESS_NAME="办公用品申请流程 V5"
PROCESS_CATEGORY="default"
FORM_CODE="${WALKTHROUGH_FORM_CODE:-office_supplies_request_v5_form}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BPMN_FILE="${WALKTHROUGH_BPMN_FILE:-${REPO_ROOT}/docs/office_supplies_request_v5.bpmn.xml}"
TODO_PAGE_SIZE="${TODO_PAGE_SIZE:-100}"
TASK_POLL_ATTEMPTS="${TASK_POLL_ATTEMPTS:-15}"
TASK_POLL_INTERVAL_SECONDS="${TASK_POLL_INTERVAL_SECONDS:-1}"
RESULT_DIR="${RESULT_DIR:-output/walkthrough}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
RESULT_FILE="${RESULT_DIR}/headless-bpm-${RUN_ID}.json"
CURRENT_STEP="初始化"
declare -a SCENARIO_RESULTS=()

# 每个 Portal 调用都必须有边界，避免网络或远端数据库异常时遗留无限等待的测试进程。
curl() {
  command curl --silent --show-error --fail --connect-timeout 10 --max-time 90 "$@"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少必需命令: $1" >&2
    exit 1
  }
}

write_result_file() {
  local status="$1"
  local message="${2:-}"
  mkdir -p "$RESULT_DIR"
  printf '%s\n' "${SCENARIO_RESULTS[@]:-}" | jq -s \
    --arg runId "$RUN_ID" \
    --arg status "$status" \
    --arg message "$message" \
    --arg processDefinitionKey "$PROCESS_KEY" \
    --arg baseUrl "$BASE_URL" \
    --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{runId: $runId, status: $status, message: $message, processDefinitionKey: $processDefinitionKey,
      baseUrl: $baseUrl, generatedAt: $generatedAt, scenarios: .}' > "$RESULT_FILE"
}

record_scenario() {
  local name="$1"
  local process_instance_id="$2"
  local status="$3"
  local existing existing_process_instance_id
  local -a updated_results=()
  for existing in "${SCENARIO_RESULTS[@]:-}"; do
    [ -n "${existing}" ] || continue
    existing_process_instance_id=$(echo "${existing}" | jq -r '.processInstanceId')
    if [ "${existing_process_instance_id}" != "${process_instance_id}" ]; then
      updated_results+=("${existing}")
    fi
  done
  if [ "${#updated_results[@]}" -gt 0 ]; then
    SCENARIO_RESULTS=("${updated_results[@]}")
  else
    SCENARIO_RESULTS=()
  fi
  SCENARIO_RESULTS+=("$(jq -cn --arg name "$name" --arg processInstanceId "$process_instance_id" --argjson status "$status" \
    '{name: $name, processInstanceId: $processInstanceId, status: $status}')")
  write_result_file "running" "已完成 ${name}"
}

fail() {
  local message="$1"
  echo "❌ ${message}" >&2
  write_result_file "failed" "$message"
  echo "  结果文件: ${RESULT_FILE}" >&2
  exit 1
}

on_unexpected_error() {
  local exit_code="$1"
  trap - ERR
  write_result_file "failed" "执行失败，当前步骤：${CURRENT_STEP}"
  echo "❌ 执行失败，当前步骤：${CURRENT_STEP}" >&2
  echo "  结果文件: ${RESULT_FILE}" >&2
  exit "$exit_code"
}

trap 'on_unexpected_error $?' ERR

require_command curl
require_command jq

[ -r "${BPMN_FILE}" ] || {
  echo "缺少或无法读取 BPMN 文件: ${BPMN_FILE}" >&2
  exit 1
}
[[ -n "${FORM_CODE}" ]] || {
  echo "WALKTHROUGH_FORM_CODE 不能为空" >&2
  exit 1
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

# 构造仅供本地 Headless Mock 使用的 Base64URL token。
# 生产 Portal 必须使用已经过验签的 JWT 或受信任网关身份，不能复用本 token。
make_jwt() {
  local user_id="$1"
  local role="$2"
  local header_b64=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '\n=' | tr '+/' '-_')
  local payload_b64=$(echo -n "{\"userId\":\"${user_id}\",\"tenantId\":1,\"role\":\"${role}\",\"headlessMock\":true}" | base64 | tr -d '\n=' | tr '+/' '-_')
  echo "${header_b64}.${payload_b64}.fake_signature"
}

# 登录身份准备
TOKEN_PORTAL_REQUESTER=$(make_jwt "portal-requester-a1f2" "ROLE_USER")
TOKEN_PORTAL_MANAGER=$(make_jwt "portal-manager-b3c4" "ROLE_MANAGER")
TOKEN_PORTAL_ADMIN=$(make_jwt "portal-admin-d5e6" "ROLE_ADMIN")
TOKEN_PORTAL_SUPPLIER_A=$(make_jwt "portal-supplier-e7f8" "ROLE_SUPPLIER")
TOKEN_PORTAL_SUPPLIER_B=$(make_jwt "portal-supplier-f9a0" "ROLE_SUPPLIER")

echo -e "${BOLD}${MAGENTA}>>> [V5 流程节点确定模式说明]:${RESET}"
echo -e "  1. 部门经理节点 (Activity_Manager) : START_USER_SELECT，发起时传入 [\"portal-manager-b3c4\"]"
echo -e "  2. 行政管理员节点 (Activity_Admin)  : ROLE(70) + ROLE_ADMIN -> mock 返回 [\"portal-admin-d5e6\"]"
echo -e "  3. 供应商节点 (Activity_Supplier)   : ROLE(70) + ROLE_SUPPLIER -> mock 返回 [\"portal-supplier-e7f8\", \"portal-supplier-f9a0\"]\n"

# ==============================================================================
# 前置检查：读取可发起的最新定义；未发布时上传当前 BPMN 并一键保存、发布。
# ==============================================================================
get_published_definition() {
  CURRENT_STEP="检查流程定义 ${PROCESS_KEY} 是否已发布"
  DEF_RESP=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-definition/get?key=${PROCESS_KEY}" \
    -H "Authorization: Bearer ${TOKEN_PORTAL_REQUESTER}")
  assert_api_success "${DEF_RESP}" "检查流程定义 ${PROCESS_KEY}"
  DEF_ID=$(echo "${DEF_RESP}" | jq -r '.data.id // empty')
}

resolve_form_id() {
  CURRENT_STEP="按表单 code ${FORM_CODE} 查询动态表单"
  local form_list_resp form_matches form_match_count
  form_list_resp=$(curl -X GET "${BASE_URL}/admin-api/bpm/form/list-all-simple" \
    -H "Authorization: Bearer ${TOKEN_PORTAL_MANAGER}")
  assert_api_success "${form_list_resp}" "查询动态表单 ${FORM_CODE}"
  form_matches=$(echo "${form_list_resp}" | jq -c --arg formCode "${FORM_CODE}" \
    '[.data[]? | select(.code == $formCode)]')
  form_match_count=$(echo "${form_matches}" | jq 'length')
  if [ "${form_match_count}" != "1" ]; then
    fail "表单 code ${FORM_CODE} 应唯一匹配一个 bpm_form，实际匹配数量: ${form_match_count}"
  fi
  FORM_ID=$(echo "${form_matches}" | jq -r '.[0].id // empty')
  [[ "${FORM_ID}" =~ ^[0-9]+$ ]] || fail "表单 code ${FORM_CODE} 未返回有效的表单 ID"
  echo -e "${GREEN}✓ 已按表单 code 解析动态表单: ${FORM_CODE} -> ID = ${FORM_ID}${RESET}"
}

ensure_process_definition_published() {
  get_published_definition
  if [ -n "${DEF_ID}" ]; then
    echo -e "${GREEN}✓ 流程定义已发布，跳过创建和发布: key = ${PROCESS_KEY}, ID = ${DEF_ID}${RESET}"
    return 0
  fi

  echo -e "${YELLOW}⚠ 流程定义未发布，准备上传并发布 BPMN: ${BPMN_FILE}${RESET}"
  resolve_form_id
  CURRENT_STEP="查找流程模型 ${PROCESS_KEY}"
  local model_list_resp model_id model_json deploy_resp deployed_definition_id
  model_list_resp=$(curl -X GET "${BASE_URL}/admin-api/bpm/model/list" \
    -H "Authorization: Bearer ${TOKEN_PORTAL_MANAGER}")
  assert_api_success "${model_list_resp}" "查找流程模型 ${PROCESS_KEY}"
  model_id=$(echo "${model_list_resp}" | jq -r --arg processKey "${PROCESS_KEY}" \
    '.data[]? | select(.key == $processKey) | .id' | head -n 1)

  if [ -n "${model_id}" ]; then
    echo -e "${YELLOW}  - 找到未发布模型，复用模型 ID: ${model_id}${RESET}"
  else
    echo -e "${YELLOW}  - 未找到流程模型，将创建新模型。${RESET}"
  fi

  model_json=$(jq -cn \
    --arg modelId "${model_id}" \
    --arg key "${PROCESS_KEY}" \
    --arg name "${PROCESS_NAME}" \
    --arg category "${PROCESS_CATEGORY}" \
    --argjson formId "${FORM_ID}" \
    '{key: $key, name: $name, category: $category, type: 10, formType: 10, formId: $formId,
      visible: true, managerRoleCodes: ["ROLE_BPM_MODEL_MANAGER"]}
     + (if $modelId == "" then {} else {id: $modelId} end)')

  CURRENT_STEP="发布流程定义 ${PROCESS_KEY}"
  deploy_resp=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-definition/deploy-xml" \
    -H "Authorization: Bearer ${TOKEN_PORTAL_MANAGER}" \
    -F "model=${model_json};type=application/json" \
    -F "file=@${BPMN_FILE};type=application/xml")
  assert_api_success "${deploy_resp}" "发布流程定义 ${PROCESS_KEY}"
  deployed_definition_id=$(echo "${deploy_resp}" | jq -r '.data // empty')
  [ -n "${deployed_definition_id}" ] || fail "发布流程定义 ${PROCESS_KEY} 未返回流程定义 ID"

  get_published_definition
  [ -n "${DEF_ID}" ] || fail "流程定义 ${PROCESS_KEY} 发布后仍不可发起"
  echo -e "${GREEN}✓ 流程定义创建并发布成功: ID = ${DEF_ID}${RESET}"
}

# ==============================================================================
# 核心函数：根据 Bearer Token 包含的用户与角色在待办列表中寻找并审批任务
# ==============================================================================
assert_api_success() {
  local response="$1"
  local action="$2"
  local code
  code=$(echo "${response}" | jq -r '.code // empty')
  if [ "${code}" != "0" ]; then
    fail "${action} 返回业务错误：$(echo "${response}" | jq -c '{code, msg}')"
  fi
}

fetch_task_node() {
  local token="$1"
  local proc_inst_id="$2"
  local attempt todo_resp task_node
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    todo_resp=$(curl -X GET "${BASE_URL}/admin-api/bpm/task/todo-page?pageNo=1&pageSize=${TODO_PAGE_SIZE}" \
      -H "Authorization: Bearer ${token}")
    assert_api_success "${todo_resp}" "查询待办（第 ${attempt} 次）"
    task_node=$(echo "${todo_resp}" | jq -c --arg processInstanceId "${proc_inst_id}" \
      '.data.list[]? | select(.processInstance.id == $processInstanceId)' | head -n 1)
    if [ -n "${task_node}" ] && [ "${task_node}" != "null" ]; then
      echo "${task_node}"
      return 0
    fi
    sleep "${TASK_POLL_INTERVAL_SECONDS}"
  done
  return 1
}

wait_for_process_status() {
  local token="$1"
  local proc_inst_id="$2"
  local expected_status="$3"
  local attempt detail_resp actual_status
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    detail_resp=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${proc_inst_id}" \
      -H "Authorization: Bearer ${token}")
    assert_api_success "${detail_resp}" "查询流程 ${proc_inst_id} 的审批轨迹（第 ${attempt} 次）"
    actual_status=$(echo "${detail_resp}" | jq -r '.data.status // empty')
    if [ "${actual_status}" = "${expected_status}" ]; then
      echo "${actual_status}"
      return 0
    fi
    sleep "${TASK_POLL_INTERVAL_SECONDS}"
  done
  fail "流程 ${proc_inst_id} 状态未在 ${TASK_POLL_ATTEMPTS} 次轮询内变为 ${expected_status}"
}

fetch_and_approve_task() {
  local user_name="$1"
  local role_name="$2"
  local token="$3"
  local proc_inst_id="$4"
  local reason="$5"

  CURRENT_STEP="${user_name} 查询流程 ${proc_inst_id} 的待办"
  local task_node
  if ! task_node=$(fetch_task_node "${token}" "${proc_inst_id}"); then
    fail "[用户 ${user_name} (角色:${role_name})] 在 ${TASK_POLL_ATTEMPTS} 次轮询内未找到流程 ${proc_inst_id} 的待办"
  fi

  local task_id=$(echo "${task_node}" | jq -r '.id')
  local task_name=$(echo "${task_node}" | jq -r '.name')

  echo -e "${CYAN}  🔍 [用户 ${user_name} (角色:${role_name})] 成功查获待办任务 -> 任务名: '${task_name}', Task ID: ${task_id}${RESET}"

  # 2. 提交 PUT /admin-api/bpm/task/approve
  CURRENT_STEP="${user_name} 同意任务 ${task_id}"
  local approve_resp=$(curl -X PUT "${BASE_URL}/admin-api/bpm/task/approve" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${task_id}\",\"reason\":\"${reason}\"}")

  assert_api_success "${approve_resp}" "[用户 ${user_name}] 同意任务 ${task_id}"
  echo -e "${GREEN}  ✓ [用户 ${user_name}] 对任务 [${task_name}] 办理同意成功 (PUT /task/approve)${RESET}"
}

fetch_and_reject_task() {
  local user_name="$1"
  local role_name="$2"
  local token="$3"
  local proc_inst_id="$4"
  local reason="$5"

  CURRENT_STEP="${user_name} 查询流程 ${proc_inst_id} 的拒绝待办"
  local task_node
  if ! task_node=$(fetch_task_node "${token}" "${proc_inst_id}"); then
    fail "[用户 ${user_name} (角色:${role_name})] 在 ${TASK_POLL_ATTEMPTS} 次轮询内未找到流程 ${proc_inst_id} 的拒绝待办"
  fi
  local task_id=$(echo "${task_node}" | jq -r '.id')
  local task_name=$(echo "${task_node}" | jq -r '.name')

  echo -e "${CYAN}  🔍 [用户 ${user_name} (角色:${role_name})] 成功查获待办任务 -> 任务名: '${task_name}', Task ID: ${task_id}${RESET}"

  CURRENT_STEP="${user_name} 拒绝任务 ${task_id}"
  local reject_resp=$(curl -X PUT "${BASE_URL}/admin-api/bpm/task/reject" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${task_id}\",\"reason\":\"${reason}\"}")

  assert_api_success "${reject_resp}" "[用户 ${user_name}] 拒绝任务 ${task_id}"
  echo -e "${RED}  ✓ [用户 ${user_name}] 拒绝任务 [${task_name}]，一票否决终止流程 (PUT /task/reject)${RESET}"
}

# ==============================================================================
# 步骤 1：确认 BPMN 流程图已发布，再由 Portal API 拉取流程定义与表单 Schema
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 步骤 1：检查/发布 BPMN，再拉取流程定义与动态表单 Schema${RESET}"
ensure_process_definition_published
FORM_FIELDS=$(echo "${DEF_RESP}" | jq -c '.data.formFields // []')
echo -e "${GREEN}✓ 流程定义拉取成功: ID = ${DEF_ID}${RESET}"
echo -e "  - 表单 Schema 字段: ${FORM_FIELDS}\n"

# ==============================================================================
# 场景一：小额申请直通流程 (totalAmount = 500 <= 1000元)
# 说明：排他网关跳过 Activity_Manager；后续节点在到达时由 Portal mock 解算。
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 启动 场景一：小额申请直通流程 (totalAmount = 500元 <= 1000元)${RESET}"

CURRENT_STEP="发起场景一流程"
CREATE_RESP_1=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
  -H "Authorization: Bearer ${TOKEN_PORTAL_REQUESTER}" \
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

assert_api_success "${CREATE_RESP_1}" "发起场景一流程"
PROC_ID_1=$(echo "${CREATE_RESP_1}" | jq -r '.data // empty')
[ -n "${PROC_ID_1}" ] || fail "场景一未返回流程实例 ID"
record_scenario "小额直通" "${PROC_ID_1}" "1"
echo -e "${GREEN}✓ [发起人 portal-requester-a1f2] 流程发起成功（下游候选人由 Portal mock 在节点到达时解算），实例 ID: ${PROC_ID_1}${RESET}"

# 1. 行政管理员（Portal mock 依据 ROLE_ADMIN 解算为用户 portal-admin-d5e6）查获待办并办理
fetch_and_approve_task "portal-admin-d5e6 行政管理员" "ROLE_ADMIN" "${TOKEN_PORTAL_ADMIN}" "${PROC_ID_1}" "同意直通采购"

# 2. 供应商会签（Portal mock 依据 ROLE_SUPPLIER 解算为用户 portal-supplier-e7f8、portal-supplier-f9a0）全部办理
fetch_and_approve_task "portal-supplier-e7f8 供应商成员A" "ROLE_SUPPLIER" "${TOKEN_PORTAL_SUPPLIER_A}" "${PROC_ID_1}" "供应商portal-supplier-e7f8确认发货派送"
fetch_and_approve_task "portal-supplier-f9a0 供应商成员B" "ROLE_SUPPLIER" "${TOKEN_PORTAL_SUPPLIER_B}" "${PROC_ID_1}" "供应商portal-supplier-f9a0确认发货派送"

# 3. 校验流程履历
CURRENT_STEP="校验场景一最终状态"
STATUS_1=$(wait_for_process_status "${TOKEN_PORTAL_REQUESTER}" "${PROC_ID_1}" "2")
record_scenario "小额直通" "${PROC_ID_1}" "${STATUS_1}"
echo -e "${BOLD}${GREEN}✓ 场景一全流程测试完毕，流程状态 code: ${STATUS_1} (2=正常完成)${RESET}\n"


# ==============================================================================
# 场景二：大额申请全流程同意 (totalAmount = 3500 > 1000元)
# 说明：发起时仅为经理节点指定用户，行政与供应商节点由 Portal mock 解算。
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 启动 场景二：大额申请全流程通过 (totalAmount = 3500元 > 1000元)${RESET}"

CURRENT_STEP="发起场景二流程"
CREATE_RESP_2=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
  -H "Authorization: Bearer ${TOKEN_PORTAL_REQUESTER}" \
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
      "Activity_Manager": ["portal-manager-b3c4"]
    }
  }')

assert_api_success "${CREATE_RESP_2}" "发起场景二流程"
PROC_ID_2=$(echo "${CREATE_RESP_2}" | jq -r '.data // empty')
[ -n "${PROC_ID_2}" ] || fail "场景二未返回流程实例 ID"
record_scenario "大额审批与会签" "${PROC_ID_2}" "1"
echo -e "${GREEN}✓ [发起人 portal-requester-a1f2] 流程发起成功（仅经理在发起时指定），实例 ID: ${PROC_ID_2}${RESET}"

# 1. 部门经理 (指定个人 portal-manager-b3c4) 查获待办并办理
fetch_and_approve_task "portal-manager-b3c4 部门经理" "ROLE_MANAGER" "${TOKEN_PORTAL_MANAGER}" "${PROC_ID_2}" "部门大额同意"

# 2. 办公室管理员（Portal mock 解算的用户 portal-admin-d5e6）查获待办并办理
fetch_and_approve_task "portal-admin-d5e6 办公室管理员" "ROLE_ADMIN" "${TOKEN_PORTAL_ADMIN}" "${PROC_ID_2}" "行政备案同意"

# 3. 供应商会签（Portal mock 解算的用户 portal-supplier-e7f8、portal-supplier-f9a0）全部办理
fetch_and_approve_task "portal-supplier-e7f8 供应商成员A" "ROLE_SUPPLIER" "${TOKEN_PORTAL_SUPPLIER_A}" "${PROC_ID_2}" "大额订单出库派送"
fetch_and_approve_task "portal-supplier-f9a0 供应商成员B" "ROLE_SUPPLIER" "${TOKEN_PORTAL_SUPPLIER_B}" "${PROC_ID_2}" "大额订单确认派送"

# 4. 校验流程状态
CURRENT_STEP="校验场景二最终状态"
STATUS_2=$(wait_for_process_status "${TOKEN_PORTAL_REQUESTER}" "${PROC_ID_2}" "2")
record_scenario "大额审批与会签" "${PROC_ID_2}" "${STATUS_2}"
echo -e "${BOLD}${GREEN}✓ 场景二全流程测试完毕，流程状态 code: ${STATUS_2} (2=正常完成)${RESET}\n"


# ==============================================================================
# 场景三：供应商拒单/一票否决终止流程 (totalAmount = 2500 > 1000元)
# ==============================================================================
echo -e "${BOLD}${YELLOW}>>> 启动 场景三：供应商拒单一票否决终止流程 (totalAmount = 2500元)${RESET}"

CURRENT_STEP="发起场景三流程"
CREATE_RESP_3=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
  -H "Authorization: Bearer ${TOKEN_PORTAL_REQUESTER}" \
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
      "Activity_Manager": ["portal-manager-b3c4"]
    }
  }')

assert_api_success "${CREATE_RESP_3}" "发起场景三流程"
PROC_ID_3=$(echo "${CREATE_RESP_3}" | jq -r '.data // empty')
[ -n "${PROC_ID_3}" ] || fail "场景三未返回流程实例 ID"
record_scenario "供应商拒绝终止" "${PROC_ID_3}" "1"
echo -e "${GREEN}✓ [发起人 portal-requester-a1f2] 流程发起成功，实例 ID: ${PROC_ID_3}${RESET}"

# 1. 部门经理 (指定个人 portal-manager-b3c4) 查获待办并办理
fetch_and_approve_task "portal-manager-b3c4 部门经理" "ROLE_MANAGER" "${TOKEN_PORTAL_MANAGER}" "${PROC_ID_3}" "部门同意"

# 2. 办公室管理员（Portal mock 解算的用户 portal-admin-d5e6）查获待办并办理
fetch_and_approve_task "portal-admin-d5e6 办公室管理员" "ROLE_ADMIN" "${TOKEN_PORTAL_ADMIN}" "${PROC_ID_3}" "行政同意"

# 3. 供应商会签中用户 portal-supplier-e7f8 查获待办后拒单
fetch_and_reject_task "portal-supplier-e7f8 供应商成员A" "ROLE_SUPPLIER" "${TOKEN_PORTAL_SUPPLIER_A}" "${PROC_ID_3}" "【供应商拒单】商品断货且物流受阻，无法完成派送"

# 4. 校验流程状态 (3=不通过/终止)
CURRENT_STEP="校验场景三最终状态"
STATUS_3=$(wait_for_process_status "${TOKEN_PORTAL_REQUESTER}" "${PROC_ID_3}" "3")
record_scenario "供应商拒绝终止" "${PROC_ID_3}" "${STATUS_3}"
echo -e "${BOLD}${GREEN}✓ 场景三全流程测试完毕，流程状态 code: ${STATUS_3} (3=拒绝/终止)${RESET}\n"


echo -e "${BOLD}${GREEN}==============================================================================${RESET}"
echo -e "${BOLD}${GREEN}  🎉 Portal mock ROLE 候选人解算自动化测试全部通过！  ${RESET}"
echo -e "${BOLD}${GREEN}  场景 1 (小额直通): 实例 ${PROC_ID_1} -> 状态: ${STATUS_1}${RESET}"
echo -e "${BOLD}${GREEN}  场景 2 (大额通过): 实例 ${PROC_ID_2} -> 状态: ${STATUS_2}${RESET}"
echo -e "${BOLD}${GREEN}  场景 3 (拒单终止): 实例 ${PROC_ID_3} -> 状态: ${STATUS_3}${RESET}"
echo -e "${BOLD}${GREEN}==============================================================================${RESET}"
write_result_file "passed" "三个场景均通过"
echo -e "${GREEN}  结构化结果: ${RESULT_FILE}${RESET}"
