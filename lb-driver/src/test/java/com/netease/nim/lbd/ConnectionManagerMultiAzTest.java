package com.netease.nim.lbd;

import com.netease.nim.lbd.test.MockConfigServer;
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
 * 多 az 亲和、降级兜底与恢复的端到端单测
 * <p>
 * 真实 RemoteSqlProxyProvider + mock config-server + 假 jdbc driver，不依赖真实配置中心与数据库；
 * 降级由 provider 内部完成，本测试同时验证 ConnectionManager 对 az 完全无感知
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class ConnectionManagerMultiAzTest {

    private static final String AZ_HOST_PORT = "10.0.0.1:6000";
    private static final String FALLBACK_HOST_PORT = "10.0.0.2:6000";
    private static final String FALLBACK_HOST_PORT_2 = "10.0.0.3:6000";

    private MockConfigServer configServer;
    private FakeJdbcDriver fakeDriver;

    @Before
    public void setUp() throws Exception {
        configServer = new MockConfigServer();
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        fakeDriver = new FakeJdbcDriver();
        LBDriverEnv.realDriver = fakeDriver;
    }

    @After
    public void tearDown() {
        configServer.stop();
    }

    private static SqlProxy proxy(String hostPort) {
        String[] split = hostPort.split(":");
        return new SqlProxy(split[0], Integer.parseInt(split[1]));
    }

    private LBDriverUrl url(String params) throws SQLException {
        String url = "jdbc:mysql:lb:remote://127.0.0.1:" + configServer.getPort() + "/mydatabase"
                + "?checkHealthIntervalSeconds=1&checkBalanceIntervalSeconds=1"
                + "&azRefreshIntervalSeconds=1&configServerSchema=mydatabase" + params;
        return LBDriverUrlParser.parseUrl(url, new Properties());
    }

    private static RealConnection connectUntilSuccess(ConnectionManager manager, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                return manager.getConnection();
            } catch (SQLException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    //等待负载均衡切到期望的节点，最长 15s
    private static RealConnection awaitProxy(ConnectionManager manager, SqlProxy expect) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            RealConnection connection = connectUntilSuccess(manager, 3);
            if (connection != null) {
                if (expect.equals(connection.getSqlProxy())) {
                    return connection;
                }
                manager.returnConnection(connection);
            }
            Thread.sleep(200);
        }
        return null;
    }

    private static Set<SqlProxy> onlineProxySet(ConnectionManager manager) {
        Set<SqlProxy> set = new HashSet<>();
        for (LbdStats.SqlProxyStats stats : manager.stats().getStatsList()) {
            if (stats.isOnline()) {
                set.add(stats.getSqlProxy());
            }
        }
        return set;
    }

    //等待池内节点集合收敛到期望值，最长 15s
    private static void awaitOnlineProxySet(ConnectionManager manager, Set<SqlProxy> expect) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (expect.equals(onlineProxySet(manager))) {
                return;
            }
            Thread.sleep(100);
        }
        Assert.assertEquals(expect, onlineProxySet(manager));
    }

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

    private static int idleOf(ConnectionManager manager, SqlProxy sqlProxy) {
        return statsOf(manager, sqlProxy).getIdle();
    }

    private static boolean reachableOf(ConnectionManager manager, SqlProxy sqlProxy) {
        return statsOf(manager, sqlProxy).isReachable();
    }

    //只读 stats 等待不可达：断言之前不能对 az 池做任何借还，借还会顺手把滞留连接回收掉，掩盖问题
    private static void awaitUnreachable(ConnectionManager manager, SqlProxy sqlProxy) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (!reachableOf(manager, sqlProxy)) {
                return;
            }
            Thread.sleep(100);
        }
        Assert.fail("等待 sql proxy 不可达超时，proxy = " + sqlProxy);
    }

    @Test
    public void testAffinityActiveWhenReachable() throws Exception {
        SqlProxy affinity = proxy(AZ_HOST_PORT);
        ConnectionManager manager = new ConnectionManager(url("&az=az1"), new RemoteSqlProxyProvider(url("&az=az1")));
        for (int i = 0; i < 5; i++) {
            RealConnection connection = manager.getConnection();
            Assert.assertEquals(affinity, connection.getSqlProxy());
            manager.returnConnection(connection);
        }
        //az 集合可达时不会触发任何兜底请求，兜底节点也不会进入连接池
        Assert.assertEquals(0, configServer.getFallbackRequestCount());
        Assert.assertEquals(1, onlineProxySet(manager).size());
    }

    @Test
    public void testDegradeToFallbackWhenAzUnreachable() throws Exception {
        SqlProxy affinity = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);
        LBDriverUrl lbDriverUrl = url("&az=az1");
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));
        RealConnection connection = manager.getConnection();
        Assert.assertEquals(affinity, connection.getSqlProxy());
        manager.returnConnection(connection);

        //az 集合整体故障：健康检查发现后由 provider 拉取兜底集合接管新连接
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        RealConnection degraded = awaitProxy(manager, fallback);
        Assert.assertNotNull("az 集合故障后应降级到兜底集合", degraded);
        manager.returnConnection(degraded);

        for (int i = 0; i < 10; i++) {
            RealConnection each = connectUntilSuccess(manager, 3);
            Assert.assertNotNull(each);
            Assert.assertEquals(fallback, each.getSqlProxy());
            manager.returnConnection(each);
        }
        //降级是边沿触发：兜底集合只请求一次，且 az 节点仍留在池中继续被探测
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
        Assert.assertEquals(2, onlineProxySet(manager).size());
    }

    @Test
    public void testRecoverAfterConsecutiveHealthyChecks() throws Exception {
        SqlProxy affinity = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);
        LBDriverUrl lbDriverUrl = url("&az=az1&recoverCheckCount=2");
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));

        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        RealConnection degraded = awaitProxy(manager, fallback);
        Assert.assertNotNull(degraded);
        manager.returnConnection(degraded);

        //az 集合恢复：连续 2 次健康检查通过后切回 az 集合
        fakeDriver.setUnreachable(AZ_HOST_PORT, false);
        RealConnection recovered = awaitProxy(manager, affinity);
        Assert.assertNotNull("az 集合连续恢复后应切回 az 集合", recovered);
        manager.returnConnection(recovered);

        //切回后兜底集合从池中摘掉：不再占着休眠节点与休眠连接
        awaitOnlineProxySet(manager, Collections.singleton(affinity));
        for (int i = 0; i < 5; i++) {
            RealConnection each = connectUntilSuccess(manager, 3);
            Assert.assertNotNull(each);
            Assert.assertEquals(affinity, each.getSqlProxy());
            manager.returnConnection(each);
        }

        //再次故障：兜底集合重新进池并接管流量
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        RealConnection degradedAgain = awaitProxy(manager, fallback);
        Assert.assertNotNull("兜底集合摘掉后再降级应能重新加入池中", degradedAgain);
        manager.returnConnection(degradedAgain);
    }

    @Test
    public void testRecoverImmediatelyWhenRecoverCheckCountIsOne() throws Exception {
        SqlProxy affinity = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);
        LBDriverUrl lbDriverUrl = url("&az=az1&recoverCheckCount=1");
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));

        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        RealConnection degraded = awaitProxy(manager, fallback);
        Assert.assertNotNull(degraded);
        manager.returnConnection(degraded);

        fakeDriver.setUnreachable(AZ_HOST_PORT, false);
        RealConnection recovered = awaitProxy(manager, affinity);
        Assert.assertNotNull(recovered);
        manager.returnConnection(recovered);
    }

    @Test
    public void testStartupWithUnreachableAz() throws Exception {
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        LBDriverUrl lbDriverUrl = url("&az=az1");
        //初始化时 az 集合已不可达：应直接走降级流程，而不是抛异常
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));
        RealConnection connection = manager.getConnection();
        Assert.assertEquals(fallback, connection.getSqlProxy());
        manager.returnConnection(connection);
    }

    @Test
    public void testStartupWithUnreachableAzAndSlowFallback() throws Exception {
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);
        //兜底响应慢 1s：降级边沿的拉取是异步的，启动线程必须同步等它落地，不能误判成启动失败
        configServer.setFallbackDelayMillis(1000);
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        LBDriverUrl lbDriverUrl = url("&az=az1");
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));
        RealConnection connection = manager.getConnection();
        Assert.assertEquals(fallback, connection.getSqlProxy());
        manager.returnConnection(connection);
    }

    @Test
    public void testStartupFailedWhenAllUnreachable() throws Exception {
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        fakeDriver.setUnreachable(FALLBACK_HOST_PORT, true);
        LBDriverUrl lbDriverUrl = url("&az=az1");
        try {
            new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));
            Assert.fail("az 集合与兜底集合都不可用时应该抛出异常");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("There is no reachable sql proxy", e.getMessage());
        }
    }

    @Test
    public void testNoDegradeWhenServerNotAffinityCapable() throws Exception {
        //服务端未配置 az policy：driver 侧行为与引入多 az 之前完全一致，不存在兜底路径
        configServer.setAffinityPolicyField(null);
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        LBDriverUrl lbDriverUrl = url("&az=az1");
        try {
            new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));
            Assert.fail("没有亲和能力时不应有可用的兜底路径");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("There is no reachable sql proxy", e.getMessage());
        }
        Assert.assertEquals(0, configServer.getFallbackRequestCount());
    }

    @Test
    public void testLocalMode() throws Exception {
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(
                "jdbc:mysql:lb:local://10.0.0.1:6000,10.0.0.2:6000/mydatabase?checkHealthIntervalSeconds=1",
                new Properties());
        LocalSqlProxyProvider provider = new LocalSqlProxyProvider(lbDriverUrl);
        Assert.assertEquals(2, provider.load().size());
        //local 模式没有 az 相关行为，也没有兜底请求
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, provider);
        RealConnection connection = manager.getConnection();
        Assert.assertNotNull(connection.getSqlProxy());
        manager.returnConnection(connection);
        Assert.assertEquals(2, manager.stats().getStatsList().size());
    }

    //长时间降级期间：选点与均衡完全落在兜底集合内部，本可用区节点不再参与
    @Test
    public void testBalanceStaysOnFallbackWhileAzUnreachable() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT, FALLBACK_HOST_PORT_2);
        SqlProxy affinity = proxy(AZ_HOST_PORT);
        SqlProxy fallback1 = proxy(FALLBACK_HOST_PORT);
        SqlProxy fallback2 = proxy(FALLBACK_HOST_PORT_2);
        LBDriverUrl lbDriverUrl = url("&az=az1");
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));

        //az 节点全部故障：首个业务建连失败即触发降级，之后连接必须在兜底集合内部轮转
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        Set<SqlProxy> used = new HashSet<>();
        List<RealConnection> held = new ArrayList<>();
        try {
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && held.size() < 6) {
                RealConnection connection = connectUntilSuccess(manager, 3);
                if (connection == null) {
                    Thread.sleep(100);
                    continue;
                }
                used.add(connection.getSqlProxy());
                held.add(connection);
            }
        } finally {
            for (RealConnection connection : held) {
                manager.returnConnection(connection);
            }
        }
        Assert.assertEquals("兜底集合内的两个节点都应参与均衡", 2, used.size());
        Assert.assertTrue("本可用区节点不应参与选点，used = " + used,
                used.equals(new HashSet<SqlProxy>(Arrays.asList(fallback1, fallback2))));
        Assert.assertFalse("本可用区节点不应出现在选点结果里", used.contains(affinity));
    }

    //az 长时间故障：新建连不上、已建连接也坏掉。此时降级由健康检查（而不是业务建连失败）驱动，
    //验证降级后流量稳定落在兜底集合内部、az 不再有任何连接计入 totalCount，且 az 恢复后能切回来
    @Test
    public void testLongOutageWithBrokenEstablishedConnections() throws Exception {
        SqlProxy affinity = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);
        LBDriverUrl lbDriverUrl = url("&az=az1&recoverCheckCount=2");
        ConnectionManager manager = new ConnectionManager(lbDriverUrl, new RemoteSqlProxyProvider(lbDriverUrl));

        //az 上留一条空闲连接，同时业务手上还握着一条（故障期间这条要等归还时才出账）
        manager.returnConnection(manager.getConnection());
        RealConnection held = manager.getConnection();
        manager.returnConnection(manager.getConnection());
        Assert.assertEquals(affinity, held.getSqlProxy());
        Assert.assertEquals(1, idleOf(manager, affinity));

        //az 整体故障
        fakeDriver.setUnreachable(AZ_HOST_PORT, true);
        fakeDriver.setEstablishedBroken(AZ_HOST_PORT, true);

        awaitUnreachable(manager, affinity);
        try {
            Assert.assertEquals("az 被摘出负载均衡后不应再留空闲连接", 0, idleOf(manager, affinity));
        } finally {
            //故障期间一直握着的连接：归还时按不可用关闭，不再计入 totalCount
            manager.returnConnection(held);
        }

        RealConnection connection = awaitProxy(manager, fallback);
        Assert.assertNotNull("az 不可达后应降级到兜底集合", connection);
        manager.returnConnection(connection);

        //故障持续期间：az 不再有任何连接计入 totalCount，选点只在兜底集合内部
        for (int i = 0; i < 5; i++) {
            Thread.sleep(300);
            RealConnection each = manager.getConnection();
            Assert.assertEquals("故障持续期间 az 不应再被选中", fallback, each.getSqlProxy());
            manager.returnConnection(each);
        }
        LbdStats stats = manager.stats();
        Assert.assertEquals(0, statsOf(stats, affinity).getIdle());
        Assert.assertEquals(0, statsOf(stats, affinity).getUsing());
        Assert.assertEquals("az 池不应有连接残留在 totalCount 里",
                statsOf(stats, fallback).getIdle() + statsOf(stats, fallback).getUsing(), stats.getTotalCount());

        //az 恢复：坏连接已被清掉，重新建链后切回本可用区，兜底集合从池中摘掉
        fakeDriver.setEstablishedBroken(AZ_HOST_PORT, false);
        fakeDriver.setUnreachable(AZ_HOST_PORT, false);
        RealConnection recovered = awaitProxy(manager, affinity);
        Assert.assertNotNull("az 恢复后应切回本可用区", recovered);
        manager.returnConnection(recovered);
        awaitOnlineProxySet(manager, Collections.singleton(affinity));
    }

}
