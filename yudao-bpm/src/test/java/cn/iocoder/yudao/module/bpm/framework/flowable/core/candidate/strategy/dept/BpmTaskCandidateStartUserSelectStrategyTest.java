package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmTaskCandidateStartUserSelectStrategyTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskCandidateStartUserSelectStrategy strategy;
    @Mock
    private BpmProcessInstanceService processInstanceService;

    @Test
    void calculateAssigneeIdsByTask_preservesPortalUserId() {
        ProcessInstance processInstance = mock(ProcessInstance.class);
        DelegateExecution execution = mock(DelegateExecution.class);
        when(processInstanceService.getProcessInstance(eq(execution.getProcessInstanceId()))).thenReturn(processInstance);
        when(execution.getCurrentActivityId()).thenReturn("approve-1");
        when(processInstance.getProcessVariables()).thenReturn(Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                MapUtil.of("approve-1", List.of("portal-approver-b3c4"))));

        assertEquals(Set.of("portal-approver-b3c4"), strategy.calculateAssigneeIdsByTask(execution, null));
    }

    @Test
    void calculateAssigneeIdsByActivity_preservesPortalUserId() {
        Map<String, Object> variables = Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                MapUtil.of("approve-1", List.of("portal-approver-b3c4")));

        assertEquals(Set.of("portal-approver-b3c4"), strategy.calculateAssigneeIdsByActivity(null,
                "approve-1", null, "portal-requester-a1f2", "definition-1", variables));
    }

}
