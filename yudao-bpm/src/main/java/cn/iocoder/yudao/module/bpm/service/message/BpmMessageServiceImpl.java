package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.module.bpm.enums.message.BpmMessageEnum;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalNotificationApi;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * BPM 消息 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class BpmMessageServiceImpl implements BpmMessageService {

    @Resource
    private BpmPortalNotificationApi portalNotificationApi;

    @Override
    public void sendMessageWhenProcessInstanceApprove(BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO) {
        portalNotificationApi.notify(new BpmPortalNotificationApi.Notification(
                BpmMessageEnum.PROCESS_INSTANCE_APPROVE.getEventType(), reqDTO.getStartUserId(),
                reqDTO.getProcessInstanceId(), null, Map.of(
                        "processInstanceName", reqDTO.getProcessInstanceName())));
    }

    @Override
    public void sendMessageWhenProcessInstanceReject(BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO) {
        portalNotificationApi.notify(new BpmPortalNotificationApi.Notification(
                BpmMessageEnum.PROCESS_INSTANCE_REJECT.getEventType(), reqDTO.getStartUserId(),
                reqDTO.getProcessInstanceId(), null, Map.of(
                        "processInstanceName", reqDTO.getProcessInstanceName(), "reason", reqDTO.getReason())));
    }

    @Override
    public void sendMessageWhenTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO) {
        portalNotificationApi.notify(new BpmPortalNotificationApi.Notification(
                BpmMessageEnum.TASK_ASSIGNED.getEventType(), reqDTO.getAssigneeUserId(),
                reqDTO.getProcessInstanceId(), reqDTO.getTaskId(), Map.of(
                        "processInstanceName", reqDTO.getProcessInstanceName(), "taskName", reqDTO.getTaskName(),
                        "startUserId", reqDTO.getStartUserId())));
    }

    @Override
    public void sendMessageWhenTaskTimeout(BpmMessageSendWhenTaskTimeoutReqDTO reqDTO) {
        portalNotificationApi.notify(new BpmPortalNotificationApi.Notification(
                BpmMessageEnum.TASK_TIMEOUT.getEventType(), reqDTO.getAssigneeUserId(),
                reqDTO.getProcessInstanceId(), reqDTO.getTaskId(), Map.of(
                        "processInstanceName", reqDTO.getProcessInstanceName(), "taskName", reqDTO.getTaskName())));
    }

}
