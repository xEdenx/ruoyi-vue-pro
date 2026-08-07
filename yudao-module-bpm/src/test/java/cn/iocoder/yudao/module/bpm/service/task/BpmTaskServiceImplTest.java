package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class BpmTaskServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskServiceImpl taskServiceImpl;

    @Mock
    private TaskService taskService;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private Task task;

    @Test
    public void testValidateTask_withPortalStringId() {
        String taskId = "task-001";
        String portalUserId = "b943f25d-4064-4f5f-8b8f-70437e4d6fd3";
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
        when(taskQuery.includeTaskLocalVariables()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getAssignee()).thenReturn(portalUserId);

        assertSame(task, taskServiceImpl.validateTask(portalUserId, taskId));
        assertThrows(RuntimeException.class, () -> taskServiceImpl.validateTask("another-portal-user", taskId));
    }

}
