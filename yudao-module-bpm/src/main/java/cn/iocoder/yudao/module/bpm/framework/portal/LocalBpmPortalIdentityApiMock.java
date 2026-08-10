package cn.iocoder.yudao.module.bpm.framework.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 本地 walkthrough 的 Portal 身份与角色 mock。 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class LocalBpmPortalIdentityApiMock implements BpmPortalIdentityApi, BpmPortalOrganizationApi {

    private static final Map<String, BpmPortalOrganizationApi.PortalUser> USERS = Map.of(
            "portal-requester-a1f2", user("portal-requester-a1f2", "申请人", "portal-dept-general", "通用部门", Set.of("ROLE_USER")),
            "portal-manager-b3c4", user("portal-manager-b3c4", "部门经理", "portal-dept-general", "通用部门", Set.of("ROLE_MANAGER", "ROLE_BPM_MODEL_MANAGER")),
            "portal-admin-d5e6", user("portal-admin-d5e6", "行政管理员", "portal-dept-admin", "行政部", Set.of("ROLE_ADMIN", "ROLE_BPM_MODEL_MANAGER")),
            "portal-supplier-e7f8", user("portal-supplier-e7f8", "供应商成员A", "portal-dept-supplier", "供应商部", Set.of("ROLE_SUPPLIER")),
            "portal-supplier-f9a0", user("portal-supplier-f9a0", "供应商成员B", "portal-dept-supplier", "供应商部", Set.of("ROLE_SUPPLIER"))
    );

    @Override
    public BpmPortalOrganizationApi.PortalUser getUser(String userId) {
        return USERS.get(userId);
    }

    @Override
    public Set<String> resolveUserIds(String selectorType, Collection<String> selectorValues, String startUserId,
                                      String processInstanceId) {
        Set<String> selectorSet = new LinkedHashSet<>(selectorValues);
        return USERS.values().stream()
                .filter(BpmPortalOrganizationApi.PortalUser::active)
                .filter(user -> switch (selectorType) {
                    case "ROLE" -> user.roleCodes().stream().anyMatch(selectorSet::contains);
                    case "POST" -> user.postCodes().stream().anyMatch(selectorSet::contains);
                    case "DEPT" -> selectorSet.contains(user.departmentId());
                    case "USER" -> selectorSet.contains(user.id());
                    default -> false;
                })
                .map(BpmPortalOrganizationApi.PortalUser::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static BpmPortalOrganizationApi.PortalUser user(String id, String displayName, String departmentId,
                                                              String departmentName,
                                                              Set<String> roleCodes) {
        return new BpmPortalOrganizationApi.PortalUser(id, displayName, null, departmentId, departmentName,
                true, roleCodes, Set.of());
    }
}
