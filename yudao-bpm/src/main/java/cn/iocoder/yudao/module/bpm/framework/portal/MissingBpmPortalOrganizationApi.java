package cn.iocoder.yudao.module.bpm.framework.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 未接入 Portal 组织目录时禁止回退查询本地 system 表。 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MissingBpmPortalOrganizationApi implements BpmPortalOrganizationApi {

    @Override
    public PortalUser getUser(String userId) {
        throw new IllegalStateException("未配置 Portal 组织目录适配器，无法读取 BPM 用户信息");
    }

    @Override
    public PortalDepartment getDepartment(String departmentId) {
        throw new IllegalStateException("未配置 Portal 组织目录适配器，无法读取 BPM 部门信息");
    }

    @Override
    public List<PortalUser> listSelectableUsers() {
        throw new IllegalStateException("未配置 Portal 组织目录适配器，无法读取 BPM 用户目录");
    }

    @Override
    public List<PortalDepartment> listSelectableDepartments() {
        throw new IllegalStateException("未配置 Portal 组织目录适配器，无法读取 BPM 部门目录");
    }

    @Override
    public Set<String> resolveUserIds(String selectorType, Collection<String> selectorValues, String startUserId,
                                      String processInstanceId) {
        throw new IllegalStateException("未配置 Portal 组织目录适配器，无法解析 BPM 审批人");
    }
}
