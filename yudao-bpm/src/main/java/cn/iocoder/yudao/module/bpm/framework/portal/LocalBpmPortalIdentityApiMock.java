package cn.iocoder.yudao.module.bpm.framework.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 本地 walkthrough 的 Portal 身份与角色 mock。 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class LocalBpmPortalIdentityApiMock implements BpmPortalIdentityApi, BpmPortalOrganizationApi {

    private static final Map<String, BpmPortalOrganizationApi.PortalUser> USERS = Map.ofEntries(
            Map.entry("portal-requester-a1f2", user("portal-requester-a1f2", "申请人", "portal-dept-general", "通用部门", Set.of("ROLE_USER"))),
            Map.entry("portal-manager-b3c4", user("portal-manager-b3c4", "业务负责人", "portal-dept-general", "通用部门", Set.of("ROLE_MANAGER", "ROLE_BPM_MODEL_MANAGER"))),
            Map.entry("portal-admin-d5e6", user("portal-admin-d5e6", "流程管理员", "portal-dept-admin", "行政部", Set.of("ROLE_ADMIN", "ROLE_BPM_MODEL_MANAGER"))),
            Map.entry("portal-business-owner-g1h2", user("portal-business-owner-g1h2", "业务归口负责人", "portal-dept-business", "业务管理部", Set.of("ROLE_BUSINESS_OWNER"))),
            Map.entry("portal-legal-h2j3", user("portal-legal-h2j3", "法务复核人A", "portal-dept-legal", "法务部", Set.of("ROLE_LEGAL"))),
            Map.entry("portal-legal-j3k4", user("portal-legal-j3k4", "法务复核人B", "portal-dept-legal", "法务部", Set.of("ROLE_LEGAL"))),
            Map.entry("portal-risk-k4m5", user("portal-risk-k4m5", "风控委员A", "portal-dept-risk", "风险管理部", Set.of("ROLE_RISK"))),
            Map.entry("portal-risk-m5n6", user("portal-risk-m5n6", "风控委员B", "portal-dept-risk", "风险管理部", Set.of("ROLE_RISK"))),
            Map.entry("portal-executive-n6p7", user("portal-executive-n6p7", "最终授权人", "portal-dept-executive", "管理层", Set.of("ROLE_EXECUTIVE"))),
            Map.entry("portal-supplier-e7f8", user("portal-supplier-e7f8", "供应商成员A", "portal-dept-supplier", "供应商部", Set.of("ROLE_SUPPLIER"))),
            Map.entry("portal-supplier-f9a0", user("portal-supplier-f9a0", "供应商成员B", "portal-dept-supplier", "供应商部", Set.of("ROLE_SUPPLIER")))
    );

    private static final Map<String, String> DEPARTMENT_LEADERS = Map.of(
            "portal-dept-general", "portal-manager-b3c4",
            "portal-dept-admin", "portal-admin-d5e6",
            "portal-dept-supplier", "portal-supplier-e7f8"
    );

    private static final Map<String, BpmPortalOrganizationApi.PortalDepartment> DEPARTMENTS = Map.ofEntries(
            Map.entry("portal-dept-general", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-general", "通用部门")),
            Map.entry("portal-dept-admin", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-admin", "行政部")),
            Map.entry("portal-dept-business", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-business", "业务管理部")),
            Map.entry("portal-dept-legal", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-legal", "法务部")),
            Map.entry("portal-dept-risk", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-risk", "风险管理部")),
            Map.entry("portal-dept-executive", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-executive", "管理层")),
            Map.entry("portal-dept-supplier", new BpmPortalOrganizationApi.PortalDepartment("portal-dept-supplier", "供应商部"))
    );

    @Override
    public BpmPortalOrganizationApi.PortalUser getUser(String userId) {
        return USERS.get(userId);
    }

    @Override
    public BpmPortalOrganizationApi.PortalDepartment getDepartment(String departmentId) {
        return DEPARTMENTS.get(departmentId);
    }

    @Override
    public List<BpmPortalOrganizationApi.PortalUser> listSelectableUsers() {
        return USERS.values().stream()
                .filter(BpmPortalOrganizationApi.PortalUser::isActive)
                .sorted(Comparator.comparing(BpmPortalOrganizationApi.PortalUser::getId))
                .toList();
    }

    @Override
    public List<BpmPortalOrganizationApi.PortalDepartment> listSelectableDepartments() {
        return DEPARTMENTS.values().stream()
                .sorted(Comparator.comparing(BpmPortalOrganizationApi.PortalDepartment::getId))
                .toList();
    }

    @Override
    public Set<String> resolveUserIds(String selectorType, Collection<String> selectorValues, String startUserId,
                                      String processInstanceId) {
        if ("DEPT_LEADER_OF_USER".equals(selectorType)) {
            BpmPortalOrganizationApi.PortalUser startUser = USERS.get(startUserId);
            if (startUser == null) {
                return Set.of();
            }
            String leaderUserId = DEPARTMENT_LEADERS.get(startUser.getDepartmentId());
            return leaderUserId != null ? Set.of(leaderUserId) : Set.of();
        }
        Set<String> selectorSet = new LinkedHashSet<>(selectorValues);
        return USERS.values().stream()
                .filter(BpmPortalOrganizationApi.PortalUser::isActive)
                .filter(user -> switch (selectorType) {
                    case "ROLE" -> user.getRoleCodes().stream().anyMatch(selectorSet::contains);
                    case "POST" -> user.getPostCodes().stream().anyMatch(selectorSet::contains);
                    case "DEPT" -> selectorSet.contains(user.getDepartmentId());
                    case "USER" -> selectorSet.contains(user.getId());
                    default -> false;
                })
                .map(BpmPortalOrganizationApi.PortalUser::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static BpmPortalOrganizationApi.PortalUser user(String id, String displayName, String departmentId,
                                                              String departmentName,
                                                              Set<String> roleCodes) {
        return new BpmPortalOrganizationApi.PortalUser(id, displayName, null, departmentId, departmentName,
                true, roleCodes, Set.of());
    }
}
