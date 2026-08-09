package cn.iocoder.yudao.module.bpm.framework.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** 本地 walkthrough 的 Portal 身份与角色 mock。 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class LocalBpmPortalIdentityApiMock implements BpmPortalIdentityApi {

    private static final Map<String, PortalUser> USERS = Map.of(
            "portal-requester-a1f2", new PortalUser("portal-requester-a1f2", "申请人", "portal-dept-general", Set.of("ROLE_USER")),
            "portal-manager-b3c4", new PortalUser("portal-manager-b3c4", "部门经理", "portal-dept-general", Set.of("ROLE_MANAGER", "ROLE_BPM_MODEL_MANAGER")),
            "portal-admin-d5e6", new PortalUser("portal-admin-d5e6", "行政管理员", "portal-dept-admin", Set.of("ROLE_ADMIN", "ROLE_BPM_MODEL_MANAGER")),
            "portal-supplier-e7f8", new PortalUser("portal-supplier-e7f8", "供应商成员A", "portal-dept-supplier", Set.of("ROLE_SUPPLIER")),
            "portal-supplier-f9a0", new PortalUser("portal-supplier-f9a0", "供应商成员B", "portal-dept-supplier", Set.of("ROLE_SUPPLIER"))
    );

    @Override
    public PortalUser getUser(String userId) {
        return USERS.get(userId);
    }
}
