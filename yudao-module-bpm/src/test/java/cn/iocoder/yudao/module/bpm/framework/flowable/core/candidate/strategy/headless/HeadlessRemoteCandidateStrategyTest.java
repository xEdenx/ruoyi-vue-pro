package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.headless;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeadlessRemoteCandidateStrategyTest extends BaseMockitoUnitTest {

    @Test
    void calculateAssigneeIdsByTask_resolvesCandidatesFromPortal() {
        HeadlessRemoteCandidateStrategy.PortalCandidateApi portalCandidateApi =
                mock(HeadlessRemoteCandidateStrategy.PortalCandidateApi.class);
        BpmProcessInstanceService processInstanceService = mock(BpmProcessInstanceService.class);
        DelegateExecution execution = mock(DelegateExecution.class);
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(execution.getProcessInstanceId()).thenReturn("process-1");
        when(execution.getCurrentActivityId()).thenReturn("activity-1");
        when(processInstanceService.getProcessInstance("process-1")).thenReturn(processInstance);
        when(processInstance.getStartUserId()).thenReturn("portal-user-1");
        when(portalCandidateApi.resolveAssigneeIds("portal-user-1", "activity-1", "ROLE_ADMIN", "process-1"))
                .thenReturn(new LinkedHashSet<>(List.of("portal-user-2")));

        HeadlessRemoteCandidateStrategy strategy = new HeadlessRemoteCandidateStrategy(
                Optional.of(portalCandidateApi), processInstanceService);

        assertEquals(Set.of("portal-user-2"),
                strategy.calculateAssigneeIdsByTask(execution, "ROLE_ADMIN"));
    }

    @Test
    void calculateAssigneeIdsByTask_failsWhenPortalResolverIsMissing() {
        HeadlessRemoteCandidateStrategy strategy = new HeadlessRemoteCandidateStrategy(Optional.empty(),
                mock(BpmProcessInstanceService.class));
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceId()).thenReturn("process-1");
        when(execution.getCurrentActivityId()).thenReturn("activity-1");

        assertThrows(IllegalStateException.class,
                () -> strategy.calculateAssigneeIdsByTask(execution, "ROLE_ADMIN"));
    }

    @Test
    void calculateAssigneeIdsByActivity_failsWhenPortalReturnsNoCandidates() {
        HeadlessRemoteCandidateStrategy.PortalCandidateApi portalCandidateApi =
                mock(HeadlessRemoteCandidateStrategy.PortalCandidateApi.class);
        when(portalCandidateApi.resolveAssigneeIds("100", "activity-1", "ROLE_ADMIN", null))
                .thenReturn(Set.of());
        HeadlessRemoteCandidateStrategy strategy = new HeadlessRemoteCandidateStrategy(
                Optional.of(portalCandidateApi), mock(BpmProcessInstanceService.class));

        assertThrows(IllegalStateException.class,
                () -> strategy.calculateAssigneeIdsByActivity(null, "activity-1", "ROLE_ADMIN", 100L, null, null));
    }

    @Test
    void calculateAssigneeIdsByActivity_keepsPortalStartUserId() {
        HeadlessRemoteCandidateStrategy.PortalCandidateApi portalCandidateApi =
                mock(HeadlessRemoteCandidateStrategy.PortalCandidateApi.class);
        when(portalCandidateApi.resolveAssigneeIds("portal-requester-a1f2", "activity-1", "ROLE_ADMIN", null))
                .thenReturn(new LinkedHashSet<>(List.of("portal-admin-d5e6")));
        HeadlessRemoteCandidateStrategy strategy = new HeadlessRemoteCandidateStrategy(
                Optional.of(portalCandidateApi), mock(BpmProcessInstanceService.class));

        assertEquals(Set.of("portal-admin-d5e6"), strategy.calculateAssigneeIdsByActivity(null,
                "activity-1", "ROLE_ADMIN", "portal-requester-a1f2", null, null));
    }

}
