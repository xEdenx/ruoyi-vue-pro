package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "管理后台 - 流程任务的转办 Request VO")
@Data
public class BpmTaskTransferReqVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "任务编号不能为空")
    private String id;

    @Schema(description = "新审批人的 Portal 用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "portal-assignee-a1f2")
    @NotEmpty(message = "新审批人的用户编号不能为空")
    private String assigneeUserId;

    @Schema(description = "转办原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "做不了决定，需要你先帮忙瞅瞅")
    @NotEmpty(message = "转办原因不能为空")
    private String reason;

}
