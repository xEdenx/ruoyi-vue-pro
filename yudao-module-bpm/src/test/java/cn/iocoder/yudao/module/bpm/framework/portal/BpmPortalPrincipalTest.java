package cn.iocoder.yudao.module.bpm.framework.portal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BpmPortalPrincipalTest {

    @Test
    void shouldPreservePortalStringIdsAndSnapshotAuthorities() {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("bpm:task:query");
        BpmPortalPrincipal principal = BpmPortalPrincipal.of("portal-user-7e11", authorities);

        authorities.add("bpm:model:update");

        assertEquals("portal-user-7e11", principal.getUserId());
        assertEquals(Set.of("bpm:task:query"), principal.getAuthorities());
        assertThrows(UnsupportedOperationException.class,
                () -> principal.getAuthorities().add("bpm:task:update"));
    }

}
