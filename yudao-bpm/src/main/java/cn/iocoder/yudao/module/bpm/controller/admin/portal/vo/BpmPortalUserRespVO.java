package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - Portal 用户 Response VO")
@Data
public class BpmPortalUserRespVO {

    @Schema(description = "Portal 用户 ID", example = "portal-requester-a1f2")
    private String id;
    @Schema(description = "展示名称", example = "申请人")
    private String displayName;
    @Schema(description = "头像 URL")
    private String avatar;
    @Schema(description = "部门 ID", example = "portal-dept-general")
    private String departmentId;
    @Schema(description = "部门名称", example = "通用部门")
    private String departmentName;
    @Schema(description = "角色编码")
    private Set<String> roleCodes;

}
