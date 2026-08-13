#!/usr/bin/env bash
# Contract-exception Headless BPM acceptance walkthrough. It uses HTTP/curl/jq only.
# Prerequisite: run script/sql/init-mock-data.sql (or SQL Server equivalent) in a
# local disposable bpm schema, then start yudao-bpm with headless mock enabled.

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:48080}"
PROCESS_KEY="contract_exception_review_v1"
PROCESS_NAME="合同例外评审全场景流程 V1"
FORM_CODE="${WALKTHROUGH_FORM_CODE:-contract_exception_review_v1_form}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BPMN_FILE="${WALKTHROUGH_BPMN_FILE:-${REPO_ROOT}/script/bpmn/contract_exception_review_v1.bpmn.xml}"
TODO_PAGE_SIZE="${TODO_PAGE_SIZE:-100}"
TASK_POLL_ATTEMPTS="${TASK_POLL_ATTEMPTS:-20}"
TASK_POLL_INTERVAL_SECONDS="${TASK_POLL_INTERVAL_SECONDS:-1}"
TIMER_WAIT_SECONDS="${TIMER_WAIT_SECONDS:-32}"
RESULT_DIR="${RESULT_DIR:-output/walkthrough}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
RESULT_FILE="${RESULT_DIR}/contract-exception-${RUN_ID}.json"
CURRENT_STEP="初始化"
declare -a SCENARIOS=()

curl() { command curl --silent --show-error --fail --connect-timeout 10 --max-time 90 "$@"; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少必需命令: $1" >&2; exit 1; }
}

write_result() {
  local status="$1" message="${2:-}" scenarios_json
  mkdir -p "$RESULT_DIR"
  if [ "${#SCENARIOS[@]}" -eq 0 ]; then
    scenarios_json='[]'
  else
    scenarios_json=$(printf '%s\n' "${SCENARIOS[@]}" | jq -s '.')
  fi
  jq -n --arg status "$status" --arg message "$message" --arg runId "$RUN_ID" \
    --arg definitionKey "$PROCESS_KEY" --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --argjson scenarios "$scenarios_json" \
    '{status: $status, message: $message, runId: $runId, processDefinitionKey: $definitionKey,
      generatedAt: $generatedAt, scenarios: $scenarios}' > "$RESULT_FILE"
}

fail() {
  echo "❌ ${1}" >&2
  write_result "failed" "$1"
  echo "结果文件: ${RESULT_FILE}" >&2
  exit 1
}

trap 'exit_code=$?; trap - ERR; write_result "failed" "执行失败，当前步骤：${CURRENT_STEP}"; exit "$exit_code"' ERR

assert_success() {
  local response="$1" action="$2" code
  code=$(jq -r '.code // empty' <<<"$response")
  [ "$code" = "0" ] || fail "${action} 失败：$(jq -c '{code, msg}' <<<"$response")"
}

record() {
  SCENARIOS+=("$(jq -cn --arg name "$1" --arg processInstanceId "$2" --arg status "$3" \
    '{name: $name, processInstanceId: $processInstanceId, status: $status}')")
  write_result "running" "已完成 $1"
}

make_jwt() {
  local user_id="$1" role="$2" header payload
  header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '\n=' | tr '+/' '-_')
  payload=$(printf '{"userId":"%s","role":"%s","headlessMock":true}' "$user_id" "$role" | base64 | tr -d '\n=' | tr '+/' '-_')
  printf '%s.%s.fake_signature' "$header" "$payload"
}

fetch_task() {
  local token="$1" process_instance_id="$2" expected_key="$3" attempt response task
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    response=$(curl -X GET "${BASE_URL}/admin-api/bpm/task/todo-page?pageNo=1&pageSize=${TODO_PAGE_SIZE}" \
      -H "Authorization: Bearer ${token}")
    assert_success "$response" "查询待办（第 ${attempt} 次）"
    task=$(jq -c --arg processInstanceId "$process_instance_id" --arg taskKey "$expected_key" \
      '.data.list[]? | select(.processInstance.id == $processInstanceId and .taskDefinitionKey == $taskKey)' <<<"$response" | head -n 1)
    if [ -n "$task" ] && [ "$task" != "null" ]; then
      printf '%s\n' "$task"
      return 0
    fi
    sleep "$TASK_POLL_INTERVAL_SECONDS"
  done
  return 1
}

