package com.netease.nim.lbd.config.server.model;

/**
 * `proxy.policy` 的取值，决定 config-server 按什么策略给客户端下发 sql-proxy 集合
 * <p>
 * 可用区（AZ）只是其中一种模式：后续新增模式时在此追加枚举常量，并在 {@code SqlProxyListResolver} 中补对应分支。
 * 取值非法时加载期直接拒绝，不会静默回落到 {@link #DEFAULT}
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public enum ProxyPolicy {

    /**
     * 默认集合：所有客户端都下发默认（兜底）集合
     */
    DEFAULT("default"),

    /**
     * 按客户端可用区下发：命中可用区下发该可用区集合，否则下发默认集合
     */
    AZ("az"),
    ;

    private final String value;

    ProxyPolicy(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * parse
     * @param value value
     * @return ProxyPolicy，非法值返回 null
     */
    public static ProxyPolicy parse(String value) {
        if (value == null) {
            return null;
        }
        for (ProxyPolicy policy : values()) {
            if (policy.value.equalsIgnoreCase(value.trim())) {
                return policy;
            }
        }
        return null;
    }
}
