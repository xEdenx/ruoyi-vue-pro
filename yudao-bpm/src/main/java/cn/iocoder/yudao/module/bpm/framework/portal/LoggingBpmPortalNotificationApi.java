package cn.iocoder.yudao.module.bpm.framework.portal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Portal 通知端口的默认实现：仅记录待投递事件。
 *
 * 不因通知渠道未接入而阻断或回滚 Flowable 状态变化；生产接入 Portal 后由 HTTP/mTLS 实现替换。
 */
@Slf4j
@Component
public class LoggingBpmPortalNotificationApi implements BpmPortalNotificationApi {

    @Override
    public void notify(Notification notification) {
        log.info("[Portal 通知待投递][type={}][recipient={}][processInstanceId={}][taskId={}]",
                notification.getType(), notification.getRecipientUserId(), notification.getProcessInstanceId(), notification.getTaskId());
    }
}
