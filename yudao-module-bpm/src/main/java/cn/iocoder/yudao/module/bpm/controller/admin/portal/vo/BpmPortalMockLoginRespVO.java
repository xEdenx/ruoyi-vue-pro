package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 本地 Headless BPM Mock 登录 Response VO")
@Data
public class BpmPortalMockLoginRespVO {

    @Schema(description = "访问令牌；仅可在启用本地 Headless Mock 时使用")
    private String accessToken;
    @Schema(description = "本地 Mock 的 BPM 租户编号")
    private Long tenantId;
    @Schema(description = "当前 Portal 用户")
    private BpmPortalUserRespVO user;

}
