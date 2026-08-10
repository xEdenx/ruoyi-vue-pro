BEGIN;

-- 旧 OA 请假样例已下线。保留数据以便审计/回滚，不直接 DROP。
DO $$
BEGIN
    IF to_regclass('bpm.bpm_oa_leave') IS NOT NULL
            AND to_regclass('bpm.bak_bpm_oa_leave') IS NULL THEN
        ALTER TABLE bpm.bpm_oa_leave RENAME TO bak_bpm_oa_leave;
    ELSIF to_regclass('bpm.bpm_oa_leave') IS NOT NULL THEN
        RAISE EXCEPTION 'bpm.bpm_oa_leave and bpm.bak_bpm_oa_leave both exist; resolve the backup collision first';
    ELSIF to_regclass('bpm.bak_bpm_oa_leave') IS NULL THEN
        RAISE EXCEPTION 'neither bpm.bpm_oa_leave nor bpm.bak_bpm_oa_leave exists';
    END IF;
END $$;

COMMIT;
