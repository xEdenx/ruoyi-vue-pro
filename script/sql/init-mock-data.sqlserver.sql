-- Optional local Headless BPM walkthrough data for SQL Server.
-- Run after init-bpm.sqlserver.sql in a disposable/local bpm schema. No GO is
-- used so this can run as one DBeaver batch. It contains no runtime/process data.

IF NOT EXISTS (
    SELECT 1 FROM [bpm_category] WHERE [code] = N'default' AND [deleted] = 0 AND [tenant_id] = 1
)
BEGIN
    INSERT INTO [bpm_category] (
        [name], [code], [description], [status], [sort],
        [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]
    ) VALUES (
        N'默认', N'default', N'本地演练默认流程分类', 0, 0,
        N'mock', SYSDATETIME(), N'mock', SYSDATETIME(), 0, 1
    );
END;

IF EXISTS (SELECT 1 FROM [bpm_form] WHERE [code] = N'purchase_requisition_with_subprocess_v1_form' AND [deleted] = 0)
BEGIN
    UPDATE [bpm_form]
    SET [name] = N'采购申请（含合规子流程）V1 表单', [status] = 0,
        [conf] = N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}',
        [fields] = N'[
            "{\"type\":\"input\",\"field\":\"purchaseTitle\",\"title\":\"采购事项\",\"$required\":true}",
            "{\"type\":\"select\",\"field\":\"procurementType\",\"title\":\"采购类别\",\"options\":[{\"value\":\"GOODS\",\"label\":\"货物\"},{\"value\":\"SERVICE\",\"label\":\"服务\"}],\"$required\":true}",
            "{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"采购金额\",\"$required\":true}"
        ]',
        [remark] = N'parent process form for Call Activity walkthrough', [updater] = N'mock', [update_time] = SYSDATETIME()
    WHERE [code] = N'purchase_requisition_with_subprocess_v1_form' AND [deleted] = 0;
END
ELSE
BEGIN
    INSERT INTO [bpm_form] ([code], [name], [status], [conf], [fields], [remark], [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]) VALUES
    (N'purchase_requisition_with_subprocess_v1_form', N'采购申请（含合规子流程）V1 表单', 0,
     N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}',
     N'["{\"type\":\"input\",\"field\":\"purchaseTitle\",\"title\":\"采购事项\",\"$required\":true}","{\"type\":\"select\",\"field\":\"procurementType\",\"title\":\"采购类别\",\"options\":[{\"value\":\"GOODS\",\"label\":\"货物\"},{\"value\":\"SERVICE\",\"label\":\"服务\"}],\"$required\":true}","{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"采购金额\",\"$required\":true}]',
     N'parent process form for Call Activity walkthrough', N'mock', SYSDATETIME(), N'mock', SYSDATETIME(), 0, 1);
END;

IF EXISTS (SELECT 1 FROM [bpm_form] WHERE [code] = N'purchase_requisition_compliance_subprocess_v1_form' AND [deleted] = 0)
BEGIN
    UPDATE [bpm_form]
    SET [name] = N'采购申请合规子流程 V1 表单', [status] = 0,
        [conf] = N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}',
        [fields] = N'["{\"type\":\"input\",\"field\":\"purchaseTitle\",\"title\":\"采购事项\",\"$required\":true}","{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"采购金额\",\"$required\":true}]',
        [remark] = N'child process metadata form; normal execution receives variables from the parent Call Activity', [updater] = N'mock', [update_time] = SYSDATETIME()
    WHERE [code] = N'purchase_requisition_compliance_subprocess_v1_form' AND [deleted] = 0;
END
ELSE
BEGIN
    INSERT INTO [bpm_form] ([code], [name], [status], [conf], [fields], [remark], [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]) VALUES
    (N'purchase_requisition_compliance_subprocess_v1_form', N'采购申请合规子流程 V1 表单', 0,
     N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}',
     N'["{\"type\":\"input\",\"field\":\"purchaseTitle\",\"title\":\"采购事项\",\"$required\":true}","{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"采购金额\",\"$required\":true}]',
     N'child process metadata form; normal execution receives variables from the parent Call Activity', N'mock', SYSDATETIME(), N'mock', SYSDATETIME(), 0, 1);
END;

