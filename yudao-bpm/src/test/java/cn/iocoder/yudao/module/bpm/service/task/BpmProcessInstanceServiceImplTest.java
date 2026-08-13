package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class BpmProcessInstanceServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmProcessInstanceServiceImpl processInstanceService;

    @Mock
    private BpmProcessDefinitionService processDefinitionService;
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
