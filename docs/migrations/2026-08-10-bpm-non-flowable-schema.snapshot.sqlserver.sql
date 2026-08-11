-- SQL Server semantic translation of the live Supabase PostgreSQL `bpm` schema.
-- Scope: base tables except act_*, flw_*, and bak_*.
-- This is destructive bootstrap DDL: it drops and recreates these tables in the
-- current schema. Select the target schema before executing it.
-- Includes the walkthrough form seed identified by office_supplies_request_v5_form.
-- PostgreSQL -> SQL Server mappings: character varying/text -> nvarchar/nvarchar(max),
-- boolean -> bit, timestamp without time zone -> datetime2(6).
-- Source inspection: 2026-08-10, 7 tables and no table/column comments.
-- This bootstrap adds bpm_form_seq so its seed and later inserts receive generated IDs.

DROP TABLE IF EXISTS [bpm_process_instance_copy];
DROP TABLE IF EXISTS [bpm_process_definition_info];
DROP TABLE IF EXISTS [bpm_process_listener];
DROP TABLE IF EXISTS [bpm_process_expression];
DROP TABLE IF EXISTS [bpm_form];
DROP SEQUENCE IF EXISTS [bpm_form_seq];
DROP TABLE IF EXISTS [bpm_category];
DROP TABLE IF EXISTS [dual];
GO

CREATE TABLE [bpm_category] (
    [id] bigint NOT NULL,
    [name] nvarchar(30) NULL CONSTRAINT [DF_bpm_category_name] DEFAULT N'',
    [code] nvarchar(30) NULL CONSTRAINT [DF_bpm_category_code] DEFAULT N'',
    [description] nvarchar(255) NOT NULL CONSTRAINT [DF_bpm_category_description] DEFAULT N'',
    [status] smallint NULL,
    [sort] int NULL,
    [creator] nvarchar(64) NULL CONSTRAINT [DF_bpm_category_creator] DEFAULT N'',
    [create_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_category_create_time] DEFAULT SYSDATETIME(),
    [updater] nvarchar(64) NULL CONSTRAINT [DF_bpm_category_updater] DEFAULT N'',
    [update_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_category_update_time] DEFAULT SYSDATETIME(),
    [deleted] smallint NOT NULL CONSTRAINT [DF_bpm_category_deleted] DEFAULT 0,
    [tenant_id] bigint NOT NULL CONSTRAINT [DF_bpm_category_tenant_id] DEFAULT 0,
    CONSTRAINT [bpm_category_pkey] PRIMARY KEY ([id])
);
GO

CREATE SEQUENCE [bpm_form_seq] AS bigint START WITH 1 INCREMENT BY 1;
GO

CREATE TABLE [bpm_form] (
    [id] bigint NOT NULL CONSTRAINT [DF_bpm_form_id] DEFAULT (NEXT VALUE FOR [bpm_form_seq]),
    [code] nvarchar(64) NULL,
    [name] nvarchar(64) NOT NULL,
    [status] smallint NOT NULL,
    [conf] nvarchar(max) NOT NULL,
    [fields] nvarchar(max) NOT NULL,
    [remark] nvarchar(255) NULL,
    [creator] nvarchar(64) NULL CONSTRAINT [DF_bpm_form_creator] DEFAULT N'',
    [create_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_form_create_time] DEFAULT SYSDATETIME(),
    [updater] nvarchar(64) NULL CONSTRAINT [DF_bpm_form_updater] DEFAULT N'',
    [update_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_form_update_time] DEFAULT SYSDATETIME(),
    [deleted] smallint NOT NULL CONSTRAINT [DF_bpm_form_deleted] DEFAULT 0,
    [tenant_id] bigint NOT NULL CONSTRAINT [DF_bpm_form_tenant_id] DEFAULT 0,
    CONSTRAINT [bpm_form_pkey] PRIMARY KEY ([id])
);
GO

INSERT INTO [bpm_form] (
    [code], [name], [status], [conf], [fields], [remark],
    [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]
) VALUES (
    N'office_supplies_request_v5_form', N'办公用品申请流程 V5表单',
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
GO

CREATE UNIQUE INDEX [bpm_form_code_uq]
    ON [bpm_form] ([code]) WHERE [code] IS NOT NULL AND [deleted] = 0;
GO

CREATE TABLE [bpm_process_definition_info] (
    [id] bigint NOT NULL,
    [process_definition_id] nvarchar(64) NOT NULL,
    [model_id] nvarchar(64) NOT NULL,
    [model_type] smallint NOT NULL CONSTRAINT [DF_bpm_process_definition_info_model_type] DEFAULT 10,
    [category] nvarchar(64) NOT NULL,
    [icon] nvarchar(512) NULL,
    [description] nvarchar(255) NULL,
    [form_type] smallint NOT NULL,
    [form_id] bigint NULL,
    [form_conf] nvarchar(max) NULL,
    [form_fields] nvarchar(max) NULL,
    [form_custom_create_path] nvarchar(255) NULL,
    [form_custom_view_path] nvarchar(255) NULL,
    [simple_model] nvarchar(max) NULL,
    [sort] bigint NULL CONSTRAINT [DF_bpm_process_definition_info_sort] DEFAULT 0,
    [visible] bit NOT NULL CONSTRAINT [DF_bpm_process_definition_info_visible] DEFAULT 1,
    [start_user_ids] nvarchar(256) NULL,
    [start_dept_ids] nvarchar(256) NULL,
    [manager_user_ids] nvarchar(256) NULL,
    [allow_cancel_running_process] bit NOT NULL CONSTRAINT [DF_bpm_process_definition_info_allow_cancel_running_process] DEFAULT 1,
    [allow_withdraw_task] bit NOT NULL CONSTRAINT [DF_bpm_process_definition_info_allow_withdraw_task] DEFAULT 0,
    [process_id_rule] nvarchar(255) NULL,
    [auto_approval_type] smallint NOT NULL CONSTRAINT [DF_bpm_process_definition_info_auto_approval_type] DEFAULT 0,
    [title_setting] nvarchar(512) NULL,
    [summary_setting] nvarchar(512) NULL,
    [process_before_trigger_setting] nvarchar(1024) NULL,
    [process_after_trigger_setting] nvarchar(1024) NULL,
    [task_before_trigger_setting] nvarchar(1024) NULL,
    [task_after_trigger_setting] nvarchar(1024) NULL,
    [print_template_setting] nvarchar(4096) NULL,
    [creator] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_definition_info_creator] DEFAULT N'',
    [create_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_definition_info_create_time] DEFAULT SYSDATETIME(),
    [updater] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_definition_info_updater] DEFAULT N'',
    [update_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_definition_info_update_time] DEFAULT SYSDATETIME(),
    [deleted] smallint NOT NULL CONSTRAINT [DF_bpm_process_definition_info_deleted] DEFAULT 0,
    [tenant_id] bigint NOT NULL CONSTRAINT [DF_bpm_process_definition_info_tenant_id] DEFAULT 0,
    CONSTRAINT [bpm_process_definition_info_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [idx_process_definition_id] UNIQUE ([process_definition_id])
);
GO

