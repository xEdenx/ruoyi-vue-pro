package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * BPM 任务的候选人的策略接口
 * <p>
 * 例如说：分配审批人
 *
 * @author 芋道源码
 */
public interface BpmTaskCandidateStrategy {

    /**
     * 对应策略
     *
     * @return 策略
     */
    BpmTaskCandidateStrategyEnum getStrategy();

    /**
     * 校验参数
     *
     * @param param 参数
     */
    void validateParam(String param);

    /**
     * 是否一定要输入参数
     *
     * @return 是否
     */
    default boolean isParamRequired() {
        return true;
    }

    /**
     * 基于候选人参数，获得任务的候选用户们
     *
     * 注意：实现 calculateUsers 系列方法时，有两种选择：
     * 1. 只重写 calculateUsers 默认方法
     * 2. 都重写 calculateUsersByTask 和 calculateUsersByActivity 两个方法
     *
     * @param param 执行任务
     * @return 用户编号集合
     */
    default Set<Long> calculateUsers(String param) {
        throw new UnsupportedOperationException("该分配方法未实现，请检查！");
    }

    /**
     * 基于【执行任务】，获得任务的候选用户们
     *
     * @param execution 执行任务
     * @return 用户编号集合
     */
    default Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        return calculateUsers(param);
    }

    /**
     * 基于【流程活动】，获得任务的候选用户们
     * <p>
     * 目的：用于获取未执行节点的候选用户们
     *
     * @param bpmnModel 流程图
     * @param activityId 活动 ID (对应 Bpmn XML id)
     * @param param     节点的参数
     * @param startUserId  流程发起人编号
     * @param processDefinitionId 流程定义编号
     * @param processVariables 流程变量
     * @return 用户编号集合
     */
    @SuppressWarnings("unused")
    default Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                               Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        return calculateUsers(param);
    }

    /**
     * 计算写入 Flowable 任务的处理人 ID。
     *
     * 内置策略仍基于本地 Long 用户；Portal 动态选人可覆写此方法以保留外部 String ID。
     */
    default Set<String> calculateAssigneeIdsByTask(DelegateExecution execution, String param) {
        Set<String> result = new LinkedHashSet<>();
        calculateUsersByTask(execution, param).forEach(userId -> result.add(String.valueOf(userId)));
        return result;
    }

    /**
     * 计算审批详情中展示的处理人 ID。
     */
    default Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        Long startUserId, String processDefinitionId,
                                                        Map<String, Object> processVariables) {
        Set<String> result = new LinkedHashSet<>();
        calculateUsersByActivity(bpmnModel, activityId, param, startUserId, processDefinitionId, processVariables)
                .forEach(userId -> result.add(String.valueOf(userId)));
        return result;
    }

    /**
     * 使用 Portal 原始发起人 ID 预测候选人。旧本地策略仍要求数值 ID，默认仅在可转换时调用旧实现。
     */
    default Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                        String startUserId, String processDefinitionId,
                                                        Map<String, Object> processVariables) {
        return calculateAssigneeIdsByActivity(bpmnModel, activityId, param, NumberUtils.parseLong(startUserId),
                processDefinitionId, processVariables);
    }

}
