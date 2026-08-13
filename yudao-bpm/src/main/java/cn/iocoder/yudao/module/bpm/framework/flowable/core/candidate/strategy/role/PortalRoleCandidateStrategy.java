package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.role;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Portal 角色候选人策略 {@link BpmTaskCandidateStrategy}。
 * BPM 不保存角色数据；任务到达时由 Portal 根据发起人、节点上下文和目标角色编码解算最终用户 ID。
 *
 * @author Antigravity
 */
@Component
public class PortalRoleCandidateStrategy implements BpmTaskCandidateStrategy {

    private final PortalRoleCandidateApi portalRoleCandidateApi;
    private final BpmProcessInstanceService processInstanceService;

    public PortalRoleCandidateStrategy(Optional<PortalRoleCandidateApi> portalRoleCandidateApi,
                                       @org.springframework.context.annotation.Lazy BpmProcessInstanceService processInstanceService) {
        this.portalRoleCandidateApi = portalRoleCandidateApi.orElse(null);
        this.processInstanceService = processInstanceService;
    }

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.ROLE;
    }

    @Override
    public void validateParam(String param) {
        if (StrUtil.isBlank(param)) {
            throw new IllegalArgumentException("Portal 角色编码不能为空");
        }
    }

    @Override
    public Set<String> calculateAssigneeIdsByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        String startUserId = processInstance != null ? processInstance.getStartUserId() : null;
        String activityId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();

        return resolveRoleAssigneeIds(startUserId, activityId, param, processInstanceId);
    }

    @Override
    public Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        String startUserId, String processDefinitionId,
                                                        Map<String, Object> processVariables) {
        return resolveRoleAssigneeIds(startUserId, activityId, param, null);
    }

    /**
     * Portal 是角色候选人的唯一权威来源。未接入 Portal 或未返回有效候选人时必须失败关闭，
     * 不得回退到 BPM 本地角色、用户或部门表。
     */
    private Set<String> resolveRoleAssigneeIds(String startUserId, String activityId, String roleCode,
                                               String processInstanceId) {
        if (portalRoleCandidateApi == null) {
            throw new IllegalStateException("未配置 PortalRoleCandidateApi，无法解析 Portal 角色候选人");
        }
        Set<String> assigneeIds = portalRoleCandidateApi.resolveRoleAssigneeIds(startUserId, activityId, roleCode,
                processInstanceId);
        if (CollUtil.isEmpty(assigneeIds) || assigneeIds.stream().anyMatch(StrUtil::isBlank)) {
            throw new IllegalStateException("Portal 未返回有效的角色候选人");
        }
        return new LinkedHashSet<>(assigneeIds);
    }

    /**
     * Portal 角色候选人解算端口。
     */
    public interface PortalRoleCandidateApi {
        /**
         * 按目标角色解算候选人 ID 集合。
         *
         * @param startUserId 流程发起人 ID
         * @param activityId 当前流程节点 XML ID
         * @param roleCode Portal 目标角色编码
         * @param processInstanceId 流程实例 ID
         * @return 匹配目标审批人的 User ID 集合
         */
        Set<String> resolveRoleAssigneeIds(String startUserId, String activityId, String roleCode,
                                           String processInstanceId);
    }

}
