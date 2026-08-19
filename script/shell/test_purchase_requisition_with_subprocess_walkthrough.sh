#!/usr/bin/env bash
# Call Activity acceptance walkthrough. HTTP/curl/jq only; no MCP or direct DB access.
# Prerequisite: run script/sql/init-mock-data.sql in a disposable bpm schema, then
# start yudao-bpm with yudao.bpm.headless-mock.enabled=true.

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:48080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PARENT_KEY="purchase_requisition_with_subprocess_v1"
PARENT_NAME="采购申请（含合规子流程）V1"
PARENT_FORM_CODE="purchase_requisition_with_subprocess_v1_form"
PARENT_BPMN_FILE="${PARENT_BPMN_FILE:-${REPO_ROOT}/script/bpmn/purchase_requisition_with_subprocess_v1.bpmn.xml}"
CHILD_KEY="purchase_requisition_compliance_subprocess_v1"
CHILD_NAME="采购申请合规子流程 V1"
CHILD_FORM_CODE="purchase_requisition_compliance_subprocess_v1_form"
CHILD_BPMN_FILE="${CHILD_BPMN_FILE:-${REPO_ROOT}/script/bpmn/purchase_requisition_compliance_subprocess_v1.bpmn.xml}"
TODO_PAGE_SIZE="${TODO_PAGE_SIZE:-100}"
TASK_POLL_ATTEMPTS="${TASK_POLL_ATTEMPTS:-20}"
TASK_POLL_INTERVAL_SECONDS="${TASK_POLL_INTERVAL_SECONDS:-1}"
RESULT_DIR="${RESULT_DIR:-output/walkthrough}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
RESULT_FILE="${RESULT_DIR}/purchase-subprocess-${RUN_ID}.json"
CURRENT_STEP="初始化"
declare -a SCENARIOS=()

curl() { command curl --silent --show-error --fail --connect-timeout 10 --max-time 90 "$@"; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少必需命令: $1" >&2; exit 1; }
}

write_result() {
  local status="$1" message="${2:-}" scenarios_json
  mkdir -p "$RESULT_DIR"
  if [ "${#SCENARIOS[@]}" -eq 0 ]; then scenarios_json='[]'; else scenarios_json=$(printf '%s\n' "${SCENARIOS[@]}" | jq -s '.'); fi
  jq -n --arg status "$status" --arg message "$message" --arg runId "$RUN_ID" \
    --arg parentDefinitionKey "$PARENT_KEY" --arg childDefinitionKey "$CHILD_KEY" --argjson scenarios "$scenarios_json" \
    '{status: $status, message: $message, runId: $runId, parentDefinitionKey: $parentDefinitionKey,
      childDefinitionKey: $childDefinitionKey, scenarios: $scenarios}' > "$RESULT_FILE"
}

fail() { echo "❌ $1" >&2; write_result failed "$1"; echo "结果文件: ${RESULT_FILE}" >&2; exit 1; }
trap 'exit_code=$?; trap - ERR; write_result failed "执行失败，当前步骤：${CURRENT_STEP}"; exit "$exit_code"' ERR

assert_success() {
  local response="$1" action="$2"
  [ "$(jq -r '.code // empty' <<<"$response")" = "0" ] || fail "${action} 失败：$(jq -c '{code, msg}' <<<"$response")"
}

make_jwt() {
  local user_id="$1" role="$2" header payload
  header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '\n=' | tr '+/' '-_')
  payload=$(printf '{"userId":"%s","role":"%s","headlessMock":true}' "$user_id" "$role" | base64 | tr -d '\n=' | tr '+/' '-_')
  printf '%s.%s.fake_signature' "$header" "$payload"
}

find_form_id() {
  local form_code="$1" response matches
  response=$(curl -X GET "${BASE_URL}/admin-api/bpm/form/list-all-simple" -H "Authorization: Bearer ${TOKEN_MANAGER}")
  assert_success "$response" "查询表单 ${form_code}"
  matches=$(jq -c --arg code "$form_code" '[.data[]? | select(.code == $code)]' <<<"$response")
  [ "$(jq length <<<"$matches")" = "1" ] || fail "表单 ${form_code} 未唯一命中；请先执行 script/sql/init-mock-data.sql"
  jq -r '.[0].id' <<<"$matches"
}

