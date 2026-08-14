package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BpmProcessInstanceServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmProcessInstanceServiceImpl processInstanceService;

    @Mock
    private BpmProcessDefinitionService processDefinitionService;
    @Mock
    private BpmTaskService taskService;
    @Mock
    private HistoryService historyService;

    @Test
    void getApprovalDetail_previewWithPortalUserId() {
        ProcessDefinition processDefinition = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(processDefinition.getId()).thenReturn("definition-1");
        when(processDefinitionService.getProcessDefinition("definition-1")).thenReturn(processDefinition);
        when(processDefinitionService.getProcessDefinitionInfo("definition-1")).thenReturn(
                new BpmProcessDefinitionInfoDO().setProcessDefinitionId("definition-1")
                        .setModelType(BpmModelTypeEnum.BPMN.getType()));
        when(processDefinitionService.getProcessDefinitionBpmnModel("definition-1")).thenReturn(createBpmnModel());
        BpmApprovalDetailRespVO result = processInstanceService.getApprovalDetail("portal-requester-a1f2",
                new BpmApprovalDetailReqVO().setProcessDefinitionId("definition-1"));

        assertEquals(BpmProcessInstanceStatusEnum.NOT_START.getStatus(), result.getStatus());
    }

    @Test
    void getApprovalDetail_returnsTodoTaskForCurrentPortalUser() {
        HistoricProcessInstance processInstance = org.mockito.Mockito.mock(HistoricProcessInstance.class);
        HistoricProcessInstanceQuery processInstanceQuery = org.mockito.Mockito.mock(HistoricProcessInstanceQuery.class);
        ProcessDefinition processDefinition = org.mockito.Mockito.mock(ProcessDefinition.class);
        BpmTaskRespVO todoTask = new BpmTaskRespVO().setId("task-1");

        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.includeProcessVariables()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("instance-1");
        when(processInstance.getProcessDefinitionId()).thenReturn("definition-1");
        when(processInstance.getProcessVariables()).thenReturn(Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.RUNNING.getStatus()));
        when(processDefinition.getId()).thenReturn("definition-1");
        when(processDefinitionService.getProcessDefinition("definition-1")).thenReturn(processDefinition);
        when(processDefinitionService.getProcessDefinitionInfo("definition-1")).thenReturn(
                new BpmProcessDefinitionInfoDO().setProcessDefinitionId("definition-1")
                        .setModelType(BpmModelTypeEnum.BPMN.getType()));
        when(processDefinitionService.getProcessDefinitionBpmnModel("definition-1")).thenReturn(createBpmnModel());
        when(taskService.getActivityListByProcessInstanceId("instance-1")).thenReturn(List.of());
        when(taskService.getTaskListByProcessInstanceId("instance-1", true)).thenReturn(List.of());
        when(taskService.getAttachments(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anySet(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(taskService.getTodoTask("portal-approver", "task-1", "instance-1")).thenReturn(todoTask);

        BpmApprovalDetailRespVO result = processInstanceService.getApprovalDetail("portal-approver",
                new BpmApprovalDetailReqVO().setProcessInstanceId("instance-1").setTaskId("task-1"));

        assertSame(todoTask, result.getTodoTask());
        verify(taskService).getTodoTask("portal-approver", "task-1", "instance-1");
    }

    private static BpmnModel createBpmnModel() {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("process");
        model.addProcess(process);

        StartEvent start = new StartEvent();
        start.setId("start");
        UserTask approve = new UserTask();
        approve.setId("approve");
        approve.setName("审批");
        EndEvent end = new EndEvent();
        end.setId("end");
        process.addFlowElement(start);
        process.addFlowElement(approve);
        process.addFlowElement(end);
        process.addFlowElement(new SequenceFlow("start", "approve"));
        process.addFlowElement(new SequenceFlow("approve", "end"));
        return model;
    }

}
