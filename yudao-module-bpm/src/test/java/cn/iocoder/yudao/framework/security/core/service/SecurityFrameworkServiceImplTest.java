package cn.iocoder.yudao.framework.security.core.service;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

class SecurityFrameworkServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private PermissionCommonApi permissionApi;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthorizeBpmPortalApiForJwtWithRoleWithoutLocalUserRole() {
        setPortalJwtLoginUser("ROLE_SUPPLIER");

        assertTrue(new SecurityFrameworkServiceImpl(permissionApi).hasPermission("bpm:task:update"));
        verifyNoInteractions(permissionApi);
    }

    @Test
    void shouldRejectPortalJwtWithoutRole() {
        setPortalJwtLoginUser(null);

        assertFalse(new SecurityFrameworkServiceImpl(permissionApi).hasPermission("bpm:task:update"));
        verifyNoInteractions(permissionApi);
    }

    @Test
    void shouldNotGrantSystemPermissionToPortalJwt() {
        setPortalJwtLoginUser("ROLE_ADMIN");

        assertFalse(new SecurityFrameworkServiceImpl(permissionApi).hasPermission("system:user:query"));
        verifyNoInteractions(permissionApi);
    }

    private static void setPortalJwtLoginUser(String role) {
        Map<String, String> info = new java.util.HashMap<>();
        info.put(TokenAuthenticationFilter.PORTAL_JWT_INFO_KEY, "true");
        if (role != null) {
            info.put(TokenAuthenticationFilter.PORTAL_ROLE_INFO_KEY, role);
        }
        LoginUser loginUser = new LoginUser().setId("portal-user-102").setSystemUserId(103L).setInfo(info);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

}