require_task() {
  local user_label="$1" token="$2" process_instance_id="$3" expected_key="$4"
  CURRENT_STEP="${user_label} 等待 ${expected_key}"
  TASK=$(fetch_task "$token" "$process_instance_id" "$expected_key") \
    || fail "${user_label} 未获取到流程 ${process_instance_id} 的 ${expected_key} 待办"
  TASK_ID=$(jq -r '.id' <<<"$TASK")
  echo "  ✓ ${user_label}: $(jq -r '.name' <<<"$TASK") (${TASK_ID})"
}

put_task_action() {
  local token="$1" endpoint="$2" payload="$3" action="$4" response
  CURRENT_STEP="$action"
  response=$(curl -X PUT "${BASE_URL}/admin-api/bpm/task/${endpoint}" \
    -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "$payload")
  assert_success "$response" "$action"
}

approve_current() {
  local token="$1" reason="$2"
  put_task_action "$token" approve "$(jq -cn --arg id "$TASK_ID" --arg reason "$reason" '{id: $id, reason: $reason}')" "同意任务 ${TASK_ID}"
}

wait_for_status() {
  local token="$1" process_instance_id="$2" expected_status="$3" attempt response actual
  for ((attempt = 1; attempt <= TASK_POLL_ATTEMPTS; attempt++)); do
    response=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-instance/get-approval-detail?processInstanceId=${process_instance_id}" \
      -H "Authorization: Bearer ${token}")
    assert_success "$response" "查询审批轨迹"
    actual=$(jq -r '.data.status // empty' <<<"$response")
    [ "$actual" = "$expected_status" ] && return 0
    sleep "$TASK_POLL_INTERVAL_SECONDS"
  done
  fail "流程 ${process_instance_id} 未进入预期状态 ${expected_status}"
}

start_instance() {
  local risk_level="$1" total_amount="$2" assignees="$3" response
  CURRENT_STEP="发起 ${risk_level} 合同例外流程"
  response=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-instance/create" \
    -H "Authorization: Bearer ${TOKEN_REQUESTER}" -H 'Content-Type: application/json' \
    -d "$(jq -cn --arg definitionId "$DEF_ID" --arg riskLevel "$risk_level" --argjson totalAmount "$total_amount" \
      --argjson assignees "$assignees" '{processDefinitionId: $definitionId,
        variables: {contractTitle: "walkthrough contract", exceptionType: "付款条件例外", riskLevel: $riskLevel,
          totalAmount: $totalAmount, exceptionReason: "验证复杂流程与 Portal ID 透传"},
        startUserSelectAssignees: $assignees}')")
  assert_success "$response" "发起合同例外流程"
  PROCESS_ID=$(jq -r '.data // empty' <<<"$response")
  [ -n "$PROCESS_ID" ] || fail "发起流程未返回实例 ID"
  echo "  ✓ 已发起 ${risk_level} 场景: ${PROCESS_ID}"
}

resolve_form_and_deploy() {
  local forms form_matches model_list model_id model_json deploy_response
  CURRENT_STEP="查询 mock 表单 ${FORM_CODE}"
  forms=$(curl -X GET "${BASE_URL}/admin-api/bpm/form/list-all-simple" -H "Authorization: Bearer ${TOKEN_MANAGER}")
  assert_success "$forms" "查询表单"
  form_matches=$(jq -c --arg code "$FORM_CODE" '[.data[]? | select(.code == $code)]' <<<"$forms")
  [ "$(jq 'length' <<<"$form_matches")" = "1" ] || fail "表单 ${FORM_CODE} 未唯一命中；请先执行 script/sql/init-mock-data.sql"
  FORM_ID=$(jq -r '.[0].id' <<<"$form_matches")

  CURRENT_STEP="查找流程模型 ${PROCESS_KEY}"
  model_list=$(curl -X GET "${BASE_URL}/admin-api/bpm/model/list" -H "Authorization: Bearer ${TOKEN_MANAGER}")
  assert_success "$model_list" "查询模型"
  model_id=$(jq -r --arg key "$PROCESS_KEY" '.data[]? | select(.key == $key) | .id' <<<"$model_list" | head -n 1)
  model_json=$(jq -cn --arg id "$model_id" --arg key "$PROCESS_KEY" --arg name "$PROCESS_NAME" --argjson formId "$FORM_ID" \
    '{key: $key, name: $name, category: "default", type: 10, formType: 10, formId: $formId,
      visible: true, managerRoleCodes: ["ROLE_BPM_MODEL_MANAGER"]} + (if $id == "" then {} else {id: $id} end)')
  CURRENT_STEP="部署 BPMN ${PROCESS_KEY}"
  deploy_response=$(curl -X POST "${BASE_URL}/admin-api/bpm/process-definition/deploy-xml" \
    -H "Authorization: Bearer ${TOKEN_MANAGER}" \
    -F "model=${model_json};type=application/json" -F "file=@${BPMN_FILE};type=application/xml")
  assert_success "$deploy_response" "部署 BPMN"

  local definition
  definition=$(curl -X GET "${BASE_URL}/admin-api/bpm/process-definition/get?key=${PROCESS_KEY}" \
    -H "Authorization: Bearer ${TOKEN_REQUESTER}")
  assert_success "$definition" "读取最新流程定义"
  DEF_ID=$(jq -r '.data.id // empty' <<<"$definition")
  [ -n "$DEF_ID" ] || fail "部署后未获得可发起定义"
  echo "✓ 已部署定义 ${DEF_ID}，表单 ${FORM_CODE} -> ${FORM_ID}"
}

