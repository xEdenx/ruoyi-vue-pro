package cn.iocoder.yudao.framework.common.biz.portal.logging;

import lombok.Data;

import java.time.LocalDateTime;

/** API 异常审计事件。 */
@Data
public class PortalApiErrorLogEvent {

    private String traceId;
    private String userId;
    private Integer userType;
    private String applicationName;
    private String requestMethod;
    private String requestUrl;
    private String requestParams;
    private String userIp;
    private String userAgent;
    private LocalDateTime exceptionTime;
    private String exceptionName;
    private String exceptionClassName;
    private String exceptionFileName;
    private String exceptionMethodName;
    private Integer exceptionLineNumber;
    private String exceptionStackTrace;
    private String exceptionRootCauseMessage;
    private String exceptionMessage;

}
