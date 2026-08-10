package cn.iocoder.yudao.framework.common.biz.portal.logging;

/**
 * 应用日志外部投递端口。
 *
 * <p>实现可以接入 Portal 审计服务或 OTel；默认实现只写应用日志，不依赖 infra 持久化表。</p>
 */
public interface PortalApplicationLogApi {

    void createApiAccessLog(PortalApiAccessLogEvent event);

    void createApiErrorLog(PortalApiErrorLogEvent event);

}
