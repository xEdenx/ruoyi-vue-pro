package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.role;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalPortalRoleCandidateApiMockTest {

    private final LocalPortalRoleCandidateApiMock mock = new LocalPortalRoleCandidateApiMock();

    @Test
    void resolveRoleAssigneeIds_returnsCandidatesForConfiguredPortalRoles() {
        assertEquals(Set.of("portal-admin-d5e6"),
                mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_Admin", "ROLE_ADMIN", "process-1"));
        assertEquals(Set.of("portal-supplier-e7f8", "portal-supplier-f9a0"),
                mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_Supplier", "ROLE_SUPPLIER", "process-1"));
        assertEquals(Set.of("portal-business-owner-g1h2"),
                mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_BusinessOwner", "ROLE_BUSINESS_OWNER", "process-1"));
        assertEquals(Set.of("portal-risk-k4m5", "portal-risk-m5n6"),
                mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_RiskAllSign", "ROLE_RISK", "process-1"));
        assertEquals(Set.of("portal-finance-r8s9"),
                mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_Subprocess_FinanceReview", "ROLE_FINANCE", "process-1"));
        assertEquals(Set.of("portal-procurement-q7r8"),
                mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_Subprocess_ProcurementCompliance", "ROLE_PROCUREMENT", "process-1"));
    }

    @Test
    void resolveRoleAssigneeIds_rejectsUnknownActivityAndRoleCombination() {
        assertThrows(IllegalArgumentException.class,
                () -> mock.resolveRoleAssigneeIds("portal-requester-a1f2", "Activity_Admin", "ROLE_SUPPLIER", "process-1"));
    }

}
