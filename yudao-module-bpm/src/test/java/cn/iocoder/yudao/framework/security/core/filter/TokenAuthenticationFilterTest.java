package cn.iocoder.yudao.framework.security.core.filter;

import cn.hutool.core.codec.Base64;
import cn.hutool.json.JSONObject;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class TokenAuthenticationFilterTest extends BaseMockitoUnitTest {

    @Test
    public void testParseLoginUserFromJwt_successWithUuidAndRole() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(null, null, null);

        // 构造 JWT 载荷 (Payload)
        JSONObject payload = new JSONObject();
        payload.set("userId", "b943f25d-4064-4f5f-8b8f-70437e4d6fd3");
        payload.set("role", "ROLE_ADMIN");
        payload.set("deptId", 100);

        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String jwtToken = header + "." + payloadBase64 + ".fake_signature";

        LoginUser loginUser = filter.parseLoginUserFromJwt(jwtToken, 1);
        assertNotNull(loginUser);
        assertEquals("b943f25d-4064-4f5f-8b8f-70437e4d6fd3", loginUser.getInfo().get("portalUserId"));
        assertEquals("ROLE_ADMIN", loginUser.getInfo().get("role"));
        assertEquals("100", loginUser.getInfo().get("deptId"));
    }

    @Test
    public void testParseLoginUserFromJwt_successWithNumericUserId() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(null, null, null);

        // 构造 JWT 载荷 (Payload)
        JSONObject payload = new JSONObject();
        payload.set("sub", "102");
        payload.set("roles", "ROLE_MANAGER");

        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String jwtToken = header + "." + payloadBase64 + ".fake_signature";

        LoginUser loginUser = filter.parseLoginUserFromJwt(jwtToken, 1);
        assertNotNull(loginUser);
        assertEquals(102L, loginUser.getId());
        assertEquals("102", loginUser.getInfo().get("portalUserId"));
        assertEquals("ROLE_MANAGER", loginUser.getInfo().get("role"));
    }

}
