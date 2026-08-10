package cn.iocoder.yudao.framework.security.core.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.Set;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.skipPermissionCheck;

/**
 * 默认的 {@link SecurityFrameworkService} 实现类
 *
 * @author 芋道源码
 */
@AllArgsConstructor
public class SecurityFrameworkServiceImpl implements SecurityFrameworkService {

    /**
     * 临时无头 Portal mock 可调用的 BPM API 权限。
     *
     * 真实 Portal 接入后，应由已验证的 Portal token 和其权限策略替代本白名单，
     * 不得回退到 system_user_role。
     */
    private static final Set<String> HEADLESS_PORTAL_BPM_PERMISSIONS = Set.of(
            "bpm:process-instance:query", "bpm:task:query", "bpm:task:update");
    private static final String BPM_MODEL_MANAGER_ROLE = "ROLE_BPM_MODEL_MANAGER";

    private final PermissionCommonApi permissionApi;

    @Override
    public boolean hasPermission(String permission) {
        return hasAnyPermissions(permission);
    }

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        // 特殊：跨租户访问
        if (skipPermissionCheck()) {
            return true;
        }

        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (isPortalJwt(loginUser)) {
            // Portal JWT 不再读取 system_user_role；没有角色声明时必须拒绝。
            return hasPortalRole(loginUser)
                    && Arrays.stream(permissions).anyMatch(permission ->
                    HEADLESS_PORTAL_BPM_PERMISSIONS.contains(permission)
                            || (getPortalRoles(loginUser).contains(BPM_MODEL_MANAGER_ROLE)
                            && permission.startsWith("bpm:")));
        }

        // 权限校验
        String userId = getLoginUserId();
        if (StrUtil.isBlank(userId)) {
            return false;
        }
        Long localUserId = cn.iocoder.yudao.framework.common.util.number.NumberUtils.parseLong(userId);
        return localUserId != null && permissionApi.hasAnyPermissions(localUserId, permissions);
    }

    @Override
    public boolean hasRole(String role) {
        return hasAnyRoles(role);
    }

    @Override
    public boolean hasAnyRoles(String... roles) {
        // 特殊：跨租户访问
        if (skipPermissionCheck()) {
            return true;
        }

        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (isPortalJwt(loginUser)) {
            return Arrays.stream(roles).anyMatch(role -> getPortalRoles(loginUser).contains(role));
        }

        // 权限校验
        String userId = getLoginUserId();
        if (StrUtil.isBlank(userId)) {
            return false;
        }
        Long localUserId = cn.iocoder.yudao.framework.common.util.number.NumberUtils.parseLong(userId);
        return localUserId != null && permissionApi.hasAnyRoles(localUserId, roles);
    }

    private static boolean isPortalJwt(LoginUser loginUser) {
        return loginUser != null && loginUser.getInfo() != null && StrUtil.equals(Boolean.TRUE.toString(),
                loginUser.getInfo().get(TokenAuthenticationFilter.PORTAL_JWT_INFO_KEY));
    }

    private static boolean hasPortalRole(LoginUser loginUser) {
        return !getPortalRoles(loginUser).isEmpty();
    }

    private static Set<String> getPortalRoles(LoginUser loginUser) {
        if (loginUser.getInfo() == null) {
            return Set.of();
        }
        String rawRoles = loginUser.getInfo().get(TokenAuthenticationFilter.PORTAL_ROLE_INFO_KEY);
        if (StrUtil.isBlank(rawRoles)) {
            return Set.of();
        }
        return Set.copyOf(StrUtil.splitTrim(rawRoles.replace("[", "").replace("]", "")
                .replace("\"", ""), ','));
    }

    @Override
    public boolean hasScope(String scope) {
        return hasAnyScopes(scope);
    }

    @Override
    public boolean hasAnyScopes(String... scope) {
        // 特殊：跨租户访问
        if (skipPermissionCheck()) {
            return true;
        }

        // 权限校验
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            return false;
        }
        return CollUtil.containsAny(user.getScopes(), Arrays.asList(scope));
    }

}
