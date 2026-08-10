-- Headless BPM no longer reads these local system / infra tables.
-- Archive instead of dropping so the data remains recoverable until the
-- Portal production integration and retention review are complete.
BEGIN;

DO $$
DECLARE
    table_name text;
    backup_table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'infra_config',
        'infra_file',
        'infra_file_config',
        'infra_file_content',
        'bpm_oa_leave',
        'bpm_user_group',
        'system_dept',
        'system_dict_data',
        'system_dict_type',
        'system_login_log',
        'system_mail_account',
        'system_mail_log',
        'system_mail_template',
        'system_menu',
        'system_notice',
        'system_notify_message',
        'system_notify_template',
        'system_oauth2_access_token',
        'system_oauth2_approve',
        'system_oauth2_client',
        'system_oauth2_code',
        'system_oauth2_refresh_token',
        'system_operate_log',
        'system_post',
        'system_role',
        'system_role_menu',
        'system_sms_channel',
        'system_sms_code',
        'system_sms_log',
        'system_sms_template',
        'system_social_client',
        'system_social_user',
        'system_social_user_bind',
        'system_tenant',
        'system_tenant_package',
        'system_user_post',
        'system_user_role',
        'system_users'
    ]
    LOOP
        backup_table_name := 'bak_' || table_name;
        IF to_regclass(format('bpm.%I', table_name)) IS NOT NULL THEN
            IF to_regclass(format('bpm.%I', backup_table_name)) IS NOT NULL THEN
                RAISE EXCEPTION 'bpm.% and bpm.% both exist; resolve the backup collision first',
                    table_name, backup_table_name;
            END IF;
            EXECUTE format('ALTER TABLE bpm.%I RENAME TO %I', table_name, backup_table_name);
        ELSIF to_regclass(format('bpm.%I', backup_table_name)) IS NULL THEN
            RAISE EXCEPTION 'neither bpm.% nor bpm.% exists', table_name, backup_table_name;
        END IF;
    END LOOP;
END $$;

COMMIT;
