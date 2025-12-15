package com.netease.nim.lbd.config.server.model;

import java.util.List;
import java.util.Set;

/**
 * Created by caojiajun on 2025/12/15
 */
public class SchemaConfig {
    private String schema;
    private boolean authEnable;
    private Set<String> apiKeys;
    private List<String> proxyList;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

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

    public List<String> getProxyList() {
        return proxyList;
    }

    public void setProxyList(List<String> proxyList) {
        this.proxyList = proxyList;
    }
}
