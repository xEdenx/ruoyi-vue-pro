package cn.iocoder.yudao.module.bpm.controller.admin.portal;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalMockLoginReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalMockLoginRespVO;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class BpmPortalAuthControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmPortalAuthController controller;

    @Mock
    private BpmPortalOrganizationApi portalOrganizationApi;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "loginPassword", "portal-local-dev");
        ReflectionTestUtils.setField(controller, "tenantId", 1L);
    }

    @Test
    void shouldLoginKnownActivePortalUser() {
        BpmPortalOrganizationApi.PortalUser user = new BpmPortalOrganizationApi.PortalUser(
                "portal-requester-a1f2", "申请人", null, "portal-dept-general", "通用部门", true,
                Set.of("ROLE_USER"), Set.of());
        when(portalOrganizationApi.getUser(user.id())).thenReturn(user);

        BpmPortalMockLoginReqVO reqVO = new BpmPortalMockLoginReqVO();
        reqVO.setUserId(user.id());
        reqVO.setPassword("portal-local-dev");
        BpmPortalMockLoginRespVO response = controller.login(reqVO).getCheckedData();

        assertEquals(user.id(), response.getUser().getId());
        assertEquals(user.displayName(), response.getUser().getDisplayName());
        assertEquals(1L, response.getTenantId());
        String payload = new String(Base64.getUrlDecoder().decode(response.getAccessToken().split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertEquals(user.id(), JSONUtil.parseObj(payload).getStr("userId"));
        assertTrue(JSONUtil.parseObj(payload).getBool("headlessMock"));
    }

    @Test
    void shouldRejectWrongPasswordWithoutDisclosingUserState() {
        BpmPortalMockLoginReqVO reqVO = new BpmPortalMockLoginReqVO();
        reqVO.setUserId("portal-requester-a1f2");
        reqVO.setPassword("incorrect");

        assertServiceException(() -> controller.login(reqVO), GlobalErrorCodeConstants.UNAUTHORIZED);
    }

}
