package com.netease.nim.lbd.test;

import com.netease.nim.lbd.LBDriverUrl;
import com.netease.nim.lbd.RemoteSqlProxyProvider;
import com.netease.nim.lbd.SqlProxy;
import com.netease.nim.lbd.SqlProxyCallback;
import com.netease.nim.lbd.util.LBDriverUrlParser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RemoteSqlProxyProvider 的单测：请求参数、双变体 md5 隔离、降级/恢复状态机
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class RemoteSqlProxyProviderTest {

    private static final String AZ_HOST_PORT = "10.1.1.1:6000";
    private static final String FALLBACK_HOST_PORT = "10.2.1.1:6000";

    private MockConfigServer configServer;

    @Before
    public void setUp() throws Exception {
        configServer = new MockConfigServer();
    }

    @After
    public void tearDown() {
        configServer.stop();
    }

    private LBDriverUrl url(String params) throws SQLException {
        String url = "jdbc:mysql:lb:remote://127.0.0.1:" + configServer.getPort() + "/mydatabase" + params;
        return LBDriverUrlParser.parseUrl(url, new Properties());
    }

    private static SqlProxy proxy(String hostPort) {
        String[] split = hostPort.split(":");
        return new SqlProxy(split[0], Integer.parseInt(split[1]));
    }

    private static Set<SqlProxy> set(SqlProxy... sqlProxies) {
        return new HashSet<>(Arrays.asList(sqlProxies));
    }

    private static Set<SqlProxy> set(List<SqlProxy> list) {
        return new HashSet<>(list);
    }

    //降级边沿的兜底拉取是异步的（不能阻塞业务线程），断言前先等它落地
    private void awaitUsable(RecordingCallback callback, Set<SqlProxy> expect) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (expect.equals(callback.usable())) {
                return;
            }
            Thread.sleep(20);
        }
        Assert.fail("兜底集合未在预期时间内生效，expect = " + expect + ", actual = " + callback.usable());
    }

    private void awaitFallbackRequestCount(int expect) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (configServer.getFallbackRequestCount() >= expect) {
                return;
            }
            Thread.sleep(20);
        }
        Assert.fail("兜底请求未在预期时间内到达，expect = " + expect
                + ", actual = " + configServer.getFallbackRequestCount());
    }

    //记录 provider 下发给 ConnectionManager 的集合变化与可用性
    private static class RecordingCallback implements SqlProxyCallback {

        private final List<SqlProxy> added = new CopyOnWriteArrayList<>();
        private final List<SqlProxy> removed = new CopyOnWriteArrayList<>();
        private final Set<SqlProxy> usable = ConcurrentHashMap.newKeySet();
        private final Set<SqlProxy> unusable = ConcurrentHashMap.newKeySet();

        @Override
        public void add(List<SqlProxy> list) {
            added.addAll(list);
        }

        @Override
        public void remove(List<SqlProxy> list) {
            removed.addAll(list);
        }

        @Override
        public void setUsable(List<SqlProxy> list, boolean value) {
            if (value) {
                usable.addAll(list);
                unusable.removeAll(list);
            } else {
                unusable.addAll(list);
                usable.removeAll(list);
            }
        }

        Set<SqlProxy> usable() {
            return new HashSet<>(usable);
        }

        Set<SqlProxy> unusable() {
            return new HashSet<>(unusable);
        }
    }

    @Test
    public void testRequestParams() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT, "10.2.1.2:6000");
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(
                url("?az=az1&ip=10.1.1.1&configServerSchema=im_user"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);

        //az 集合不可达时进入降级，触发一次兜底请求
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitUsable(callback, set(proxy(FALLBACK_HOST_PORT), proxy("10.2.1.2:6000")));
        Assert.assertEquals(2, callback.usable().size());

        MockConfigServer.Request normal = configServer.getRequests(false).get(0);
        Assert.assertEquals("im_user", normal.get("schema"));
        Assert.assertEquals("az1", normal.get("az"));
        Assert.assertEquals("10.1.1.1", normal.get("ip"));
        //恒定携带能力等级与版本号，正常请求不携带 policy
        Assert.assertEquals("2", normal.get("clientFeatureLevel"));
        Assert.assertNotNull(normal.get("clientVersion"));
        Assert.assertNull(normal.get("policy"));
        Assert.assertNull(normal.get("md5"));

        MockConfigServer.Request fallback = configServer.getRequests(true).get(0);
        //降级请求显式声明要默认集合，否则服务端会按请求来源 ip 又下发本 az 集合
        Assert.assertEquals("default", fallback.get("policy"));
        Assert.assertEquals("2", fallback.get("clientFeatureLevel"));
        Assert.assertEquals("az1", fallback.get("az"));
        Assert.assertEquals("10.1.1.1", fallback.get("ip"));
        Assert.assertNull(fallback.get("md5"));
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
    }

    @Test
    public void testDegradeOnlyOnEdge() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy az = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);

        //az 集合整体不可达：摘除 az 集合、拉取并启用兜底集合
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitUsable(callback, set(fallback));
        Assert.assertTrue(callback.added.contains(fallback));
        Assert.assertEquals(set(az), callback.unusable());
        Assert.assertEquals(1, configServer.getFallbackRequestCount());

        //后续轮次仍不可达：降级边沿只触发一次，不会重复请求兜底集合
        provider.onReachabilityCheck(Collections.emptyList(), false);
        provider.onReachabilityCheck(Collections.emptyList(), false);
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
        Assert.assertEquals(set(fallback), callback.usable());

        //driver 需要保留的完整集合 = az 亲和集合 + 兜底集合
        Assert.assertEquals(set(az, fallback), set(provider.load()));
    }

    @Test
    public void testFallbackRefreshCoalescedWhileInFlight() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        configServer.setFallbackDelayMillis(1500);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(
                url("?az=az1&ip=10.1.1.1&recoverCheckCount=2"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy az = proxy(AZ_HOST_PORT);

        //三次降级边沿、两次立即恢复：兜底集合尚未落地，恢复滞回被压成 1 次健康检查
        provider.onReachabilityCheck(Collections.emptyList(), false);
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        provider.onReachabilityCheck(Collections.emptyList(), false);
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        provider.onReachabilityCheck(Collections.emptyList(), false);

        //首次拉取还在飞（服务端延迟 1.5s）：这段时间里不应出现第二次兜底请求
        Thread.sleep(800);
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
        //边沿合并不能把降级本身丢掉：兜底集合最终仍要生效
        awaitUsable(callback, set(proxy(FALLBACK_HOST_PORT)));
    }

    @Test
    public void testRecoverNeedsConsecutiveHealthyChecks() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1&recoverCheckCount=2"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy az = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);

        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitUsable(callback, set(fallback));

        //az 集合首次恢复：滞回未达标，仍由兜底集合承接
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        Assert.assertEquals(set(fallback), callback.usable());
        Assert.assertEquals(set(az), callback.unusable());

        //连续第 2 次恢复：切回 az 集合，兜底集合从池中摘掉（下次降级再按需重新加入）
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        Assert.assertEquals(set(az), callback.usable());
        Assert.assertEquals(set(fallback), callback.unusable());
        Assert.assertEquals(set(az), set(provider.load()));
        Assert.assertTrue(callback.removed.contains(fallback));
    }

    @Test
    public void testRecoverImmediatelyWithOneCheck() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1&recoverCheckCount=1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy az = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);

        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitUsable(callback, set(fallback));

        provider.onReachabilityCheck(Collections.singletonList(az), false);
        Assert.assertEquals(set(az), callback.usable());
        Assert.assertEquals(set(fallback), callback.unusable());
    }

    @Test
    public void testKeepAzUsableWhenFallbackListEmpty() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList();
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1&recoverCheckCount=2"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy az = proxy(AZ_HOST_PORT);

        //服务端没有默认集合：不能把 az 集合摘掉，否则会出现一个可用节点都没有的空窗
        provider.onReachabilityCheck(Collections.emptyList(), false);
        Assert.assertEquals(set(az), callback.usable());
        Assert.assertTrue(callback.unusable().isEmpty());

        //兜底集合为空时滞回上限压到 2 次：单轮抖动不翻转降级态，连续 2 次可达才恢复
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        Assert.assertEquals(set(az), callback.usable());
    }

    //az 抖动（交替可达/不可达）且兜底集合未生效时，恢复滞回不能被压成 1：
    //否则每一轮都重新产生降级边沿，反复提交兜底拉取
    @Test
    public void testFlapWithoutEffectiveFallbackKeepsDegradeState() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList();
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(
                url("?az=az1&ip=10.1.1.1&recoverCheckCount=2"));
        SqlProxy az = proxy(AZ_HOST_PORT);

        //降级边沿：拉一次默认集合（服务端返回空集合，兜底始终未生效）
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitFallbackRequestCount(1);

        //交替抖动 4 轮：每轮只有 1 次可达，凑不满滞回，不应再产生降级边沿
        for (int i = 0; i < 4; i++) {
            provider.onReachabilityCheck(Collections.singletonList(az), false);
            provider.onReachabilityCheck(Collections.emptyList(), false);
        }
        Thread.sleep(500);
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
    }

    @Test
    public void testNoDegradeWhenServerNotAffinityCapable() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        SqlProxy az = proxy(AZ_HOST_PORT);

        //响应带 policy=az：具备亲和能力
        configServer.setAffinityPolicyField("az");
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitFallbackRequestCount(1);
        Assert.assertEquals(1, configServer.getFallbackRequestCount());

        //响应带 policy=default：服务端声明本次就是默认集合，不需要 driver 兜底
        configServer.setAffinityPolicyField("default");
        provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        provider.onReachabilityCheck(Collections.emptyList(), false);
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
        Assert.assertEquals(set(az), callback.usable());
        Assert.assertTrue(callback.added.isEmpty());

        //老 config-server 不返回 policy 字段，同样等价于没有亲和能力
        configServer.setAffinityPolicyField(null);
        provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        provider.onReachabilityCheck(Collections.emptyList(), false);
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
        Assert.assertEquals(set(az), callback.usable());
        Assert.assertTrue(callback.added.isEmpty());
    }

    @Test
    public void testIndependentMd5BetweenVariants() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(
                url("?az=az1&ip=10.1.1.1&recoverCheckCount=1"));
        SqlProxy az = proxy(AZ_HOST_PORT);

        //第一次降级：无 md5
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitFallbackRequestCount(1);
        //恢复后再次降级：第二次兜底请求必须携带兜底变体自己的 md5，而不是 az 亲和变体的 md5
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitFallbackRequestCount(2);

        List<MockConfigServer.Request> fallbackRequests = configServer.getRequests(true);
        Assert.assertEquals(2, fallbackRequests.size());
        Assert.assertNull(fallbackRequests.get(0).get("md5"));
        Assert.assertEquals(configServer.getFallbackMd5(), fallbackRequests.get(1).get("md5"));
        Assert.assertNotEquals(configServer.getAffinityMd5(), configServer.getFallbackMd5());
    }

    @Test
    public void testDegradeDoesNotBlockCallingThread() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        //兜底响应延迟 1s：降级边沿也不能让调用线程等这么久
        configServer.setFallbackDelayMillis(1000);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);

        //az 集合整体不可达时，onReachabilityCheck 可能跑在业务线程上，必须立刻返回
        long start = System.currentTimeMillis();
        provider.onReachabilityCheck(Collections.emptyList(), false);
        long cost = System.currentTimeMillis() - start;
        Assert.assertTrue("降级边沿不应阻塞调用线程，cost = " + cost, cost < 500);

        //兜底集合仍会在后台落地并接管
        awaitUsable(callback, set(fallback));
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
    }

    @Test
    public void testStartupCheckSettlesFallbackSynchronously() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        //兜底响应慢 1s：启动轮允许同步等待，不受影响
        configServer.setFallbackDelayMillis(1000);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);

        //启动轮由启动线程调用：兜底集合必须在本次调用内就绪，否则启动会被误判成失败
        provider.onReachabilityCheck(Collections.emptyList(), true);
        Assert.assertEquals(set(fallback), callback.usable());
        Assert.assertEquals(1, configServer.getFallbackRequestCount());
    }

    @Test
    public void testLocalIpResolvedBySocket() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        new RemoteSqlProxyProvider(url("?az=az1"));
        MockConfigServer.Request request = configServer.getRequests(false).get(0);
        Assert.assertEquals("127.0.0.1", request.get("ip"));
    }

    @Test
    public void testIpSentWithoutAz() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        new RemoteSqlProxyProvider(url(""));
        MockConfigServer.Request request = configServer.getRequests(false).get(0);
        //未配置 az 时正是依赖服务端按 ip 自动识别可用区的场景，本机 ip 仍要上报
        Assert.assertNull(request.get("az"));
        Assert.assertEquals("127.0.0.1", request.get("ip"));
    }

    @Test
    public void testExplicitIpOverrideAutoResolved() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        new RemoteSqlProxyProvider(url("?ip=10.9.9.9"));
        MockConfigServer.Request request = configServer.getRequests(false).get(0);
        Assert.assertEquals("10.9.9.9", request.get("ip"));
    }

    //未降级时不拉默认集合：refreshTick 每轮只刷新 az 集合，兜底集合是「降级时才拉」，
    //所以首次降级之前 ConnectionManager 完全看不到兜底节点，也就不存在「下发但 usable=false」
    @Test
    public void testFallbackNotFetchedBeforeDegrade() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(url("?az=az1&ip=10.1.1.1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);

        //等 refreshTick 至少跑过一轮（首轮在 5s 后）：az 变体会被再次请求，兜底变体不应被请求
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline && configServer.getRequests(false).size() < 2) {
            Thread.sleep(50);
        }
        Assert.assertTrue("refreshTick 未在预期时间内执行", configServer.getRequests(false).size() >= 2);

        Assert.assertEquals(0, configServer.getFallbackRequestCount());
        Assert.assertEquals(set(proxy(AZ_HOST_PORT)), set(provider.load()));
        Assert.assertTrue(callback.added.isEmpty());
        Assert.assertTrue(callback.removed.isEmpty());
    }

    //恢复后兜底集合从池中摘掉（不再长期占着休眠节点与休眠连接），下次降级再按需重新加入
    @Test
    public void testFallbackDetachedAfterRecoverAndReaddedOnNextDegrade() throws Exception {
        configServer.setAffinityList(AZ_HOST_PORT);
        configServer.setFallbackList(FALLBACK_HOST_PORT);
        RemoteSqlProxyProvider provider = new RemoteSqlProxyProvider(
                url("?az=az1&ip=10.1.1.1&recoverCheckCount=1"));
        RecordingCallback callback = new RecordingCallback();
        provider.addSqlProxyCallback(callback);
        SqlProxy az = proxy(AZ_HOST_PORT);
        SqlProxy fallback = proxy(FALLBACK_HOST_PORT);

        //降级 → 恢复：driver 只需要维护 az 集合，兜底集合摘掉
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitUsable(callback, set(fallback));
        provider.onReachabilityCheck(Collections.singletonList(az), false);
        Assert.assertEquals(set(az), callback.usable());
        Assert.assertEquals(set(az), set(provider.load()));
        Assert.assertTrue(callback.removed.contains(fallback));

        //再次降级：兜底集合必须重新进池（即便这一轮服务端返回 304、集合来自缓存）
        provider.onReachabilityCheck(Collections.emptyList(), false);
        awaitUsable(callback, set(fallback));
        Assert.assertEquals(set(az, fallback), set(provider.load()));
    }

}
