package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.headless;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 本地演练用的 Portal 候选人解算器。
 *
 * 仅在 yudao.bpm.headless-mock.enabled=true 时注册，真实 Portal 接入时关闭该配置，
 * 再提供 {@link HeadlessRemoteCandidateStrategy.PortalCandidateApi} 的 HTTP 适配器即可。
 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "true")
public class LocalPortalCandidateApiMock implements HeadlessRemoteCandidateStrategy.PortalCandidateApi {

    private static final Map<ResolutionKey, Set<String>> CANDIDATES = Map.of(
            new ResolutionKey("Activity_Admin", "ROLE_ADMIN"), Set.of("portal-admin-d5e6"),
            new ResolutionKey("Activity_Supplier", "ROLE_SUPPLIER"), Set.of("portal-supplier-e7f8", "portal-supplier-f9a0")
    );

    @Override
    public Set<String> resolveAssigneeIds(String startUserId, String activityId, String roleParam,
                                          String processInstanceId) {
        Set<String> assigneeIds = CANDIDATES.get(new ResolutionKey(activityId, roleParam));
        if (assigneeIds == null) {
            throw new IllegalArgumentException("本地 Portal mock 未配置候选人: activityId=" + activityId
                    + ", roleParam=" + roleParam);
        }
        return assigneeIds;
    }

    private record ResolutionKey(String activityId, String roleParam) {
    }

}