require_command curl
require_command jq
[ -r "$BPMN_FILE" ] || fail "无法读取 BPMN 文件: ${BPMN_FILE}"

TOKEN_REQUESTER=$(make_jwt portal-requester-a1f2 ROLE_USER)
TOKEN_MANAGER=$(make_jwt portal-manager-b3c4 ROLE_MANAGER)
TOKEN_ADMIN=$(make_jwt portal-admin-d5e6 ROLE_ADMIN)
TOKEN_BUSINESS_OWNER=$(make_jwt portal-business-owner-g1h2 ROLE_BUSINESS_OWNER)
TOKEN_LEGAL_A=$(make_jwt portal-legal-h2j3 ROLE_LEGAL)
TOKEN_LEGAL_B=$(make_jwt portal-legal-j3k4 ROLE_LEGAL)
TOKEN_RISK_A=$(make_jwt portal-risk-k4m5 ROLE_RISK)
TOKEN_RISK_B=$(make_jwt portal-risk-m5n6 ROLE_RISK)
TOKEN_EXECUTIVE=$(make_jwt portal-executive-n6p7 ROLE_EXECUTIVE)

echo "== 合同例外复杂流程 walkthrough =="
resolve_form_and_deploy

# 1. 常规路径：SKIP、委派、前加签、非中断催办、退回、串行与并行会签。
start_instance STANDARD 50000 '{"Activity_RequesterSelfCheck":["portal-requester-a1f2"],"Activity_Manager":["portal-manager-b3c4"],"Activity_LegalSequential":["portal-legal-h2j3","portal-legal-j3k4"]}'
STANDARD_ID="$PROCESS_ID"
require_task "业务负责人" "$TOKEN_MANAGER" "$STANDARD_ID" Activity_Manager
put_task_action "$TOKEN_MANAGER" delegate "$(jq -cn --arg id "$TASK_ID" '{id: $id, delegateUserId: "portal-admin-d5e6", reason: "委派流程管理员复核"}')" "委派任务"
require_task "受委派流程管理员" "$TOKEN_ADMIN" "$STANDARD_ID" Activity_Manager
approve_current "$TOKEN_ADMIN" "受委派人处理并归还"
require_task "业务负责人（委派归还后）" "$TOKEN_MANAGER" "$STANDARD_ID" Activity_Manager
put_task_action "$TOKEN_MANAGER" create-sign "$(jq -cn --arg id "$TASK_ID" '{id: $id, userIds: ["portal-legal-h2j3"], type: "before", reason: "前加签法务预审"}')" "创建前加签"
require_task "前加签法务" "$TOKEN_LEGAL_A" "$STANDARD_ID" Activity_Manager
approve_current "$TOKEN_LEGAL_A" "前加签预审通过"
require_task "业务负责人（前加签完成后）" "$TOKEN_MANAGER" "$STANDARD_ID" Activity_Manager
approve_current "$TOKEN_MANAGER" "业务负责人同意"
require_task "法务复核人A" "$TOKEN_LEGAL_A" "$STANDARD_ID" Activity_LegalSequential
echo "  …等待 ${TIMER_WAIT_SECONDS}s，验证非中断催办后任务仍保持可办"
sleep "$TIMER_WAIT_SECONDS"
require_task "法务复核人A（催办后）" "$TOKEN_LEGAL_A" "$STANDARD_ID" Activity_LegalSequential
put_task_action "$TOKEN_LEGAL_A" return "$(jq -cn --arg id "$TASK_ID" '{id: $id, targetTaskDefinitionKey: "Activity_Manager", reason: "法务要求补充业务说明"}')" "退回业务负责人"
require_task "业务负责人（被退回）" "$TOKEN_MANAGER" "$STANDARD_ID" Activity_Manager
approve_current "$TOKEN_MANAGER" "补充说明后再次同意"
require_task "法务复核人A（串行第 1 人）" "$TOKEN_LEGAL_A" "$STANDARD_ID" Activity_LegalSequential
approve_current "$TOKEN_LEGAL_A" "法务 A 同意"
require_task "法务复核人B（串行第 2 人）" "$TOKEN_LEGAL_B" "$STANDARD_ID" Activity_LegalSequential
approve_current "$TOKEN_LEGAL_B" "法务 B 同意"
require_task "风控委员A（全员会签）" "$TOKEN_RISK_A" "$STANDARD_ID" Activity_RiskAllSign
approve_current "$TOKEN_RISK_A" "风控 A 同意"
require_task "风控委员B（全员会签）" "$TOKEN_RISK_B" "$STANDARD_ID" Activity_RiskAllSign
approve_current "$TOKEN_RISK_B" "风控 B 同意"
require_task "流程管理员归档" "$TOKEN_ADMIN" "$STANDARD_ID" Activity_FinalArchive
approve_current "$TOKEN_ADMIN" "常规例外归档"
wait_for_status "$TOKEN_REQUESTER" "$STANDARD_ID" 2
record "常规复杂链路" "$STANDARD_ID" "2"

