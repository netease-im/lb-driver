package com.netease.nim.lbd.config.server.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.conf.ConfigType;
import com.netease.nim.lbd.config.server.model.AzDefinition;
import com.netease.nim.lbd.config.server.model.ProxyPolicy;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by caojiajun on 2025/12/10
 */
public class ConfigUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);

    private static final int REACHABLE_CHECK_THREAD_COUNT = 8;
    private static final long REACHABLE_CHECK_TIMEOUT_MS = 3000;
    //可达性探测是阻塞 I/O（每节点 1s 连接超时），逐个串行探测会随节点数线性放大：统一并发执行。
    //队列必须有界并配 CallerRunsPolicy：无界队列下池满时任务只会排队，等到 get 超时就被误判成「不可达」；
    //改成池满由提交线程直接执行后，最差也只是提交线程参与探测（队列会腾空，不会退化成串行），结论不会失真。
    private static final ExecutorService reachableCheckExecutor = new ThreadPoolExecutor(
            REACHABLE_CHECK_THREAD_COUNT, REACHABLE_CHECK_THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(REACHABLE_CHECK_THREAD_COUNT),
            new NamedThreadFactory("lbd-proxy-reachable-check", true),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * sql-proxy 可达性探测结论
     */
    public enum ReachableState {
        REACHABLE,//探测连通
        UNREACHABLE,//探测明确连不通
        UNKNOWN//探测未在等待预算内回收，结论未知（不能当成不可达）
    }

    public static JSONObject toJson(SchemaConfig schemaConfig) {
        JSONObject json = new JSONObject();
        json.put("schema", schemaConfig.getSchema());
        json.put("auth.enable", schemaConfig.isAuthEnable());
        json.put("api.keys", schemaConfig.getApiKeys());
        json.put("proxy.policy", schemaConfig.getPolicy().getValue());
        json.put("az.list", azListJson(schemaConfig.getAzList()));
        json.put("az.proxy", schemaConfig.getAzProxyMap());
        json.put("proxy", schemaConfig.getProxyList());
        return json;
    }

    private static JSONArray azListJson(List<AzDefinition> azList) {
        JSONArray jsonArray = new JSONArray();
        for (AzDefinition azDefinition : azList) {
            JSONObject json = new JSONObject();
            json.put("name", azDefinition.getName());
            json.put("cidr", azDefinition.getCidr());
            jsonArray.add(json);
        }
        return jsonArray;
    }

    public static SchemaConfig parse(String content) {
        return parse(content, new ArrayList<>());
    }

    /**
     * parse
     * @param content schema 配置内容
     * @param globalAzList config-server 全局配置的 az.list，schema 内配置了则以 schema 内为准
     * @return SchemaConfig
     */
    public static SchemaConfig parse(String content, List<AzDefinition> globalAzList) {
        JSONObject json = JSONObject.parseObject(content);

        String schema = json.getString("schema");
        if (schema == null) {
            throw new IllegalArgumentException("missing 'schema'");
        }

        boolean authEnable = json.getBooleanValue("auth.enable");
        Set<String> apiKeys = new HashSet<>();
        JSONArray jsonArray = json.getJSONArray("api.keys");
        if (jsonArray != null) {
            for (Object o : jsonArray) {
                String apiKey = String.valueOf(o);
                apiKeys.add(apiKey);
            }
        }
        if (authEnable && apiKeys.isEmpty()) {
            throw new IllegalArgumentException("`auth.enable` is true but `api.keys` is empty");
        }

        ProxyPolicy policy = parsePolicy(json.getString("proxy.policy"));
        List<AzDefinition> azList = parseAzList(json.getJSONArray("az.list"), globalAzList);
        Map<String, List<String>> azProxyMap = parseAzProxyMap(schema, json.getJSONObject("az.proxy"), azList);
        if (policy == ProxyPolicy.AZ) {
            //开启 AZ 亲和却没有任何可用区定义/节点时，行为会静默退化成「永远下发默认集合」，在加载期就报错而不是让它悄悄不生效
            if (azList.isEmpty()) {
                throw new IllegalArgumentException("`proxy.policy` is az but effective 'az.list' is empty, schema = " + schema);
            }
            if (azProxyMap.isEmpty()) {
                throw new IllegalArgumentException("`proxy.policy` is az but 'az.proxy' is empty, schema = " + schema);
            }
        } else if (!azProxyMap.isEmpty()) {
            //允许先铺配置再切策略：此时只提示，不拒绝加载
            logger.warn("'az.proxy' is configured but `proxy.policy` is not az, az config will be ignored, schema = {}", schema);
        }

        JSONArray proxyJson = json.getJSONArray("proxy");
        //默认（兜底）集合用 set 去重并保持稳定顺序：运维重复配置同一节点不应导致重复下发与重复探测
        Set<String> proxySet = new TreeSet<>();
        if (proxyJson != null) {
            for (Object object : proxyJson) {
                String proxy = object.toString().trim();
                if (proxySet.contains(proxy)) {
                    continue;
                }
                checkProxy(proxy);
                proxySet.add(proxy);
            }
        }
        if (proxySet.isEmpty()) {
            //缺省 `proxy` 时默认集合取所有可用区节点的并集
            for (List<String> each : azProxyMap.values()) {
                proxySet.addAll(each);
            }
        }
        if (proxySet.isEmpty()) {
            throw new IllegalArgumentException("proxy list is empty and no az.proxy configured, schema = " + schema);
        }
        List<String> proxyList = new ArrayList<>(proxySet);

        //可达性检查放在配置解析末尾统一做：默认集合与各可用区节点去重后并发探测
        checkProxyReachable(schema, proxyList, azProxyMap.values());

        SchemaConfig schemaConfig = new SchemaConfig();
        schemaConfig.setSchema(schema);
        schemaConfig.setAuthEnable(authEnable);
        schemaConfig.setApiKeys(apiKeys);
        schemaConfig.setProxyList(proxyList);
        schemaConfig.setPolicy(policy);
        schemaConfig.setAzList(azList);
        schemaConfig.setAzProxyMap(azProxyMap);
        return schemaConfig;
    }

    /**
     * parseGlobalAzList
     * @param config config-server 的 config 配置项
     * @return 全局 az.list，未配置时返回空列表
     */
    public static List<AzDefinition> parseGlobalAzList(Map<String, String> config) {
        if (config == null) {
            return new ArrayList<>();
        }
        String azListJson = config.get("az.list");
        if (azListJson == null || azListJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return parseAzList(JSONArray.parseArray(azListJson), new ArrayList<>());
        } catch (Exception e) {
            throw new IllegalArgumentException("illegal global 'az.list', please check config-server yml", e);
        }
    }

    private static ProxyPolicy parsePolicy(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ProxyPolicy.DEFAULT;
        }
        ProxyPolicy policy = ProxyPolicy.parse(value);
        if (policy == null) {
            throw new IllegalArgumentException("illegal 'proxy.policy' = " + value + ", should be default or az");
        }
        return policy;
    }

    private static List<AzDefinition> parseAzList(JSONArray azListJson, List<AzDefinition> globalAzList) {
        if (azListJson == null || azListJson.isEmpty()) {
            return globalAzList == null ? new ArrayList<>() : new ArrayList<>(globalAzList);
        }
        List<AzDefinition> azList = new ArrayList<>();
        for (Object object : azListJson) {
            JSONObject json = (JSONObject) object;
            String name = json.getString("name");
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("missing 'az.list[].name'");
            }
            name = name.trim();
            for (AzDefinition each : azList) {
                if (each.getName().equals(name)) {
                    throw new IllegalArgumentException("duplicate az name in 'az.list', az = " + name);
                }
            }
            JSONArray cidrJson = json.getJSONArray("cidr");
            if (cidrJson == null || cidrJson.isEmpty()) {
                throw new IllegalArgumentException("missing 'az.list[].cidr', az = " + name);
            }
            List<String> cidrList = new ArrayList<>();
            for (Object cidr : cidrJson) {
                String value = String.valueOf(cidr).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("empty cidr in 'az.list', az = " + name);
                }
                cidrList.add(value);
            }
            AzDefinition azDefinition = new AzDefinition();
            azDefinition.setName(name);
            //setCidr 会解析网段，格式非法时直接抛错，避免运行期静默不匹配
            azDefinition.setCidr(cidrList);
            azList.add(azDefinition);
        }
        checkAzCidrOverlap(azList);
        return azList;
    }

    //网段不得重叠，否则同一个 ip 会命中两个可用区，匹配结果取决于顺序
    private static void checkAzCidrOverlap(List<AzDefinition> azList) {
        for (int i = 0; i < azList.size(); i++) {
            AzDefinition current = azList.get(i);
            for (int j = i + 1; j < azList.size(); j++) {
                AzDefinition other = azList.get(j);
                for (Cidr cidr : current.getCidrList()) {
                    for (Cidr otherCidr : other.getCidrList()) {
                        if (cidr.overlaps(otherCidr)) {
                            throw new IllegalArgumentException("cidr overlap between az = " + current.getName()
                                    + " and az = " + other.getName() + ", cidr = " + cidr + " / " + otherCidr);
                        }
                    }
                }
            }
        }
    }

    private static Map<String, List<String>> parseAzProxyMap(String schema, JSONObject azProxyJson, List<AzDefinition> azList) {
        Map<String, List<String>> azProxyMap = new TreeMap<>();
        if (azProxyJson == null || azProxyJson.isEmpty()) {
            return azProxyMap;
        }
        Set<String> azNames = new TreeSet<>();
        for (AzDefinition azDefinition : azList) {
            azNames.add(azDefinition.getName());
        }
        Map<String, String> proxyOwner = new HashMap<>();
        for (Map.Entry<String, Object> entry : azProxyJson.entrySet()) {
            String az = entry.getKey() == null ? null : entry.getKey().trim();
            if (az == null || az.isEmpty()) {
                throw new IllegalArgumentException("illegal 'az.proxy' key");
            }
            if (!azNames.contains(az)) {
                throw new IllegalArgumentException("'az.proxy' key = " + az + " not found in effective 'az.list'");
            }
            if (!(entry.getValue() instanceof JSONArray)) {
                throw new IllegalArgumentException("illegal 'az.proxy' value, az = " + az + ", should be an array");
            }
            List<String> proxyList = new ArrayList<>();
            for (Object object : (JSONArray) entry.getValue()) {
                String proxy = String.valueOf(object).trim();
                if (proxy.isEmpty()) {
                    continue;
                }
                String owner = proxyOwner.get(proxy);
                if (owner != null && !owner.equals(az)) {
                    //同一节点归属多个可用区会让「本可用区集合」失去意义
                    throw new IllegalArgumentException("sql-proxy = " + proxy + " belongs to multiple az: "
                            + owner + " and " + az);
                }
                if (owner != null) {
                    //同一可用区内重复配置同一节点时去重
                    continue;
                }
                checkProxy(proxy);
                proxyOwner.put(proxy, az);
                proxyList.add(proxy);
            }
            azProxyMap.put(az, proxyList);
        }
        return azProxyMap;
    }

    //只做格式校验，可达性检查统一在 checkProxyReachable 里并发执行
    private static void checkProxy(String proxy) {
        String[] split = proxy.split(":");
        if (split.length != 2) {
            throw new IllegalArgumentException("proxy parse error, proxy = " + proxy);
        }
        String host = split[0];
        int port;
        try {
            port = Integer.parseInt(split[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("proxy parse error, proxy = " + proxy);
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("proxy parse error, host is empty, proxy = " + proxy);
        }
        if (port <= 0) {
            throw new IllegalArgumentException("proxy parse error, port should > 0, proxy = " + proxy);
        }
    }

    //可达性检查：默认集合与各可用区节点去重后并发探测，同一节点只探测一次
    private static void checkProxyReachable(String schema, Collection<String> proxyList, Collection<List<String>> azProxyLists) {
        Set<String> proxies = new TreeSet<>(proxyList);
        for (List<String> each : azProxyLists) {
            proxies.addAll(each);
        }
        if (proxies.isEmpty()) {
            return;
        }
        Map<String, ReachableState> reachable = checkReachable(proxies);
        for (String proxy : proxies) {
            ReachableState state = reachable.get(proxy);
            if (state == ReachableState.UNREACHABLE) {
                logger.warn("schema = {}, sql-proxy = {} not reachable, please check config", schema, proxy);
            } else if (state == null) {
                //没回收到的任务结论未知（如域名解析慢导致探测未在预算内返回），不能报成不可达误导排查
                logger.warn("schema = {}, sql-proxy = {} reachability probe not finished, result unknown", schema, proxy);
            }
        }
    }

    /**
     * checkReachable
     * @param proxies 待探测的 sql-proxy 列表
     * @return 节点 -> 探测结论；未在等待预算内回收的节点不会出现在结果里（调用方按「结论未知」处理）
     */
    public static Map<String, ReachableState> checkReachable(Collection<String> proxies) {
        Map<String, ReachableState> result = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        for (String proxy : new TreeSet<>(proxies)) {
            futures.add(reachableCheckExecutor.submit(() -> result.put(proxy, probe(proxy))));
        }
        for (Future<?> future : futures) {
            try {
                future.get(REACHABLE_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.warn("check sql proxy reachable timeout or error, {}", e.toString());
            }
        }
        return result;
    }

    private static ReachableState probe(String proxy) {
        String[] split = proxy.split(":");
        if (split.length != 2) {
            return ReachableState.UNREACHABLE;
        }
        try {
            return checkHostPort(split[0], Integer.parseInt(split[1]))
                    ? ReachableState.REACHABLE : ReachableState.UNREACHABLE;
        } catch (Exception e) {
            return ReachableState.UNREACHABLE;
        }
    }

    //1 可达 / 0 不可达 / -1 探测未完成（结论未知，不要当成不可达）
    private static int reachableFlag(ReachableState state) {
        if (state == null || state == ReachableState.UNKNOWN) {
            return -1;
        }
        return state == ReachableState.REACHABLE ? 1 : 0;
    }

    public static boolean checkHostPort(String host, int port) {
        try (Socket socket = new Socket()) {
            try {
                int timeout = 1000;
                socket.connect(new InetSocketAddress(host, port), timeout);
                return true;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            logger.error("checkHostPort error, host = {}, port = {}", host, port, e);
            return false;
        }
    }

    public static JSONObject monitorJson(Map<String, SchemaConfig> configMap, ConfigType configType) {
        JSONObject monitorJson = new JSONObject();
        JSONArray infoJsonArray = new JSONArray();
        JSONObject info = new JSONObject();
        info.put("configType", configType);
        info.put("schemaSize", configMap.size());
        infoJsonArray.add(info);
        monitorJson.put("info", infoJsonArray);

        JSONArray proxyJsonArray = new JSONArray();
        for (Map.Entry<String, SchemaConfig> entry : configMap.entrySet()) {
            SchemaConfig schemaConfig = entry.getValue();
            JSONObject json = new JSONObject();
            json.put("schema", schemaConfig.getSchema());
            json.put("authEnable", schemaConfig.isAuthEnable() ? 1 : 0);
            json.put("apiKeySize", schemaConfig.getApiKeys().size());
            json.put("policy", schemaConfig.getPolicy().getValue());
            json.put("azSize", schemaConfig.getAzList().size());
            json.put("proxySize", schemaConfig.getProxyList().size());
            proxyJsonArray.add(json);
        }
        monitorJson.put("schemaInfo", proxyJsonArray);

        //可达性统一批量探测、按节点去重：同一节点可能同时出现在默认集合与多个可用区里，
        //逐条串行探测会让 /monitor 退化成 2 x N 次 1s 连接
        Set<String> allProxies = new TreeSet<>();
        for (SchemaConfig schemaConfig : configMap.values()) {
            allProxies.addAll(schemaConfig.getProxyList());
            Map<String, List<String>> azProxyMap = schemaConfig.getAzProxyMap();
            if (azProxyMap != null) {
                for (List<String> each : azProxyMap.values()) {
                    allProxies.addAll(each);
                }
            }
        }
        Map<String, ReachableState> reachable = checkReachable(allProxies);

        JSONArray azProxyDetailJsonArray = new JSONArray();
        for (Map.Entry<String, SchemaConfig> entry : configMap.entrySet()) {
            SchemaConfig schemaConfig = entry.getValue();
            if (!schemaConfig.isAzPolicy()) {
                continue;
            }
            for (Map.Entry<String, List<String>> azEntry : schemaConfig.getAzProxyMap().entrySet()) {
                for (String proxy : azEntry.getValue()) {
                    JSONObject json = new JSONObject();
                    json.put("schema", entry.getKey());
                    json.put("az", azEntry.getKey());
                    json.put("proxy", proxy);
                    json.put("reachable", reachableFlag(reachable.get(proxy)));
                    azProxyDetailJsonArray.add(json);
                }
            }
        }
        monitorJson.put("azProxyDetail", azProxyDetailJsonArray);

        JSONArray proxyDetailJsonArray = new JSONArray();
        for (Map.Entry<String, SchemaConfig> entry : configMap.entrySet()) {
            SchemaConfig schemaConfig = entry.getValue();
            for (String proxy : schemaConfig.getProxyList()) {
                JSONObject json = new JSONObject();
                json.put("schema", entry.getKey());
                json.put("proxy", proxy);
                json.put("reachable", reachableFlag(reachable.get(proxy)));
                proxyDetailJsonArray.add(json);
            }
        }
        monitorJson.put("proxyDetail", proxyDetailJsonArray);
        return monitorJson;
    }

}
