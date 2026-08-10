package cn.iocoder.yudao.module.bpm.controller.admin.base.user;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 将 Flowable 保存的原始用户 ID 投影为 Portal 用户展示信息。
 *
 * 不在 BPM 复制用户、部门数据；Portal 不可用或用户不存在时，调用方应继续返回原始 ID，展示对象保持为空。
 */
@Component
public class BpmPortalUserProjection {

    @Resource
    private BpmPortalOrganizationApi portalOrganizationApi;

    public Map<String, BpmPortalOrganizationApi.PortalUser> getUserMap(Collection<String> userIds) {
        return portalOrganizationApi.getUserMap(userIds);
    }

    public UserSimpleBaseVO buildUser(String userId,
                                      Map<String, BpmPortalOrganizationApi.PortalUser> userMap) {
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        BpmPortalOrganizationApi.PortalUser user = userMap.get(userId);
        if (user == null) {
            return null;
        }
        return new UserSimpleBaseVO().setId(user.getId()).setNickname(user.getDisplayName()).setAvatar(user.getAvatar())
                .setDeptId(user.getDepartmentId()).setDeptName(user.getDepartmentName());
    }
}
