package cn.iocoder.yudao.module.bpm.framework.portal;

import cn.iocoder.yudao.framework.common.biz.portal.tenant.PortalTenantApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 本地 walkthrough 的 Portal 租户 mock。
 *
 * <p>生产环境必须由 Portal 的已验证租户边界实现 {@link PortalTenantApi}，不能注册本类。</p>
 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class LocalBpmPortalTenantApiMock implements PortalTenantApi {

    @Value("${yudao.bpm.headless-mock.tenant-id:1}")
    private Long tenantId;

    @Override
    public List<Long> getTenantIdList() {
        return List.of(tenantId);
    }

    @Override
    public void validateTenant(Long id) {
        if (!tenantId.equals(id)) {
            throw exception(FORBIDDEN);
        }
    }

}
