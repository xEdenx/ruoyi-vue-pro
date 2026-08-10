package cn.iocoder.yudao.module.bpm.framework.portal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBpmPortalIdentityApiMockTest {

    private final LocalBpmPortalIdentityApiMock mock = new LocalBpmPortalIdentityApiMock();

    @Test
    void modelManagerRoleComesFromPortalMock() {
        assertTrue(mock.hasAnyRole("portal-manager-b3c4", List.of("ROLE_BPM_MODEL_MANAGER")));
        assertFalse(mock.hasAnyRole("portal-requester-a1f2", List.of("ROLE_BPM_MODEL_MANAGER")));
    }

    @Test
    void organizationSelectorsResolveFinalPortalUserIds() {
        assertEquals(Set.of("portal-supplier-e7f8", "portal-supplier-f9a0"),
                mock.resolveUserIds("ROLE", Set.of("ROLE_SUPPLIER"), "portal-requester-a1f2", "instance-1"));
        assertEquals(Set.of("portal-admin-d5e6"),
                mock.resolveUserIds("DEPT", Set.of("portal-dept-admin"), "portal-requester-a1f2", "instance-1"));
    }
}
