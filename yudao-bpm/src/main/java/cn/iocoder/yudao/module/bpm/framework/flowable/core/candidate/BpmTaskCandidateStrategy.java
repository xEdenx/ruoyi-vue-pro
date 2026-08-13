package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;

import java.util.Map;
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
     * 计算写入 Flowable 任务的处理人 ID。ID 必须保持 Portal 原始字符串。
     */
    Set<String> calculateAssigneeIdsByTask(DelegateExecution execution, String param);

    /**
     * 计算审批详情中展示的处理人 ID。发起人 ID 必须保持 Portal 原始字符串。
     */
    Set<String> calculateAssigneeIdsByActivity(BpmnModel bpmnModel, String activityId, String param,
                                                String startUserId, String processDefinitionId,
                                                Map<String, Object> processVariables);

}
