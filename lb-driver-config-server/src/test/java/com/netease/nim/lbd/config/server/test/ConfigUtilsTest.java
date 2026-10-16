package com.netease.nim.lbd.config.server.test;

import com.netease.nim.lbd.config.server.model.AzDefinition;
import com.netease.nim.lbd.config.server.model.ProxyPolicy;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * schema 配置解析与校验，配置项格式必须与历史 json 兼容
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class ConfigUtilsTest {

    private static final String LEGACY_CONFIG = "{\n"
            + "  \"schema\": \"im_user\",\n"
            + "  \"auth.enable\": true,\n"
            + "  \"api.keys\": [\"aaaa\", \"bbbb\"],\n"
            + "  \"proxy\": [\"127.0.0.1:1\", \"127.0.0.1:2\"]\n"
            + "}";

    private static final String AZ_CONFIG = "{\n"
            + "  \"schema\": \"im_user\",\n"
            + "  \"proxy.policy\": \"az\",\n"
            + "  \"az.list\": [\n"
            + "    {\"name\": \"az1\", \"cidr\": [\"10.1.0.0/16\"]},\n"
            + "    {\"name\": \"az2\", \"cidr\": [\"10.2.0.0/16\"]}\n"
            + "  ],\n"
            + "  \"az.proxy\": {\n"
            + "    \"az1\": [\"127.0.0.1:1\"],\n"
            + "    \"az2\": [\"127.0.0.1:2\"]\n"
            + "  },\n"
            + "  \"proxy\": [\"127.0.0.1:1\", \"127.0.0.1:2\"]\n"
            + "}";

    @Test
    public void testAzPolicyWithoutAzListRejected() {
        //开了 AZ 亲和却没有可用区定义：行为会静默退化成永远下发默认集合，加载期直接报错
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\",\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("az 策略缺少 az.list 应拒绝加载");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("az.list"));
        }
    }

    @Test
    public void testAzPolicyWithoutAzProxyRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}],\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("az 策略缺少 az.proxy 应拒绝加载");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("az.proxy"));
        }
    }

    @Test
    public void testAzProxyIgnoredWhenPolicyIsDefault() {
        //允许先把 az 配置铺好、策略稍后再切：不拒绝加载，但配置不生效
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"default\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:2\"]}";
        SchemaConfig schemaConfig = ConfigUtils.parse(content);
        Assert.assertEquals(ProxyPolicy.DEFAULT, schemaConfig.getPolicy());
        Assert.assertEquals(Arrays.asList("127.0.0.1:2"), schemaConfig.getProxyList());
    }

    @Test
    public void testDuplicateProxyInSameAzDeduplicated() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\",\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        SchemaConfig schemaConfig = ConfigUtils.parse(content);
        Assert.assertEquals(Arrays.asList("127.0.0.1:1"), schemaConfig.getAzProxyList("az1"));
    }

    @Test
    public void testDuplicateProxyInDefaultListDeduplicated() {
        String content = "{\"schema\":\"im_user\",\"proxy\":[\"127.0.0.1:2\",\"127.0.0.1:1\",\"127.0.0.1:2\"]}";
        SchemaConfig schemaConfig = ConfigUtils.parse(content);
        //默认集合内部去重，并归一化成升序：重复配置只下发一次，配置项重排也不会被 equals 当成变更
        Assert.assertEquals(Arrays.asList("127.0.0.1:1", "127.0.0.1:2"), schemaConfig.getProxyList());
        Assert.assertEquals(schemaConfig.getProxyList(),
                ConfigUtils.parse("{\"schema\":\"im_user\",\"proxy\":[\"127.0.0.1:1\",\"127.0.0.1:2\"]}").getProxyList());
    }

    @Test
    public void testParseLegacyConfig() {
        SchemaConfig schemaConfig = ConfigUtils.parse(LEGACY_CONFIG);
        Assert.assertEquals("im_user", schemaConfig.getSchema());
        Assert.assertTrue(schemaConfig.isAuthEnable());
        Assert.assertEquals(2, schemaConfig.getApiKeys().size());
        //历史配置没有 az 相关字段：策略为 default，行为与引入多 az 之前一致
        Assert.assertEquals(ProxyPolicy.DEFAULT, schemaConfig.getPolicy());
        Assert.assertFalse(schemaConfig.isAzPolicy());
        Assert.assertTrue(schemaConfig.getAzList().isEmpty());
        Assert.assertEquals(Arrays.asList("127.0.0.1:1", "127.0.0.1:2"), schemaConfig.getProxyList());
    }

    @Test
    public void testParseAzConfig() {
        SchemaConfig schemaConfig = ConfigUtils.parse(AZ_CONFIG);
        Assert.assertEquals(ProxyPolicy.AZ, schemaConfig.getPolicy());
        Assert.assertTrue(schemaConfig.isAzPolicy());
        Assert.assertEquals(2, schemaConfig.getAzList().size());
        Assert.assertEquals(Arrays.asList("127.0.0.1:1"), schemaConfig.getAzProxyList("az1"));
        Assert.assertEquals("az1", schemaConfig.azOf("10.1.2.3"));
        Assert.assertEquals("az2", schemaConfig.azOf("10.2.9.9"));
        Assert.assertNull(schemaConfig.azOf("10.3.0.1"));
    }

    @Test
    public void testProxyIsOptionalWhenAzProxyConfigured() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]},{\"name\":\"az2\",\"cidr\":[\"10.2.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"],\"az2\":[\"127.0.0.1:2\"]}}";
        SchemaConfig schemaConfig = ConfigUtils.parse(content);
        //缺省 proxy 时默认集合取所有可用区节点的并集
        Assert.assertEquals(Arrays.asList("127.0.0.1:1", "127.0.0.1:2"), schemaConfig.getProxyList());
    }

    @Test
    public void testGlobalAzListUsedWhenSchemaOmitsIt() {
        Map<String, String> globalConfig = new HashMap<>();
        globalConfig.put("az.list", "[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}]");
        List<AzDefinition> globalAzList = ConfigUtils.parseGlobalAzList(globalConfig);
        Assert.assertEquals(1, globalAzList.size());

        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        SchemaConfig schemaConfig = ConfigUtils.parse(content, globalAzList);
        Assert.assertTrue(schemaConfig.isAzPolicy());
        Assert.assertEquals("az1", schemaConfig.azOf("10.1.5.5"));
    }

    @Test
    public void testSchemaAzListOverrideGlobal() {
        Map<String, String> globalConfig = new HashMap<>();
        globalConfig.put("az.list", "[{\"name\":\"global-az\",\"cidr\":[\"10.9.0.0/16\"]}]");
        List<AzDefinition> globalAzList = ConfigUtils.parseGlobalAzList(globalConfig);
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        SchemaConfig schemaConfig = ConfigUtils.parse(content, globalAzList);
        Assert.assertEquals("az1", schemaConfig.azOf("10.1.5.5"));
        Assert.assertNull(schemaConfig.azOf("10.9.5.5"));
    }

    @Test
    public void testParseGlobalAzListEmpty() {
        Assert.assertTrue(ConfigUtils.parseGlobalAzList(new HashMap<>()).isEmpty());
        Assert.assertTrue(ConfigUtils.parseGlobalAzList(null).isEmpty());
    }

    @Test
    public void testIllegalPolicyRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"azz\",\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("非法 policy 应拒绝加载");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("proxy.policy"));
        }
    }

    @Test
    public void testAzProxyKeyMustExistInAzList() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]}],"
                + "\"az.proxy\":{\"az9\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("az.proxy 的 key 必须存在于生效的 az.list");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("az.proxy"));
        }
    }

    @Test
    public void testSameProxyInMultipleAzRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]},{\"name\":\"az2\",\"cidr\":[\"10.2.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"],\"az2\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("同一节点不得归属多个可用区");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("multiple az"));
        }
    }

    @Test
    public void testOverlappingCidrRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]},{\"name\":\"az2\",\"cidr\":[\"10.1.1.0/24\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"],\"az2\":[\"127.0.0.1:2\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("网段不得重叠");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("overlap"));
        }
    }

    @Test
    public void testIllegalCidrRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/33\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("非法网段应拒绝加载");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("prefix"));
        }
    }

    @Test
    public void testDuplicateAzNameRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy.policy\":\"az\","
                + "\"az.list\":[{\"name\":\"az1\",\"cidr\":[\"10.1.0.0/16\"]},{\"name\":\"az1\",\"cidr\":[\"10.2.0.0/16\"]}],"
                + "\"az.proxy\":{\"az1\":[\"127.0.0.1:1\"]},\"proxy\":[\"127.0.0.1:1\"]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("可用区名不得重复");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("duplicate az name"));
        }
    }

    @Test
    public void testEmptyProxyRejected() {
        String content = "{\"schema\":\"im_user\",\"proxy\":[]}";
        try {
            ConfigUtils.parse(content);
            Assert.fail("没有配置任何节点时应拒绝加载");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("proxy list is empty"));
        }
    }

    @Test
    public void testIllegalGlobalAzListRejected() {
        Map<String, String> globalConfig = new HashMap<>();
        globalConfig.put("az.list", "not-a-json");
        try {
            ConfigUtils.parseGlobalAzList(globalConfig);
            Assert.fail("全局 az.list 非法时应拒绝加载");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("az.list"));
        }
    }

    @Test
    public void testToJsonContainsAzFields() {
        SchemaConfig schemaConfig = ConfigUtils.parse(AZ_CONFIG);
        String json = ConfigUtils.toJson(schemaConfig).toJSONString();
        Assert.assertTrue(json.contains("\"proxy.policy\":\"az\""));
        Assert.assertTrue(json.contains("az.list"));
        Assert.assertTrue(json.contains("az.proxy"));
    }

    @Test
    public void testAzConfigEqualsStable() {
        //同一份 AZ 配置解析两次必须相等：AzDefinition 缺 equals 会让每轮配置刷新都被误判成「配置已变更」
        SchemaConfig first = ConfigUtils.parse(AZ_CONFIG);
        SchemaConfig second = ConfigUtils.parse(AZ_CONFIG);
        Assert.assertEquals(first, second);
        Assert.assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testAzConfigEqualsFalseWhenAzChanged() {
        SchemaConfig origin = ConfigUtils.parse(AZ_CONFIG);
        SchemaConfig otherAz = ConfigUtils.parse(AZ_CONFIG.replace("az2", "az3"));
        Assert.assertNotEquals(origin, otherAz);
        SchemaConfig otherCidr = ConfigUtils.parse(AZ_CONFIG.replace("10.2.0.0/16", "10.3.0.0/16"));
        Assert.assertNotEquals(origin, otherCidr);
    }

    @Test
    public void testCheckReachableReturnsResultForEveryProxy() throws Exception {
        //去重后每个节点都要有结论，且「明确连不通」与「没测到」必须区分开
        Map<String, ConfigUtils.ReachableState> unreachable = ConfigUtils.checkReachable(
                Arrays.asList("127.0.0.1:1", "127.0.0.1:1", "127.0.0.1:2"));
        Assert.assertEquals(2, unreachable.size());
        Assert.assertEquals(ConfigUtils.ReachableState.UNREACHABLE, unreachable.get("127.0.0.1:1"));
        Assert.assertEquals(ConfigUtils.ReachableState.UNREACHABLE, unreachable.get("127.0.0.1:2"));
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            String listening = "127.0.0.1:" + serverSocket.getLocalPort();
            Assert.assertEquals(ConfigUtils.ReachableState.REACHABLE,
                    ConfigUtils.checkReachable(Arrays.asList(listening)).get(listening));
        }
    }

    @Test
    public void testCheckReachableKeepsResultWhenPoolSaturated() {
        //节点数超过「线程数 + 队列容量」时会有任务改由提交线程执行（CallerRunsPolicy）：
        //这条背压路径同样必须写回结论，不能因为排队或丢任务让节点变成「结论未知」
        List<String> proxies = new ArrayList<>();
        for (int port = 1; port <= 40; port++) {
            proxies.add("127.0.0.1:" + port);
        }
        Map<String, ConfigUtils.ReachableState> reachable = ConfigUtils.checkReachable(proxies);
        Assert.assertEquals(proxies.size(), reachable.size());
        for (String proxy : proxies) {
            ConfigUtils.ReachableState state = reachable.get(proxy);
            Assert.assertNotNull(proxy + " 必须给出结论", state);
            Assert.assertFalse(proxy + " 不应是结论未知", ConfigUtils.ReachableState.UNKNOWN == state);
        }
    }
}
