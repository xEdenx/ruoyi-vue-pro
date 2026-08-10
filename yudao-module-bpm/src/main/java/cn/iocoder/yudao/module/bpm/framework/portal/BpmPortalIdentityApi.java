package cn.iocoder.yudao.module.bpm.framework.portal;

import java.util.Collection;
import java.util.Set;

/**
 * Portal 是 BPM 人员与角色信息的唯一来源。
 *
 * BPM 仅通过这个端口读取展示信息和管理授权依据，不访问 system_user、system_role 或 system_dept。
 */
public interface BpmPortalIdentityApi {

    BpmPortalOrganizationApi.PortalUser getUser(String userId);

    default boolean hasAnyRole(String userId, Collection<String> roleCodes) {
        BpmPortalOrganizationApi.PortalUser user = getUser(userId);
        return user != null && user.getRoleCodes().stream().anyMatch(roleCodes::contains);
    }
}
