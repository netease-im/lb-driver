package com.netease.nim.lbd;

/**
 * Created by caojiajun on 2025/12/3
 */
public enum UnsupportedMethodBehavior {

    ThrowException("throwException"),
    IgnoreCall("ignoreCall");

    private final String name;

    UnsupportedMethodBehavior(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static UnsupportedMethodBehavior fromName(String name) {
        for (UnsupportedMethodBehavior behavior : UnsupportedMethodBehavior.values()) {
            if (behavior.getName().equals(name)) {
                return behavior;
            }
        }
        return null;
    }
}
