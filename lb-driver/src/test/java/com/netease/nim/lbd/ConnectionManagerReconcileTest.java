package com.netease.nim.lbd;

import com.netease.nim.lbd.util.LBDriverUrlParser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * ConnectionManager 的单测：周期校准（checkSqlProxyListFromProvider）与池被摘出负载均衡时的连接回收
 * <p>
 * 用一个只通过 load() 暴露期望集合、不做任何回调推送的 provider，验证校准以池内实际节点为基线：
 * 被守卫 skip 掉的移除会在下一轮校准中重试，不会因为「期望集合已经更新过」而永久丢失
 * <p>
 * Created by caojiajun on 2026/10/16
 */
public class ConnectionManagerReconcileTest {

    private static final String HOST_A_PORT = "10.0.0.1:6000";
    private static final String HOST_B_PORT = "10.0.0.2:6000";

    private FakeJdbcDriver fakeDriver;

    @Before
    public void setUp() {
        fakeDriver = new FakeJdbcDriver();
        LBDriverEnv.realDriver = fakeDriver;
    }

    @After
    public void tearDown() {
        LBDriverEnv.realDriver = null;
    }

    private static SqlProxy proxy(String hostPort) {
        String[] split = hostPort.split(":");
        return new SqlProxy(split[0], Integer.parseInt(split[1]));
    }

    private static LBDriverUrl url() throws SQLException {
        return url(1, 1);
    }

    private static LBDriverUrl url(int checkHealthIntervalSeconds, int checkBalanceIntervalSeconds) throws SQLException {
        return LBDriverUrlParser.parseUrl("jdbc:mysql:lb:remote://127.0.0.1:1/mydatabase"
                + "?checkHealthIntervalSeconds=" + checkHealthIntervalSeconds
                + "&checkBalanceIntervalSeconds=" + checkBalanceIntervalSeconds
                + "&configServerSchema=mydatabase",
                new Properties());
    }

    //stats() 里的 create/reuse/close 是 getAndSet(0) 的增量，一次断言只取一份快照
    private static LbdStats.SqlProxyStats statsOf(ConnectionManager manager, SqlProxy sqlProxy) {
        return statsOf(manager.stats(), sqlProxy);
    }

    private static LbdStats.SqlProxyStats statsOf(LbdStats lbdStats, SqlProxy sqlProxy) {
        for (LbdStats.SqlProxyStats stats : lbdStats.getStatsList()) {
            if (sqlProxy.equals(stats.getSqlProxy())) {
                return stats;
            }
        }
        throw new IllegalStateException("sql proxy not found, sql proxy = " + sqlProxy);
    }

    private static boolean isOnline(ConnectionManager manager, SqlProxy sqlProxy) {
        for (LbdStats.SqlProxyStats stats : manager.stats().getStatsList()) {
            if (sqlProxy.equals(stats.getSqlProxy())) {
                return stats.isOnline();
            }
        }
        return false;
    }

    private static boolean isReachable(ConnectionManager manager, SqlProxy sqlProxy) {
        for (LbdStats.SqlProxyStats stats : manager.stats().getStatsList()) {
            if (sqlProxy.equals(stats.getSqlProxy())) {
                return stats.isReachable();
            }
        }
        return false;
    }

