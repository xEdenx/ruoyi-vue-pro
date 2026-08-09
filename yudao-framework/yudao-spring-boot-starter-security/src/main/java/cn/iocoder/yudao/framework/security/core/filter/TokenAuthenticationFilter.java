package cn.iocoder.yudao.framework.security.core.filter;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Token 过滤器，验证 token 的有效性
 * 验证通过后，获得 {@link LoginUser} 信息，并加入到 Spring Security 上下文
 *
 * @author 芋道源码
 */
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String PORTAL_JWT_INFO_KEY = "portalJwt";
    public static final String PORTAL_ROLE_INFO_KEY = "role";

    private final SecurityProperties securityProperties;

    private final GlobalExceptionHandler globalExceptionHandler;

    private final OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotEmpty(token)) {
            Integer userType = WebFrameworkUtils.getLoginUserType(request);
            try {
                // 1.1 基于 token 构建登录用户
                LoginUser loginUser = buildLoginUserByToken(token, userType);
                // 1.2 模拟 Login 功能，方便日常开发调试
                if (loginUser == null) {
                    loginUser = mockLoginUser(request, token, userType);
                }

                // 2. 设置当前用户
                if (loginUser != null) {
                    SecurityFrameworkUtils.setLoginUser(loginUser, request);
                }
            } catch (Throwable ex) {
                CommonResult<?> result = globalExceptionHandler.allExceptionHandler(request, ex);
                ServletUtils.writeJSON(response, result);
                return;
            }
        }

        // 继续过滤链
        chain.doFilter(request, response);
    }

    private LoginUser buildLoginUserByToken(String token, Integer userType) {
        // 1. 尝试直接解包 Portal 传入的 Bearer JWT Token (Payload 包含 userId 与 role 声明)
        LoginUser jwtLoginUser = parseLoginUserFromJwt(token, userType);
        if (jwtLoginUser != null) {
            return jwtLoginUser;
        }

        // 2. 尝试基于系统表中的 OAuth2 AccessToken 校验
        try {
            OAuth2AccessTokenCheckRespDTO accessToken = oauth2TokenApi.checkAccessToken(token);
            if (accessToken == null) {
                return null;
            }
            // 用户类型不匹配，无权限
            // 注意：只有 /admin-api/* 和 /app-api/* 有 userType，才需要比对用户类型
            // 类似 WebSocket 的 /ws/* 连接地址，是不需要比对用户类型的
            if (userType != null
                    && ObjectUtil.notEqual(accessToken.getUserType(), userType)) {
                throw new AccessDeniedException("错误的用户类型");
            }
            // 构建登录用户
            return new LoginUser().setId(String.valueOf(accessToken.getUserId()))
                    .setSystemUserId(accessToken.getUserId()).setUserType(accessToken.getUserType())
                    .setInfo(accessToken.getUserInfo()) // 额外的用户信息
                    .setTenantId(accessToken.getTenantId()).setScopes(accessToken.getScopes())
                    .setExpiresTime(accessToken.getExpiresTime());
        } catch (ServiceException serviceException) {
            // 校验 Token 不通过时，考虑到一些接口是无需登录的，所以直接返回 null 即可
            return null;
        }
    }

    /**
     * 从 Bearer JWT Token 中直接解包 Payload，提取 userId 和 role 声明构建 LoginUser。
     *
     * @param token Bearer Token 文本 (Format: Header.Payload.Signature)
     * @param userType 用户类型
     * @return 解析成功返回 LoginUser，否则返回 null
     */
    LoginUser parseLoginUserFromJwt(String token, Integer userType) {
        if (StrUtil.isEmpty(token)) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payloadBytes;
            try {
                payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
            } catch (Throwable e) {
                payloadBytes = cn.hutool.core.codec.Base64.decode(parts[1]);
            }
            String payloadJsonStr = StrUtil.utf8Str(payloadBytes);
            if (!cn.hutool.json.JSONUtil.isTypeJSON(payloadJsonStr)) {
                return null;
            }
            cn.hutool.json.JSONObject payload = cn.hutool.json.JSONUtil.parseObj(payloadJsonStr);

            // 1. 提取 userId 声明（兼容 userId, user_id, sub, id）
            String userIdStr = payload.getStr("userId");
            if (StrUtil.isEmpty(userIdStr)) {
                userIdStr = payload.getStr("user_id");
            }
            if (StrUtil.isEmpty(userIdStr)) {
                userIdStr = payload.getStr("sub");
            }
            if (StrUtil.isEmpty(userIdStr)) {
                userIdStr = payload.getStr("id");
            }
            if (StrUtil.isEmpty(userIdStr)) {
                return null;
            }

            // 2. 提取 role 声明（兼容 role, roles）
            String roleStr = payload.getStr("role");
            if (StrUtil.isEmpty(roleStr)) {
                roleStr = payload.getStr("roles");
            }

            // 3. 构造 LoginUser 的 info 存储结构
            java.util.Map<String, String> info = new java.util.HashMap<>();
            payload.forEach((k, v) -> {
                if (v != null) {
                    info.put(k, String.valueOf(v));
                }
            });
            info.put("portalUserId", userIdStr);
            info.put(PORTAL_JWT_INFO_KEY, Boolean.TRUE.toString());
            if (StrUtil.isNotBlank(roleStr)) {
                info.put(PORTAL_ROLE_INFO_KEY, roleStr);
            }

            Integer resolvedUserType = userType != null ? userType : cn.iocoder.yudao.framework.common.enums.UserTypeEnum.ADMIN.getValue();
            Long tenantId = payload.getLong("tenantId");
            Long systemUserId = null;
            try {
                systemUserId = Long.valueOf(userIdStr);
            } catch (NumberFormatException ignored) {
                // Portal 用户 ID 可以是 UUID 等非数字字符串；此时不映射为本地 system_user ID。
            }
            return new LoginUser()
                    .setId(userIdStr)
                    .setSystemUserId(systemUserId)
                    .setUserType(resolvedUserType)
                    .setInfo(info)
                    .setTenantId(tenantId);
        } catch (Throwable ex) {
            return null;
        }
    }

    /**
     * 模拟登录用户，方便日常开发调试
     *
     * 注意，在线上环境下，一定要关闭该功能！！！
     *
     * @param request 请求
     * @param token 模拟的 token，格式为 {@link SecurityProperties#getMockSecret()} + 用户编号
     * @param userType 用户类型
     * @return 模拟的 LoginUser
     */
    private LoginUser mockLoginUser(HttpServletRequest request, String token, Integer userType) {
        if (!securityProperties.getMockEnable()) {
            return null;
        }
        // 必须以 mockSecret 开头
        if (!token.startsWith(securityProperties.getMockSecret())) {
            return null;
        }
        // 构建模拟用户
        String userId = token.substring(securityProperties.getMockSecret().length());
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        return new LoginUser().setId(userId).setUserType(userType)
                .setInfo(java.util.Map.of("portalUserId", userId))
                .setTenantId(WebFrameworkUtils.getTenantId(request));
    }

}
