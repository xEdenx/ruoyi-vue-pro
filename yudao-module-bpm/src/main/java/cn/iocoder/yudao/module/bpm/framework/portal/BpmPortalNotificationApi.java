package cn.iocoder.yudao.module.bpm.framework.portal;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * Portal 通知投递端口。
 *
 * BPM 负责在流程状态变化时产生通知事件；Portal 负责具体渠道、收件箱和用户展示。
 */
public interface BpmPortalNotificationApi {

    void notify(Notification notification);

    @Getter
    @AllArgsConstructor
    class Notification {

        private final String type;
        private final String recipientUserId;
        private final String processInstanceId;
        private final String taskId;
        private final Map<String, Object> payload;
    }
}
