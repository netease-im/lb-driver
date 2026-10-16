package com.netease.nim.lbd.config.server.test;

import com.netease.nim.lbd.config.server.model.ProxyPolicy;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.service.SqlProxyListResolver;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 下发集合的判定优先级与响应摘要
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class SqlProxyListResolverTest {

    private SchemaConfig azConfig;
    private SchemaConfig defaultConfig;

    @Before
    public void setUp() {
        azConfig = ConfigUtils.parse("{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]},{\"name\":\"az2\",\"cidr\":[\"10.2.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"],\"az2\":[\"127.0.0.1:2\"]},"
                + "\"proxy\":[\"127.0.0.1:1\",\"127.0.0.1:2\"]}");
        defaultConfig = ConfigUtils.parse("{\"schema\":\"im_user\",\"proxy\":[\"127.0.0.1:1\",\"127.0.0.1:2\"]}");
    }

    private static SqlProxyListResolver.Result resolve(SchemaConfig schemaConfig, String policy,
                                                       String az, String ip, List<String> clientIps, int level) {
        return SqlProxyListResolver.resolve(schemaConfig, policy, az, ip, clientIps, level);
    }

    @Test
    public void testNotAzPolicyAlwaysDefault() {
        SqlProxyListResolver.Result result = resolve(defaultConfig, null, "az1", "10.1.1.1",
                Collections.singletonList("10.1.1.1"), 2);
        Assert.assertEquals(ProxyPolicy.DEFAULT, result.getPolicy());
        Assert.assertNull(result.getAz());
        Assert.assertEquals(2, result.getProxyList().size());
    }

    @Test
    public void testAzMatchedByAzParam() {
        SqlProxyListResolver.Result result = resolve(azConfig, null, "az1", null, null, 2);
        Assert.assertEquals(ProxyPolicy.AZ, result.getPolicy());
        Assert.assertEquals("az1", result.getAz());
        Assert.assertEquals(Collections.singletonList("127.0.0.1:1"), result.getProxyList());
    }

    @Test
    public void testAzParamTakesPrecedenceOverIp() {
        //显式 az 优先于 ip：ip 属于 az2，但 az 参数声明 az1
        SqlProxyListResolver.Result result = resolve(azConfig, null, "az1", "10.2.1.1", null, 2);
        Assert.assertEquals("az1", result.getAz());
    }

    @Test
    public void testAzUnknownNameFallsThroughToIp() {
        SqlProxyListResolver.Result result = resolve(azConfig, null, "az-not-exist", "10.2.1.1", null, 2);
        Assert.assertEquals(ProxyPolicy.AZ, result.getPolicy());
        Assert.assertEquals("az2", result.getAz());
    }

    @Test
    public void testAzMatchedByIpParam() {
        SqlProxyListResolver.Result result = resolve(azConfig, null, null, "10.2.3.4", null, 2);
        Assert.assertEquals("az2", result.getAz());
        Assert.assertEquals(Collections.singletonList("127.0.0.1:2"), result.getProxyList());
    }

    @Test
    public void testAzMatchedBySourceIps() {
        //本机 ip 未上报时退回请求来源 ip：X-Forwarded-For 首值优先
        SqlProxyListResolver.Result result = resolve(azConfig, null, null, null,
                Arrays.asList("10.1.9.9", "10.2.9.9"), 2);
        Assert.assertEquals("az1", result.getAz());

        SqlProxyListResolver.Result fallback = resolve(azConfig, null, null, null,
                Arrays.asList("not-an-ip", "10.2.9.9"), 2);
        Assert.assertEquals("az2", fallback.getAz());
    }

    @Test
    public void testNoAzMatchedFallsBackToDefault() {
        SqlProxyListResolver.Result result = resolve(azConfig, null, null, "10.9.9.9",
                Collections.singletonList("10.9.9.9"), 2);
        Assert.assertEquals(ProxyPolicy.DEFAULT, result.getPolicy());
        Assert.assertNull(result.getAz());
        Assert.assertEquals(2, result.getProxyList().size());
    }

    @Test
    public void testLegacyClientAlwaysGetsDefaultList() {
        //老客户端没有兜底能力：即使命中了可用区也只能下发默认集合
        SqlProxyListResolver.Result result = resolve(azConfig, null, "az1", "10.1.1.1", null, 1);
        Assert.assertEquals(ProxyPolicy.DEFAULT, result.getPolicy());
        Assert.assertNull(result.getAz());
        Assert.assertEquals(2, result.getProxyList().size());
    }

    @Test
    public void testExplicitPolicyDefaultWinsOverAz() {
        //客户端降级时会显式声明 policy=default，此时必须给默认集合而不是本可用区集合
        SqlProxyListResolver.Result result = resolve(azConfig, "default", "az1", "10.1.1.1", null, 2);
        Assert.assertEquals(ProxyPolicy.DEFAULT, result.getPolicy());
        Assert.assertNull(result.getAz());
        Assert.assertEquals(2, result.getProxyList().size());
    }

    @Test
    public void testAzWithoutProxyListFallsBackToDefault() {
        SchemaConfig config = ConfigUtils.parse("{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]},{\"name\":\"az2\",\"cidr\":[\"10.2.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\",\"127.0.0.1:2\"]}");
        SqlProxyListResolver.Result result = resolve(config, null, null, "10.2.1.1", null, 2);
        Assert.assertEquals(ProxyPolicy.DEFAULT, result.getPolicy());
        Assert.assertEquals(2, result.getProxyList().size());
    }

    @Test
    public void testParseClientFeatureLevel() {
        Assert.assertEquals(1, SqlProxyListResolver.parseClientFeatureLevel(null));
        Assert.assertEquals(1, SqlProxyListResolver.parseClientFeatureLevel(""));
        Assert.assertEquals(1, SqlProxyListResolver.parseClientFeatureLevel("abc"));
        Assert.assertEquals(2, SqlProxyListResolver.parseClientFeatureLevel("2"));
        Assert.assertEquals(3, SqlProxyListResolver.parseClientFeatureLevel(" 3 "));
    }

    @Test
    public void testDigest() {
        SqlProxyListResolver.Result az1 = resolve(azConfig, null, "az1", null, null, 2);
        SqlProxyListResolver.Result az2 = resolve(azConfig, null, "az2", null, null, 2);
        SqlProxyListResolver.Result defaultResult = resolve(azConfig, "default", null, null, null, 2);
        //摘要把 policy 与可用区算进去：可用区集合与默认集合恰好相同时（同一节点既在本可用区也在默认集合里），
        //两个变体的 md5 也必须不同，否则降级请求会复用亲和变体的 md5 而拿到过期的策略认知
        SchemaConfig sameList = ConfigUtils.parse("{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},"
                + "\"proxy\":[\"127.0.0.1:1\"]}");
        SqlProxyListResolver.Result azSame = resolve(sameList, null, "az1", null, null, 2);
        SqlProxyListResolver.Result defaultSame = resolve(sameList, "default", null, null, null, 2);
        Assert.assertEquals(azSame.getProxyList(), defaultSame.getProxyList());
        Assert.assertNotEquals(azSame.digest(), defaultSame.digest());
        //集合不同时摘要自然也不同
        Assert.assertNotEquals(az1.digest(), defaultResult.digest());
        //同一变体、同一集合：摘要稳定
        Assert.assertEquals(az1.digest(), resolve(azConfig, null, "az1", null, null, 2).digest());
    }
}