CREATE TABLE [bpm_process_expression] (
    [id] bigint NOT NULL,
    [name] nvarchar(64) NOT NULL CONSTRAINT [DF_bpm_process_expression_name] DEFAULT N'',
    [status] smallint NOT NULL,
    [expression] nvarchar(1024) NOT NULL,
    [creator] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_expression_creator] DEFAULT N'',
    [create_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_expression_create_time] DEFAULT SYSDATETIME(),
    [updater] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_expression_updater] DEFAULT N'',
    [update_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_expression_update_time] DEFAULT SYSDATETIME(),
    [deleted] smallint NOT NULL CONSTRAINT [DF_bpm_process_expression_deleted] DEFAULT 0,
    [tenant_id] bigint NOT NULL CONSTRAINT [DF_bpm_process_expression_tenant_id] DEFAULT 0,
    CONSTRAINT [bpm_process_expression_pkey] PRIMARY KEY ([id])
);
GO

CREATE TABLE [bpm_process_instance_copy] (
    [id] bigint NOT NULL,
    [user_id] bigint NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_user_id] DEFAULT 0,
    [start_user_id] bigint NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_start_user_id] DEFAULT 0,
    [process_instance_id] nvarchar(64) NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_process_instance_id] DEFAULT N'',
    [process_instance_name] nvarchar(64) NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_process_instance_name] DEFAULT N'',
    [process_definition_id] nvarchar(64) NOT NULL,
    [category] nvarchar(64) NOT NULL,
    [activity_id] nvarchar(64) NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_activity_id] DEFAULT N'',
    [activity_name] nvarchar(64) NOT NULL,
    [task_id] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_instance_copy_task_id] DEFAULT N'',
    [reason] nvarchar(256) NULL CONSTRAINT [DF_bpm_process_instance_copy_reason] DEFAULT N'',
    [creator] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_instance_copy_creator] DEFAULT N'',
    [create_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_create_time] DEFAULT SYSDATETIME(),
    [updater] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_instance_copy_updater] DEFAULT N'',
    [update_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_update_time] DEFAULT SYSDATETIME(),
    [deleted] smallint NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_deleted] DEFAULT 0,
    [tenant_id] bigint NOT NULL CONSTRAINT [DF_bpm_process_instance_copy_tenant_id] DEFAULT 0,
    CONSTRAINT [bpm_process_instance_copy_pkey] PRIMARY KEY ([id])
);
GO

CREATE TABLE [bpm_process_listener] (
    [id] bigint NOT NULL,
    [name] nvarchar(30) NOT NULL CONSTRAINT [DF_bpm_process_listener_name] DEFAULT N'',
    [type] nvarchar(255) NOT NULL,
    [status] smallint NOT NULL,
    [event] nvarchar(30) NOT NULL CONSTRAINT [DF_bpm_process_listener_event] DEFAULT N'',
    [value_type] nvarchar(64) NOT NULL CONSTRAINT [DF_bpm_process_listener_value_type] DEFAULT N'',
    [value] nvarchar(1024) NOT NULL,
    [creator] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_listener_creator] DEFAULT N'',
    [create_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_listener_create_time] DEFAULT SYSDATETIME(),
    [updater] nvarchar(64) NULL CONSTRAINT [DF_bpm_process_listener_updater] DEFAULT N'',
    [update_time] datetime2(6) NOT NULL CONSTRAINT [DF_bpm_process_listener_update_time] DEFAULT SYSDATETIME(),
    [deleted] smallint NOT NULL CONSTRAINT [DF_bpm_process_listener_deleted] DEFAULT 0,
    [tenant_id] bigint NOT NULL CONSTRAINT [DF_bpm_process_listener_tenant_id] DEFAULT 0,
    CONSTRAINT [bpm_process_listener_pkey] PRIMARY KEY ([id])
);
GO

CREATE TABLE [dual] (
    [id] smallint NULL
);
GO

CREATE INDEX [bpm_process_definition_info_idx_model_id]
    ON [bpm_process_definition_info] ([model_id]);
GO

CREATE INDEX [bpm_process_instance_copy_idx_process_instance_id]
    ON [bpm_process_instance_copy] ([process_instance_id]);
GO

CREATE INDEX [bpm_process_instance_copy_idx_user_id]
    ON [bpm_process_instance_copy] ([user_id]);
GO