IF EXISTS (SELECT 1 FROM [bpm_form] WHERE [code] = N'office_supplies_request_v5_form' AND [deleted] = 0)
BEGIN
    UPDATE [bpm_form]
    SET [name] = N'办公用品申请流程 V5 表单',
        [status] = 0,
        [conf] = N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}',
        [fields] = N'[
            "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
            "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
            "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
            "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
        ]',
        [remark] = N'V5 walkthrough mock form',
        [updater] = N'mock',
        [update_time] = SYSDATETIME()
    WHERE [code] = N'office_supplies_request_v5_form' AND [deleted] = 0;
END
ELSE
BEGIN
    INSERT INTO [bpm_form] (
        [code], [name], [status], [conf], [fields], [remark],
        [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]
    ) VALUES (
        N'office_supplies_request_v5_form', N'办公用品申请流程 V5 表单', 0,
        N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"100px"}}',
        N'[
            "{\"type\":\"input\",\"field\":\"name\",\"title\":\"申请事项\",\"$required\":true}",
            "{\"type\":\"inputNumber\",\"field\":\"count\",\"title\":\"数量\",\"$required\":true}",
            "{\"type\":\"input\",\"field\":\"price\",\"title\":\"单价\",\"$required\":true}",
            "{\"type\":\"input\",\"field\":\"totalAmount\",\"title\":\"总金额\",\"$required\":true}"
        ]',
        N'V5 walkthrough mock form', N'mock', SYSDATETIME(), N'mock', SYSDATETIME(), 0, 1
    );
END;

IF EXISTS (SELECT 1 FROM [bpm_form] WHERE [code] = N'contract_exception_review_v1_form' AND [deleted] = 0)
BEGIN
    UPDATE [bpm_form]
    SET [name] = N'合同例外评审全场景流程 V1 表单',
        [status] = 0,
        [conf] = N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}',
        [fields] = N'[
            "{\"type\":\"input\",\"field\":\"contractTitle\",\"title\":\"合同名称\",\"$required\":true}",
            "{\"type\":\"input\",\"field\":\"exceptionType\",\"title\":\"例外类型\",\"$required\":true}",
            "{\"type\":\"select\",\"field\":\"riskLevel\",\"title\":\"风险等级\",\"options\":[{\"value\":\"LOW\",\"label\":\"低\"},{\"value\":\"STANDARD\",\"label\":\"常规\"},{\"value\":\"HIGH\",\"label\":\"高\"}],\"$required\":true}",
            "{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"合同金额\",\"$required\":true}",
            "{\"type\":\"textarea\",\"field\":\"exceptionReason\",\"title\":\"例外原因\",\"$required\":true}"
        ]',
        [remark] = N'contract exception walkthrough mock form',
        [updater] = N'mock',
        [update_time] = SYSDATETIME()
    WHERE [code] = N'contract_exception_review_v1_form' AND [deleted] = 0;
END
ELSE
BEGIN
    INSERT INTO [bpm_form] (
        [code], [name], [status], [conf], [fields], [remark],
        [creator], [create_time], [updater], [update_time], [deleted], [tenant_id]
    ) VALUES (
        N'contract_exception_review_v1_form', N'合同例外评审全场景流程 V1 表单', 0,
        N'{"form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"120px"}}',
        N'[
            "{\"type\":\"input\",\"field\":\"contractTitle\",\"title\":\"合同名称\",\"$required\":true}",
            "{\"type\":\"input\",\"field\":\"exceptionType\",\"title\":\"例外类型\",\"$required\":true}",
            "{\"type\":\"select\",\"field\":\"riskLevel\",\"title\":\"风险等级\",\"options\":[{\"value\":\"LOW\",\"label\":\"低\"},{\"value\":\"STANDARD\",\"label\":\"常规\"},{\"value\":\"HIGH\",\"label\":\"高\"}],\"$required\":true}",
            "{\"type\":\"inputNumber\",\"field\":\"totalAmount\",\"title\":\"合同金额\",\"$required\":true}",
            "{\"type\":\"textarea\",\"field\":\"exceptionReason\",\"title\":\"例外原因\",\"$required\":true}"
        ]',
        N'contract exception walkthrough mock form', N'mock', SYSDATETIME(), N'mock', SYSDATETIME(), 0, 1
    );
END;
