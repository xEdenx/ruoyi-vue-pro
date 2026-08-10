package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.BpmPortalUserProjection;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.convert.task.BpmTaskConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmAttachmentTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Attachment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.bpm.controller.admin.task.BpmPortalUserIdUtils.getCurrentUserId;

@Tag(name = "管理后台 - 流程任务实例")
@RestController
@RequestMapping("/bpm/task")
@Validated
public class BpmTaskController {

    @Resource
    private BpmTaskService taskService;
    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmFormService formService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    @Resource
    private BpmPortalUserProjection portalUserProjection;

    @GetMapping("todo-page")
    @Operation(summary = "获取 Todo 待办任务分页")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<PageResult<BpmTaskRespVO>> getTaskTodoPage(@Valid BpmTaskPageReqVO pageVO) {
        PageResult<Task> pageResult = taskService.getTaskTodoPage(getCurrentUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 拼接数据
        Map<String, ProcessInstance> processInstanceMap = processInstanceService.getProcessInstanceMap(
                convertSet(pageResult.getList(), Task::getProcessInstanceId));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), Task::getProcessDefinitionId));
        PageResult<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTodoTaskPage(pageResult, processInstanceMap,
                Map.of(), processDefinitionInfoMap);
        projectTaskUsers(result.getList(), convertMap(processInstanceMap.values(),
                org.flowable.engine.runtime.ProcessInstance::getId, org.flowable.engine.runtime.ProcessInstance::getStartUserId));
        return success(result);
    }

    @GetMapping("done-page")
    @Operation(summary = "获取 Done 已办任务分页")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<PageResult<BpmTaskRespVO>> getTaskDonePage(@Valid BpmTaskPageReqVO pageVO) {
        PageResult<HistoricTaskInstance> pageResult = taskService.getTaskDonePage(getCurrentUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 拼接数据
        Map<String, HistoricProcessInstance> processInstanceMap = processInstanceService.getHistoricProcessInstanceMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessInstanceId));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessDefinitionId));
        Map<String, List<Attachment>> taskAttachmentMap = getTaskAttachmentMap(pageResult.getList());
        PageResult<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTaskPage(pageResult, processInstanceMap, Map.of(), Map.of(),
                processDefinitionInfoMap, taskAttachmentMap);
        projectTaskUsers(result.getList(), convertMap(processInstanceMap.values(),
                HistoricProcessInstance::getId, HistoricProcessInstance::getStartUserId));
        return success(result);
    }

    @GetMapping("manager-page")
    @Operation(summary = "获取全部任务的分页", description = "用于【流程任务】菜单")
    @PreAuthorize("@ss.hasPermission('bpm:task:manager-query')")
    public CommonResult<PageResult<BpmTaskRespVO>> getTaskManagerPage(@Valid BpmTaskPageReqVO pageVO) {
        PageResult<HistoricTaskInstance> pageResult = taskService.getTaskPage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 拼接数据
        Map<String, HistoricProcessInstance> processInstanceMap = processInstanceService.getHistoricProcessInstanceMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessInstanceId));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessDefinitionId));
        Map<String, List<Attachment>> taskAttachmentMap = getTaskAttachmentMap(pageResult.getList());
        PageResult<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTaskPage(pageResult, processInstanceMap, Map.of(), Map.of(),
                processDefinitionInfoMap, taskAttachmentMap);
        projectTaskUsers(result.getList(), convertMap(processInstanceMap.values(),
                HistoricProcessInstance::getId, HistoricProcessInstance::getStartUserId));
        return success(result);
    }

    @GetMapping("/list-by-process-instance-id")
    @Operation(summary = "获得指定流程实例的任务列表", description = "包括完成的、未完成的")
    @Parameter(name = "processInstanceId", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<List<BpmTaskRespVO>> getTaskListByProcessInstanceId(
            @RequestParam("processInstanceId") String processInstanceId) {
        List<HistoricTaskInstance> taskList = taskService.getTaskListByProcessInstanceId(processInstanceId, true);
        if (CollUtil.isEmpty(taskList)) {
            return success(Collections.emptyList());
        }

        // 拼接数据
        // 获得 Form Map
        Map<Long, BpmFormDO> formMap = formService.getFormMap(
                convertSet(taskList, task -> NumberUtils.parseLong(task.getFormKey())));
        Map<String, List<Attachment>> taskAttachmentMap = getTaskAttachmentMap(taskList);
        List<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTaskListByProcessInstanceId(taskList,
                formMap, Map.of(), Map.of(), taskAttachmentMap);
        projectTaskUsers(result, Map.of());
        return success(result);
    }

    @PutMapping("/approve")
    @Operation(summary = "通过任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> approveTask(@Valid @RequestBody BpmTaskApproveReqVO reqVO) {
        taskService.approveTask(getCurrentUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "不通过任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> rejectTask(@Valid @RequestBody BpmTaskRejectReqVO reqVO) {
        taskService.rejectTask(getCurrentUserId(), reqVO);
        return success(true);
    }

    @GetMapping("/list-by-return")
    @Operation(summary = "获取所有可退回的节点", description = "用于【流程详情】的【退回】按钮")
    @Parameter(name = "taskId", description = "当前任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<List<BpmTaskRespVO>> getTaskListByReturn(@RequestParam("id") String id) {
        List<UserTask> userTaskList = taskService.getUserTaskListByReturn(id);
        return success(convertList(userTaskList, userTask -> // 只返回 id 和 name
                new BpmTaskRespVO().setName(userTask.getName()).setTaskDefinitionKey(userTask.getId())));
    }

    @PutMapping("/return")
    @Operation(summary = "退回任务", description = "用于【流程详情】的【退回】按钮")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> returnTask(@Valid @RequestBody BpmTaskReturnReqVO reqVO) {
        taskService.returnTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/delegate")
    @Operation(summary = "委派任务", description = "用于【流程详情】的【委派】按钮")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> delegateTask(@Valid @RequestBody BpmTaskDelegateReqVO reqVO) {
        taskService.delegateTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/transfer")
    @Operation(summary = "转派任务", description = "用于【流程详情】的【转派】按钮")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> transferTask(@Valid @RequestBody BpmTaskTransferReqVO reqVO) {
        taskService.transferTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/create-sign")
    @Operation(summary = "加签", description = "before 前加签，after 后加签")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> createSignTask(@Valid @RequestBody BpmTaskSignCreateReqVO reqVO) {
        taskService.createSignTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete-sign")
    @Operation(summary = "减签")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> deleteSignTask(@Valid @RequestBody BpmTaskSignDeleteReqVO reqVO) {
        taskService.deleteSignTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/copy")
    @Operation(summary = "抄送任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> copyTask(@Valid @RequestBody BpmTaskCopyReqVO reqVO) {
        taskService.copyTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> withdrawTask(@RequestParam("taskId") String taskId) {
        taskService.withdrawTask(getLoginUserId(), taskId);
        return success(true);
    }

    @GetMapping("/list-by-parent-task-id")
    @Operation(summary = "获得指定父级任务的子任务列表") // 目前用于，减签的时候，获得子任务列表
    @Parameter(name = "parentTaskId", description = "父级任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<List<BpmTaskRespVO>> getTaskListByParentTaskId(@RequestParam("parentTaskId") String parentTaskId) {
        List<Task> taskList = taskService.getTaskListByParentTaskId(parentTaskId);
        if (CollUtil.isEmpty(taskList)) {
            return success(Collections.emptyList());
        }
        // 拼接数据
        List<BpmTaskRespVO> result = BpmTaskConvert.INSTANCE.buildTaskListByParentTaskId(taskList, Map.of(), Map.of());
        projectTaskUsers(result, Map.of());
        return success(result);
    }

    /**
     * 获得任务附件 Map
     *
     * @param taskList 历史任务列表
     * @return 任务附件 Map，key 为任务编号
     */
    private Map<String, List<Attachment>> getTaskAttachmentMap(List<HistoricTaskInstance> taskList) {
        // 1.1 获得流程实例的任务 Map
        Map<String, List<HistoricTaskInstance>> processTaskMap = convertMultiMap(taskList,
                HistoricTaskInstance::getProcessInstanceId);
        // 1.2 获得任务附件列表
        List<Attachment> attachments = new ArrayList<>();
        processTaskMap.forEach((processInstanceId, tasks) -> attachments.addAll(taskService.getAttachments(
                processInstanceId, convertSet(tasks, HistoricTaskInstance::getId), BpmAttachmentTypeEnum.TASK_ATTACHMENT)));
        // 2. 返回 Map
        return convertMultiMap(attachments, Attachment::getTaskId);
    }

    private void projectTaskUsers(List<BpmTaskRespVO> tasks, Map<String, String> processStartUserIds) {
        Set<String> userIds = new LinkedHashSet<>();
        processStartUserIds.values().stream().filter(StrUtil::isNotBlank).forEach(userIds::add);
        tasks.forEach(task -> collectTaskUserIds(task, userIds));
        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalUserProjection.getUserMap(userIds);
        tasks.forEach(task -> fillTaskUsers(task, userMap, processStartUserIds));
    }

    private void collectTaskUserIds(BpmTaskRespVO task, Set<String> userIds) {
        if (task == null) {
            return;
        }
        if (StrUtil.isNotBlank(task.getAssignee())) {
            userIds.add(task.getAssignee());
        }
        if (StrUtil.isNotBlank(task.getOwner())) {
            userIds.add(task.getOwner());
        }
        if (task.getChildren() != null) {
            task.getChildren().forEach(child -> collectTaskUserIds(child, userIds));
        }
    }

    private void fillTaskUsers(BpmTaskRespVO task, Map<String, BpmPortalOrganizationApi.PortalUser> userMap,
                               Map<String, String> processStartUserIds) {
        if (task == null) {
            return;
        }
        if (task.getAssigneeUser() == null) {
            task.setAssigneeUser(portalUserProjection.buildUser(task.getAssignee(), userMap));
        }
        if (task.getOwnerUser() == null) {
            task.setOwnerUser(portalUserProjection.buildUser(task.getOwner(), userMap));
        }
        if (task.getProcessInstance() != null && task.getProcessInstance().getStartUser() == null) {
            task.getProcessInstance().setStartUser(portalUserProjection.buildUser(
                    processStartUserIds.get(task.getProcessInstanceId()), userMap));
        }
        if (task.getChildren() != null) {
            task.getChildren().forEach(child -> fillTaskUsers(child, userMap, processStartUserIds));
        }
    }

}
