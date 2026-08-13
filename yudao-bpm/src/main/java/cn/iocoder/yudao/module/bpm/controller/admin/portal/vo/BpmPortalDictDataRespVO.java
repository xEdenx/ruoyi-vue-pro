package cn.iocoder.yudao.module.bpm.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - Headless BPM 固定枚举 Response VO")
@Data
public class BpmPortalDictDataRespVO {

    @Schema(description = "枚举类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "bpm_task_status")
    private String dictType;

    @Schema(description = "枚举值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Object value;

    @Schema(description = "展示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "审批中")
    private String label;

    @Schema(description = "颜色类型")
    private String colorType;

    @Schema(description = "CSS class")
    private String cssClass;

}