deploy_definition() {
  local key="$1" name="$2" form_code="$3" bpmn_file="$4" output_var="$5"
  local form_id model_list model_id model_json deploy_response definition definition_id
  CURRENT_STEP="查询 ${key} 表单"
  form_id=$(find_form_id "$form_code")
  model_list=$(curl -X GET "${BASE_URL}/admin-api/bpm/model/list" -H "Authorization: Bearer ${TOKEN_MANAGER}")
  assert_success "$model_list" "查找流程模型 ${key}"
  model_id=$(jq -r --arg key "$key" '.data[]? | select(.key == $key) | .id' <<<"$model_list" | head -n 1)
  model_json=$(jq -cn --arg id "$model_id" --arg key "$key" --arg name "$name" --argjson formId "$form_id" \
    '{key: $key, name: $name, category: "default", type: 10, formType: 10, formId: $formId,
      visible: true, managerRoleCodes: ["ROLE_BPM_MODEL_MANAGER"]} + (if $id == "" then {} else {id: $id} end)')
  CURRENT_STEP="部署 ${key}"
  deploy_response=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-definition/deploy-xml" \
    -H "Authorization: Bearer ${TOKEN_MANAGER}" \
    -F "model=${model_json};type=application/json" -F "file=@${bpmn_file};type=application/xml")
  assert_success "$deploy_response" "部署 ${key}"
  definition=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-definition/get?key=${key}" -H "Authorization: Bearer ${TOKEN_REQUESTER}")
  assert_success "$definition" "读取定义 ${key}"
  definition_id=$(jq -r '.data.id // empty' <<<"$definition")
  [ -n "$definition_id" ] || fail "部署 ${key} 后未获得定义 ID"
  printf -v "$output_var" '%s' "$definition_id"
  echo "✓ 已部署 ${key}: ${definition_id}（表单 ${form_code} -> ${form_id}）"
}

fetch_task() {
  local token="$1" definition_id="$2" task_key="$3" attempt response task
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    response=$(curl -X GET "${BASE_URL}/admin-api/bpm/task/todo-page?pageNo=1&pageSize=${TODO_PAGE_SIZE}" -H "Authorization: Bearer ${token}")
    assert_success "$response" "查询待办（第 ${attempt} 次）"
    task=$(jq -c --arg definitionId "$definition_id" --arg taskKey "$task_key" \
      '.data.list[]? | select(.processInstance.processDefinitionId == $definitionId and .taskDefinitionKey == $taskKey)' <<<"$response" | head -n 1)
    if [ -n "$task" ] && [ "$task" != null ]; then printf '%s\n' "$task"; return 0; fi
    sleep "$TASK_POLL_INTERVAL_SECONDS"
  done
  return 1
}

require_task() {
  local user_label="$1" token="$2" definition_id="$3" task_key="$4"
  CURRENT_STEP="${user_label} 等待 ${task_key}"
  TASK=$(fetch_task "$token" "$definition_id" "$task_key") || fail "${user_label} 未获得 ${task_key} 待办"
  TASK_ID=$(jq -r '.id' <<<"$TASK")
  TASK_PROCESS_ID=$(jq -r '.processInstanceId' <<<"$TASK")
  echo "  ✓ ${user_label}: $(jq -r '.name' <<<"$TASK") (${TASK_ID})"
}

approve_current() {
  local token="$1" reason="$2" response
  CURRENT_STEP="同意任务 ${TASK_ID}"
  response=$(curl -X PUT "${BASE_URL}/admin-api/bpm/task/approve" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
    -d "$(jq -cn --arg id "$TASK_ID" --arg reason "$reason" '{id: $id, reason: $reason}')")
  assert_success "$response" "同意任务 ${TASK_ID}"
}

wait_for_status() {
  local process_id="$1" expected_status="$2" attempt response actual
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    response=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${process_id}" -H "Authorization: Bearer ${TOKEN_REQUESTER}")
    assert_success "$response" "查询审批轨迹 ${process_id}"
    actual=$(jq -r '.data.status // empty' <<<"$response")
    [ "$actual" = "$expected_status" ] && return 0
    sleep "$TASK_POLL_INTERVAL_SECONDS"
  done
  fail "流程 ${process_id} 未进入预期状态 ${expected_status}"
}

assert_child_completed_and_get_id() {
  local parent_id="$1" attempt response child_id child_status
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    response=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${parent_id}" -H "Authorization: Bearer ${TOKEN_REQUESTER}")
    assert_success "$response" "读取父流程子流程轨迹"
    child_id=$(jq -r '.data.activityNodes[]? | select(.nodeType == 20) | .processInstanceId' <<<"$response" | head -n 1)
    if [ -n "$child_id" ] && [ "$child_id" != null ]; then
      child_status=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${child_id}" -H "Authorization: Bearer ${TOKEN_REQUESTER}")
      assert_success "$child_status" "读取子流程轨迹"
      [ "$(jq -r '.data.status // empty' <<<"$child_status")" = "2" ] || fail "子流程 ${child_id} 未以通过状态结束"
      printf '%s\n' "$child_id"
      return 0
    fi
    sleep "$TASK_POLL_INTERVAL_SECONDS"
  done
  fail "父流程 ${parent_id} 未返回 Call Activity 的子流程实例 ID"
}

start_parent() {
  local amount="$1" response
  CURRENT_STEP="发起采购申请 ${amount}"
  response=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" -H "Authorization: Bearer ${TOKEN_REQUESTER}" -H 'Content-Type: application/json' \
    -d "$(jq -cn --arg definitionId "$PARENT_DEF_ID" --argjson totalAmount "$amount" '{processDefinitionId: $definitionId,
          variables: {purchaseTitle: "walkthrough 采购申请", procurementType: "GOODS", totalAmount: $totalAmount},
          startUserSelectAssignees: {Activity_ParentManagerApproval: ["portal-manager-b3c4"]}}')")
  assert_success "$response" "发起采购申请"
  PARENT_INSTANCE_ID=$(jq -r '.data // empty' <<<"$response")
  [ -n "$PARENT_INSTANCE_ID" ] || fail "发起采购申请未返回实例 ID"
  echo "✓ 已发起父流程: ${PARENT_INSTANCE_ID}"
}

