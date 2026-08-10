package cn.iocoder.yudao.module.bpm.framework.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 未接入 Portal 时，管理操作必须失败关闭，禁止回退到本地 system 用户和角色表。
 */
@Component
@ConditionalOnProperty(prefix = "yudao.bpm.headless-mock", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnMissingBean(BpmPortalIdentityApi.class)
public class MissingBpmPortalIdentityApi implements BpmPortalIdentityApi {

    @Override
    public BpmPortalOrganizationApi.PortalUser getUser(String userId) {
        throw new IllegalStateException("未配置 Portal 身份适配器，无法校验 BPM 管理角色");
    }
}
