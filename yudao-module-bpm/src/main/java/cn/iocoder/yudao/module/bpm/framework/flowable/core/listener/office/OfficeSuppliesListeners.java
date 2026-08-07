package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.office;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 办公用品申请流程 - 节点执行监听器汇总文件
 * 使用 PREFIX 常量，直观进行 Bean 名称前缀拼接
 */
public class OfficeSuppliesListeners {

    /** 流程监听器 Bean 命名空间前缀 */
    public static final String PREFIX = "officeSuppliesListeners_";

    /**
     * 部门经理审批节点监听器
     * 流程图监听器表达式填：${officeSuppliesListeners_deptManagerApproval}
     */
    @Component(PREFIX + "deptManagerApproval")
    @Slf4j
    public static class DeptManagerApproval implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            log.info("==================== [BPM 节点执行代码: 部门经理审批] ====================");
            log.info("流程实例 ID: {}", execution.getProcessInstanceId());
            log.info("当前节点 ID: {}", execution.getCurrentActivityId());
            log.info("当前流程传入全量参数: {}", execution.getVariables());
            log.info("=========================================================================");
        }

    }

    /**
     * 办公室管理员审批节点监听器
     * 流程图监听器表达式填：${officeSuppliesListeners_officeAdminApproval}
     */
    @Component(PREFIX + "officeAdminApproval")
    @Slf4j
    public static class OfficeAdminApproval implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            log.info("==================== [BPM 节点执行代码: 办公室管理员审批] ====================");
            log.info("流程实例 ID: {}", execution.getProcessInstanceId());
            log.info("当前节点 ID: {}", execution.getCurrentActivityId());
            log.info("当前流程传入全量参数: {}", execution.getVariables());
            log.info("=============================================================================");
        }

    }

    /**
     * 供应商收单与派送节点监听器
     * 流程图监听器表达式填：${officeSuppliesListeners_supplierApproval}
     */
    @Component(PREFIX + "supplierApproval")
    @Slf4j
    public static class SupplierApproval implements JavaDelegate {

        @Override
        public void execute(DelegateExecution execution) {
            log.info("==================== [BPM 节点执行代码: 供应商收单与派送 (多实例)] ====================");
            log.info("流程实例 ID: {}", execution.getProcessInstanceId());
            log.info("当前节点 ID: {}", execution.getCurrentActivityId());
            log.info("多实例会签总数(nrOfInstances): {}", execution.getVariable("nrOfInstances"));
            log.info("已完成会签数(nrOfCompletedInstances): {}", execution.getVariable("nrOfCompletedInstances"));
            log.info("当前子实例索引(loopCounter): {}", execution.getVariable("loopCounter"));
            log.info("当前流程传入全量参数: {}", execution.getVariables());
            log.info("===================================================================================");
        }

    }

}
