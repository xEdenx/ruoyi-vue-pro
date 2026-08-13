package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.BpmPortalUserProjection;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.cc.BpmProcessInstanceCopyRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCopyPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalOrganizationApi;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalPrincipalUtils.getCurrentUserId;

@Tag(name = "管理后台 - 流程实例抄送")
@RestController
@RequestMapping("/bpm/process-instance/copy")
@Validated
public class BpmProcessInstanceCopyController {

    @Resource
    private BpmProcessInstanceCopyService processInstanceCopyService;
    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    @Resource
    private BpmPortalOrganizationApi portalOrganizationApi;
    @Resource
    private BpmPortalUserProjection portalUserProjection;

    @GetMapping("/page")
    @Operation(summary = "获得抄送流程分页列表")
    @PreAuthorize("@ss.hasPermission('bpm:process-instance-cc:query')")
    public CommonResult<PageResult<BpmProcessInstanceCopyRespVO>> getProcessInstanceCopyPage(
            @Valid BpmProcessInstanceCopyPageReqVO pageReqVO) {
        PageResult<BpmProcessInstanceCopyDO> pageResult = processInstanceCopyService.getProcessInstanceCopyPage(
                getCurrentUserId(), pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(new PageResult<>(pageResult.getTotal()));
        }

        // 拼接返回
        Map<String, HistoricProcessInstance> processInstanceMap = processInstanceService.getHistoricProcessInstanceMap(
                convertSet(pageResult.getList(), BpmProcessInstanceCopyDO::getProcessInstanceId));

        Map<String, BpmPortalOrganizationApi.PortalUser> userMap = portalOrganizationApi.getUserMap(
                convertSetByFlatMap(pageResult.getList(), copy -> java.util.List.of(copy.getStartUserId(), copy.getUserId()),
                        java.util.Collection::stream));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), BpmProcessInstanceCopyDO::getProcessDefinitionId));
        return success(convertPage(pageResult, copy -> {
            BpmProcessInstanceCopyRespVO copyVO = BeanUtils.toBean(copy, BpmProcessInstanceCopyRespVO.class);
            MapUtils.findAndThen(userMap, copy.getUserId(),
                    user -> copyVO.setCreateUser(portalUserProjection.buildUser(user.getId(), userMap)));
            MapUtils.findAndThen(userMap, copy.getStartUserId(),
                    user -> copyVO.setStartUser(portalUserProjection.buildUser(user.getId(), userMap)));
            MapUtils.findAndThen(processInstanceMap, copyVO.getProcessInstanceId(),
                    processInstance -> {
                        copyVO.setSummary(FlowableUtils.getSummary(
                                processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                                processInstance.getProcessVariables()));
                        copyVO.setProcessInstanceStartTime(DateUtils.of(processInstance.getStartTime()));
                    });
            return copyVO;
        }));
    }
}
