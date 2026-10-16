package com.netease.nim.lbd.config.server.model;

import com.netease.nim.lbd.config.server.utils.Cidr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 可用区定义：可用区名 + 该可用区所属网段
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class AzDefinition {

    private String name;
    private List<String> cidr;
    private List<Cidr> cidrList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getCidr() {
        return cidr;
    }

    public void setCidr(List<String> cidr) {
        this.cidr = cidr;
        List<Cidr> cidrList = new ArrayList<>();
        if (cidr != null) {
            for (String each : cidr) {
                cidrList.add(Cidr.parse(each));
            }
        }
        this.cidrList = cidrList;
    }

    //取解析后的网段，用于匹配与重叠校验
    public List<Cidr> getCidrList() {
        return cidrList == null ? new ArrayList<>() : cidrList;
    }

    /**
     * matches
     * @param ip ip
     * @return 该 ip 是否属于本可用区
     */
    public boolean matches(String ip) {
        for (Cidr each : getCidrList()) {
            if (each.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    //SchemaConfig.equals 依赖本方法：缺少它会让每轮配置刷新都被判定为「配置已变更」并打出假的更新日志
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AzDefinition that = (AzDefinition) o;
        return Objects.equals(name, that.name) && Objects.equals(cidr, that.cidr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, cidr);
    }
}
