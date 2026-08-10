package cn.iocoder.yudao.framework.common.biz.portal.tenant;

import java.util.List;

/**
 * Portal 提供的租户边界。
 *
 * <p>数据库租户列当前仍使用 Long，因此该接口保留 Long 租户编号；用户、组织等 Portal 身份仍使用 String。</p>
 */
public interface PortalTenantApi {

    /**
     * 获得可执行租户任务的租户编号。
     */
    List<Long> getTenantIdList();

    /**
     * 校验当前请求租户是否合法。
     */
    void validateTenant(Long id);

}
