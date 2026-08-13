package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCopyMapper;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class BpmProcessInstanceCopyServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmProcessInstanceCopyServiceImpl copyService;

    @Mock
    private BpmProcessInstanceCopyMapper copyMapper;
    @Mock
    private BpmProcessInstanceService processInstanceService;
    @Mock
    private BpmProcessDefinitionService processDefinitionService;
    @Mock
    private BpmPortalOrganizationApi portalOrganizationApi;

    @Test
    void createProcessInstanceCopy_keepsPortalUserIds() {
        ProcessInstance processInstance = mock(ProcessInstance.class);
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(processInstanceService.getProcessInstance("process-1")).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("definition-1");
        when(processInstance.getStartUserId()).thenReturn("portal-requester-a1f2");
        when(processInstance.getName()).thenReturn("采购申请");
        when(processDefinitionService.getProcessDefinition("definition-1")).thenReturn(processDefinition);
        when(processDefinition.getCategory()).thenReturn("purchase");
        when(portalOrganizationApi.isUserActive("portal-reader-b3c4")).thenReturn(true);

        AtomicReference<List<BpmProcessInstanceCopyDO>> copies = new AtomicReference<>();
        doAnswer(invocation -> {
            copies.set(invocation.getArgument(0));
            return null;
        }).when(copyMapper).insertBatch(anyCollection());

        copyService.createProcessInstanceCopy(List.of("portal-reader-b3c4"), "请知悉", "process-1",
                "CopyNode_1", "抄送", null);

        BpmProcessInstanceCopyDO copy = copies.get().getFirst();
        assertEquals("portal-reader-b3c4", copy.getUserId());
        assertEquals("portal-requester-a1f2", copy.getStartUserId());
    }

    @Test
    void createProcessInstanceCopy_rejectsInactivePortalUser() {
        when(portalOrganizationApi.isUserActive("portal-disabled-c5d6")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> copyService.createProcessInstanceCopy(
                List.of("portal-disabled-c5d6"), "请知悉", "process-1", "CopyNode_1", "抄送", null));
        verifyNoInteractions(processInstanceService, processDefinitionService, copyMapper);
    }
}
