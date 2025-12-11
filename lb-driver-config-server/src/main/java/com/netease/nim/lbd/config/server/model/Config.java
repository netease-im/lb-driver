package com.netease.nim.lbd.config.server.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by caojiajun on 2025/12/10
 */
public class Config {

    private boolean authEnable;
    private Set<String> apiKeys;
    private Map<String, List<String>> proxyConfig;

    public boolean isAuthEnable() {
        return authEnable;
    }

    public void setAuthEnable(boolean authEnable) {
        this.authEnable = authEnable;
    }

    public Set<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(Set<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public Map<String, List<String>> getProxyConfig() {
        return proxyConfig;
    }

    public void setProxyConfig(Map<String, List<String>> proxyConfig) {
        this.proxyConfig = proxyConfig;
    }
}
