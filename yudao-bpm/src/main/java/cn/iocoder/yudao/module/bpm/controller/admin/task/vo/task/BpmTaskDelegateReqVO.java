package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 委派流程任务的 Request VO")
@Data
public class BpmTaskDelegateReqVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "任务编号不能为空")
    private String id;

    @Schema(description = "被委派 Portal 用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "portal-delegate-a1f2")
    @NotEmpty(message = "被委派人 ID 不能为空")
    private String delegateUserId;

    @Schema(description = "委派原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "做不了决定，需要你先帮忙瞅瞅")
    @NotEmpty(message = "委派原因不能为空")
    private String reason;

}
