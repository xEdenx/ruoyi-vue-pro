package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PurchaseRequisitionSubprocessBpmnTest {

    private static final Path BPMN_DIR = Path.of("..", "script", "bpmn");

    @Test
    void parentBpmn_parsesCallActivityWithExplicitContract() throws IOException {
        BpmnModel model = parse("purchase_requisition_with_subprocess_v1.bpmn.xml");

        CallActivity callActivity = assertInstanceOf(CallActivity.class,
                model.getFlowElement("Activity_CallComplianceSubprocess"));
        assertEquals("purchase_requisition_compliance_subprocess_v1", callActivity.getCalledElement());
        assertEquals("key", callActivity.getCalledElementType());
        assertEquals(Set.of("purchaseTitle", "procurementType", "totalAmount", "PROCESS_STATUS"),
                callActivity.getInParameters().stream().map(parameter -> parameter.getSource()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("${bpmCallActivityListener}", callActivity.getExecutionListeners().get(0).getImplementation());
        assertEquals(35, BpmnModelUtils.parseCandidateStrategy(assertInstanceOf(UserTask.class,
                model.getFlowElement("Activity_ParentManagerApproval"))));
        assertEquals(70, BpmnModelUtils.parseCandidateStrategy(assertInstanceOf(UserTask.class,
                model.getFlowElement("Activity_ParentArchive"))));
        assertNotNull(model.getGraphicInfo("Activity_CallComplianceSubprocess"));
    }

    @Test
    void childBpmn_parsesBothPortalRoleTasks() throws IOException {
        BpmnModel model = parse("purchase_requisition_compliance_subprocess_v1.bpmn.xml");

        UserTask finance = assertInstanceOf(UserTask.class, model.getFlowElement("Activity_Subprocess_FinanceReview"));
        UserTask procurement = assertInstanceOf(UserTask.class, model.getFlowElement("Activity_Subprocess_ProcurementCompliance"));
        assertEquals(70, BpmnModelUtils.parseCandidateStrategy(finance));
        assertEquals("ROLE_FINANCE", BpmnModelUtils.parseCandidateParam(finance));
        assertEquals(70, BpmnModelUtils.parseCandidateStrategy(procurement));
        assertEquals("ROLE_PROCUREMENT", BpmnModelUtils.parseCandidateParam(procurement));
        assertNotNull(model.getGraphicInfo("Gateway_Sub_Amount"));
    }

    private static BpmnModel parse(String fileName) throws IOException {
        return BpmnModelUtils.getBpmnModel(Files.readAllBytes(BPMN_DIR.resolve(fileName)));
    }

}
