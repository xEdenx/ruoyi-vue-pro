package cn.iocoder.yudao.module.bpm.framework.portal;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

import java.util.LinkedHashSet;

/**
 * 当前 Portal 主体的 BPM 适配入口。
 */
public class BpmPortalPrincipalUtils {

    private BpmPortalPrincipalUtils() {
    }

    public static BpmPortalPrincipal getCurrentPrincipal() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || StrUtil.isBlank(loginUser.getId())) {
            return null;
        }
        return BpmPortalPrincipal.of(loginUser.getId(),
                loginUser.getTenantId() == null ? null : String.valueOf(loginUser.getTenantId()),
                CollUtil.isEmpty(loginUser.getScopes()) ? new LinkedHashSet<>() : new LinkedHashSet<>(loginUser.getScopes()));
    }

    public static String getCurrentUserId() {
        BpmPortalPrincipal principal = getCurrentPrincipal();
        return principal == null ? null : principal.getUserId();
    }

}
