package com.netease.nim.lbd;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/9
 */
public interface SqlProxyProvider {

    List<SqlProxy> load();

    void addSqlProxyCallback(SqlProxyCallback callback);

    /**
     * 撤销未成功启动的 ConnectionManager 注册的回调。
     * 自定义 provider 若保存了回调，应覆盖本方法；默认实现保持旧版 SPI 的兼容性。
     * @param callback 待移除的回调
     */
    default void removeSqlProxyCallback(SqlProxyCallback callback) {
    }

    /**
     * 每轮可达性检查结束后的存活快照，provider 可据此调整下发给 ConnectionManager 的集合
     * <p>
     * 实现方需要通过 addSqlProxyCallback 回调来增删节点或调整节点是否参与负载均衡
     * <p>
     * {@code startup = false} 时由周期健康检查线程调用；需要异步收敛的操作应交给实现方的刷新线程。
     * {@code startup = true} 时由启动线程调用，实现方可以直接阻塞等待收敛：
     * 启动成功与否取决于本方法返回时是否有节点被置为可用
     * @param reachableList 当前可达的节点列表
     * @param startup 是否是启动阶段的首轮检查
     */
    default void onReachabilityCheck(List<SqlProxy> reachableList, boolean startup) {
    }

    /**
     * 建连失败时按当前池状态汇总的存活快照，用于不等待下一轮健康检查就触发降级。
     * 本方法可能在业务线程调用，不得做阻塞 I/O；默认转交旧回调以兼容自定义 provider。
     * @param reachableList 当前可达的节点列表
     */
    default void onConnectionFailure(List<SqlProxy> reachableList) {
        onReachabilityCheck(reachableList, false);
    }
}
