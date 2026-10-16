package com.netease.nim.lbd;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/9
 */
public interface SqlProxyCallback {

    /**
     * 新增节点，新增的节点会被持续探测
     * @param list list
     */
    void add(List<SqlProxy> list);

    /**
     * 移除节点
     * @param list list
     */
    void remove(List<SqlProxy> list);

    /**
     * 调整节点是否参与负载均衡；usable=false 时节点仍留在池中并继续被探测，只是不再承接新连接
     * <p>
     * 默认实现为空：不使用「留池但不用」语义的 provider 无需实现（ConnectionManager 自身的可达性判定
     * 已经能让不可达节点退出选点，本回调只用于抑制恢复滞回期间的来回切换）
     * @param list list
     * @param usable usable
     */
    default void setUsable(List<SqlProxy> list, boolean usable) {
    }
}
