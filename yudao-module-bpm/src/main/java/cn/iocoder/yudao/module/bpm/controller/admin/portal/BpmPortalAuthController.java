package cn.iocoder.yudao.module.bpm.controller.admin.portal;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalMockLoginReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalMockLoginRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalUserRespVO;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 仅用于本地 walkthrough 的 Portal 登录入口。
 *
 * <p>该 Controller 在 mock 关闭时不会注册。生产环境必须由 Portal 颁发并验证其令牌，不能开启本入口。</p>
 */
@Tag(name = "管理后台 - 本地 Headless BPM Portal 认证")
@RestController
@RequestMapping("/bpm/portal-auth")
@Validated
@TenantIgnore
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class BpmPortalAuthController {

    private static final String LOCAL_MOCK_CLAIM = "headlessMock";

    @Resource
    private BpmPortalOrganizationApi portalOrganizationApi;

    @Value("${yudao.bpm.headless-mock.login-password}")
    private String loginPassword;
    @Value("${yudao.bpm.headless-mock.tenant-id:1}")
    private Long tenantId;

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "使用 Portal 用户 ID 登录本地 Headless BPM Mock")
    public CommonResult<BpmPortalMockLoginRespVO> login(@Valid @RequestBody BpmPortalMockLoginReqVO reqVO) {
        BpmPortalOrganizationApi.PortalUser user = portalOrganizationApi.getUser(reqVO.getUserId());
        if (!isValidPassword(reqVO.getPassword()) || user == null || !user.active()) {
            throw exception(UNAUTHORIZED);
        }
        BpmPortalMockLoginRespVO response = new BpmPortalMockLoginRespVO();
        response.setAccessToken(issueLocalMockToken(user));
        response.setTenantId(tenantId);
        response.setUser(convertUser(user));
        return success(response);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前本地 Headless BPM Mock Portal 用户")
    public CommonResult<BpmPortalUserRespVO> getCurrentUser() {
        return success(convertUser(requireCurrentMockUser()));
    }

    private boolean isValidPassword(String password) {
        return password != null && MessageDigest.isEqual(loginPassword.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8));
    }

    private BpmPortalOrganizationApi.PortalUser requireCurrentMockUser() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getInfo() == null
                || !StrUtil.equals(Boolean.TRUE.toString(), loginUser.getInfo().get(LOCAL_MOCK_CLAIM))) {
            throw exception(UNAUTHORIZED);
        }
        BpmPortalOrganizationApi.PortalUser user = portalOrganizationApi.getUser(loginUser.getId());
        if (user == null || !user.active()) {
            throw exception(UNAUTHORIZED);
        }
        return user;
    }

    private String issueLocalMockToken(BpmPortalOrganizationApi.PortalUser user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.id());
        payload.put("role", String.join(",", user.roleCodes()));
        payload.put("tenantId", tenantId);
        payload.put(LOCAL_MOCK_CLAIM, true);
        payload.put("iss", "local-headless-bpm-mock");
        String header = encodeBase64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        return header + "." + encodeBase64Url(JSONUtil.toJsonStr(payload)) + ".local-development-only";
    }

    private static String encodeBase64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static BpmPortalUserRespVO convertUser(BpmPortalOrganizationApi.PortalUser user) {
        BpmPortalUserRespVO response = new BpmPortalUserRespVO();
        response.setId(user.id());
        response.setDisplayName(user.displayName());
        response.setAvatar(user.avatar());
        response.setDepartmentId(user.departmentId());
        response.setDepartmentName(user.departmentName());
        response.setRoleCodes(user.roleCodes());
        return response;
    }

}
