package cn.iocoder.yudao.framework.security.core.filter;

import cn.hutool.core.util.StrUtil;
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
        // 仅本地 Mock 允许读取未签名开发 token；生产 Portal 必须在网关或替换适配器中先完成验签。
        return parseLoginUserFromJwt(token, userType);
    }

    /**
     * 从 Bearer JWT Token 中直接解包 Payload，提取 userId 和 role 声明构建 LoginUser。
     *
     * @param token Bearer Token 文本 (Format: Header.Payload.Signature)
     * @param userType 用户类型
     * @return 解析成功返回 LoginUser，否则返回 null
     */
    LoginUser parseLoginUserFromJwt(String token, Integer userType) {
        if (!Boolean.TRUE.equals(securityProperties.getMockEnable()) || StrUtil.isEmpty(token)) {
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
            if (!Boolean.TRUE.equals(payload.getBool("headlessMock"))) {
                return null;
            }

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
            return new LoginUser()
                    .setId(userIdStr)
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
