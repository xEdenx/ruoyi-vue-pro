package cn.iocoder.yudao.module.bpm.framework.portal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalBpmPortalIdentityApiMockTest {

    private final LocalBpmPortalIdentityApiMock portalApi = new LocalBpmPortalIdentityApiMock();

    @Test
    void resolveUserIds_resolvesDepartmentLeaderFromPortalDirectory() {
        assertEquals(Set.of("portal-manager-b3c4"), portalApi.resolveUserIds("DEPT_LEADER_OF_USER",
                List.of("portal-requester-a1f2"), "portal-requester-a1f2", "process-1"));
    }

    @Test
    void getDepartment_returnsPortalStringIdProjection() {
        BpmPortalOrganizationApi.PortalDepartment department = portalApi.getDepartment("portal-dept-general");

        assertEquals("portal-dept-general", department.getId());
        assertEquals("通用部门", department.getName());
    }

    @Test
    void listSelectableDirectory_returnsPortalStringIds() {
        assertEquals(List.of("portal-admin-d5e6", "portal-manager-b3c4", "portal-requester-a1f2",
                        "portal-supplier-e7f8", "portal-supplier-f9a0"),
                portalApi.listSelectableUsers().stream().map(BpmPortalOrganizationApi.PortalUser::getId).toList());
        assertEquals(List.of("portal-dept-admin", "portal-dept-general", "portal-dept-supplier"),
                portalApi.listSelectableDepartments().stream()
                        .map(BpmPortalOrganizationApi.PortalDepartment::getId).toList());
    }
}
