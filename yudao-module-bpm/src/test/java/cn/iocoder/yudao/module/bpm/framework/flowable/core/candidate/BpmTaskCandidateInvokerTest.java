package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskAssignStartUserHandlerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;

import java.util.*;

import static cn.iocoder.yudao.framework.common.util.collection.SetUtils.asSet;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomString;
import static org.flowable.bpmn.constants.BpmnXMLConstants.FLOWABLE_EXTENSIONS_NAMESPACE;
import static org.flowable.bpmn.constants.BpmnXMLConstants.FLOWABLE_EXTENSIONS_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link BpmTaskCandidateInvoker} 的单元测试
 *
 * @author 芋道源码
 */
public class BpmTaskCandidateInvokerTest extends BaseMockitoUnitTest {

    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Mock
    private BpmPortalOrganizationApi portalOrganizationApi;

    @Mock
    private BpmProcessInstanceService processInstanceService;

    @Spy
    private BpmTaskCandidateStrategy userStrategy;
    @Spy
    private List<BpmTaskCandidateStrategy> strategyList;

    @BeforeEach
    public void setUp() {
        userStrategy = new BpmTaskCandidateStrategy() {
            @Override
            public BpmTaskCandidateStrategyEnum getStrategy() {
                return BpmTaskCandidateStrategyEnum.USER;
            }

            @Override
            public void validateParam(String param) {
            }

            @Override
            public Set<Long> calculateUsers(String param) {
                return new LinkedHashSet<>(Arrays.stream(param.split(",")).map(Long::valueOf).toList());
            }
        };
        strategyList = ListUtil.of(userStrategy); // 创建 strategyList
        taskCandidateInvoker = new BpmTaskCandidateInvoker(strategyList, portalOrganizationApi);
    }

