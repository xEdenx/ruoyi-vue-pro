package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.role;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 本地演练用的 Portal 角色候选人解算器。
 *
 * 仅在 yudao.bpm.headless-mock.enabled=true 时注册，真实 Portal 接入时关闭该配置，
 * 再提供 {@link PortalRoleCandidateStrategy.PortalRoleCandidateApi} 的 HTTP 适配器即可。
 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class LocalPortalRoleCandidateApiMock implements PortalRoleCandidateStrategy.PortalRoleCandidateApi {

    private static final Map<ResolutionKey, Set<String>> CANDIDATES = Map.of(
            new ResolutionKey("Activity_Admin", "ROLE_ADMIN"), Set.of("portal-admin-d5e6"),
            new ResolutionKey("Activity_BusinessOwner", "ROLE_BUSINESS_OWNER"), Set.of("portal-business-owner-g1h2"),
            new ResolutionKey("Activity_RiskAllSign", "ROLE_RISK"), Set.of("portal-risk-k4m5", "portal-risk-m5n6"),
            new ResolutionKey("Activity_RiskAnySign", "ROLE_RISK"), Set.of("portal-risk-k4m5", "portal-risk-m5n6"),
            new ResolutionKey("Activity_FinalArchive", "ROLE_ADMIN"), Set.of("portal-admin-d5e6"),
            new ResolutionKey("Activity_Supplier", "ROLE_SUPPLIER"), Set.of("portal-supplier-e7f8", "portal-supplier-f9a0")
    );

    @Override
    public Set<String> resolveRoleAssigneeIds(String startUserId, String activityId, String roleCode,
                                              String processInstanceId) {
        Set<String> assigneeIds = CANDIDATES.get(new ResolutionKey(activityId, roleCode));
        if (assigneeIds == null) {
            throw new IllegalArgumentException("本地 Portal mock 未配置候选人: activityId=" + activityId
                    + ", roleCode=" + roleCode);
        }
        return assigneeIds;
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    private static class ResolutionKey {

        private final String activityId;
        private final String roleCode;
    }

}
