package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.headless;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalPortalCandidateApiMockTest {

    private final LocalPortalCandidateApiMock mock = new LocalPortalCandidateApiMock();

    @Test
    void resolveAssigneeIds_returnsCandidatesForConfiguredPortalRoles() {
        assertEquals(Set.of("103"),
                mock.resolveAssigneeIds("101", "Activity_Admin", "ROLE_ADMIN", "process-1"));
        assertEquals(Set.of("104", "105"),
                mock.resolveAssigneeIds("101", "Activity_Supplier", "ROLE_SUPPLIER", "process-1"));
    }

    @Test
    void resolveAssigneeIds_rejectsUnknownActivityAndRoleCombination() {
        assertThrows(IllegalArgumentException.class,
                () -> mock.resolveAssigneeIds("101", "Activity_Admin", "ROLE_SUPPLIER", "process-1"));
    }

}
