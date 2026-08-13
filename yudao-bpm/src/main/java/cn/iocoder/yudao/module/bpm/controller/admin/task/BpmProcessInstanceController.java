package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.BpmPortalUserProjection;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.*;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.convert.task.BpmProcessInstanceConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import cn.iocoder.yudao.module.bpm.service.definition.BpmCategoryService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalPrincipalUtils.getCurrentUserId;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;

@Tag(name = "管理后台 - 流程实例") // 流程实例，通过流程定义创建的一次“申请”
@RestController
@RequestMapping("/bpm/process-instance")
@Validated
public class BpmProcessInstanceController {

    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmTaskService taskService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    private BpmCategoryService categoryService;

    @Resource
    private BpmPortalOrganizationApi portalOrganizationApi;
    @Resource
    private BpmPortalUserProjection portalUserProjection;

    @GetMapping("/my-page")
    @Operation(summary = "获得我的实例分页列表", description = "在【我的流程】菜单中，进行调用")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<PageResult<BpmProcessInstanceRespVO>> getProcessInstanceMyPage(
            @Valid BpmProcessInstancePageReqVO pageReqVO) {
        PageResult<HistoricProcessInstance> pageResult = processInstanceService.getProcessInstancePage(
                getCurrentUserId(), pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 拼接返回
        Map<String, List<Task>> taskMap = taskService.getTaskMapByProcessInstanceIds(
                convertList(pageResult.getList(), HistoricProcessInstance::getId));
        Map<String, ProcessDefinition> processDefinitionMap = processDefinitionService.getProcessDefinitionMap(
                convertSet(pageResult.getList(), HistoricProcessInstance::getProcessDefinitionId));
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(
                convertSet(processDefinitionMap.values(), ProcessDefinition::getCategory));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), HistoricProcessInstance::getProcessDefinitionId));
        PageResult<BpmProcessInstanceRespVO> response = BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePage(pageResult,
                processDefinitionMap, categoryMap, taskMap, Map.of(), Map.of(), processDefinitionInfoMap);
        enrichPortalUsers(response.getList());
        return success(response);
    }

    @GetMapping("/manager-page")
    @Operation(summary = "获得管理流程实例的分页列表", description = "在【流程实例】菜单中，进行调用")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:manager-query')")
    public CommonResult<PageResult<BpmProcessInstanceRespVO>> getProcessInstanceManagerPage(
            @Valid BpmProcessInstancePageReqVO pageReqVO) {
        PageResult<HistoricProcessInstance> pageResult = processInstanceService.getProcessInstancePage(
                null, pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 拼接返回
        Map<String, List<Task>> taskMap = taskService.getTaskMapByProcessInstanceIds(
                convertList(pageResult.getList(), HistoricProcessInstance::getId));
        Map<String, ProcessDefinition> processDefinitionMap = processDefinitionService.getProcessDefinitionMap(
                convertSet(pageResult.getList(), HistoricProcessInstance::getProcessDefinitionId));
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(
                convertSet(processDefinitionMap.values(), ProcessDefinition::getCategory));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), HistoricProcessInstance::getProcessDefinitionId));
        PageResult<BpmProcessInstanceRespVO> response = BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePage(pageResult,
                processDefinitionMap, categoryMap, taskMap, Map.of(), Map.of(), processDefinitionInfoMap);
        enrichPortalUsers(response.getList());
        return success(response);
    }

    @PostMapping("/create")
    @Operation(summary = "新建流程实例")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<String> createProcessInstance(@Valid @RequestBody BpmProcessInstanceCreateReqVO createReqVO) {
        return success(processInstanceService.createProcessInstance(getCurrentUserId(), createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得指定流程实例", description = "在【流程详细】界面中，进行调用")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<BpmProcessInstanceRespVO> getProcessInstance(@RequestParam("id") String id) {
        HistoricProcessInstance processInstance = processInstanceService.getHistoricProcessInstance(id);
        if (processInstance == null) {
            return success(null);
        }

        // 拼接返回
        ProcessDefinition processDefinition = processDefinitionService.getProcessDefinition(
                processInstance.getProcessDefinitionId());
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.getProcessDefinitionInfo(
                processInstance.getProcessDefinitionId());
        BpmProcessInstanceRespVO response = BpmProcessInstanceConvert.INSTANCE.buildProcessInstance(processInstance,
                processDefinition, processDefinitionInfo, null, null);
        enrichPortalUsers(response);
        return success(response);
    }

    @DeleteMapping("/cancel-by-start-user")
    @Operation(summary = "用户取消流程实例", description = "取消发起的流程")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:cancel')")
    public CommonResult<Boolean> cancelProcessInstanceByStartUser(
            @Valid @RequestBody BpmProcessInstanceCancelReqVO cancelReqVO) {
        processInstanceService.cancelProcessInstanceByStartUser(getCurrentUserId(), cancelReqVO);
        return success(true);
    }

    @DeleteMapping("/cancel-by-admin")
    @Operation(summary = "管理员取消流程实例", description = "管理员撤回流程")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:cancel-by-admin')")
    public CommonResult<Boolean> cancelProcessInstanceByManager(
            @Valid @RequestBody BpmProcessInstanceCancelReqVO cancelReqVO) {
        processInstanceService.cancelProcessInstanceByAdmin(getCurrentUserId(), cancelReqVO);
        return success(true);
    }

    @GetMapping("/get-approval-detail")
    @Operation(summary = "获得审批详情")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    @SuppressWarnings("unchecked")
    public CommonResult<BpmApprovalDetailRespVO> getApprovalDetail(@Valid BpmApprovalDetailReqVO reqVO) {
        if (StrUtil.isNotEmpty(reqVO.getProcessVariablesStr())) {
            reqVO.setProcessVariables(JsonUtils.parseObject(reqVO.getProcessVariablesStr(), Map.class));
        }
        BpmApprovalDetailRespVO response = processInstanceService.getApprovalDetail(getCurrentUserId(), reqVO);
        enrichPortalUsers(response);
        return success(response);
    }

    @GetMapping("/get-next-approval-nodes")
    @Operation(summary = "获取下一个执行的流程节点")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    @SuppressWarnings("unchecked")
    public CommonResult<List<BpmApprovalDetailRespVO.ActivityNode>> getNextApprovalNodes(@Valid BpmApprovalDetailReqVO reqVO) {
        if (StrUtil.isNotEmpty(reqVO.getProcessVariablesStr())) {
            reqVO.setProcessVariables(JsonUtils.parseObject(reqVO.getProcessVariablesStr(), Map.class));
        }
        List<BpmApprovalDetailRespVO.ActivityNode> response = processInstanceService.getNextApprovalNodes(
                getCurrentUserId(), reqVO);
        enrichPortalCandidateUsers(response);
        return success(response);
    }

    @GetMapping("/get-bpmn-model-view")
    @Operation(summary = "获取流程实例的 BPMN 模型视图", description = "在【流程详细】界面中，进行调用")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<BpmProcessInstanceBpmnModelViewRespVO> getProcessInstanceBpmnModelView(
            @RequestParam(value = "id") String id) {
        BpmProcessInstanceBpmnModelViewRespVO response = processInstanceService.getProcessInstanceBpmnModelView(id);
        enrichPortalUsers(response);
        return success(response);
    }

    /**
     * Flowable 持久化 Portal 原始用户 ID；展示时再由 Portal 组织目录投影显示信息。
     */
    private void enrichPortalUsers(BpmApprovalDetailRespVO response) {
        if (response == null) {
            return;
        }
        Set<String> userIds = new java.util.LinkedHashSet<>();
        if (response.getActivityNodes() != null) {
            response.getActivityNodes().forEach(node -> {
                if (node.getTasks() != null) {
                    node.getTasks().forEach(task -> {
                        addPortalUserId(userIds, task.getAssignee());
                        addPortalUserId(userIds, task.getOwner());
                    });
                }
            });
        }
        if (response.getTodoTask() != null) {
            addPortalUserId(userIds, response.getTodoTask().getAssignee());
            addPortalUserId(userIds, response.getTodoTask().getOwner());
        }
        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalOrganizationApi.getUserMap(userIds);
        if (response.getActivityNodes() != null) {
            response.getActivityNodes().forEach(node -> {
                if (node.getTasks() != null) {
                    node.getTasks().forEach(task -> enrichPortalUser(task, userMap));
                }
            });
        }
        enrichPortalUser(response.getTodoTask(), userMap);
    }

    private void enrichPortalUsers(List<BpmProcessInstanceRespVO> processInstances) {
        if (CollUtil.isEmpty(processInstances)) {
            return;
        }
        Set<String> userIds = new java.util.LinkedHashSet<>();
        processInstances.forEach(processInstance -> {
            addPortalUserId(userIds, processInstance.getStartUserId());
            if (processInstance.getTasks() != null) {
                processInstance.getTasks().forEach(task -> addPortalUserId(userIds, task.getAssignee()));
            }
        });
        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalOrganizationApi.getUserMap(userIds);
        processInstances.forEach(processInstance -> enrichPortalUser(processInstance, userMap));
    }

    private void enrichPortalUsers(BpmProcessInstanceRespVO processInstance) {
        if (processInstance == null) {
            return;
        }
        Set<String> userIds = new java.util.LinkedHashSet<>();
        addPortalUserId(userIds, processInstance.getStartUserId());
        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalOrganizationApi.getUserMap(userIds);
        enrichPortalUser(processInstance, userMap);
    }

    private void enrichPortalCandidateUsers(List<BpmApprovalDetailRespVO.ActivityNode> activityNodes) {
        if (CollUtil.isEmpty(activityNodes)) {
            return;
        }
        Set<String> userIds = new java.util.LinkedHashSet<>();
        activityNodes.forEach(node -> {
            if (node.getCandidateUserIds() != null) {
                node.getCandidateUserIds().forEach(userId -> addPortalUserId(userIds, userId));
            }
        });
        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalOrganizationApi.getUserMap(userIds);
        activityNodes.forEach(node -> {
            if (node.getCandidateUserIds() != null) {
                node.setCandidateUsers(node.getCandidateUserIds().stream().map(String::valueOf)
                        .map(userId -> buildPortalUser(userId, userMap)).filter(java.util.Objects::nonNull).toList());
            }
        });
    }

    private void enrichPortalUsers(BpmProcessInstanceBpmnModelViewRespVO response) {
        if (response == null) {
            return;
        }
        Set<String> userIds = new java.util.LinkedHashSet<>();
        if (response.getProcessInstance() != null) {
            addPortalUserId(userIds, response.getProcessInstance().getStartUserId());
        }
        if (response.getTasks() != null) {
            response.getTasks().forEach(task -> {
                addPortalUserId(userIds, task.getAssignee());
                addPortalUserId(userIds, task.getOwner());
            });
        }
        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalOrganizationApi.getUserMap(userIds);
        if (response.getProcessInstance() != null) {
            enrichPortalUser(response.getProcessInstance(), userMap);
        }
        if (response.getTasks() != null) {
            response.getTasks().forEach(task -> enrichPortalUser(task, userMap));
        }
    }

    private static void addPortalUserId(Set<String> userIds, String userId) {
        if (StrUtil.isNotBlank(userId)) {
            userIds.add(userId);
        }
    }

    private static void enrichPortalUser(BpmApprovalDetailRespVO.ActivityNodeTask task,
                                         Map<String, BpmPortalOrganizationApi.PortalUser> userMap) {
        if (task == null) {
            return;
        }
        task.setAssigneeUser(buildPortalUser(task.getAssignee(), userMap));
        task.setOwnerUser(buildPortalUser(task.getOwner(), userMap));
    }

    private static void enrichPortalUser(BpmTaskRespVO task,
                                         Map<String, BpmPortalOrganizationApi.PortalUser> userMap) {
        if (task == null) {
            return;
        }
        task.setAssigneeUser(buildPortalUser(task.getAssignee(), userMap));
        task.setOwnerUser(buildPortalUser(task.getOwner(), userMap));
    }

    private static void enrichPortalUser(BpmProcessInstanceRespVO processInstance,
                                         Map<String, BpmPortalOrganizationApi.PortalUser> userMap) {
        processInstance.setStartUser(buildPortalUser(processInstance.getStartUserId(), userMap));
        if (processInstance.getTasks() != null) {
            processInstance.getTasks().forEach(task -> {
                task.setAssigneeUser(buildPortalUser(task.getAssignee(), userMap));
            });
        }
    }

    private static UserSimpleBaseVO buildPortalUser(String userId,
                                                     Map<String, BpmPortalOrganizationApi.PortalUser> userMap) {
        BpmPortalOrganizationApi.PortalUser user = userMap.get(userId);
        return user == null ? null : new UserSimpleBaseVO().setId(user.getId()).setNickname(user.getDisplayName())
                .setAvatar(user.getAvatar()).setDeptName(user.getDepartmentName());
    }

    @GetMapping("/get-print-data")
    @Operation(summary = "获得流程实例的打印数据")
    @Parameter(name = "id", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:process-instance:query')")
    public CommonResult<BpmProcessPrintDataRespVO> getProcessInstancePrintData(
            @RequestParam("processInstanceId") String processInstanceId) {
        HistoricProcessInstance historicProcessInstance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (historicProcessInstance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        List<HistoricTaskInstance> tasks = taskService.getFinishedTaskListByProcessInstanceIdWithoutCancel(processInstanceId);
        Set<String> userIds = convertSet(tasks, HistoricTaskInstance::getAssignee);
        userIds.add(historicProcessInstance.getStartUserId());
        Map<String, BpmPortalOrganizationApi.PortalUser> portalUserMap = portalOrganizationApi.getUserMap(userIds);
        Map<String, UserSimpleBaseVO> userMap = new java.util.LinkedHashMap<>();
        portalUserMap.forEach((userId, user) -> userMap.put(userId, portalUserProjection.buildUser(userId, portalUserMap)));
        return success(BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePrintData(historicProcessInstance,
                processDefinitionService.getProcessDefinitionInfo(historicProcessInstance.getProcessDefinitionId()),
                tasks, userMap,
                userMap.get(historicProcessInstance.getStartUserId())));
    }

}