# 2. 高风险路径：转办、并行或签与最终授权。
start_instance HIGH 200000 '{"Activity_RequesterSelfCheck":["portal-requester-a1f2"],"Activity_Manager":["portal-manager-b3c4"],"Activity_ExecutiveReview":["portal-executive-n6p7"]}'
HIGH_ID="$PROCESS_ID"
require_task "业务负责人" "$TOKEN_MANAGER" "$HIGH_ID" Activity_Manager
put_task_action "$TOKEN_MANAGER" transfer "$(jq -cn --arg id "$TASK_ID" '{id: $id, assigneeUserId: "portal-executive-n6p7", reason: "高风险转办最终授权人"}')" "转办任务"
require_task "最终授权人（转办后）" "$TOKEN_EXECUTIVE" "$HIGH_ID" Activity_Manager
approve_current "$TOKEN_EXECUTIVE" "同意进入紧急风控或签"
require_task "风控委员A（或签）" "$TOKEN_RISK_A" "$HIGH_ID" Activity_RiskAnySign
approve_current "$TOKEN_RISK_A" "风控 A 先行同意"
require_task "最终授权人（最终授权）" "$TOKEN_EXECUTIVE" "$HIGH_ID" Activity_ExecutiveReview
approve_current "$TOKEN_EXECUTIVE" "最终授权通过"
require_task "流程管理员归档" "$TOKEN_ADMIN" "$HIGH_ID" Activity_FinalArchive
approve_current "$TOKEN_ADMIN" "高风险例外归档"
wait_for_status "$TOKEN_REQUESTER" "$HIGH_ID" 2
record "高风险转办与或签" "$HIGH_ID" "2"

# 3. 拒绝终止。
start_instance STANDARD 30000 '{"Activity_RequesterSelfCheck":["portal-requester-a1f2"],"Activity_Manager":["portal-manager-b3c4"],"Activity_LegalSequential":["portal-legal-h2j3","portal-legal-j3k4"]}'
REJECT_ID="$PROCESS_ID"
require_task "业务负责人（拒绝场景）" "$TOKEN_MANAGER" "$REJECT_ID" Activity_Manager
put_task_action "$TOKEN_MANAGER" reject "$(jq -cn --arg id "$TASK_ID" '{id: $id, reason: "例外依据不足，驳回申请"}')" "拒绝任务"
wait_for_status "$TOKEN_REQUESTER" "$REJECT_ID" 3
record "拒绝终止" "$REJECT_ID" "3"

# 4. 低风险路径：只经过 70 角色解算的业务归口与归档节点。
start_instance LOW 5000 '{"Activity_RequesterSelfCheck":["portal-requester-a1f2"]}'
LOW_ID="$PROCESS_ID"
require_task "业务归口负责人" "$TOKEN_BUSINESS_OWNER" "$LOW_ID" Activity_BusinessOwner
approve_current "$TOKEN_BUSINESS_OWNER" "低风险例外确认"
require_task "流程管理员归档" "$TOKEN_ADMIN" "$LOW_ID" Activity_FinalArchive
approve_current "$TOKEN_ADMIN" "低风险例外归档"
wait_for_status "$TOKEN_REQUESTER" "$LOW_ID" 2
record "低风险角色解算" "$LOW_ID" "2"

write_result "passed" "四个场景均通过"
echo "🎉 通过：${RESULT_FILE}"