record() {
  SCENARIOS+=("$(jq -cn --arg name "$1" --arg parentProcessInstanceId "$2" --arg childProcessInstanceId "$3" \
    '{name: $name, parentProcessInstanceId: $parentProcessInstanceId, childProcessInstanceId: $childProcessInstanceId, status: "2"}')")
  write_result running "已完成 $1"
}

require_command curl
require_command jq
[ -r "$PARENT_BPMN_FILE" ] || fail "无法读取父流程 BPMN: ${PARENT_BPMN_FILE}"
[ -r "$CHILD_BPMN_FILE" ] || fail "无法读取子流程 BPMN: ${CHILD_BPMN_FILE}"

TOKEN_REQUESTER=$(make_jwt portal-requester-a1f2 ROLE_USER)
TOKEN_MANAGER=$(make_jwt portal-manager-b3c4 ROLE_MANAGER)
TOKEN_FINANCE=$(make_jwt portal-finance-r8s9 ROLE_FINANCE)
TOKEN_PROCUREMENT=$(make_jwt portal-procurement-q7r8 ROLE_PROCUREMENT)
TOKEN_ADMIN=$(make_jwt portal-admin-d5e6 ROLE_ADMIN)

echo '== 采购申请 Call Activity 子流程 walkthrough =='
# 必须先部署子流程，父流程的 calledElement 按 key 查找活动定义。
deploy_definition "$CHILD_KEY" "$CHILD_NAME" "$CHILD_FORM_CODE" "$CHILD_BPMN_FILE" CHILD_DEF_ID
deploy_definition "$PARENT_KEY" "$PARENT_NAME" "$PARENT_FORM_CODE" "$PARENT_BPMN_FILE" PARENT_DEF_ID

# 场景一：低金额跳过财务复核，直接进入采购合规子流程节点。
start_parent 30000
NORMAL_PARENT_ID="$PARENT_INSTANCE_ID"
require_task '业务负责人' "$TOKEN_MANAGER" "$PARENT_DEF_ID" Activity_ParentManagerApproval
approve_current "$TOKEN_MANAGER" '确认采购必要性'
require_task '采购合规专员（子流程）' "$TOKEN_PROCUREMENT" "$CHILD_DEF_ID" Activity_Subprocess_ProcurementCompliance
NORMAL_CHILD_ID="$TASK_PROCESS_ID"
approve_current "$TOKEN_PROCUREMENT" '采购合规通过'
[ "$(assert_child_completed_and_get_id "$NORMAL_PARENT_ID")" = "$NORMAL_CHILD_ID" ] || fail '父流程轨迹返回的子流程实例与子任务实例不一致'
require_task '流程管理员（父流程恢复）' "$TOKEN_ADMIN" "$PARENT_DEF_ID" Activity_ParentArchive
approve_current "$TOKEN_ADMIN" '归档采购申请'
wait_for_status "$NORMAL_PARENT_ID" 2
record '3 万元：子流程直接采购合规' "$NORMAL_PARENT_ID" "$NORMAL_CHILD_ID"

# 场景二：高金额在子流程内先经过财务，再到采购合规，然后父流程恢复归档。
start_parent 60000
HIGH_PARENT_ID="$PARENT_INSTANCE_ID"
require_task '业务负责人' "$TOKEN_MANAGER" "$PARENT_DEF_ID" Activity_ParentManagerApproval
approve_current "$TOKEN_MANAGER" '确认高金额采购必要性'
require_task '财务复核专员（子流程）' "$TOKEN_FINANCE" "$CHILD_DEF_ID" Activity_Subprocess_FinanceReview
HIGH_CHILD_ID="$TASK_PROCESS_ID"
approve_current "$TOKEN_FINANCE" '预算可用'
require_task '采购合规专员（子流程）' "$TOKEN_PROCUREMENT" "$CHILD_DEF_ID" Activity_Subprocess_ProcurementCompliance
approve_current "$TOKEN_PROCUREMENT" '采购合规通过'
[ "$(assert_child_completed_and_get_id "$HIGH_PARENT_ID")" = "$HIGH_CHILD_ID" ] || fail '父流程轨迹返回的子流程实例与子任务实例不一致'
require_task '流程管理员（父流程恢复）' "$TOKEN_ADMIN" "$PARENT_DEF_ID" Activity_ParentArchive
approve_current "$TOKEN_ADMIN" '归档高金额采购申请'
wait_for_status "$HIGH_PARENT_ID" 2
record '6 万元：子流程财务复核后采购合规' "$HIGH_PARENT_ID" "$HIGH_CHILD_ID"

write_result passed '两个子流程场景均通过，父流程轨迹均可关联子流程实例 ID'
echo "🎉 通过：${RESULT_FILE}"
