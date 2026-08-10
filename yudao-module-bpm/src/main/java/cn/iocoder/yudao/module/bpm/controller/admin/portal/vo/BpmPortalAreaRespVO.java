package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - Headless BPM Portal 地区 Response VO")
@Data
public class BpmPortalAreaRespVO {

    @Schema(description = "Portal 地区 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "地区名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "子地区")
    private List<BpmPortalAreaRespVO> children;

}
