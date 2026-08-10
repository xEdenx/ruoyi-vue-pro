package cn.iocoder.yudao.module.bpm.controller.admin.base.dept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "部门精简信息 VO")
@Data
public class DeptSimpleBaseVO {

    @Schema(description = "部门编号。Portal 部门使用其原始 String ID", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "portal-dept-general")
    private String id;
    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "技术部")
    private String name;

}