    private static void awaitOnline(ConnectionManager manager, SqlProxy sqlProxy, boolean expect) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (isOnline(manager, sqlProxy) == expect) {
                return;
            }
            sleep(50);
        }
        Assert.fail("等待 sql proxy 在线状态超时，proxy = " + sqlProxy + ", expect = " + expect);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assert.fail("interrupted");
        }
    }

    //只通过 load() 暴露期望集合、不做任何回调推送：节点增删全部靠周期校准收敛
    private static class MutableListProvider implements SqlProxyProvider {

        private volatile List<SqlProxy> list;

        MutableListProvider(SqlProxy... proxies) {
            this.list = new ArrayList<>(Arrays.asList(proxies));
        }

        void set(SqlProxy... proxies) {
            this.list = new ArrayList<>(Arrays.asList(proxies));
        }

        @Override
        public List<SqlProxy> load() {
            return new ArrayList<>(list);
        }

        @Override
        public void addSqlProxyCallback(SqlProxyCallback callback) {
        }
    }

    //可主动下发 usable 的 provider：模拟 az 降级时「本可用区集合留在池中但被摘出负载均衡」
    private static class ControllableProvider implements SqlProxyProvider {

        private final List<SqlProxy> list;
        private volatile SqlProxyCallback callback;

        ControllableProvider(SqlProxy... proxies) {
            this.list = Arrays.asList(proxies);
        }

        @Override
        public List<SqlProxy> load() {
            return new ArrayList<>(list);
        }

        @Override
        public void addSqlProxyCallback(SqlProxyCallback callback) {
            this.callback = callback;
        }

        void setUsable(List<SqlProxy> list, boolean usable) {
            callback.setUsable(list, usable);
        }
    }

    //池被摘出负载均衡（usable=false，az 降级期间本可用区集合就是这种状态）时，池内滞留的空闲连接
    //必须被立即回收：它们业务取不到、rebalance 也收不到，留着只会让 totalCount 虚高，
    //rebalance 据此误判「物理连接富余」，反复从活跃池回收空闲连接（表现为兜底节点一直在建连、断连）
    @Test
    public void testIdleConnectionsReclaimedWhenPoolLeavesLoadBalancing() throws Exception {
        SqlProxy a = proxy(HOST_A_PORT);
        ControllableProvider provider = new ControllableProvider(a);
        //把健康检查与 rebalance 周期放到 60s：确保后面观察到的回收只可能来自「摘出负载均衡」这个动作
        ConnectionManager manager = new ConnectionManager(url(60, 60), provider);

        RealConnection held1 = manager.getConnection();
        RealConnection held2 = manager.getConnection();
        manager.returnConnection(held2);
        LbdStats stats = manager.stats();
        Assert.assertEquals("应有 2 条物理连接（1 条空闲 + 1 条使用中）", 2, stats.getTotalCount());
        Assert.assertEquals(1, statsOf(stats, a).getIdle());
        Assert.assertEquals(1, statsOf(stats, a).getUsing());

        //provider 把 A 摘出负载均衡
        provider.setUsable(Collections.singletonList(a), false);

        //空闲的那条立即回收，使用中的那条在归还时关闭
        LbdStats statsAfterLeave = manager.stats();
        Assert.assertEquals("摘出负载均衡后不应再留空闲连接", 0, statsOf(statsAfterLeave, a).getIdle());
        Assert.assertEquals(1, statsOf(statsAfterLeave, a).getClose());
        Assert.assertEquals(1, statsAfterLeave.getTotalCount());
        manager.returnConnection(held1);
        LbdStats statsAfterReturn = manager.stats();
        Assert.assertEquals("摘出负载均衡后 totalCount 不应再计入该池的连接", 0, statsAfterReturn.getTotalCount());
        Assert.assertEquals(0, statsAfterReturn.getUsingCount());
        Assert.assertEquals(1, statsOf(statsAfterReturn, a).getClose());

        //节点已被摘出负载均衡，不应再承接新连接
        try {
            RealConnection connection = manager.getConnection();
            manager.returnConnection(connection);
            Assert.fail("摘出负载均衡后不应有可用节点");
        } catch (SQLException e) {
            Assert.assertTrue("异常信息 = " + e.getMessage(),
                    e.getMessage().contains("There is no reachable sql proxy"));
        }
    }

    //摘出负载均衡的池不参与选点，也不参与「是否均衡」的判断：流量与均衡完全落在剩余节点内部
    @Test
    public void testUnusablePoolNeitherSelectedNorBalanced() throws Exception {
        SqlProxy a = proxy(HOST_A_PORT);
        SqlProxy b = proxy(HOST_B_PORT);
        ControllableProvider provider = new ControllableProvider(a, b);
        ConnectionManager manager = new ConnectionManager(url(60, 60), provider);

        //节点数量相同时按「使用中的连接数」选点，两条连接会分别落在两个节点上
        Set<SqlProxy> used = new HashSet<>();
        for (int i = 0; i < 4 && used.size() < 2; i++) {
            RealConnection connection = manager.getConnection();
            used.add(connection.getSqlProxy());
            manager.returnConnection(connection);
        }
        Assert.assertEquals("两个节点都应参与选点", new HashSet<>(Arrays.asList(a, b)), used);

        //A 摘出负载均衡后，选点只可能落在 B
        provider.setUsable(Collections.singletonList(a), false);
        for (int i = 0; i < 5; i++) {
            RealConnection connection = manager.getConnection();
            Assert.assertEquals("被摘出负载均衡的节点不应再被选中", b, connection.getSqlProxy());
            manager.returnConnection(connection);
        }
        Assert.assertEquals(0, statsOf(manager.stats(), a).getIdle());
    }

    @Test
    public void testSkippedRemovalRetriedOnNextReconcile() throws Exception {
        SqlProxy a = proxy(HOST_A_PORT);
        SqlProxy b = proxy(HOST_B_PORT);
        //B 从启动起就不可达：探活连接建不起来，B 会被正确判为不可达
        fakeDriver.setUnreachable(HOST_B_PORT, true);
        MutableListProvider provider = new MutableListProvider(a, b);
        ConnectionManager manager = new ConnectionManager(url(), provider);
        Assert.assertEquals(2, manager.stats().getStatsList().size());
        Assert.assertTrue("A 应可达", isReachable(manager, a));
        Assert.assertFalse("B 应被判为不可达", isReachable(manager, b));

        //期望集合收敛为 {B}：此时摘掉 A 会导致一个可用节点都不剩，两道守卫会 skip
        provider.set(b);
        //跑过几轮校准，A 不应被摘掉
        Thread.sleep(2500);
        Assert.assertTrue("B 不可达时摘掉 A 会被守卫 skip，A 应仍在线", isOnline(manager, a));

        //B 恢复后，下一轮校准应重试同一次移除（而不是因为「期望集合已更新过」永久丢失这次移除）
        fakeDriver.setUnreachable(HOST_B_PORT, false);
        awaitOnline(manager, a, false);
    }

    //池被健康检查判定不可达（usable 仍为 true）时同样要回收空闲连接：这条边没有 setUsable 参与
    @Test
    public void testIdleConnectionsReclaimedWhenPoolBecomesUnreachable() throws Exception {
        SqlProxy a = proxy(HOST_A_PORT);
        ControllableProvider provider = new ControllableProvider(a);
        //健康检查放到 1s（用它触发不可达），rebalance 放到 60s（否则它可能先一步把空闲连接收走，掩盖问题）
        ConnectionManager manager = new ConnectionManager(url(1, 60), provider);

        manager.returnConnection(manager.getConnection());
        Assert.assertEquals(1, statsOf(manager, a).getIdle());

        //已建连接坏掉：健康检查用的就是池内那条已建连接，于是被判为不可达
        fakeDriver.setEstablishedBroken(HOST_A_PORT, true);
        awaitUnreachable(manager, a);
        Assert.assertEquals("判定不可达时池内的空闲连接应被回收", 0, statsOf(manager, a).getIdle());
        Assert.assertEquals("不可达池的连接不应再计入 totalCount", 0, manager.stats().getTotalCount());

        //故障恢复：坏连接已被清掉，重新建链即可，池重新参与选点
        fakeDriver.setEstablishedBroken(HOST_A_PORT, false);
        RealConnection connection = null;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline && connection == null) {
            try {
                connection = manager.getConnection();
            } catch (SQLException e) {
                sleep(50);
            }
        }
        Assert.assertNotNull("恢复可达后应能重新建链", connection);
        manager.returnConnection(connection);
        Assert.assertEquals(1, statsOf(manager, a).getIdle());
    }

    private static void awaitUnreachable(ConnectionManager manager, SqlProxy sqlProxy) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (!statsOf(manager, sqlProxy).isReachable()) {
                return;
            }
            sleep(50);
        }
        Assert.fail("等待 sql proxy 不可达超时，sql proxy = " + sqlProxy);
    }
}
