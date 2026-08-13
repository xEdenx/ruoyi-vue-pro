package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - Headless BPM Portal 部门 Response VO")
@Data
public class BpmPortalDepartmentRespVO {

    @Schema(description = "Portal 部门 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "portal-dept-general")
    private String id;

    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "通用部门")
    private String name;

    @Schema(description = "父部门 ID；当前 mock 目录为一级部门")
    private String parentId;

}
