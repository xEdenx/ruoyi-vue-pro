-- SQL Server semantic translation of the live Supabase PostgreSQL `bpm` schema.
-- Scope: base tables except act_*, flw_*, and bak_*.
-- This is a reference/bootstrap DDL, not an incremental migration. Do not execute it
-- directly against an existing bpm schema because CREATE TABLE statements are not idempotent.
-- PostgreSQL -> SQL Server mappings: character varying/text -> nvarchar/nvarchar(max),
-- boolean -> bit, timestamp without time zone -> datetime2(6).
-- Source inspection: 2026-08-10, 7 tables, no table-owned sequences, no table/column comments.

IF SCHEMA_ID(N'bpm') IS NULL
    EXEC(N'CREATE SCHEMA [bpm]');
GO

CREATE TABLE [bpm].[bpm_category] (
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

CREATE TABLE [bpm].[bpm_form] (
    [id] bigint NOT NULL,
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

CREATE TABLE [bpm].[bpm_process_definition_info] (
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

CREATE TABLE [bpm].[bpm_process_expression] (
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

CREATE TABLE [bpm].[bpm_process_instance_copy] (
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

CREATE TABLE [bpm].[bpm_process_listener] (
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

CREATE TABLE [bpm].[dual] (
    [id] smallint NULL
);
GO

CREATE INDEX [bpm_process_definition_info_idx_model_id]
    ON [bpm].[bpm_process_definition_info] ([model_id]);
GO

CREATE INDEX [bpm_process_instance_copy_idx_process_instance_id]
    ON [bpm].[bpm_process_instance_copy] ([process_instance_id]);
GO

CREATE INDEX [bpm_process_instance_copy_idx_user_id]
    ON [bpm].[bpm_process_instance_copy] ([user_id]);
GO
