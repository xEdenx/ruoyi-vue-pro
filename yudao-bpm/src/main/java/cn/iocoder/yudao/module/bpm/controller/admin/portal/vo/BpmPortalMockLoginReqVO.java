package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 本地 Headless BPM Mock 登录 Request VO")
@Data
public class BpmPortalMockLoginReqVO {

    @Schema(description = "Portal 用户 ID", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "portal-requester-a1f2")
    @NotBlank(message = "用户 ID 不能为空")
    private String userId;

    @Schema(description = "本地 mock 固定密码", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "portal-local-dev")
    @NotBlank(message = "密码不能为空")
    private String password;

}
