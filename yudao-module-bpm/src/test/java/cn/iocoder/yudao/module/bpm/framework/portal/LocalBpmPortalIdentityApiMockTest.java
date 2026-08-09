package cn.iocoder.yudao.module.bpm.framework.portal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBpmPortalIdentityApiMockTest {

    private final LocalBpmPortalIdentityApiMock mock = new LocalBpmPortalIdentityApiMock();

    @Test
    void modelManagerRoleComesFromPortalMock() {
        assertTrue(mock.hasAnyRole("portal-manager-b3c4", List.of("ROLE_BPM_MODEL_MANAGER")));
        assertFalse(mock.hasAnyRole("portal-requester-a1f2", List.of("ROLE_BPM_MODEL_MANAGER")));
    }
}