    /**
     * 场景：成功计算到候选人，但是移除了发起人的用户
     */
    @Test
    public void testCalculateUsersByTask_some() {
        try (MockedStatic<SpringUtil> springUtilMockedStatic = mockStatic(SpringUtil.class)) {
            // 准备参数
            String param = "1,2";
            DelegateExecution execution = mock(DelegateExecution.class);
            // mock 方法（DelegateExecution）
            UserTask userTask = mock(UserTask.class);
            String processInstanceId = randomString();
            when(execution.getProcessInstanceId()).thenReturn(processInstanceId);
            when(execution.getCurrentFlowElement()).thenReturn(userTask);
            when(userTask.getAttributeValue(eq(BpmnModelConstants.NAMESPACE), eq(BpmnModelConstants.USER_TASK_CANDIDATE_STRATEGY)))
                    .thenReturn(BpmTaskCandidateStrategyEnum.USER.getStrategy().toString());
            when(userTask.getAttributeValue(eq(BpmnModelConstants.NAMESPACE), eq(BpmnModelConstants.USER_TASK_CANDIDATE_PARAM)))
                    .thenReturn(param);
            when(portalOrganizationApi.isUserActive("1")).thenReturn(true);
            when(portalOrganizationApi.isUserActive("2")).thenReturn(true);
            // mock 移除发起人的用户
            springUtilMockedStatic.when(() -> SpringUtil.getBean(BpmProcessInstanceService.class))
                    .thenReturn(processInstanceService);
            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstanceService.getProcessInstance(eq(processInstanceId))).thenReturn(processInstance);
            when(processInstance.getStartUserId()).thenReturn("1");
            mockFlowElementExtensionElement(userTask, BpmnModelConstants.USER_TASK_ASSIGN_START_USER_HANDLER_TYPE,
                    String.valueOf(BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType()));

            // 调用
            Set<Long> results = taskCandidateInvoker.calculateUsersByTask(execution);
            // 断言
            assertEquals(asSet(2L), results);
        }
    }

    /**
     * 场景：候选人被 Portal 过滤后为空时，不回退到本地“审批人为空”策略
     */
    @Test
    public void testCalculateUsersByTask_none() {
        try (MockedStatic<SpringUtil> springUtilMockedStatic = mockStatic(SpringUtil.class)) {
            // 准备参数
            String param = "1,2";
            DelegateExecution execution = mock(DelegateExecution.class);
            // mock 方法（DelegateExecution）
            UserTask userTask = mock(UserTask.class);
            String processInstanceId = randomString();
            when(execution.getProcessInstanceId()).thenReturn(processInstanceId);
            when(execution.getCurrentFlowElement()).thenReturn(userTask);
            when(userTask.getAttributeValue(eq(BpmnModelConstants.NAMESPACE), eq(BpmnModelConstants.USER_TASK_CANDIDATE_STRATEGY)))
                    .thenReturn(BpmTaskCandidateStrategyEnum.USER.getStrategy().toString());
            when(userTask.getAttributeValue(eq(BpmnModelConstants.NAMESPACE), eq(BpmnModelConstants.USER_TASK_CANDIDATE_PARAM)))
                    .thenReturn(param);
            when(portalOrganizationApi.isUserActive("1")).thenReturn(false);
            when(portalOrganizationApi.isUserActive("2")).thenReturn(false);
            // mock 移除发起人的用户
            springUtilMockedStatic.when(() -> SpringUtil.getBean(BpmProcessInstanceService.class))
                    .thenReturn(processInstanceService);
            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstanceService.getProcessInstance(eq(processInstanceId))).thenReturn(processInstance);
            when(processInstance.getStartUserId()).thenReturn("1");

            // 调用
            Set<Long> results = taskCandidateInvoker.calculateUsersByTask(execution);
            // 断言
            assertEquals(Collections.emptySet(), results);
        }
    }

    /**
     * 场景：没有计算到候选人，但是被禁用移除，最终通过 empty 进行分配
     */
    @Test
    public void testCalculateUsersByActivity_some() {
        try (MockedStatic<BpmnModelUtils> bpmnModelUtilsMockedStatic = mockStatic(BpmnModelUtils.class)) {
            // 准备参数
            String param = "1,2";
            BpmnModel bpmnModel = mock(BpmnModel.class);
            String activityId = randomString();
            Long startUserId = 1L;
            String processDefinitionId = randomString();
            Map<String, Object> processVariables = new HashMap<>();
            // mock 方法（DelegateExecution）
            UserTask userTask = mock(UserTask.class);
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.parseCandidateStrategy(same(userTask)))
                    .thenReturn(BpmTaskCandidateStrategyEnum.USER.getStrategy());
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.parseCandidateParam(same(userTask)))
                    .thenReturn(param);
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.getFlowElementById(same(bpmnModel), eq(activityId))).thenReturn(userTask);
            when(portalOrganizationApi.isUserActive("1")).thenReturn(true);
            when(portalOrganizationApi.isUserActive("2")).thenReturn(true);
            // mock 移除发起人的用户
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.parseAssignStartUserHandlerType(same(userTask)))
                    .thenReturn(BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType());

            // 调用
            Set<Long> results = taskCandidateInvoker.calculateUsersByActivity(bpmnModel, activityId,
                    startUserId, processDefinitionId, processVariables);
            // 断言
            assertEquals(asSet(2L), results);
        }
    }

    /**
     * 场景：候选人被 Portal 过滤后为空时，不回退到本地“审批人为空”策略
     */
    @Test
    public void testCalculateUsersByActivity_none() {
        try (MockedStatic<BpmnModelUtils> bpmnModelUtilsMockedStatic = mockStatic(BpmnModelUtils.class)) {
            // 准备参数
            String param = "1,2";
            BpmnModel bpmnModel = mock(BpmnModel.class);
            String activityId = randomString();
            Long startUserId = 1L;
            String processDefinitionId = randomString();
            Map<String, Object> processVariables = new HashMap<>();
            // mock 方法（DelegateExecution）
            UserTask userTask = mock(UserTask.class);
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.parseCandidateStrategy(same(userTask)))
                    .thenReturn(BpmTaskCandidateStrategyEnum.USER.getStrategy());
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.parseCandidateParam(same(userTask)))
                    .thenReturn(param);
            bpmnModelUtilsMockedStatic.when(() -> BpmnModelUtils.getFlowElementById(same(bpmnModel), eq(activityId))).thenReturn(userTask);
            when(portalOrganizationApi.isUserActive("1")).thenReturn(false);
            when(portalOrganizationApi.isUserActive("2")).thenReturn(false);
            // 调用
            Set<Long> results = taskCandidateInvoker.calculateUsersByActivity(bpmnModel, activityId,
                    startUserId, processDefinitionId, processVariables);
            // 断言
            assertEquals(Collections.emptySet(), results);
        }
    }

    private static void mockFlowElementExtensionElement(FlowElement element, String name, String value) {
        if (value == null) {
            return;
        }
        ExtensionElement extensionElement = new ExtensionElement();
        extensionElement.setNamespace(FLOWABLE_EXTENSIONS_NAMESPACE);
        extensionElement.setNamespacePrefix(FLOWABLE_EXTENSIONS_PREFIX);
        extensionElement.setElementText(value);
        extensionElement.setName(name);
        // mock
        Map<String, List<ExtensionElement>> extensionElements = element.getExtensionElements();
        if (extensionElements == null) {
            extensionElements = new LinkedHashMap<>();
        }
        extensionElements.put(name, Collections.singletonList(extensionElement));
        when(element.getExtensionElements()).thenReturn(extensionElements);
    }

    @Test
    public void testRemoveDisableUsers() {
        // 准备参数：1L 可参与，2L 禁用，3L 不存在。
        Set<Long> assigneeUserIds = asSet(1L, 2L, 3L);
        when(portalOrganizationApi.isUserActive("1")).thenReturn(true);
        when(portalOrganizationApi.isUserActive("2")).thenReturn(false);
        when(portalOrganizationApi.isUserActive("3")).thenReturn(false);

        // 调用
        taskCandidateInvoker.removeDisableUsers(assigneeUserIds);
        // 断言
        assertEquals(asSet(1L), assigneeUserIds);
    }

}
