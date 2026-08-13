package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 发起人自选 {@link BpmTaskCandidateStrategy} 实现类
 *
 * @author 芋道源码
 */
@Component
public class BpmTaskCandidateStartUserSelectStrategy implements BpmTaskCandidateStrategy {

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
    public LinkedHashSet<String> calculateAssigneeIdsByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        Assert.notNull(processInstance, "流程实例({})不能为空", execution.getProcessInstanceId());
        Map<String, List<String>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processInstance);
        Assert.notNull(startUserSelectAssignees, "流程实例({}) 的发起人自选审批人不能为空",
                execution.getProcessInstanceId());
        // 获得审批人
        return toAssigneeIdSet(startUserSelectAssignees.get(execution.getCurrentActivityId()));
    }

    @Override
    public LinkedHashSet<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                                 String startUserId, String processDefinitionId,
                                                                 Map<String, Object> processVariables) {
        if (processVariables == null) {
            return new LinkedHashSet<>();
        }
        Map<String, List<String>> startUserSelectAssignees = FlowableUtils.getStartUserSelectAssignees(processVariables);
        if (startUserSelectAssignees == null) {
            return new LinkedHashSet<>();
        }
        return toAssigneeIdSet(startUserSelectAssignees.get(activityId));
    }

    private LinkedHashSet<String> toAssigneeIdSet(List<?> assignees) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (assignees != null) {
            assignees.stream().filter(Objects::nonNull).map(String::valueOf).forEach(result::add);
        }
        return result;
    }

}
