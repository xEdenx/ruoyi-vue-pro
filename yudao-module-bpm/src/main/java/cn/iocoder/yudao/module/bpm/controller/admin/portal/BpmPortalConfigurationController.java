package cn.iocoder.yudao.module.bpm.controller.admin.portal;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalAreaRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.portal.vo.BpmPortalDictDataRespVO;
import cn.iocoder.yudao.module.bpm.framework.portal.BpmPortalConfigurationApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Headless BPM 配置")
@RestController
@RequestMapping("/bpm/portal-config")
@Validated
@TenantIgnore
public class BpmPortalConfigurationController {

    @Resource
    private BpmPortalConfigurationApi portalConfigurationApi;

    @GetMapping("/dict-data/simple-list")
    @Operation(summary = "获得 Headless BPM 页面需要的固定枚举")
    public CommonResult<List<BpmPortalDictDataRespVO>> getSimpleDictDataList() {
        List<BpmPortalDictDataRespVO> response = portalConfigurationApi.listDictionaryItems().stream()
                .map(item -> {
                    BpmPortalDictDataRespVO vo = new BpmPortalDictDataRespVO();
                    vo.setDictType(item.getDictType());
                    vo.setValue(item.getValue());
                    vo.setLabel(item.getLabel());
                    vo.setColorType(item.getColorType());
                    vo.setCssClass(item.getCssClass());
                    return vo;
                })
                .toList();
        return success(response);
    }

    @GetMapping("/area-tree")
    @Operation(summary = "获得 Portal 地区树")
    public CommonResult<List<BpmPortalAreaRespVO>> getAreaTree() {
        return success(portalConfigurationApi.listAreaTree().stream()
                .map(BpmPortalConfigurationController::convertArea)
                .toList());
    }

    private static BpmPortalAreaRespVO convertArea(BpmPortalConfigurationApi.PortalArea area) {
        BpmPortalAreaRespVO response = new BpmPortalAreaRespVO();
        response.setId(area.getId());
        response.setName(area.getName());
        response.setChildren(area.getChildren().stream()
                .map(BpmPortalConfigurationController::convertArea)
                .toList());
        return response;
    }

}
