package cn.iocoder.yudao.module.bpm.service.message.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/**
 * BPM 发送任务被分配 Request DTO
 */
@Data
public class BpmMessageSendWhenTaskCreatedReqDTO {

    /**
     * 流程实例的编号
     */
    @NotEmpty(message = "流程实例的编号不能为空")
    private String processInstanceId;
    /**
     * 流程实例的名字
     */
    @NotEmpty(message = "流程实例的名字不能为空")
    private String processInstanceName;
    @NotBlank(message = "发起人的用户编号不能为空")
    private String startUserId;

    /**
     * 流程任务的编号
     */
    @NotEmpty(message = "流程任务的编号不能为空")
    private String taskId;
    /**
     * 流程任务的名字
     */
    @NotEmpty(message = "流程任务的名字不能为空")
    private String taskName;

    /**
     * 审批人的用户编号
     */
    @NotBlank(message = "审批人的用户编号不能为空")
    private String assigneeUserId;

}
