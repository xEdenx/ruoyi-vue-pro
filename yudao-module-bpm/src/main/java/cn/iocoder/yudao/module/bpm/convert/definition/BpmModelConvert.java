package cn.iocoder.yudao.module.bpm.convert.definition;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.dept.DeptSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ProcessDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 流程模型 Convert
 *
 * @author yunlongn
 */
@Mapper
public interface BpmModelConvert {

    BpmModelConvert INSTANCE = Mappers.getMapper(BpmModelConvert.class);

    default List<BpmModelRespVO> buildModelList(List<Model> list,
                                                Map<Long, BpmFormDO> formMap,
                                                Map<String, BpmCategoryDO> categoryMap,
                                                Map<String, Deployment> deploymentMap,
                                                Map<String, ProcessDefinition> processDefinitionMap,
                                                Map<String, BpmPortalOrganizationApi.PortalUser> userMap,
                                                Map<String, BpmPortalOrganizationApi.PortalDepartment> deptMap) {
        List<BpmModelRespVO> result = convertList(list, model -> {
            BpmModelMetaInfoVO metaInfo = parseMetaInfo(model);
            BpmFormDO form = metaInfo != null ? formMap.get(metaInfo.getFormId()) : null;
            BpmCategoryDO category = categoryMap.get(model.getCategory());
            Deployment deployment = model.getDeploymentId() != null ? deploymentMap.get(model.getDeploymentId()) : null;
            ProcessDefinition processDefinition = model.getDeploymentId() != null ?
                    processDefinitionMap.get(model.getDeploymentId()) : null;
            List<BpmPortalOrganizationApi.PortalUser> startUsers = metaInfo != null ? convertList(metaInfo.getStartUserIds(), userMap::get) : null;
            List<BpmPortalOrganizationApi.PortalDepartment> startDepts = metaInfo != null ? convertList(metaInfo.getStartDeptIds(), deptMap::get) : null;
            return buildModel0(model, metaInfo, form, category, deployment, processDefinition, startUsers, startDepts);
        });
        // 排序
        result.sort(Comparator.comparing(BpmModelMetaInfoVO::getSort));
        return result;
    }

    default BpmModelRespVO buildModel(Model model, byte[] bpmnBytes, BpmSimpleModelNodeVO simpleModel) {
        BpmModelMetaInfoVO metaInfo = parseMetaInfo(model);
        BpmModelRespVO modelVO = buildModel0(model, metaInfo, null, null, null, null, null, null);
        if (ArrayUtil.isNotEmpty(bpmnBytes)) {
            modelVO.setBpmnXml(BpmnModelUtils.getBpmnXml(bpmnBytes));
        }
        modelVO.setSimpleModel(simpleModel);
        return modelVO;
    }

    default BpmModelRespVO buildModel0(Model model,
                                       BpmModelMetaInfoVO metaInfo, BpmFormDO form, BpmCategoryDO category,
                                       Deployment deployment, ProcessDefinition processDefinition,
                                       List<BpmPortalOrganizationApi.PortalUser> startUsers,
                                       List<BpmPortalOrganizationApi.PortalDepartment> startDepts) {
        BpmModelRespVO modelRespVO = new BpmModelRespVO().setId(model.getId()).setName(model.getName())
                .setKey(model.getKey()).setCategory(model.getCategory())
                .setCreateTime(DateUtils.of(model.getCreateTime()));
        // Form
        BeanUtils.copyProperties(metaInfo, modelRespVO);
        if (form != null) {
            modelRespVO.setFormName(form.getName());
        }
        // Category
        if (category != null) {
            modelRespVO.setCategoryName(category.getName());
        }
        // ProcessDefinition
        if (processDefinition != null) {
            modelRespVO.setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class));
            modelRespVO.getProcessDefinition().setSuspensionState(processDefinition.isSuspended() ?
                    SuspensionState.SUSPENDED.getStateCode() : SuspensionState.ACTIVE.getStateCode());
            if (deployment != null) {
                modelRespVO.getProcessDefinition().setDeploymentTime(DateUtils.of(deployment.getDeploymentTime()));
            }
        }
        // User、Dept
        modelRespVO.setStartUsers(convertList(startUsers, this::buildUser))
                .setStartDepts(convertList(startDepts, this::buildDepartment));
        return modelRespVO;
    }

    default UserSimpleBaseVO buildUser(BpmPortalOrganizationApi.PortalUser user) {
        if (user == null) {
            return null;
        }
        return new UserSimpleBaseVO().setId(user.getId()).setNickname(user.getDisplayName()).setAvatar(user.getAvatar())
                .setDeptId(user.getDepartmentId()).setDeptName(user.getDepartmentName());
    }

    default DeptSimpleBaseVO buildDepartment(BpmPortalOrganizationApi.PortalDepartment department) {
        if (department == null) {
            return null;
        }
        return new DeptSimpleBaseVO().setId(department.getId()).setName(department.getName());
    }

    default void copyToModel(Model model, BpmModelSaveReqVO reqVO) {
        model.setName(reqVO.getName());
        model.setKey(reqVO.getKey());
        model.setCategory(reqVO.getCategory());
        model.setMetaInfo(JsonUtils.toJsonString(BeanUtils.toBean(reqVO, BpmModelMetaInfoVO.class)));
    }

    default BpmModelSaveReqVO convertToSaveReqVO(Model model) {
        BpmModelMetaInfoVO metaInfo = parseMetaInfo(model);
        BpmModelSaveReqVO reqVO = new BpmModelSaveReqVO();
        reqVO.setKey(model.getKey());
        reqVO.setName(model.getName());
        reqVO.setCategory(model.getCategory());
        if (metaInfo != null) {
            BeanUtils.copyProperties(metaInfo, reqVO);
        }
        return reqVO;
    }

    default BpmModelMetaInfoVO parseMetaInfo(Model model) {
        BpmModelMetaInfoVO vo = JsonUtils.parseObject(model.getMetaInfo(), BpmModelMetaInfoVO.class);
        if (vo == null) {
            return null;
        }
        if (vo.getManagerUserIds() == null) {
            vo.setManagerUserIds(Collections.emptyList());
        }
        if (vo.getManagerRoleCodes() == null) {
            vo.setManagerRoleCodes(Collections.emptyList());
        }
        if (vo.getStartUserIds() == null) {
            vo.setStartUserIds(Collections.emptyList());
        }
        // 如果为空，兜底处理，使用 createTime 创建时间
        if (vo.getSort() == null) {
            vo.setSort(model.getCreateTime().getTime());
        }
        return vo;
    }

}
