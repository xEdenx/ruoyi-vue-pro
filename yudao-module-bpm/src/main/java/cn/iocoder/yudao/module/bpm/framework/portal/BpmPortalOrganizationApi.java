package cn.iocoder.yudao.module.bpm.framework.portal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Portal 组织目录端口。
 *
 * BPM 通过该端口读取用户、部门以及角色、岗位、部门关系的解算结果；实现可以是本地 mock 或 Portal HTTP 客户端，
 * 但 BPM 业务代码不得再直接访问 system 的用户、角色、部门或岗位 API。
 */
public interface BpmPortalOrganizationApi {

    PortalUser getUser(String userId);

    default Map<String, PortalUser> getUserMap(Collection<String> userIds) {
        Map<String, PortalUser> result = new LinkedHashMap<>();
        if (userIds == null) {
            return result;
        }
        userIds.forEach(userId -> {
            PortalUser user = getUser(userId);
            if (user != null) {
                result.put(userId, user);
            }
        });
        return result;
    }

    /**
     * 按 Portal 原生组织条件解算最终用户。角色、岗位和部门的参数语义由 Portal 维护，BPM 只接收用户 ID。
     */
    Set<String> resolveUserIds(String selectorType, Collection<String> selectorValues, String startUserId,
                               String processInstanceId);

    /**
     * 判断用户是否可参与 BPM；禁用或不存在的用户不能被创建为任务 assignee。
     */
    default boolean isUserActive(String userId) {
        PortalUser user = getUser(userId);
        return user != null && user.active();
    }

    record PortalUser(String id, String displayName, String avatar, String departmentId, String departmentName, boolean active,
                      Set<String> roleCodes, Set<String> postCodes) {
    }
}
