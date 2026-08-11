package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModelNodeTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalIdentityApi;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ModelQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link BpmModelServiceImpl} 的单元测试
 *
 * @author 芋道源码
 */
public class BpmModelServiceImplTest extends BaseMockitoUnitTest {

    private static final String TENANT_ID = ProcessEngineConfiguration.NO_TENANT_ID;
    private static final String MODEL_ID = "model-id";

    @InjectMocks
    private BpmModelServiceImpl modelService;

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ModelQuery modelQuery;
    @Mock
    private BpmPortalIdentityApi portalIdentityApi;

    @Test
    public void testExportModel_bpmn() {
        // 准备参数
        Model model = mockModel(BpmModelTypeEnum.BPMN.getType());
        String bpmnXml = "<definitions />";
        // mock 方法（repositoryService）
        mockGetModel(model);
        when(repositoryService.getModelEditorSource(eq(MODEL_ID))).thenReturn(StrUtil.utf8Bytes(bpmnXml));

        // 调用
        BpmModelSaveReqVO result = modelService.exportModel(MODEL_ID);

        // 断言
        assertEquals(model.getKey(), result.getKey());
        assertEquals(model.getName(), result.getName());
        assertEquals(model.getCategory(), result.getCategory());
        assertEquals(BpmModelTypeEnum.BPMN.getType(), result.getType());
        assertEquals(bpmnXml, result.getBpmnXml());
        assertNull(result.getSimpleModel());
    }

    @Test
    public void testExportModel_simple() {
        // 准备参数
        Model model = mockModel(BpmModelTypeEnum.SIMPLE.getType());
        BpmSimpleModelNodeVO simpleModel = new BpmSimpleModelNodeVO();
        simpleModel.setId("start");
        simpleModel.setType(BpmSimpleModelNodeTypeEnum.START_NODE.getType());
        // mock 方法（repositoryService）
        mockGetModel(model);
        when(repositoryService.getModelEditorSourceExtra(eq(MODEL_ID)))
                .thenReturn(JsonUtils.toJsonByte(simpleModel));

        // 调用
        BpmModelSaveReqVO result = modelService.exportModel(MODEL_ID);

        // 断言
        assertEquals(BpmModelTypeEnum.SIMPLE.getType(), result.getType());
        assertNotNull(result.getSimpleModel());
        assertEquals(simpleModel.getId(), result.getSimpleModel().getId());
        assertEquals(simpleModel.getType(), result.getSimpleModel().getType());
        assertNull(result.getBpmnXml());
    }

    @Test
    public void testExportModel_notExists() {
        // mock 方法（repositoryService）
        when(repositoryService.createModelQuery()).thenReturn(modelQuery);
        when(modelQuery.modelId(eq(MODEL_ID))).thenReturn(modelQuery);
        when(modelQuery.modelTenantId(eq(TENANT_ID))).thenReturn(modelQuery);
        when(modelQuery.singleResult()).thenReturn(null);

        // 调用，并断言异常
        assertServiceException(() -> modelService.exportModel(MODEL_ID), MODEL_NOT_EXISTS);
    }

    @Test
    public void testImportModel_bpmn() {
        // 准备参数
        BpmModelSaveReqVO reqVO = new BpmModelSaveReqVO();
        reqVO.setKey("test_process");
        reqVO.setName("测试流程");
        reqVO.setCategory("OA");
        reqVO.setType(BpmModelTypeEnum.BPMN.getType());
        reqVO.setBpmnXml("<definitions />");
        reqVO.setStartUserIds(Arrays.asList("portal-user-10", "portal-user-20"));
        reqVO.setStartDeptIds(Collections.singletonList("portal-dept-30"));
        reqVO.setManagerRoleCodes(Collections.singletonList("ROLE_BPM_MODEL_MANAGER"));
        Model model = mock(Model.class);
        when(model.getId()).thenReturn(MODEL_ID);
        // mock 方法（repositoryService）
        when(repositoryService.createModelQuery()).thenReturn(modelQuery);
        when(modelQuery.modelTenantId(eq(TENANT_ID))).thenReturn(modelQuery);
        when(modelQuery.modelKey(eq(reqVO.getKey()))).thenReturn(modelQuery);
        when(modelQuery.singleResult()).thenReturn(null);
        when(repositoryService.newModel()).thenReturn(model);

        // 调用
        String result = modelService.importModel(reqVO);

        // 断言
        assertEquals(MODEL_ID, result);
        verify(model).setTenantId(TENANT_ID);
        verify(repositoryService).saveModel(same(model));
        verify(repositoryService).addModelEditorSource(eq(MODEL_ID),
                aryEq(StrUtil.utf8Bytes(reqVO.getBpmnXml())));
        ArgumentCaptor<String> metaInfoCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).setMetaInfo(metaInfoCaptor.capture());
        BpmModelMetaInfoVO metaInfo = JsonUtils.parseObject(metaInfoCaptor.getValue(), BpmModelMetaInfoVO.class);
        assertNotNull(metaInfo);
        assertEquals(Arrays.asList("portal-user-10", "portal-user-20"), metaInfo.getStartUserIds());
        assertEquals(Collections.singletonList("portal-dept-30"), metaInfo.getStartDeptIds());
        assertEquals(Collections.singletonList("ROLE_BPM_MODEL_MANAGER"), metaInfo.getManagerRoleCodes());
    }

    @Test
    public void testValidateModelManager_legacyModelCanBeMigratedWithoutLocalUserLookup() {
        Model model = mock(Model.class);
        BpmModelMetaInfoVO metaInfo = new BpmModelMetaInfoVO();
        metaInfo.setType(BpmModelTypeEnum.BPMN.getType());
        when(model.getMetaInfo()).thenReturn(JsonUtils.toJsonString(metaInfo));
        when(model.getCreateTime()).thenReturn(new Date(1_000L));
        mockGetModel(model);

        Model result = ReflectionTestUtils.invokeMethod(modelService, "validateModelManager", MODEL_ID,
                "portal-manager-b3c4");

        assertSame(model, result);
        verifyNoInteractions(portalIdentityApi);
    }

    private Model mockModel(Integer type) {
        Model model = mock(Model.class);
        when(model.getKey()).thenReturn("test_process");
        when(model.getName()).thenReturn("测试流程");
        when(model.getCategory()).thenReturn("OA");
        when(model.getCreateTime()).thenReturn(new Date(1_000L));
        BpmModelMetaInfoVO metaInfo = new BpmModelMetaInfoVO();
        metaInfo.setType(type);
        when(model.getMetaInfo()).thenReturn(JsonUtils.toJsonString(metaInfo));
        return model;
    }

    private void mockGetModel(Model model) {
        when(repositoryService.createModelQuery()).thenReturn(modelQuery);
        when(modelQuery.modelId(eq(MODEL_ID))).thenReturn(modelQuery);
        when(modelQuery.modelTenantId(eq(TENANT_ID))).thenReturn(modelQuery);
        when(modelQuery.singleResult()).thenReturn(model);
    }

}
