package cn.iocoder.yudao.module.bpm.controller.admin.base.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户精简信息 VO")
@Data
public class UserSimpleBaseVO {

    @Schema(description = "用户编号。Portal 用户使用其原始 String ID，本地管理端过渡期可返回数值字符串", requiredMode = Schema.RequiredMode.REQUIRED, example = "portal-requester-a1f2")
    private String id;
    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    private String nickname;
    @Schema(description = "用户头像", example = "https://www.iocoder.cn/1.png")
    private String avatar;

    @Schema(description = "部门编号。由 Portal 提供时保留其原始 String ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "portal-dept-general")
    private String deptId;
    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "研发部")
    private String deptName;

}
