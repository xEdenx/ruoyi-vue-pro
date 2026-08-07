package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

/**
 * 当前 Portal 用户的流程身份。
 *
 * Portal 网关在已签名的 LoginUser.info 中写入 portalUserId 时，BPM 将它作为 Flowable assignee；
 * 未提供时保持管理后台既有的数值用户 ID 行为。
 */
public class BpmPortalUserIdUtils {

    public static final String INFO_KEY_PORTAL_USER_ID = "portalUserId";

    private BpmPortalUserIdUtils() {
    }

    public static String getCurrentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return null;
        }
        String portalUserId = MapUtil.getStr(loginUser.getInfo(), INFO_KEY_PORTAL_USER_ID);
        if (StrUtil.isNotBlank(portalUserId)) {
            return portalUserId;
        }
        return loginUser.getId() != null ? String.valueOf(loginUser.getId()) : null;
    }

}
