package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - Headless BPM 可选择 Portal 组织目录 Response VO")
@Data
public class BpmPortalDirectoryRespVO {

    @Schema(description = "可选择用户")
    private List<BpmPortalUserRespVO> users;

    @Schema(description = "可选择部门")
    private List<BpmPortalDepartmentRespVO> departments;

}
