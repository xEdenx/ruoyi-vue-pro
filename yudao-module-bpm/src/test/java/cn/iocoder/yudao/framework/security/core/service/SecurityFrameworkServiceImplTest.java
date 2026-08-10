package cn.iocoder.yudao.framework.security.core.service;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFrameworkServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthorizeBpmPortalApiForJwtWithRoleWithoutLocalUserRole() {
        setPortalJwtLoginUser("ROLE_SUPPLIER");

        assertTrue(createSecurityService().hasPermission("bpm:task:update"));
    }

    @Test
    void shouldRejectPortalJwtWithoutRole() {
        setPortalJwtLoginUser(null);

        assertFalse(createSecurityService().hasPermission("bpm:task:update"));
    }

    @Test
    void shouldNotGrantSystemPermissionToPortalJwt() {
        setPortalJwtLoginUser("ROLE_ADMIN");

        assertFalse(createSecurityService().hasPermission("system:user:query"));
    }

    @Test
    void shouldGrantAllBpmPermissionsToPortalModelManagerMock() {
        setPortalJwtLoginUser("ROLE_BPM_MODEL_MANAGER");

        assertTrue(createSecurityService().hasPermission("bpm:model:update"));
        assertFalse(createSecurityService().hasPermission("system:user:query"));
    }

    private static void setPortalJwtLoginUser(String role) {
        Map<String, String> info = new java.util.HashMap<>();
        info.put(TokenAuthenticationFilter.PORTAL_JWT_INFO_KEY, "true");
        if (role != null) {
            info.put(TokenAuthenticationFilter.PORTAL_ROLE_INFO_KEY, role);
        }
        LoginUser loginUser = new LoginUser().setId("portal-user-102").setInfo(info);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    private SecurityFrameworkServiceImpl createSecurityService() {
        return new SecurityFrameworkServiceImpl();
    }

}
