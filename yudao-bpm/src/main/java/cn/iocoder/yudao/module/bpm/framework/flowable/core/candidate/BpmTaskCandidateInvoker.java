package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskAssignStartUserHandlerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG;

/**
 * {@link BpmTaskCandidateStrategy} 的调用者，用于调用对应的策略，实现任务的候选人的计算
 *
 * @author 示例
 */
@Slf4j
public class BpmTaskCandidateInvoker {

    private final Map<BpmTaskCandidateStrategyEnum, BpmTaskCandidateStrategy> strategyMap = new HashMap<>();

    private final BpmPortalOrganizationApi portalOrganizationApi;

    public BpmTaskCandidateInvoker(List<BpmTaskCandidateStrategy> strategyList,
                                   BpmPortalOrganizationApi portalOrganizationApi) {
        strategyList.forEach(strategy -> {
            BpmTaskCandidateStrategy oldStrategy = strategyMap.put(strategy.getStrategy(), strategy);
            Assert.isNull(oldStrategy, "策略(%s) 重复", strategy.getStrategy());
        });
        this.portalOrganizationApi = portalOrganizationApi;
    }

    /**
     * 校验流程模型的任务分配规则全部都配置了
     * 目的：如果有规则未配置，会导致流程任务找不到负责人，进而流程无法进行下去！
     *
     * @param bpmnBytes BPMN XML
     */
    public void validateBpmnConfig(byte[] bpmnBytes) {
        BpmnModel bpmnModel = BpmnModelUtils.getBpmnModel(bpmnBytes);
        assert bpmnModel != null;
        List<UserTask> userTaskList = BpmnModelUtils.getBpmnModelElements(bpmnModel, UserTask.class);
        // 遍历所有的 UserTask，校验审批人配置
        userTaskList.forEach(userTask -> {
            // 1.1 非人工审批，无需校验审批人配置
            Integer approveType = BpmnModelUtils.parseApproveType(userTask);
            if (ObjectUtils.equalsAny(approveType,
                    BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType(),
                    BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType())) {
                return;
            }
            // 1.2 非空校验
            Integer strategy = BpmnModelUtils.parseCandidateStrategy(userTask);
            String param = BpmnModelUtils.parseCandidateParam(userTask);
            if (strategy == null) {
                throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, userTask.getName());
            }
            BpmTaskCandidateStrategy candidateStrategy = getCandidateStrategy(strategy);
            if (candidateStrategy.isParamRequired() && StrUtil.isBlank(param)) {
                throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, userTask.getName());
            }
            // 2. 具体策略校验
            getCandidateStrategy(strategy).validateParam(param);
        });
    }

    /**
     * 计算写入 Flowable 任务的处理人 ID。
     *
     * Portal 解算后的 ID 由 Flowable 原样保存。任何禁用或不存在用户由 Portal 组织目录过滤。
     */
    public Set<String> calculateAssigneeIdsByTask(DelegateExecution execution) {
        Integer strategy = BpmnModelUtils.parseCandidateStrategy(execution.getCurrentFlowElement());
        BpmTaskCandidateStrategy candidateStrategy = getCandidateStrategy(strategy);
        Set<String> assigneeIds = candidateStrategy.calculateAssigneeIdsByTask(execution,
                BpmnModelUtils.parseCandidateParam(execution.getCurrentFlowElement()));
        removeInactiveAssignees(assigneeIds);
        removeStartUserIfSkip(assigneeIds, execution.getCurrentFlowElement(), getStartUserId(execution));
        return assigneeIds;
    }
    /**
     * 计算审批详情中展示的处理人 ID，保留 Portal 原始发起人 ID。
     */
    public Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId,
                                                       String startUserId, String processDefinitionId,
                                                       Map<String, Object> processVariables) {
        FlowElement flowElement = BpmnModelUtils.getFlowElementById(bpmnModel, activityId);
        if (flowElement instanceof UserTask) {
            BpmTaskCandidateStrategy strategy = getCandidateStrategy(BpmnModelUtils.parseCandidateStrategy(flowElement));
            Set<String> assigneeIds = strategy.calculateAssigneeIdsByActivity(bpmnModel, activityId,
                    BpmnModelUtils.parseCandidateParam(flowElement), startUserId, processDefinitionId, processVariables);
            removeInactiveAssignees(assigneeIds);
            return assigneeIds;
        }
        return new LinkedHashSet<>();
    }

    /**
     * 如果“审批人与发起人相同时”，配置了 SKIP 跳过，则移除发起人
     *
     * 注意：如果只有一个候选人，则不处理，避免无法审批
     *
     * @param assigneeUserIds 当前分配的候选人
     * @param flowElement 当前节点
     * @param startUserId 发起人
     */
    private void removeInactiveAssignees(Set<String> assigneeIds) {
        assigneeIds.removeIf(userId -> StrUtil.isBlank(userId) || !portalOrganizationApi.isUserActive(userId));
    }

    private String getStartUserId(DelegateExecution execution) {
        ProcessInstance processInstance = SpringUtil.getBean(BpmProcessInstanceService.class)
                .getProcessInstance(execution.getProcessInstanceId());
        Assert.notNull(processInstance, "流程实例({}) 不存在", execution.getProcessInstanceId());
        return processInstance.getStartUserId();
    }

    private void removeStartUserIfSkip(Set<String> assigneeIds, FlowElement flowElement, String startUserId) {
        if (CollUtil.size(assigneeIds) <= 1) {
            return;
        }
        Integer assignStartUserHandlerType = BpmnModelUtils.parseAssignStartUserHandlerType(flowElement);
        if (ObjectUtil.notEqual(assignStartUserHandlerType, BpmUserTaskAssignStartUserHandlerTypeEnum.SKIP.getType())) {
            return;
        }
        assigneeIds.remove(startUserId);
    }

    private BpmTaskCandidateStrategy getCandidateStrategy(Integer strategy) {
        BpmTaskCandidateStrategyEnum strategyEnum = BpmTaskCandidateStrategyEnum.valueOf(strategy);
        Assert.notNull(strategyEnum, "策略(%s) 不存在", strategy);
        BpmTaskCandidateStrategy strategyObj = strategyMap.get(strategyEnum);
        Assert.notNull(strategyObj, "策略(%s) 不存在", strategy);
        return strategyObj;
    }

}
