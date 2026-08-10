package cn.iocoder.yudao.framework.common.biz.portal.logging;

import lombok.Data;

import java.time.LocalDateTime;

/** API 访问审计事件。 */
@Data
public class PortalApiAccessLogEvent {

    private String traceId;
    private String userId;
    private Integer userType;
    private String applicationName;
    private String requestMethod;
    private String requestUrl;
    private String requestParams;
    private String responseBody;
    private String userIp;
    private String userAgent;
    private String operateModule;
    private String operateName;
    private Integer operateType;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private Integer duration;
    private Integer resultCode;
    private String resultMsg;

}
