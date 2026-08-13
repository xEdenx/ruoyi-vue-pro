package cn.iocoder.yudao.module.bpm.framework.portal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * BPM 运行时认可的 Portal 调用主体。
 *
 * <p>用户标识保留 Portal 提供的原始字符串。当前由本地安全上下文适配，
 * 后续接入 Portal 的已验证 JWT 或 mTLS 时只替换适配器，不应让 Controller 或 Flowable 再感知具体认证实现。</p>
 */
@Getter
@RequiredArgsConstructor
public class BpmPortalPrincipal {

    private final String userId;
    private final Set<String> authorities;

    public static BpmPortalPrincipal of(String userId, Set<String> authorities) {
        return new BpmPortalPrincipal(userId,
                authorities == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(authorities)));
    }

}
