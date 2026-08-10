package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.headless;

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
                                           @org.springframework.context.annotation.Lazy BpmProcessInstanceService processInstanceService) {
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

        return resolveAssigneeIds(startUserId, activityId, param, processInstanceId);
    }

    @Override
    public Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        return calculateAssigneeIdsByActivity(bpmnModel, activityId, param, String.valueOf(startUserId),
                processDefinitionId, processVariables);
    }

    @Override
    public Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        String startUserId, String processDefinitionId,
                                                        Map<String, Object> processVariables) {
        return resolveAssigneeIds(startUserId, activityId, param, null);
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        return Collections.emptySet();
    }

    /**
     * 远程候选人是无头模式下的唯一权威来源。未接入 Portal 或 Portal 无法给出有效候选人时，
     * 必须终止本次计算，不能回退到本地组织架构策略后产生错误待办。
     */
    private Set<String> resolveAssigneeIds(String startUserId, String activityId, String param,
                                            String processInstanceId) {
        if (portalCandidateApi == null) {
            throw new IllegalStateException("未配置 PortalCandidateApi，无法解析 HEADLESS_REMOTE 候选人");
        }
        Set<String> assigneeIds = portalCandidateApi.resolveAssigneeIds(startUserId, activityId, param,
                processInstanceId);
        if (CollUtil.isEmpty(assigneeIds) || assigneeIds.stream().anyMatch(StrUtil::isBlank)) {
            throw new IllegalStateException("Portal 未返回有效的 HEADLESS_REMOTE 候选人");
        }
        return new LinkedHashSet<>(assigneeIds);
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
