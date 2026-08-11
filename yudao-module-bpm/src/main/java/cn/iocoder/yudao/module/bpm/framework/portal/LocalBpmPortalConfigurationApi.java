package cn.iocoder.yudao.module.bpm.framework.portal;

import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmProcessListenerTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmProcessListenerValueTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BPM 内置枚举的默认投影。
 *
 * <p>它不读取已移除的 system / infra 字典表。对接真实 Portal 配置服务时，
 * 直接以真实实现替换本类。</p>
 */
@Component
public class LocalBpmPortalConfigurationApi implements BpmPortalConfigurationApi {

    @Override
    public List<PortalDictionaryItem> listDictionaryItems() {
        List<PortalDictionaryItem> result = new ArrayList<>();
        addModelTypes(result);
        addModelFormTypes(result);
        addTaskCandidateStrategies(result);
        addProcessInstanceStatuses(result);
        addTaskStatuses(result);
        addCommentTypes(result);
        addProcessListenerTypes(result);
        addProcessListenerValueTypes(result);
        addBpmUiCompatibilityTypes(result);
        return result;
    }

    @Override
    public List<PortalArea> listAreaTree() {
        return List.of();
    }

    private static void addModelTypes(List<PortalDictionaryItem> result) {
        for (BpmModelTypeEnum item : BpmModelTypeEnum.values()) {
            add(result, "bpm_model_type", item.getType(), item.getName());
        }
    }

    private static void addModelFormTypes(List<PortalDictionaryItem> result) {
        for (BpmModelFormTypeEnum item : BpmModelFormTypeEnum.values()) {
            add(result, "bpm_model_form_type", item.getType(), item.getName());
        }
    }

    /**
     * 候选人策略是 BPMN 元数据目录，而不是当前 Spring 已注册策略的投影。
     *
     * <p>建模器据此展示可配置项；具体策略是否可执行由后端发布、运行时的
     * {@code BpmTaskCandidateInvoker} 校验。</p>
     */
    private static void addTaskCandidateStrategies(List<PortalDictionaryItem> result) {
        for (BpmTaskCandidateStrategyEnum item : BpmTaskCandidateStrategyEnum.values()) {
            add(result, "bpm_task_candidate_strategy", item.getStrategy(), item.getDescription());
        }
    }

    private static void addProcessInstanceStatuses(List<PortalDictionaryItem> result) {
        for (BpmProcessInstanceStatusEnum item : BpmProcessInstanceStatusEnum.values()) {
            add(result, "bpm_process_instance_status", item.getStatus(), item.getDesc());
        }
    }

    private static void addTaskStatuses(List<PortalDictionaryItem> result) {
        for (BpmTaskStatusEnum item : BpmTaskStatusEnum.values()) {
            add(result, "bpm_task_status", item.getStatus(), item.getName());
        }
    }

    private static void addCommentTypes(List<PortalDictionaryItem> result) {
        for (BpmCommentTypeEnum item : BpmCommentTypeEnum.values()) {
            add(result, "bpm_comment_type", item.getType(), item.getName());
        }
    }

    private static void addProcessListenerTypes(List<PortalDictionaryItem> result) {
        for (BpmProcessListenerTypeEnum item : BpmProcessListenerTypeEnum.values()) {
            add(result, "bpm_process_listener_type", item.getType(), item.getName());
        }
    }

    private static void addProcessListenerValueTypes(List<PortalDictionaryItem> result) {
        for (BpmProcessListenerValueTypeEnum item : BpmProcessListenerValueTypeEnum.values()) {
            add(result, "bpm_process_listener_value_type", item.getType(), item.getName());
        }
    }

    /**
     * 现有 BPM 管理页仍在使用的通用展示项。它们随 BPM API 返回，仅为兼容 UI，
     * 不代表 system / infra 字典仍被 BPM 依赖。
     */
    private static void addBpmUiCompatibilityTypes(List<PortalDictionaryItem> result) {
        add(result, "common_status", 0, "关闭");
        add(result, "common_status", 1, "开启");
        add(result, "infra_boolean_string", true, "是");
        add(result, "infra_boolean_string", false, "否");
    }

    private static void add(List<PortalDictionaryItem> result, String dictType, Object value, String label) {
        result.add(new PortalDictionaryItem(dictType, value, label, "", ""));
    }

}
