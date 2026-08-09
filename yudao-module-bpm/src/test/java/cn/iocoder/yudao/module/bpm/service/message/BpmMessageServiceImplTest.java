package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verifyNoInteractions;

class BpmMessageServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmMessageServiceImpl messageService;

    @Mock
    private SmsSendApi smsSendApi;

    @Test
    void shouldNotCallLocalSmsForHeadlessProcessApproval() {
        ReflectionTestUtils.setField(messageService, "headlessEnabled", true);

        messageService.sendMessageWhenProcessInstanceApprove(new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                .setProcessInstanceId("process-001").setProcessInstanceName("无头测试流程").setStartUserId(101L));

        verifyNoInteractions(smsSendApi);
    }

}
