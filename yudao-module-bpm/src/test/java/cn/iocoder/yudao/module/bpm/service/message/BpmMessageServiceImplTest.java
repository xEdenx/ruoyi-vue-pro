package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalNotificationApi;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

class BpmMessageServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmMessageServiceImpl messageService;

    @Mock
    private BpmPortalNotificationApi portalNotificationApi;

    @Test
    void shouldSendPortalEventForProcessApproval() {
        messageService.sendMessageWhenProcessInstanceApprove(new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                .setProcessInstanceId("process-001").setProcessInstanceName("无头测试流程")
                .setStartUserId("portal-requester-a1f2"));

        ArgumentCaptor<BpmPortalNotificationApi.Notification> notificationCaptor = ArgumentCaptor.forClass(
                BpmPortalNotificationApi.Notification.class);
        verify(portalNotificationApi).notify(notificationCaptor.capture());
        BpmPortalNotificationApi.Notification notification = notificationCaptor.getValue();
        assertEquals("bpm_process_instance_approve", notification.getType());
        assertEquals("portal-requester-a1f2", notification.getRecipientUserId());
        assertEquals("process-001", notification.getProcessInstanceId());
    }

}
