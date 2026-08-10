-- Supabase PostgreSQL / bpm schema
-- 将 BPM 抄送审计记录中的用户身份改为 Portal 原始 String ID。
-- 执行前先确认应用已部署支持 String ID 的版本；现有数值 ID 会无损转换为文本。

BEGIN;

ALTER TABLE bpm.bpm_process_instance_copy
    ALTER COLUMN user_id TYPE varchar(128) USING user_id::varchar(128),
    ALTER COLUMN start_user_id TYPE varchar(128) USING start_user_id::varchar(128);

COMMIT;
