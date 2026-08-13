package cn.iocoder.yudao.module.bpm.framework.portal;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Headless BPM 的前端固定配置端口。
 *
 * <p>当前实现直接投影 BPM 自己的枚举；Portal 将来需要维护这些配置时，只替换该接口的实现，
 * 不需要让前端重新接入 system 或 infra 的字典接口。</p>
 */
public interface BpmPortalConfigurationApi {

    List<PortalDictionaryItem> listDictionaryItems();

    /**
     * 返回 Portal 维护的地区树；本地 walkthrough 可以为空。
     */
    List<PortalArea> listAreaTree();

    @Getter
    @AllArgsConstructor
    class PortalDictionaryItem {

        private final String dictType;
        private final Object value;
        private final String label;
        private final String colorType;
        private final String cssClass;
    }

    @Getter
    @AllArgsConstructor
    class PortalArea {

        private final String id;
        private final String name;
        private final List<PortalArea> children;
    }

}
