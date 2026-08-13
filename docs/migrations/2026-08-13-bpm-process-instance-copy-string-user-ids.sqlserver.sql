-- SQL Server / DBeaver: migrate copy-recipient and process-starter IDs to Portal String IDs.
-- Uses the current connection's default schema; execute this complete file as one statement batch.
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @tableId int = OBJECT_ID(N'bpm_process_instance_copy', N'U');
    IF @tableId IS NULL
        THROW 51000, N'表 bpm_process_instance_copy 不存在。', 1;

    DECLARE @hasUserIdIndex bit = 0;
    IF EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = @tableId
          AND name = N'bpm_process_instance_copy_idx_user_id'
    )
        SET @hasUserIdIndex = 1;

    DECLARE @dropDefaults nvarchar(max) = N'';
    SELECT @dropDefaults = @dropDefaults
        + N'ALTER TABLE [bpm_process_instance_copy] DROP CONSTRAINT '
        + QUOTENAME(dc.name) + N';' + CHAR(10)
    FROM sys.default_constraints dc
    INNER JOIN sys.columns c
        ON c.object_id = dc.parent_object_id
       AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = @tableId
      AND c.name IN (N'user_id', N'start_user_id');

    IF @dropDefaults <> N''
        EXEC sys.sp_executesql @dropDefaults;

    IF @hasUserIdIndex = 1
        DROP INDEX [bpm_process_instance_copy_idx_user_id]
        ON [bpm_process_instance_copy];

    ALTER TABLE [bpm_process_instance_copy]
        ALTER COLUMN [user_id] varchar(64) NOT NULL;

    ALTER TABLE [bpm_process_instance_copy]
        ALTER COLUMN [start_user_id] varchar(64) NOT NULL;

    IF @hasUserIdIndex = 1
        CREATE NONCLUSTERED INDEX [bpm_process_instance_copy_idx_user_id]
        ON [bpm_process_instance_copy] ([user_id]);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
