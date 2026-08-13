-- Optional local Headless BPM walkthrough data for PostgreSQL.
-- Run after init-bpm.sql, only in a disposable/local bpm schema. This file is
-- idempotent and intentionally contains no runtime/process instance data.

INSERT INTO bpm_category (
    name, code, description, status, sort,
    creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT '默认', 'default', '本地演练默认流程分类', 0, 0,
       'mock', CURRENT_TIMESTAMP, 'mock', CURRENT_TIMESTAMP, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM bpm_category WHERE code = 'default' AND deleted = 0 AND tenant_id = 1
);

WITH mock_forms (code, name, status, conf, fields, remark, creator, create_time, updater, update_time, deleted, tenant_id) AS (
VALUES
(
    'office_supplies_request_v5_form', '办公用品申请流程 V5 表单',
    0,
    $${"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}$$,
    $$[
        "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
        "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
    ]$$,
    'V5 walkthrough mock form', 'mock', CURRENT_TIMESTAMP, 'mock', CURRENT_TIMESTAMP, 0, 1
),
(
    'contract_exception_review_v1_form', '合同例外评审全场景流程 V1 表单',
    0,
    $${"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}$$,
    $$[
        "{\"type\":\"input\",\"field\":\"contractTitle\",\"title\":\"合同名称\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"exceptionType\",\"title\":\"例外类型\",\"$required\":true}",
        "{\"type\":\"select\",\"field\":\"riskLevel\",\"title\":\"风险等级\",\"options\":[{\"value\":\"LOW\",\"label\":\"低\"},{\"value\":\"STANDARD\",\"label\":\"常规\"},{\"value\":\"HIGH\",\"label\":\"高\"}],\"$required\":true}",
        "{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"合同金额\",\"$required\":true}",
        "{\"type\":\"textarea\",\"field\":\"exceptionReason\",\"title\":\"例外原因\",\"$required\":true}"
    ]$$,
    'contract exception walkthrough mock form', 'mock', CURRENT_TIMESTAMP, 'mock', CURRENT_TIMESTAMP, 0, 1
)
), updated AS (
    UPDATE bpm_form AS target
    SET name = source.name,
        status = source.status,
        conf = source.conf,
        fields = source.fields,
        remark = source.remark,
        updater = source.updater,
        update_time = source.update_time
    FROM mock_forms AS source
    WHERE target.code = source.code AND target.deleted = 0
    RETURNING target.code
)
INSERT INTO bpm_form (
    code, name, status, conf, fields, remark,
    creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT code, name, status, conf, fields, remark,
       creator, create_time, updater, update_time, deleted, tenant_id
FROM mock_forms AS source
WHERE NOT EXISTS (SELECT 1 FROM updated WHERE updated.code = source.code);
