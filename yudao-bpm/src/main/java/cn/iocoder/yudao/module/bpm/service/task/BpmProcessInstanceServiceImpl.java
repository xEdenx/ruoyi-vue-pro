package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.*;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.*;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO.ActivityNodeTask;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.convert.task.BpmProcessInstanceConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.redis.BpmProcessIdRedisDAO;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModelNodeTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmAttachmentTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmReasonEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.event.BpmProcessInstanceEventPublisher;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.*;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.flowable.engine.task.Attachment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO.ActivityNode;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants.START_USER_NODE_ID;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.parseNodeType;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.flowable.bpmn.constants.BpmnXMLConstants.*;

/**
 * 流程实例 Service 实现类
 * <p>
 * ProcessDefinition & ProcessInstance & Execution & Task 的关系：
 * 1. <a href="https://blog.csdn.net/bobozai86/article/details/105210414" />
 * <p>
 * HistoricProcessInstance & ProcessInstance 的关系：
 * 1. <a href=" https://my.oschina.net/843294669/blog/71902" />
 * <p>
 * 简单来说，前者 = 历史 + 运行中的流程实例，后者仅是运行中的流程实例
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class BpmProcessInstanceServiceImpl implements BpmProcessInstanceService {

    @Resource
    private RuntimeService runtimeService;
    @Resource
    private HistoryService historyService;

    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    @Lazy // 避免循环依赖
    private BpmTaskService taskService;
    @Resource
    private BpmMessageService messageService;

    @Resource
    private BpmPortalOrganizationApi portalOrganizationApi;

    @Resource
    private BpmProcessInstanceEventPublisher processInstanceEventPublisher;

    @Resource
    private BpmTaskCandidateInvoker taskCandidateInvoker;

    @Resource
    private BpmProcessIdRedisDAO processIdRedisDAO;

    // ========== Query 查询相关方法 ==========

    @Override
    public ProcessInstance getProcessInstance(String id) {
        return runtimeService.createProcessInstanceQuery()
                .includeProcessVariables()
                .processInstanceId(id)
                .singleResult();
    }

    @Override
    public List<ProcessInstance> getProcessInstances(Set<String> ids) {
        return runtimeService.createProcessInstanceQuery().processInstanceIds(ids).includeProcessVariables().list();
    }

    @Override
    public HistoricProcessInstance getHistoricProcessInstance(String id) {
        return historyService.createHistoricProcessInstanceQuery().processInstanceId(id).includeProcessVariables()
                .singleResult();
    }

    @Override
    public List<HistoricProcessInstance> getHistoricProcessInstances(Set<String> ids) {
        return historyService.createHistoricProcessInstanceQuery().processInstanceIds(ids).includeProcessVariables()
                .list();
    }

    private Map<String, String> getFormFieldsPermission(BpmnModel bpmnModel,
                                                        String activityId, String taskId) {
        // 1. 获取流程活动编号。流程活动 Id 为空事，从流程任务中获取流程活动 Id
        if (StrUtil.isEmpty(activityId) && StrUtil.isNotEmpty(taskId)) {
            activityId = Optional.ofNullable(taskService.getHistoricTask(taskId))
                    .map(HistoricTaskInstance::getTaskDefinitionKey).orElse(null);
        }
        if (StrUtil.isEmpty(activityId)) {
            return null;
        }

        // 2. 从 BpmnModel 中解析表单字段权限
        return BpmnModelUtils.parseFormFieldsPermission(bpmnModel, activityId);
    }

    @Override
    public BpmApprovalDetailRespVO getApprovalDetail(String loginUserId, BpmApprovalDetailReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getProcessInstanceId())) {
            // 发起流程前，前端会按流程定义预览审批节点；此时尚不存在流程实例。
            ProcessDefinition processDefinition = processDefinitionService
                    .getProcessDefinition(reqVO.getProcessDefinitionId());
            BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                    .getProcessDefinitionInfo(processDefinition.getId());
            BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(processDefinition.getId());
            Map<String, Object> processVariables = new HashMap<>();
            if (CollUtil.isNotEmpty(reqVO.getProcessVariables())) {
                processVariables.putAll(reqVO.getProcessVariables());
            }
            processVariables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, loginUserId);
            List<ActivityNode> simulateNodes = getSimulateApproveNodeList(loginUserId, bpmnModel,
                    processDefinitionInfo, processVariables, Collections.emptyList(), Collections.emptySet());
            return buildPortalApprovalDetail(reqVO, bpmnModel, processDefinition, processDefinitionInfo,
                    null, BpmProcessInstanceStatusEnum.NOT_START.getStatus(), simulateNodes);
        }
        HistoricProcessInstance processInstance = getHistoricProcessInstance(reqVO.getProcessInstanceId());
        if (processInstance == null) {
            throw exception(ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS);
        }
        ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(processInstance.getProcessDefinitionId());
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(processDefinition.getId());
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(processDefinition.getId());
        Integer status = FlowableUtils.getProcessInstanceStatus(processInstance);
        List<HistoricActivityInstance> activities = taskService.getActivityListByProcessInstanceId(processInstance.getId());
        List<HistoricTaskInstance> tasks = taskService.getTaskListByProcessInstanceId(processInstance.getId(), true);
        List<ActivityNode> endNodes = getEndActivityNodeList(processInstance.getStartUserId(), bpmnModel,
                processDefinitionInfo, processInstance, status, activities, tasks);
        return buildPortalApprovalDetail(reqVO, bpmnModel, processDefinition, processDefinitionInfo,
                processInstance, status, endNodes);
    }

    @Override
    public List<ActivityNode> getNextApprovalNodes(String loginUserId, BpmApprovalDetailReqVO reqVO) {
        // 1.1 校验任务存在，且是当前用户的
        Task task = taskService.validateTask(loginUserId, reqVO.getTaskId());
        // 1.2 校验流程实例存在
        ProcessInstance instance = getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        HistoricProcessInstance historicProcessInstance = getHistoricProcessInstance(task.getProcessInstanceId());
        if (historicProcessInstance == null) {
            throw exception(ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS);
        }
        // 1.3 校验BpmnModel
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(task.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }

        // 2. 设置流程变量
        Map<String, Object> processVariables = new HashMap<>();
        // 2.1 获取历史中流程变量
        if (CollUtil.isNotEmpty(historicProcessInstance.getProcessVariables())) {
            processVariables.putAll(historicProcessInstance.getProcessVariables());
        }
        // 2.2 合并前端传递的流程变量，以前端为准
        if (CollUtil.isNotEmpty(reqVO.getProcessVariables())) {
            processVariables.putAll(reqVO.getProcessVariables());
        }

        // 3.1 获取下一个将要执行的节点集合
        FlowElement flowElement = bpmnModel.getFlowElement(task.getTaskDefinitionKey());
        // 3.2 获取 UserTask 节点
        List<UserTask> nextUserTaskList = BpmnModelUtils.getNextUserTasks(flowElement, bpmnModel, processVariables);
        List<ActivityNode> nextActivityNodes = convertList(nextUserTaskList, node -> new ActivityNode().setId(node.getId())
                .setName(node.getName()).setNodeType(BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType())
                .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(node))
                .setCandidateUserIds(getTaskCandidateAssigneeIdList(bpmnModel, node.getId(),
                        loginUserId, historicProcessInstance.getProcessDefinitionId(), processVariables)));
        if (CollUtil.isEmpty(nextActivityNodes)) {
            return nextActivityNodes;
        }

        // 用户展示信息由 Controller 通过 Portal 组织目录投影，服务层仅保留原始候选人 ID。
        return nextActivityNodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<HistoricProcessInstance> getProcessInstancePage(String userId,
                                                                      BpmProcessInstancePageReqVO pageReqVO) {
        // 1. 构建查询条件
        HistoricProcessInstanceQuery processInstanceQuery = historyService.createHistoricProcessInstanceQuery()
                .includeProcessVariables()
                .processInstanceTenantId(FlowableUtils.getTenantId())
                .orderByProcessInstanceStartTime().desc();
        if (userId != null) { // 【我的流程】菜单时，需要传递该字段
            processInstanceQuery.startedBy(userId);
        } else if (pageReqVO.getStartUserId() != null) { // 【管理流程】菜单时，才会传递该字段
            processInstanceQuery.startedBy(pageReqVO.getStartUserId());
        }
        if (StrUtil.isNotEmpty(pageReqVO.getName())) {
            processInstanceQuery.processInstanceNameLike("%" + pageReqVO.getName() + "%");
        }
        if (StrUtil.isNotEmpty(pageReqVO.getProcessDefinitionKey())) {
            processInstanceQuery.processDefinitionKey(pageReqVO.getProcessDefinitionKey());
        }
        if (StrUtil.isNotEmpty(pageReqVO.getCategory())) {
            processInstanceQuery.processDefinitionCategory(pageReqVO.getCategory());
        }
        if (pageReqVO.getStatus() != null) {
            processInstanceQuery.variableValueEquals(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                    pageReqVO.getStatus());
        }
        if (ArrayUtil.isNotEmpty(pageReqVO.getCreateTime())) {
            processInstanceQuery.startedAfter(DateUtils.of(pageReqVO.getCreateTime()[0]));
            processInstanceQuery.startedBefore(DateUtils.of(pageReqVO.getCreateTime()[1]));
        }
        if (ArrayUtil.isNotEmpty(pageReqVO.getEndTime())) {
            processInstanceQuery.finishedAfter(DateUtils.of(pageReqVO.getEndTime()[0]));
            processInstanceQuery.finishedBefore(DateUtils.of(pageReqVO.getEndTime()[1]));
        }
        // 表单字段查询
        Map<String, Object> formFieldsParams = JsonUtils.parseObject(pageReqVO.getFormFieldsParams(), Map.class);
        if (CollUtil.isNotEmpty(formFieldsParams)) {
            formFieldsParams.forEach((key, value) -> {
                if (StrUtil.isEmpty(String.valueOf(value))) {
                    return;
                }
                // TODO @lesan：应支持多种类型的查询方式，目前只有字符串全等
                processInstanceQuery.variableValueEquals(key, value);
            });
        }

        // 2.1 查询数量
        long processInstanceCount = processInstanceQuery.count();
        if (processInstanceCount == 0) {
            return PageResult.empty(processInstanceCount);
        }
        // 2.2 查询列表
        List<HistoricProcessInstance> processInstanceList = processInstanceQuery.listPage(PageUtils.getStart(pageReqVO),
                pageReqVO.getPageSize());
        return new PageResult<>(processInstanceList, processInstanceCount);
    }

    /**
     * 拼接审批详情的最终数据
     * <p>
     * 主要是，拼接审批人的用户信息、部门信息
     */
    private BpmApprovalDetailRespVO buildApprovalDetail(BpmApprovalDetailReqVO reqVO,
                                                        BpmnModel bpmnModel,
                                                        ProcessDefinition processDefinition,
                                                        BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                        HistoricProcessInstance processInstance,
                                                        Integer processInstanceStatus,
                                                        List<ActivityNode> endApprovalNodeInfos,
                                                        List<ActivityNode> runningApprovalNodeInfos,
                                                        List<ActivityNode> simulateApprovalNodeInfos,
                                                        BpmTaskRespVO todoTask) {
        // 1. 用户展示由 Controller 统一通过 Portal 组织目录投影，服务层不查询 system 用户或部门。
        List<ActivityNode> approveNodes = newArrayList(
                asList(endApprovalNodeInfos, runningApprovalNodeInfos, simulateApprovalNodeInfos));

        // 2. 表单权限
        String taskId = reqVO.getTaskId() == null && todoTask != null ? todoTask.getId() : reqVO.getTaskId();
        Map<String, String> formFieldsPermission = getFormFieldsPermission(bpmnModel, reqVO.getActivityId(), taskId);

        // 3. 拼接数据
        return BpmProcessInstanceConvert.INSTANCE.buildApprovalDetail(bpmnModel, processDefinition,
                processDefinitionInfo, processInstance,
                processInstanceStatus, approveNodes, todoTask, formFieldsPermission, Map.of(), Map.of());
    }

    private BpmApprovalDetailRespVO buildPortalApprovalDetail(BpmApprovalDetailReqVO reqVO,
                                                              BpmnModel bpmnModel,
                                                              ProcessDefinition processDefinition,
                                                              BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                              HistoricProcessInstance processInstance,
                                                              Integer processInstanceStatus,
                                                              List<ActivityNode> approveNodes) {
        return BpmProcessInstanceConvert.INSTANCE.buildApprovalDetail(bpmnModel, processDefinition,
                processDefinitionInfo, processInstance, processInstanceStatus, approveNodes, null,
                getFormFieldsPermission(bpmnModel, reqVO.getActivityId(), reqVO.getTaskId()), Map.of(), Map.of());
    }

    /**
     * 获得【已结束】的活动节点们
     */
    private List<ActivityNode> getEndActivityNodeList(String startUserId, BpmnModel bpmnModel,
                                                      BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                      HistoricProcessInstance historicProcessInstance, Integer processInstanceStatus,
                                                      List<HistoricActivityInstance> activities, List<HistoricTaskInstance> tasks) {
        // 遍历 tasks 列表，只处理已结束的 UserTask
        // 为什么不通过 activities 呢？因为，加签场景下，它只存在于 tasks，没有 activities，导致如果遍历 activities 的话，它无法成为一个节点
        List<HistoricTaskInstance> endTasks = filterList(tasks, task -> task.getEndTime() != null);
        // 获取已完成节点的附件
        Set<String> endTaskIds = convertSet(endTasks, HistoricTaskInstance::getId);
        List<Attachment> attachments = taskService.getAttachments(historicProcessInstance.getId(), endTaskIds, BpmAttachmentTypeEnum.TASK_ATTACHMENT);
        Map<String, List<Attachment>> taskAttachmentMap = convertMultiMap(attachments, Attachment::getTaskId);
        List<ActivityNode> approvalNodes = convertList(endTasks, task -> {
            FlowElement flowNode = BpmnModelUtils.getFlowElementById(bpmnModel, task.getTaskDefinitionKey());
            List<Attachment> taskAttachments = taskAttachmentMap.get(task.getId());
            ActivityNode activityNode = new ActivityNode().setId(task.getTaskDefinitionKey()).setName(task.getName())
                    .setNodeType(START_USER_NODE_ID.equals(task.getTaskDefinitionKey())
                            ? BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType()
                            : ObjUtil.defaultIfNull(parseNodeType(flowNode), // 目的：解决“办理节点”的识别
                            BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType()))
                    .setStatus(getEndActivityNodeStatus(task))
                    .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(flowNode))
                    .setStartTime(DateUtils.of(task.getCreateTime())).setEndTime(DateUtils.of(task.getEndTime()))
                    .setTasks(singletonList(BpmProcessInstanceConvert.INSTANCE.buildApprovalTaskInfo(task, taskAttachments)));
            // 如果是取消状态，则跳过
            if (BpmTaskStatusEnum.isCancelStatus(activityNode.getStatus())) {
                return null;
            }
            return activityNode;
        });

        // 遍历 activities，只处理已结束的 StartEvent、EndEvent
        List<HistoricActivityInstance> endActivities = filterList(activities, activity -> activity.getEndTime() != null
                && (StrUtil.equalsAny(activity.getActivityType(), ELEMENT_EVENT_START, ELEMENT_CALL_ACTIVITY, ELEMENT_EVENT_END)));
        endActivities.forEach(activity -> {
            // StartEvent：只处理 BPMN 的场景。因为，SIMPLE 情况下，已经有 START_USER_NODE 节点
            if (ELEMENT_EVENT_START.equals(activity.getActivityType())
                    && BpmModelTypeEnum.BPMN.getType().equals(processDefinitionInfo.getModelType())
                    && !CollUtil.contains(activities, // 特殊：如果已经存在用户手动创建的 START_USER_NODE_ID 节点，则忽略 StartEvent
                    historicActivity -> historicActivity.getActivityId().equals(START_USER_NODE_ID))) {
                ActivityNodeTask startTask = new ActivityNodeTask().setId(BpmnModelConstants.START_USER_NODE_ID)
                        .setAssignee(startUserId)
                        .setStatus(BpmTaskStatusEnum.APPROVE.getStatus());
                ActivityNode startNode = new ActivityNode().setId(startTask.getId())
                        .setName(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getName())
                        .setNodeType(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType())
                        .setStatus(startTask.getStatus()).setTasks(ListUtil.of(startTask))
                        .setStartTime(DateUtils.of(activity.getStartTime()))
                        .setEndTime(DateUtils.of(activity.getEndTime()));
                approvalNodes.add(0, startNode);
                return;
            }
            // EndEvent
            if (ELEMENT_EVENT_END.equals(activity.getActivityType())) {
                if (BpmProcessInstanceStatusEnum.isRejectStatus(processInstanceStatus)) {
                    // 拒绝情况下，不需要展示 EndEvent 结束节点。原因是：前端已经展示 x 效果，无需重复展示
                    return;
                }
                ActivityNode endNode = new ActivityNode().setId(activity.getId())
                        .setName(BpmSimpleModelNodeTypeEnum.END_NODE.getName())
                        .setNodeType(BpmSimpleModelNodeTypeEnum.END_NODE.getType()).setStatus(processInstanceStatus)
                        .setStartTime(DateUtils.of(activity.getStartTime()))
                        .setEndTime(DateUtils.of(activity.getEndTime()));
                String reason = FlowableUtils.getProcessInstanceReason(historicProcessInstance);
                if (StrUtil.isNotEmpty(reason)) {
                    endNode.setTasks(singletonList(new ActivityNodeTask().setId(endNode.getId())
                            .setStatus(endNode.getStatus()).setReason(reason)));
                }
                approvalNodes.add(endNode);
            }
            // CallActivity
            if (ELEMENT_CALL_ACTIVITY.equals(activity.getActivityType())) {
                ActivityNode callActivity = new ActivityNode().setId(activity.getId())
                        .setName(BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getName())
                        .setNodeType(BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getType()).setStatus(processInstanceStatus)
                        .setStartTime(DateUtils.of(activity.getStartTime()))
                        .setEndTime(DateUtils.of(activity.getEndTime()))
                        .setProcessInstanceId(activity.getCalledProcessInstanceId());
                approvalNodes.add(callActivity);
            }
        });

        // 按照时间排序
        approvalNodes.sort(Comparator.comparing(ActivityNode::getStartTime));
        return approvalNodes;
    }

    /**
     * 获取结束节点的状态
     */
    private Integer getEndActivityNodeStatus(HistoricTaskInstance task) {
        Integer status = FlowableUtils.getTaskStatus(task);
        if (status != null) {
            return status;
        }
        // 结束节点未获取到状态，为跳过状态。可见 bpmn 或者 simple 的 skipExpression
        return BpmTaskStatusEnum.SKIP.getStatus();
    }

    /**
     * 获得【预测（未来）】的活动节点们
     */
    private List<ActivityNode> getSimulateApproveNodeList(String startUserId, BpmnModel bpmnModel,
                                                          BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                          Map<String, Object> processVariables,
                                                          List<HistoricActivityInstance> activities,
                                                          Set<String> needSimulateTaskDefKeysByReturn) {
        // TODO @芋艿：【可优化】在驳回场景下，未来的预测准确性不高。原因是，驳回后，HistoricActivityInstance
        // 包括了历史的操作，不是只有 startEvent 到当前节点的记录
        Set<String> runActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId);
        // 情况一：BPMN 设计器
        if (Objects.equals(BpmModelTypeEnum.BPMN.getType(), processDefinitionInfo.getModelType())) {
            List<FlowElement> flowElements = BpmnModelUtils.simulateProcess(bpmnModel, processVariables);
            return convertList(flowElements, flowElement -> buildNotRunApproveNodeForBpmn(
                    startUserId, bpmnModel, flowElements,
                    processDefinitionInfo, processVariables, flowElement, runActivityIds, needSimulateTaskDefKeysByReturn));
        }
        // 情况二：SIMPLE 设计器
        if (Objects.equals(BpmModelTypeEnum.SIMPLE.getType(), processDefinitionInfo.getModelType())) {
            BpmSimpleModelNodeVO simpleModel = JsonUtils.parseObject(processDefinitionInfo.getSimpleModel(),
                    BpmSimpleModelNodeVO.class);
            List<BpmSimpleModelNodeVO> simpleNodes = SimpleModelUtils.simulateProcess(simpleModel, processVariables);
            return convertList(simpleNodes, simpleNode -> buildNotRunApproveNodeForSimple(
                    startUserId, bpmnModel,
                    processDefinitionInfo, processVariables, simpleNode, runActivityIds, needSimulateTaskDefKeysByReturn));
        }
        throw new IllegalArgumentException("未知设计器类型：" + processDefinitionInfo.getModelType());
    }

    private ActivityNode buildNotRunApproveNodeForSimple(String startUserId, BpmnModel bpmnModel,
                                                         BpmProcessDefinitionInfoDO processDefinitionInfo, Map<String, Object> processVariables,
                                                         BpmSimpleModelNodeVO node, Set<String> runActivityIds,
                                                         Set<String> needSimulateTaskDefKeysByReturn) {
        // TODO @芋艿：【可优化】在驳回场景下，未来的预测准确性不高。原因是，驳回后，HistoricActivityInstance
        // 包括了历史的操作，不是只有 startEvent 到当前节点的记录
        if (runActivityIds.contains(node.getId())
                && !needSimulateTaskDefKeysByReturn.contains(node.getId())) { // 特殊：回退操作时候，会记录需要预测的节点到流程变量中。即使在历史操作中，也需要预测
            return null;
        }
        Integer status = BpmTaskStatusEnum.NOT_START.getStatus();
        // 如果节点被跳过。设置状态为跳过
        if (SimpleModelUtils.isSkipNode(node, processVariables)) {
            status = BpmTaskStatusEnum.SKIP.getStatus();
        }
        ActivityNode activityNode = new ActivityNode().setId(node.getId()).setName(node.getName())
                .setNodeType(node.getType()).setCandidateStrategy(node.getCandidateStrategy())
                .setStatus(status);

        // 1. 开始节点/审批节点
        if (ObjectUtils.equalsAny(node.getType(),
                BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType(),
                BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType(),
                BpmSimpleModelNodeTypeEnum.TRANSACTOR_NODE.getType())) {
                List<String> candidateUserIds = getTaskCandidateAssigneeIdList(bpmnModel, node.getId(),
                    startUserId, processDefinitionInfo.getProcessDefinitionId(), processVariables);
            activityNode.setCandidateUserIds(candidateUserIds);
            return activityNode;
        }

        // 2. 结束节点
        if (BpmSimpleModelNodeTypeEnum.END_NODE.getType().equals(node.getType())) {
            return activityNode;
        }

        // 3. 抄送节点
        if (CollUtil.isEmpty(runActivityIds) && // 流程发起时：需要展示抄送节点，用于选择抄送人
                BpmSimpleModelNodeTypeEnum.COPY_NODE.getType().equals(node.getType())) {
            List<String> candidateUserIds = getTaskCandidateAssigneeIdList(bpmnModel, node.getId(),
                    startUserId, processDefinitionInfo.getProcessDefinitionId(), processVariables);
            activityNode.setCandidateUserIds(candidateUserIds);
            return activityNode;
        }

        // 4. 子流程节点
        if (BpmSimpleModelNodeTypeEnum.CHILD_PROCESS.getType().equals(node.getType())) {
            return activityNode;
        }
        return null;
    }

    private ActivityNode buildNotRunApproveNodeForBpmn(String startUserId, BpmnModel bpmnModel, List<FlowElement> flowElements,
                                                       BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                       Map<String, Object> processVariables,
                                                       FlowElement node, Set<String> runActivityIds,
                                                       Set<String> needSimulateTaskDefKeysByReturn) {
        // 回退操作时候，会记录需要预测的节点到流程变量中。即使节点在历史操作中，也需要预测。
        if (!needSimulateTaskDefKeysByReturn.contains(node.getId()) && runActivityIds.contains(node.getId())) {
            return null;
        }

        Integer status = BpmTaskStatusEnum.NOT_START.getStatus();
        // 如果节点被跳过，状态设置为跳过
        if (BpmnModelUtils.isSkipNode(node, processVariables)) {
            status = BpmTaskStatusEnum.SKIP.getStatus();
        }
        ActivityNode activityNode = new ActivityNode().setId(node.getId())
                .setStatus(status);

        // 1. 开始节点
        if (node instanceof StartEvent) {
            if (CollUtil.contains(flowElements, // 特殊：如果已经存在用户手动创建的 START_USER_NODE_ID 节点，则忽略 StartEvent
                    flowElement -> flowElement.getId().equals(START_USER_NODE_ID))) {
                return null;
            }
            return activityNode.setName(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getName())
                    .setNodeType(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType());
        }

        // 2. 审批节点
        if (node instanceof UserTask) {
            List<String> candidateUserIds = getTaskCandidateAssigneeIdList(bpmnModel, node.getId(),
                    startUserId, processDefinitionInfo.getProcessDefinitionId(), processVariables);
            return activityNode.setName(node.getName()).setNodeType(BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType())
                    .setCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(node))
                    .setCandidateUserIds(candidateUserIds);
        }

        // 3. 结束节点
        if (node instanceof EndEvent) {
            return activityNode.setName(BpmSimpleModelNodeTypeEnum.END_NODE.getName())
                    .setNodeType(BpmSimpleModelNodeTypeEnum.END_NODE.getType());
        }
        return null;
    }

    private List<String> getTaskCandidateAssigneeIdList(BpmnModel bpmnModel, String activityId,
                                                         String startUserId, String processDefinitionId,
                                                         Map<String, Object> processVariables) {
        Set<String> userIds = taskCandidateInvoker.calculateAssigneeIdsByActivity(bpmnModel, activityId,
                startUserId, processDefinitionId, processVariables);
        return new ArrayList<>(userIds);
    }

    @Override
    public BpmProcessInstanceBpmnModelViewRespVO getProcessInstanceBpmnModelView(String id) {
        // 1.1 获得流程实例
        HistoricProcessInstance processInstance = getHistoricProcessInstance(id);
        if (processInstance == null) {
            return null;
        }
        // 1.2 获得流程定义
        BpmnModel bpmnModel = processDefinitionService
                .getProcessDefinitionBpmnModel(processInstance.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }
        BpmSimpleModelNodeVO simpleModel = null;
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.getProcessDefinitionInfo(
                processInstance.getProcessDefinitionId());
        if (processDefinitionInfo != null
                && BpmModelTypeEnum.SIMPLE.getType().equals(processDefinitionInfo.getModelType())) {
            simpleModel = JsonUtils.parseObject(processDefinitionInfo.getSimpleModel(), BpmSimpleModelNodeVO.class);
        }
        // 1.3 获得流程实例对应的活动实例列表 + 任务列表
        List<HistoricActivityInstance> activities = taskService.getActivityListByProcessInstanceId(id);
        List<HistoricTaskInstance> tasks = taskService.getTaskListByProcessInstanceId(id, true);

        // 2.1 拼接进度信息
        Set<String> unfinishedTaskActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId,
                activityInstance -> activityInstance.getEndTime() == null);
        Set<String> finishedTaskActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId,
                activityInstance -> activityInstance.getEndTime() != null
                        && ObjectUtil.notEqual(activityInstance.getActivityType(),
                        BpmnXMLConstants.ELEMENT_SEQUENCE_FLOW));
        Set<String> finishedSequenceFlowActivityIds = convertSet(activities, HistoricActivityInstance::getActivityId,
                activityInstance -> activityInstance.getEndTime() != null
                        && ObjectUtil.equals(activityInstance.getActivityType(),
                        BpmnXMLConstants.ELEMENT_SEQUENCE_FLOW));
        // 特殊：会签情况下，会有部分已完成（审批）、部分未完成（待审批），此时需要 finishedTaskActivityIds 移除掉
        finishedTaskActivityIds.removeAll(unfinishedTaskActivityIds);
        // 特殊：如果流程实例被拒绝，则需要计算是哪个活动节点。
        // 注意，只取最后一个。因为会存在多次拒绝的情况，拒绝驳回到指定节点
        Set<String> rejectTaskActivityIds = CollUtil.newHashSet();
        if (BpmProcessInstanceStatusEnum.isRejectStatus(FlowableUtils.getProcessInstanceStatus(processInstance))) {
            tasks.stream()
                    .filter(task -> BpmTaskStatusEnum.isRejectStatus(FlowableUtils.getTaskStatus(task)))
                    .max(Comparator.comparing(HistoricTaskInstance::getEndTime))
                    .ifPresent(reject -> rejectTaskActivityIds.add(reject.getTaskDefinitionKey()));
            finishedTaskActivityIds.removeAll(rejectTaskActivityIds);
        }

        // 2.2 用户展示由 Controller 统一通过 Portal 组织目录投影。
        return BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceBpmnModelView(processInstance, tasks, bpmnModel,
                simpleModel,
                unfinishedTaskActivityIds, finishedTaskActivityIds, finishedSequenceFlowActivityIds,
                rejectTaskActivityIds,
                Map.of(), Map.of());
    }

    // ========== Update 写入相关方法 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createProcessInstance(String userId, @Valid BpmProcessInstanceCreateReqVO createReqVO) {
        return FlowableUtils.executeAuthenticatedUserId(userId, () -> {
            ProcessDefinition definition = processDefinitionService
                    .getProcessDefinition(createReqVO.getProcessDefinitionId());
            if (definition == null) {
                throw exception(PROCESS_DEFINITION_NOT_EXISTS);
            }
            if (definition.isSuspended()) {
                throw exception(PROCESS_DEFINITION_IS_SUSPENDED);
            }
            BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                    .getProcessDefinitionInfo(definition.getId());
            if (processDefinitionInfo == null) {
                throw exception(PROCESS_DEFINITION_NOT_EXISTS);
            }

            if (!processDefinitionService.canUserStartProcessDefinition(processDefinitionInfo, userId)) {
                throw exception(PROCESS_INSTANCE_START_USER_CAN_START);
            }
            validateStartUserSelectAssignees(definition, createReqVO.getStartUserSelectAssignees(),
                    createReqVO.getVariables());
            Map<String, Object> variables = createReqVO.getVariables() != null
                    ? new HashMap<>(createReqVO.getVariables()) : new HashMap<>();
            FlowableUtils.filterProcessInstanceFormVariable(variables);
            variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, userId);
            variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                    BpmProcessInstanceStatusEnum.RUNNING.getStatus());
            variables.put(BpmnVariableConstants.PROCESS_INSTANCE_SKIP_EXPRESSION_ENABLED, true);
            if (CollUtil.isNotEmpty(createReqVO.getStartUserSelectAssignees())) {
                variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                        createReqVO.getStartUserSelectAssignees());
            }

            ProcessInstanceBuilder builder = runtimeService.createProcessInstanceBuilder()
                    .processDefinitionId(definition.getId())
                    .variables(variables);
            BpmModelMetaInfoVO.ProcessIdRule processIdRule = processDefinitionInfo.getProcessIdRule();
            if (processIdRule != null && Boolean.TRUE.equals(processIdRule.getEnable())) {
                builder.predefineProcessInstanceId(processIdRedisDAO.generate(processIdRule));
            }
            builder.name(generatePortalProcessInstanceName(userId, definition, processDefinitionInfo, variables));
            return builder.start().getId();
        });
    }

    @Override
    public String createProcessInstance(String userId, @Valid BpmProcessInstanceCreateReqDTO createReqDTO) {
        return FlowableUtils.executeAuthenticatedUserId(userId, () -> {
            // 获得流程定义
            ProcessDefinition definition = processDefinitionService
                    .getActiveProcessDefinition(createReqDTO.getProcessDefinitionKey());
            // 发起流程
            return createProcessInstance0(userId, definition, createReqDTO.getVariables(),
                    createReqDTO.getBusinessKey(),
                    createReqDTO.getStartUserSelectAssignees());
        });
    }

    private String createProcessInstance0(String userId, ProcessDefinition definition,
                                          Map<String, Object> variables, String businessKey,
                                          Map<String, List<String>> startUserSelectAssignees) {
        // 1.1 校验流程定义
        if (definition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }
        if (definition.isSuspended()) {
            throw exception(PROCESS_DEFINITION_IS_SUSPENDED);
        }
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(definition.getId());
        if (processDefinitionInfo == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }
        // 1.2 校验是否能够发起
        if (!processDefinitionService.canUserStartProcessDefinition(processDefinitionInfo, userId)) {
            throw exception(PROCESS_INSTANCE_START_USER_CAN_START);
        }
        // 1.3 校验发起人自选审批人
        validateStartUserSelectAssignees(definition, startUserSelectAssignees, variables);

        // 2. 创建流程实例
        if (variables == null) {
            variables = new HashMap<>();
        }
        FlowableUtils.filterProcessInstanceFormVariable(variables); // 过滤一下，避免 ProcessInstance 系统级的变量被占用
        variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, userId); // 设置流程变量，发起人 ID
        variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, // 流程实例状态：审批中
                BpmProcessInstanceStatusEnum.RUNNING.getStatus());
        variables.put(BpmnVariableConstants.PROCESS_INSTANCE_SKIP_EXPRESSION_ENABLED, true); // 跳过表达式需要添加此变量为 true，不影响没配置 skipExpression 的节点
        if (CollUtil.isNotEmpty(startUserSelectAssignees)) {
            // 设置流程变量，发起人自选审批人
            variables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_SELECT_ASSIGNEES,
                    startUserSelectAssignees);
        }

        // 3. 创建流程
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder()
                .processDefinitionId(definition.getId())
                .businessKey(businessKey)
                .variables(variables);
        // 3.1 创建流程 ID
        BpmModelMetaInfoVO.ProcessIdRule processIdRule = processDefinitionInfo.getProcessIdRule();
        if (processIdRule != null && Boolean.TRUE.equals(processIdRule.getEnable())) {
            processInstanceBuilder.predefineProcessInstanceId(processIdRedisDAO.generate(processIdRule));
        }
        // 3.2 流程名称
        processInstanceBuilder.name(generatePortalProcessInstanceName(userId, definition, processDefinitionInfo, variables));
        // 3.3 发起流程实例
        ProcessInstance instance = processInstanceBuilder.start();
        return instance.getId();
    }

    private void validateStartUserSelectAssignees(ProcessDefinition definition,
                                                  Map<String, List<String>> startUserSelectAssignees,
                                                  Map<String, Object> variables) {
        // 预测逻辑原先会把 Portal 自选的字符串 ID 转回 Long；这里仅根据当前变量找出实际会经过的节点，
        // 并校验发起人是否提供了非空 ID，从而保持 Portal ID 的透明传递。
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(definition.getId());
        List<FlowElement> flowElements = BpmnModelUtils.simulateProcess(bpmnModel,
                variables != null ? variables : Collections.emptyMap());
        flowElements.stream()
                .filter(UserTask.class::isInstance)
                .map(UserTask.class::cast)
                .filter(task -> ObjectUtil.equal(BpmTaskCandidateStrategyEnum.START_USER_SELECT.getStrategy(),
                        BpmnModelUtils.parseCandidateStrategy(task)))
                .forEach(task -> {
            List<String> assignees = startUserSelectAssignees != null ? startUserSelectAssignees.get(task.getId()) : null;
            if (CollUtil.isEmpty(assignees) || assignees.stream().anyMatch(StrUtil::isBlank)) {
                throw exception(PROCESS_INSTANCE_START_USER_SELECT_ASSIGNEES_NOT_CONFIG, task.getName());
            }
        });
    }

    private String generatePortalProcessInstanceName(String userId,
                                                     ProcessDefinition definition,
                                                     BpmProcessDefinitionInfoDO definitionInfo,
                                                     Map<String, Object> variables) {
        if (definition == null || definitionInfo == null || definitionInfo.getTitleSetting() == null
                || !BooleanUtil.isTrue(definitionInfo.getTitleSetting().getEnable())) {
            return definition != null ? definition.getName() : null;
        }
        Map<String, Object> cloneVariables = new HashMap<>(variables);
        cloneVariables.put(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_START_USER_ID, userId);
        cloneVariables.put(BpmnVariableConstants.PROCESS_START_TIME, DateUtil.now());
        cloneVariables.put(BpmnVariableConstants.PROCESS_DEFINITION_NAME, definition.getName().trim());
        return StrUtil.format(definitionInfo.getTitleSetting().getTitle(), cloneVariables);
    }

    @Override
    public void cancelProcessInstanceByStartUser(String userId, @Valid BpmProcessInstanceCancelReqVO cancelReqVO) {
        // 1.1 校验流程实例存在
        ProcessInstance instance = getProcessInstance(cancelReqVO.getId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_EXISTS);
        }
        // 1.2 只能取消自己的
        if (!Objects.equals(instance.getStartUserId(), userId)) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_SELF);
        }
        // 1.3 校验允许撤销审批中的申请
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService
                .getProcessDefinitionInfo(instance.getProcessDefinitionId());
        Assert.notNull(processDefinitionInfo, "流程定义({})不存在", processDefinitionInfo);
        if (processDefinitionInfo.getAllowCancelRunningProcess() != null // 防止未配置 AllowCancelRunningProcess , 默认为可取消
                && BooleanUtil.isFalse(processDefinitionInfo.getAllowCancelRunningProcess())) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_ALLOW);
        }
        // 1.4 子流程不允许取消
        if (StrUtil.isNotBlank(instance.getSuperExecutionId())) {
            throw exception(PROCESS_INSTANCE_CANCEL_CHILD_FAIL_NOT_ALLOW);
        }

        // 2. 取消流程
        updateProcessInstanceCancel(cancelReqVO.getId(),
                BpmReasonEnum.CANCEL_PROCESS_INSTANCE_BY_START_USER.format(cancelReqVO.getReason()));
    }

    @Override
    public void cancelProcessInstanceByAdmin(String userId, BpmProcessInstanceCancelReqVO cancelReqVO) {
        // 1.1 校验流程实例存在
        ProcessInstance instance = getProcessInstance(cancelReqVO.getId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_CANCEL_FAIL_NOT_EXISTS);
        }

        // 2. 取消流程
        BpmPortalOrganizationApi.PortalUser user = portalOrganizationApi.getUser(userId);
        updateProcessInstanceCancel(cancelReqVO.getId(),
                BpmReasonEnum.CANCEL_PROCESS_INSTANCE_BY_ADMIN.format(
                        user != null ? user.getDisplayName() : userId, cancelReqVO.getReason()));
    }

    private void updateProcessInstanceCancel(String id, String reason) {
        // 1. 更新流程实例 status
        runtimeService.setVariable(id, BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.CANCEL.getStatus());
        runtimeService.setVariable(id, BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON, reason);

        // 2. 取消所有子流程
        List<ProcessInstance> childProcessInstances = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(id).list();
        childProcessInstances.forEach(processInstance -> updateProcessInstanceCancel(
                processInstance.getProcessInstanceId(), BpmReasonEnum.CANCEL_CHILD_PROCESS_INSTANCE_BY_MAIN_PROCESS.getReason()));

        // 3. 结束流程
        taskService.moveTaskToEnd(id, reason);
    }

    @Override
    public void updateProcessInstanceReject(ProcessInstance processInstance, String reason) {
        runtimeService.setVariable(processInstance.getProcessInstanceId(),
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.REJECT.getStatus());
        runtimeService.setVariable(processInstance.getProcessInstanceId(),
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON,
                BpmReasonEnum.REJECT_TASK.format(reason));
    }

    @Override
    public void updateProcessInstanceVariables(String id, Map<String, Object> variables) {
        runtimeService.setVariables(id, variables);
    }

    @Override
    public void removeProcessInstanceVariables(String id, Collection<String> variableNames) {
        runtimeService.removeVariables(id, variableNames);
    }

    // ========== Event 事件相关方法 ==========

    @Override
    public void processProcessInstanceCompleted(ProcessInstance instance) {
        // 1.1 获取当前状态
        Integer status = (Integer) instance.getProcessVariables()
                .get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
        String reason = (String) instance.getProcessVariables()
                .get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON);
        // 1.2 当流程状态还是审批状态中，说明审批通过了，则变更下它的状态
        // 为什么这么处理？因为流程完成，并且完成了，说明审批通过了
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            status = BpmProcessInstanceStatusEnum.APPROVE.getStatus();
            runtimeService.setVariable(instance.getId(), BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                    status);
        }

        // 1.3 如果子流程拒绝，设置其父流程也为拒绝状态，且结束父流程
        // 相关问题链接：https://t.zsxq.com/kZhyb
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())
                && StrUtil.isNotBlank(instance.getSuperExecutionId())) {
            // 1.3.1 获取父流程实例 并标记为不通过
            Execution execution = runtimeService.createExecutionQuery().executionId(instance.getSuperExecutionId()).singleResult();
            ProcessInstance parentProcessInstance = getProcessInstance(execution.getProcessInstanceId());
            updateProcessInstanceReject(parentProcessInstance, BpmReasonEnum.REJECT_CHILD_PROCESS.getReason());

            // 1.3.2 结束父流程。需要在子流程结束事务提交后执行
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(int transactionStatus) {
                    // 回滚情况，直接返回
                    if (ObjectUtil.equal(transactionStatus, TransactionSynchronization.STATUS_ROLLED_BACK)) {
                        return;
                    }
                    taskService.moveTaskToEnd(parentProcessInstance.getId(), BpmReasonEnum.REJECT_CHILD_PROCESS.getReason());
                }
            });
        }

        // 2. 保留流程结果通知语义，由 Portal 通知端口负责具体投递。
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            messageService.sendMessageWhenProcessInstanceApprove(
                    BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceApproveMessage(instance));
        } else if (Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())) {
            messageService.sendMessageWhenProcessInstanceReject(
                    BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceRejectMessage(instance, reason));
        }

        // 3. 发送流程实例的状态事件
        processInstanceEventPublisher.sendProcessInstanceResultEvent(
                BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceStatusEvent(this, instance, status, reason));

        // 4. 流程后置通知
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.
                    getProcessDefinitionInfo(instance.getProcessDefinitionId());
            if (ObjUtil.isNotNull(processDefinitionInfo) &&
                    ObjUtil.isNotNull(processDefinitionInfo.getProcessAfterTriggerSetting())) {
                BpmModelMetaInfoVO.HttpRequestSetting setting = processDefinitionInfo.getProcessAfterTriggerSetting();

                BpmHttpRequestUtils.executeBpmHttpRequest(instance,
                        setting.getUrl(), setting.getHeader(), setting.getBody(), true, setting.getResponse());
            }
        }
    }

    @Override
    public void processProcessInstanceCreated(ProcessInstance instance) {
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.
                getProcessDefinitionInfo(instance.getProcessDefinitionId());
        ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(instance.getProcessDefinitionId());
        if (processDefinition == null || processDefinitionInfo == null) {
            return;
        }

        // 自定义标题。目的：主要处理子流程的标题无法处理
        // 注意：必须使用 TransactionSynchronizationManager 事务提交后，否则不生效！！！
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                // Portal 发起人是透明字符串 ID，不能在提交后的标题刷新路径回转为本地 Long 用户 ID。
                String name = generatePortalProcessInstanceName(instance.getStartUserId(),
                        processDefinition, processDefinitionInfo, instance.getProcessVariables());
                if (ObjUtil.notEqual(instance.getName(), name)) {
                    runtimeService.setProcessInstanceName(instance.getProcessInstanceId(), name);
                }

                // 流程前置通知：需要在流程启动后(事务提交后)，保证 variables 已设置
                // 相关问题链接：https://t.zsxq.com/DF7Kq
                if (ObjUtil.isNull(processDefinitionInfo.getProcessBeforeTriggerSetting())) {
                    return;
                }
                BpmModelMetaInfoVO.HttpRequestSetting setting = processDefinitionInfo.getProcessBeforeTriggerSetting();
                BpmHttpRequestUtils.executeBpmHttpRequest(instance,
                        setting.getUrl(), setting.getHeader(), setting.getBody(), true, setting.getResponse());
            }

        });
    }

}
