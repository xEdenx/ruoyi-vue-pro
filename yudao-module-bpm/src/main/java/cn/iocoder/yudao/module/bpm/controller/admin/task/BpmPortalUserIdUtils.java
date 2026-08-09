package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

/**
 * 当前 Portal 用户的流程身份。
 *
 * Portal 的原始主体 ID 直接作为 Flowable assignee，不做数值转换。
 */
public class BpmPortalUserIdUtils {

    private BpmPortalUserIdUtils() {
    }

    public static String getCurrentUserId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return null;
        }
        return StrUtil.isBlank(loginUser.getId()) ? null : loginUser.getId();
    }

}
