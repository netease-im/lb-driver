package com.netease.nim.lbd.config.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by caojiajun on 2025/12/15
 */
public class SchemaConfig {
    private String schema;
    private boolean authEnable;
    private Set<String> apiKeys;
    /**
     * 默认（兜底）集合：没命中可用区、服务端未启用 AZ、或客户端能力等级不足时下发的集合
     * <p>
     * 配置里缺省 `proxy` 时取所有可用区节点的并集
     */
    private List<String> proxyList;
    /**
     * `proxy.policy`：default / az，默认 default
     */
    private ProxyPolicy policy = ProxyPolicy.DEFAULT;
    /**
     * `az.list`：可用区定义（含所属网段），可来自 schema 配置，也可来自 config-server 的全局配置
     */
    private List<AzDefinition> azList = new ArrayList<>();
    /**
     * `az.proxy`：可用区名 -> sql-proxy 列表
     */
    private Map<String, List<String>> azProxyMap;

    public ProxyPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ProxyPolicy policy) {
        this.policy = policy;
    }

    public boolean isAzPolicy() {
        return policy == ProxyPolicy.AZ;
    }

    public List<AzDefinition> getAzList() {
        return azList;
    }

    public void setAzList(List<AzDefinition> azList) {
        this.azList = azList;
    }

    public Map<String, List<String>> getAzProxyMap() {
        return azProxyMap;
    }

    public void setAzProxyMap(Map<String, List<String>> azProxyMap) {
        this.azProxyMap = azProxyMap;
    }

    /**
     * getAzNames
     * @return 全部可用区名
     */
    public Set<String> getAzNames() {
        Set<String> set = new TreeSet<>();
        for (AzDefinition azDefinition : azList) {
            set.add(azDefinition.getName());
        }
        return set;
    }

    /**
     * getAzProxyList
     * @param az 可用区名
     * @return 该可用区的 sql-proxy 列表，未配置时返回 null
     */
    public List<String> getAzProxyList(String az) {
        if (az == null || azProxyMap == null) {
            return null;
        }
        return azProxyMap.get(az);
    }

    /**
     * azOf
     * @param ip ip
     * @return 该 ip 命中的可用区名，未命中返回 null
     */
    public String azOf(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        for (AzDefinition azDefinition : azList) {
            if (azDefinition.matches(ip)) {
                return azDefinition.getName();
            }
        }
        return null;
    }

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SchemaConfig that = (SchemaConfig) o;
        return authEnable == that.authEnable && Objects.equals(schema, that.schema) && Objects.equals(apiKeys, that.apiKeys)
                && Objects.equals(proxyList, that.proxyList) && policy == that.policy
                && Objects.equals(azList, that.azList) && Objects.equals(azProxyMap, that.azProxyMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, authEnable, apiKeys, proxyList, policy, azList, azProxyMap);
    }
}
