package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user.BpmTaskCandidateUserStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 发起人自选 {@link BpmTaskCandidateUserStrategy} 实现类
 *
 * @author 芋道源码
 */
@Component
public class BpmTaskCandidateStartUserSelectStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private BpmProcessInstanceService processInstanceService;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_SELECT;
    }

    @Override
    public void validateParam(String param) {}

    @Override
    public boolean isParamRequired() {
        return false;
    }

    @Override
    public LinkedHashSet<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        Assert.notNull(processInstance, "流程实例({})不能为空", execution.getProcessInstanceId());
        Map<String, List<Object>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processInstance);
        Assert.notNull(startUserSelectAssignees, "流程实例({}) 的发起人自选审批人不能为空",
                execution.getProcessInstanceId());
        // 获得审批人
        List<Object> assignees = startUserSelectAssignees.get(execution.getCurrentActivityId());
        return convertAssigneesToLongSet(assignees);
    }

    @Override
    public LinkedHashSet<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        if (processVariables == null) {
            return Sets.newLinkedHashSet();
        }
        Map<String, List<Object>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processVariables);
        if (startUserSelectAssignees == null) {
            return Sets.newLinkedHashSet();
        }
        // 获得审批人
        List<Object> assignees = startUserSelectAssignees.get(activityId);
        return convertAssigneesToLongSet(assignees);
    }

    private LinkedHashSet<Long> convertAssigneesToLongSet(List<Object> assignees) {
        if (CollUtil.isEmpty(assignees)) {
            return Sets.newLinkedHashSet();
        }
        LinkedHashSet<Long> result = Sets.newLinkedHashSet();
        for (Object assignee : assignees) {
            if (assignee instanceof Number) {
                result.add(((Number) assignee).longValue());
            } else if (assignee != null) {
                try {
                    result.add(Long.parseLong(assignee.toString()));
                } catch (NumberFormatException e) {
                    result.add((long) Math.abs(assignee.toString().hashCode()));
                }
            }
        }
        return result;
    }

}
