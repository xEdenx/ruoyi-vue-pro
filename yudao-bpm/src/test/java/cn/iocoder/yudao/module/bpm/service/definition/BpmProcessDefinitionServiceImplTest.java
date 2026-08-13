package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class BpmProcessDefinitionServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmProcessDefinitionServiceImpl processDefinitionService;

    @Mock
    private BpmPortalOrganizationApi portalOrganizationApi;

    @Test
    void canUserStartProcessDefinition_matchesPortalStringUserId() {
        BpmProcessDefinitionInfoDO definition = new BpmProcessDefinitionInfoDO()
                .setStartUserIds(List.of("portal-requester-a1f2"));

        assertTrue(processDefinitionService.canUserStartProcessDefinition(definition, "portal-requester-a1f2"));
        assertFalse(processDefinitionService.canUserStartProcessDefinition(definition, "portal-manager-b3c4"));
    }

    @Test
    void canUserStartProcessDefinition_matchesPortalDepartment() {
        BpmProcessDefinitionInfoDO definition = new BpmProcessDefinitionInfoDO()
                .setStartDeptIds(List.of("portal-dept-general"));
        BpmPortalOrganizationApi.PortalUser user = new BpmPortalOrganizationApi.PortalUser(
                "portal-requester-a1f2", "申请人", null, "portal-dept-general", "通用部门", true, Set.of(), Set.of());
        when(portalOrganizationApi.getUser("portal-requester-a1f2")).thenReturn(user);

        assertTrue(processDefinitionService.canUserStartProcessDefinition(definition, "portal-requester-a1f2"));
    }
}
