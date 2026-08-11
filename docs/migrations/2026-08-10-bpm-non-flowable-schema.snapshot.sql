-- Live schema snapshot generated from the Supabase PostgreSQL `bpm` schema via DBX.
-- Scope: base tables except act_*, flw_*, and bak_*.
-- This is destructive bootstrap DDL: it drops and recreates these tables in the
-- current schema. Select the target schema before executing it.
-- Includes the walkthrough form seed identified by office_supplies_request_v5_form.
-- Source inspection: 2026-08-10, 7 tables and no table/column comments.
-- This bootstrap adds bpm_form_seq so its seed and later inserts receive generated IDs.

DROP TABLE IF EXISTS bpm_process_instance_copy;
DROP TABLE IF EXISTS bpm_process_definition_info;
DROP TABLE IF EXISTS bpm_process_listener;
DROP TABLE IF EXISTS bpm_process_expression;
DROP TABLE IF EXISTS bpm_form;
DROP SEQUENCE IF EXISTS bpm_form_seq;
DROP TABLE IF EXISTS bpm_category;
DROP TABLE IF EXISTS dual;

CREATE TABLE bpm_category (
    id bigint NOT NULL,
    name character varying(30) DEFAULT ''::character varying,
    code character varying(30) DEFAULT ''::character varying,
    description character varying(255) DEFAULT ''::character varying NOT NULL,
    status smallint,
    sort integer,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    CONSTRAINT bpm_category_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE bpm_form_seq AS bigint START WITH 1 INCREMENT BY 1;

CREATE TABLE bpm_form (
    id bigint NOT NULL DEFAULT nextval('bpm_form_seq'),
    code character varying(64),
    name character varying(64) NOT NULL,
    status smallint NOT NULL,
    conf text NOT NULL,
    fields text NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    CONSTRAINT bpm_form_pkey PRIMARY KEY (id)
);

INSERT INTO bpm_form (
    code, name, status, conf, fields, remark,
    creator, create_time, updater, update_time, deleted, tenant_id
) VALUES (
    'office_supplies_request_v5_form', '办公用品申请流程 V5表单',
    0,
    $${"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}$$,
    $$[
        "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
        "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
        "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
    ]$$,
    '系统自动生成的表单 Schema',
    '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, 0, 1
);

CREATE UNIQUE INDEX bpm_form_code_uq
    ON bpm_form (code) WHERE code IS NOT NULL AND deleted = 0;

CREATE TABLE bpm_process_definition_info (
    id bigint NOT NULL,
    process_definition_id character varying(64) NOT NULL,
    model_id character varying(64) NOT NULL,
    model_type smallint DEFAULT 10 NOT NULL,
    category character varying(64) NOT NULL,
    icon character varying(512) DEFAULT NULL::character varying,
    description character varying(255) DEFAULT NULL::character varying,
    form_type smallint NOT NULL,
    form_id bigint,
    form_conf text,
    form_fields text,
    form_custom_create_path character varying(255) DEFAULT NULL::character varying,
    form_custom_view_path character varying(255) DEFAULT NULL::character varying,
    simple_model text,
    sort bigint DEFAULT 0,
    visible boolean DEFAULT true NOT NULL,
    start_user_ids character varying(256) DEFAULT NULL::character varying,
    start_dept_ids character varying(256) DEFAULT NULL::character varying,
    manager_user_ids character varying(256) DEFAULT NULL::character varying,
    allow_cancel_running_process boolean DEFAULT true NOT NULL,
    allow_withdraw_task boolean DEFAULT false NOT NULL,
    process_id_rule character varying(255) DEFAULT NULL::character varying,
    auto_approval_type smallint DEFAULT 0 NOT NULL,
    title_setting character varying(512) DEFAULT NULL::character varying,
    summary_setting character varying(512) DEFAULT NULL::character varying,
    process_before_trigger_setting character varying(1024) DEFAULT NULL::character varying,
    process_after_trigger_setting character varying(1024) DEFAULT NULL::character varying,
    task_before_trigger_setting character varying(1024) DEFAULT NULL::character varying,
    task_after_trigger_setting character varying(1024) DEFAULT NULL::character varying,
    print_template_setting character varying(4096) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    CONSTRAINT bpm_process_definition_info_pkey PRIMARY KEY (id),
    CONSTRAINT idx_process_definition_id UNIQUE (process_definition_id)
);

CREATE TABLE bpm_process_expression (
    id bigint NOT NULL,
    name character varying(64) DEFAULT ''::character varying NOT NULL,
    status smallint NOT NULL,
    expression character varying(1024) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    CONSTRAINT bpm_process_expression_pkey PRIMARY KEY (id)
);

CREATE TABLE bpm_process_instance_copy (
    id bigint NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    start_user_id bigint DEFAULT 0 NOT NULL,
    process_instance_id character varying(64) DEFAULT ''::character varying NOT NULL,
    process_instance_name character varying(64) DEFAULT ''::character varying NOT NULL,
    process_definition_id character varying(64) NOT NULL,
    category character varying(64) NOT NULL,
    activity_id character varying(64) DEFAULT ''::character varying NOT NULL,
    activity_name character varying(64) NOT NULL,
    task_id character varying(64) DEFAULT ''::character varying,
    reason character varying(256) DEFAULT ''::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    CONSTRAINT bpm_process_instance_copy_pkey PRIMARY KEY (id)
);

CREATE TABLE bpm_process_listener (
    id bigint NOT NULL,
    name character varying(30) DEFAULT ''::character varying NOT NULL,
    type character varying(255) NOT NULL,
    status smallint NOT NULL,
    event character varying(30) DEFAULT ''::character varying NOT NULL,
    value_type character varying(64) DEFAULT ''::character varying NOT NULL,
    value character varying(1024) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    CONSTRAINT bpm_process_listener_pkey PRIMARY KEY (id)
);

CREATE TABLE dual (
    id smallint
);

CREATE INDEX bpm_process_definition_info_idx_model_id
    ON bpm_process_definition_info USING btree (model_id);

CREATE INDEX bpm_process_instance_copy_idx_process_instance_id
    ON bpm_process_instance_copy USING btree (process_instance_id);

CREATE INDEX bpm_process_instance_copy_idx_user_id
    ON bpm_process_instance_copy USING btree (user_id);
