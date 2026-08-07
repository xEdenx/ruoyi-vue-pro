package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.headless;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 纯无头 Headless 远程候选人解算策略 {@link BpmTaskCandidateStrategy}
 * 核心机制：BPM 平台底层不存储任何用户、部门、角色数据，亦无需维护 Portal 到 BPM 的 Role Mapping。
 * 当流程到达该节点时，将上下文 (startUserId, activityId, roleParam) 透明回调给 Portal SPI API，
 * 由 Portal 端自由解析并返回目标审批人的 userId 集合 Set<String>。
 *
 * @author Antigravity
 */
@Component
public class HeadlessRemoteCandidateStrategy implements BpmTaskCandidateStrategy {

    private final PortalCandidateApi portalCandidateApi;
    private final BpmProcessInstanceService processInstanceService;

    public HeadlessRemoteCandidateStrategy(Optional<PortalCandidateApi> portalCandidateApi,
                                           BpmProcessInstanceService processInstanceService) {
        this.portalCandidateApi = portalCandidateApi.orElse(null);
        this.processInstanceService = processInstanceService;
    }

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.HEADLESS_REMOTE;
    }

    @Override
    public void validateParam(String param) {
        // param 为 Portal 系统的原生角色标识/Code，透明接收，无需校验 BPM 本地角色
    }

    @Override
    public Set<String> calculateAssigneeIdsByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        String startUserId = processInstance != null ? processInstance.getStartUserId() : null;
        String activityId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();

        if (portalCandidateApi != null) {
            Set<String> assigneeIds = portalCandidateApi.resolveAssigneeIds(startUserId, activityId, param, processInstanceId);
            if (assigneeIds != null) {
                return assigneeIds;
            }
        }
        return Collections.emptySet();
    }

    @Override
    public Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        if (portalCandidateApi != null) {
            Set<String> assigneeIds = portalCandidateApi.resolveAssigneeIds(String.valueOf(startUserId), activityId, param, null);
            if (assigneeIds != null) {
                return assigneeIds;
            }
        }
        return Collections.emptySet();
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        return Collections.emptySet();
    }

    /**
     * Portal 远程解算候选人的 SPI 接口定义
     */
    public interface PortalCandidateApi {
        /**
         * 解算候选人 ID 集合
         *
         * @param startUserId 流程发起人 ID
         * @param activityId 当前流程节点 XML ID
         * @param roleParam Portal 原生角色 Code / 参数
         * @param processInstanceId 流程实例 ID
         * @return 匹配目标审批人的 User ID 集合
         */
        Set<String> resolveAssigneeIds(String startUserId, String activityId, String roleParam, String processInstanceId);
    }

}
