package cn.iocoder.yudao.module.bpm.framework.portal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBpmPortalConfigurationApiTest {

    private final LocalBpmPortalConfigurationApi configurationApi = new LocalBpmPortalConfigurationApi();

    @Test
    void listDictionaryItems_projectsBpmOwnedEnums() {
        Map<String, List<BpmPortalConfigurationApi.PortalDictionaryItem>> dictionaryItems = configurationApi
                .listDictionaryItems().stream()
                .collect(Collectors.groupingBy(BpmPortalConfigurationApi.PortalDictionaryItem::getDictType));

        assertEquals("审批中", dictionaryItems.get("bpm_process_instance_status").stream()
                .filter(item -> Integer.valueOf(1).equals(item.getValue()))
                .findFirst().orElseThrow().getLabel());
        assertEquals("审批通过中", dictionaryItems.get("bpm_task_status").stream()
                .filter(item -> Integer.valueOf(7).equals(item.getValue()))
                .findFirst().orElseThrow().getLabel());
        assertTrue(dictionaryItems.containsKey("bpm_model_type"));
        assertTrue(dictionaryItems.containsKey("bpm_comment_type"));
    }

    @Test
    void listAreaTree_returnsEmptyForLocalWalkthrough() {
        assertTrue(configurationApi.listAreaTree().isEmpty());
    }

}
