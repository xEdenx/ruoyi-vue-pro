package cn.iocoder.yudao.module.bpm.controller.admin.portal;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalDepartmentRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalDirectoryRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalUserRespVO;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Headless BPM Portal 组织目录")
@RestController
@RequestMapping("/bpm/portal-directory")
@Validated
@TenantIgnore
public class BpmPortalDirectoryController {

    @Resource
    private BpmPortalOrganizationApi portalOrganizationApi;

    @GetMapping("/simple-list")
    @Operation(summary = "获得当前用户可选择的 Portal 用户和部门目录")
    public CommonResult<BpmPortalDirectoryRespVO> getSimpleList() {
        requireLoginUser();
        BpmPortalDirectoryRespVO response = new BpmPortalDirectoryRespVO();
        response.setUsers(portalOrganizationApi.listSelectableUsers().stream()
                .map(BpmPortalDirectoryController::convertUser)
                .toList());
        response.setDepartments(portalOrganizationApi.listSelectableDepartments().stream()
                .map(BpmPortalDirectoryController::convertDepartment)
                .toList());
        return success(response);
    }

    private static LoginUser requireLoginUser() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            throw exception(UNAUTHORIZED);
        }
        return loginUser;
    }

    private static BpmPortalUserRespVO convertUser(BpmPortalOrganizationApi.PortalUser user) {
        BpmPortalUserRespVO response = new BpmPortalUserRespVO();
        response.setId(user.getId());
        response.setDisplayName(user.getDisplayName());
        response.setAvatar(user.getAvatar());
        response.setDepartmentId(user.getDepartmentId());
        response.setDepartmentName(user.getDepartmentName());
        response.setRoleCodes(user.getRoleCodes());
        return response;
    }

    private static BpmPortalDepartmentRespVO convertDepartment(BpmPortalOrganizationApi.PortalDepartment department) {
        BpmPortalDepartmentRespVO response = new BpmPortalDepartmentRespVO();
        response.setId(department.getId());
        response.setName(department.getName());
        return response;
    }

}
