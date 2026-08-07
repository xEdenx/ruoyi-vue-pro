package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

import static cn.iocoder.yudao.framework.common.util.string.StrUtils.splitToLongSet;

/**
 * 发起人部门的角色成员 {@link BpmTaskCandidateStrategy} 实现类
 * 延迟绑定/动态解算：当流程运行推进到该任务节点时，实时解算【发起人当前所属部门】下拥有【指定角色】的用户集合。
 * 完美解决预先硬编码指定人导致后续离职/调岗无人审批的问题。
 *
 * @author Antigravity
 */
@Component
public class BpmTaskCandidateStartUserDeptRoleStrategy extends AbstractBpmTaskCandidateDeptLeaderStrategy {

    @Resource
    @Lazy
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private RoleApi roleApi;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_DEPT_ROLE;
    }

    @Override
    public void validateParam(String param) {
        Set<Long> roleIds = splitToLongSet(param);
        roleApi.validRoleList(roleIds);
    }

    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        if (processInstance == null) {
            return Collections.emptySet();
        }
        Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());
        return getStartUserDeptRoleUsers(startUserId, param);
    }

    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId, Map<String, Object> processVariables) {
        return getStartUserDeptRoleUsers(startUserId, param);
    }

    private Set<Long> getStartUserDeptRoleUsers(Long startUserId, String roleParam) {
        if (startUserId == null) {
            return Collections.emptySet();
        }
        // 1. 获取发起人的当前部门
        DeptRespDTO startUserDept = super.getStartUserDept(startUserId);
        if (startUserDept == null) {
            return Collections.emptySet();
        }
        Long deptId = startUserDept.getId();

        // 2. 获取具有指定角色的所有用户编号
        Set<Long> roleIds = splitToLongSet(roleParam);
        Set<Long> roleUserIds = permissionApi.getUserRoleIdListByRoleIds(roleIds);
        if (CollUtil.isEmpty(roleUserIds)) {
            return Collections.emptySet();
        }

        // 3. 过滤出属于发起人同一部门的用户
        Map<Long, AdminUserRespDTO> roleUsersMap = adminUserApi.getUserMap(roleUserIds);
        Set<Long> result = new HashSet<>();
        roleUsersMap.forEach((userId, user) -> {
            if (user != null && Objects.equals(user.getDeptId(), deptId)) {
                result.add(userId);
            }
        });
        return result;
    }

}
