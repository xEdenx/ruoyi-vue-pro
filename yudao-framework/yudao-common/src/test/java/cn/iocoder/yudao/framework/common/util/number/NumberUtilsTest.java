package cn.iocoder.yudao.framework.common.util.number;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link NumberUtils} 的单元测试
 */
public class NumberUtilsTest {

    @Test
    public void testParseLong() {
        assertEquals(1024L, NumberUtils.parseLong("1024"));
        assertNull(NumberUtils.parseLong("portal-manager-b3c4"));
        assertNull(NumberUtils.parseLong(""));
    }

}
