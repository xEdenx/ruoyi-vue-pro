package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.headless;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalPortalCandidateApiMockTest {

    private final LocalPortalCandidateApiMock mock = new LocalPortalCandidateApiMock();

    @Test
    void resolveAssigneeIds_returnsCandidatesForConfiguredPortalRoles() {
        assertEquals(Set.of("portal-admin-d5e6"),
                mock.resolveAssigneeIds("portal-requester-a1f2", "Activity_Admin", "ROLE_ADMIN", "process-1"));
        assertEquals(Set.of("portal-supplier-e7f8", "portal-supplier-f9a0"),
                mock.resolveAssigneeIds("portal-requester-a1f2", "Activity_Supplier", "ROLE_SUPPLIER", "process-1"));
    }

    @Test
    void resolveAssigneeIds_rejectsUnknownActivityAndRoleCombination() {
        assertThrows(IllegalArgumentException.class,
                () -> mock.resolveAssigneeIds("portal-requester-a1f2", "Activity_Admin", "ROLE_SUPPLIER", "process-1"));
    }

}
