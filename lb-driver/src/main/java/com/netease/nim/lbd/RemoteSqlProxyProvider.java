package com.netease.nim.lbd;

import com.netease.nim.lbd.util.LocalAddressResolver;
import com.netease.nim.lbd.util.NamedThreadFactory;
import com.netease.nim.lbd.util.ParamBuilder;
import com.netease.nim.lbd.util.TinyJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 从 config-server 拉取 sql-proxy 集合的 provider，多可用区（az）亲和与降级兜底都收敛在本类内部
 * <p>
 * ConnectionManager 对 az 无感知，只负责连接池与负载均衡，两者之间的契约只有：
 * <ul>
 *     <li>{@link #load()} 返回当前需要 driver 维护的完整节点集合：正常态就是本可用区集合，降级期间额外
 *         带上兜底集合（兜底集合承接流量，本可用区集合留在池中继续被探测，恢复判定依赖池内节点的存活快照）；
 *         恢复后兜底集合从池中摘掉，下次降级再重新加入（代价是重新探测与建链）</li>
 *     <li>每轮可达性检查后通过 {@link #onReachabilityCheck(List, boolean)} 把存活快照回传，由本类决定降级与恢复</li>
 *     <li>集合变化通过 {@link SqlProxyCallback#add(List)} / {@link SqlProxyCallback#remove(List)} 下发</li>
 *     <li>节点是否参与负载均衡通过 {@link SqlProxyCallback#setUsable(List, boolean)} 下发。只在降级期间用得到：
 *         本可用区集合必须留在池中（恢复判定依赖池内节点的存活快照）但不得承接流量，所以标记为不参与负载均衡；
 *         非降级期间 load() 里没有任何需要"留池但不用"的节点</li>
 *     <li>降级边沿的兜底拉取跑在刷新线程上（不能阻塞业务线程），只有启动阶段的首轮回调
 *         （{@code startup = true}，由启动线程调用）才同步拉取，否则启动成功与否无法判定</li>
 * </ul>
 * 降级只由服务端声明的亲和能力（响应 policy 字段）驱动：服务端未声明亲和能力时本类不做任何介入，
 * 行为与引入多 az 之前完全一致
 * <p>
 * Created by caojiajun on 2025/12/9
 */
public class RemoteSqlProxyProvider implements SqlProxyProvider {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSqlProxyProvider.class);

    private static final String FETCH_SQL_PROXY_LIST_PATH = "/fetch_sql_proxy_list";

    private static final String PARAM_SCHEMA = "schema";
    private static final String PARAM_MD5 = "md5";
    private static final String PARAM_AZ = "az";
    private static final String PARAM_IP = "ip";
    private static final String PARAM_POLICY = "policy";
    private static final String PARAM_CLIENT_FEATURE_LEVEL = "clientFeatureLevel";
    private static final String PARAM_CLIENT_VERSION = "clientVersion";

    private static final String POLICY_DEFAULT = "default";

    private final LBDriverUrl lbDriverUrl;
    private final List<SqlProxyCallback> callbackList = new ArrayList<>();

    //降级状态机：健康检查与建连失败快照可能从不同线程到来，也可能与刷新线程并发
    private final Object stateLock = new Object();
    //请求串行化：同一变体不并发拉取，避免 md5 与集合错乱
    private final Object fetchLock = new Object();

    //az 亲和（正常）请求变体：不带 policy，是否真的按 az 下发由服务端配置决定
    private volatile String affinityMd5;
    private volatile List<SqlProxy> affinityList = new ArrayList<>();
    private volatile String affinityPolicy;
    private volatile boolean affinityCapable = false;
    private volatile long lastAffinityFetchTime = 0;

    //兜底（policy=default）请求变体：两个变体各自维护 md5，避免 304 串味
    private volatile String fallbackMd5;
    private volatile List<SqlProxy> fallbackList = new ArrayList<>();
    private volatile long lastFallbackFetchTime = 0;

    //driver 当前需要维护的集合，即 load() 的返回值，也是增删下发的基线（由 stateLock 保护）
    private volatile List<SqlProxy> desiredList = Collections.emptyList();

    //降级状态位保证兜底请求只在边沿触发一次；recoverCount 用于恢复滞回（仅由 stateLock 保护）
    private volatile boolean fallbackActive = false;
    private int recoverCount = 0;
    //降级边沿触发的异步兜底拉取做在飞去重：兜底集合尚未生效时恢复滞回被压成 1 次健康检查，
    //az 可达性抖动会连续产生降级边沿，不去重就会在单线程刷新器上排队多次拉取
    private final AtomicBoolean fallbackRefreshPending = new AtomicBoolean(false);
    //运行期的状态下发可能探测新节点或关闭连接，统一在刷新线程执行；合并高频健康快照，避免任务排队无界增长
    private final AtomicInteger stateSyncWork = new AtomicInteger();

    //本机地址，按需解析一次后缓存；解析失败后按 azRefreshIntervalSeconds 退避重试
    private volatile String localIp;
    private volatile boolean localIpResolved = false;
    private volatile long lastResolveAttemptTime = 0;

    //刷新线程：降级边沿的兜底拉取也跑在这里，避免阻塞触发降级的业务线程
    private final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("lbd-sql-proxy-provider"));
    public RemoteSqlProxyProvider(LBDriverUrl lbDriverUrl) {
        this.lbDriverUrl = lbDriverUrl;
        FetchResponse response = null;
        for (int i=0; i<3; i++) {
            response = fetch(false, affinityMd5);
            if (response != null) {
                break;
            }
        }
        if (response == null) {
            throw new IllegalArgumentException("fetch sql proxy list error");
        }
        if (response.list == null || response.list.isEmpty()) {
            throw new IllegalArgumentException("fetch sql proxy list empty");
        }
        this.affinityList = response.list;
        this.affinityMd5 = response.md5;
        this.affinityPolicy = response.policy;
        this.affinityCapable = isAffinityCapable(response.policy);
        this.lastAffinityFetchTime = System.currentTimeMillis();
        this.desiredList = new ArrayList<>(response.list);
        refreshExecutor.scheduleAtFixedRate(this::refreshTick, 5, 5, TimeUnit.SECONDS);
        logger.info("lbd remote sql proxy provider init success, configServer = {}, schema = {}, az = {}, ip = {}, policy = {}, affinityCapable = {}, sqlProxyList = {}",
                configServer(), lbDriverUrl.getConfigServerSchema(), lbDriverUrl.getAz(), lbDriverUrl.getIp(),
                affinityPolicy, affinityCapable, affinityList);
    }

    @Override
    public List<SqlProxy> load() {
        //只返回当前真正需要 driver 维护的集合，由 desiredList 统一维护（见 desiredSet）
        return shuffleCopy(desiredList);
    }

    @Override
    public void addSqlProxyCallback(SqlProxyCallback callback) {
        synchronized (callbackList) {
            callbackList.add(callback);
        }
    }

    @Override
    public void removeSqlProxyCallback(SqlProxyCallback callback) {
        synchronized (callbackList) {
            callbackList.remove(callback);
        }
    }

    @Override
    public void onReachabilityCheck(List<SqlProxy> reachableList, boolean startup) {
        handleReachabilitySnapshot(reachableList, startup, true);
    }

    @Override
    public void onConnectionFailure(List<SqlProxy> reachableList) {
        handleReachabilitySnapshot(reachableList, false, false);
    }

    private void handleReachabilitySnapshot(List<SqlProxy> reachableList, boolean startup, boolean healthCheck) {
        Set<SqlProxy> reachableSet = new HashSet<>(reachableList);
        boolean degrade = false;
        boolean recover = false;
        synchronized (stateLock) {
            if (!affinityCapable) {
                //服务端未声明亲和能力：不介入，所有节点按现状参与负载均衡
                fallbackActive = false;
                recoverCount = 0;
            } else if (!hasReachable(affinityList, reachableSet)) {
                recoverCount = 0;
                if (!fallbackActive) {
                    fallbackActive = true;
                    degrade = true;
                }
            } else if (fallbackActive) {
                //业务失败快照可以触发降级，但不增加或清空连续健康检查的恢复计数
                if (healthCheck) {
                    //az 集合已有可达节点：连续若干次健康检查通过才切回
                    int required = requiredRecoverCheckCount();
                    recoverCount++;
                    if (recoverCount >= required) {
                        recoverCount = 0;
                        fallbackActive = false;
                        recover = true;
                    }
                }
            } else {
                recoverCount = 0;
            }
        }
        if (degrade) {
            logger.warn("no reachable sql proxy in az, enter az fallback, url = {}, configServer = {}, schema = {}, az = {}",
                    lbDriverUrl.getUrl(), configServer(), lbDriverUrl.getConfigServerSchema(), lbDriverUrl.getAz());
            //降级边沿：立即拉一次兜底集合，之后由刷新线程按 5s 节奏刷新
            if (startup) {
                //启动阶段的首轮回调由启动线程调用，且启动成功与否取决于本次是否收敛，允许同步拉
                refreshFallback();
            }
        } else if (recover) {
            logger.info("sql proxy in az recovered, exit az fallback, url = {}, configServer = {}, schema = {}, az = {}",
                    lbDriverUrl.getUrl(), configServer(), lbDriverUrl.getConfigServerSchema(), lbDriverUrl.getAz());
        }
        //每一轮都下发一次生效状态：兜底集合拉取可能失败，且 ConnectionManager 的周期校准可能补入新节点。
        //运行期的下发会触发节点探测或关闭连接，不能留在可能来自业务线程的回调里执行。
        if (startup) {
            syncState();
        } else {
            submitStateSync();
            if (degrade) {
                //先用已有兜底缓存收敛，再刷新兜底集合；首次降级没有缓存时由刷新完成后的 syncState 下发
                submitFallbackRefresh();
            }
        }
    }

    private void submitStateSync() {
        if (stateSyncWork.getAndIncrement() != 0) {
            return;
        }
        try {
            refreshExecutor.execute(() -> {
                int missed = 1;
                do {
                    try {
                        syncState();
                    } catch (Exception e) {
                        logger.error("sync sql proxy state error, configServer = {}, schema = {}",
                                configServer(), lbDriverUrl.getConfigServerSchema(), e);
                    }
                    missed = stateSyncWork.addAndGet(-missed);
                } while (missed != 0);
            });
        } catch (Exception e) {
            stateSyncWork.set(0);
            logger.error("submit sql proxy state sync error, configServer = {}, schema = {}",
                    configServer(), lbDriverUrl.getConfigServerSchema(), e);
        }
    }

    //降级边沿的兜底拉取放到刷新线程执行，不阻塞触发降级的线程；
    //同一时刻只保留一次在飞的拉取，重复的边沿直接丢弃（下一次由刷新线程的 5s 节奏兜住）
    private void submitFallbackRefresh() {
        if (!fallbackRefreshPending.compareAndSet(false, true)) {
            return;
        }
        try {
            refreshExecutor.submit(() -> {
                try {
                    refreshFallback();
                } catch (Exception e) {
                    logger.error("fallback refresh error, configServer = {}, schema = {}",
                            configServer(), lbDriverUrl.getConfigServerSchema(), e);
                } finally {
                    fallbackRefreshPending.set(false);
                }
            });
        } catch (Exception e) {
            fallbackRefreshPending.set(false);
            logger.error("submit fallback refresh error, configServer = {}, schema = {}",
                    configServer(), lbDriverUrl.getConfigServerSchema(), e);
        }
    }

    private void refreshTick() {
        try {
            long now = System.currentTimeMillis();
            if (fallbackActive) {
                //降级期间：兜底集合按 5s 节奏刷新；az 集合放慢到 azRefreshIntervalSeconds，
                //但仍保持探测，以发现故障期间运维替换或扩容的节点
                refreshFallback();
                if (now - lastAffinityFetchTime >= azRefreshIntervalMillis()) {
                    refreshAffinity();
                }
            } else {
                refreshAffinity();
                if (!fallbackList.isEmpty() && now - lastFallbackFetchTime >= azRefreshIntervalMillis()) {
                    //曾经降级过：兜底集合已从池中摘掉，这里只是慢速保持缓存新鲜，降低下次降级时的拉取代价
                    refreshFallback();
                }
            }
        } catch (Exception e) {
            logger.error("refresh sql proxy list error, configServer = {}, schema = {}",
                    configServer(), lbDriverUrl.getConfigServerSchema(), e);
        }
    }

    //刷新 az 亲和集合，同时更新服务端声明的亲和能力
    private void refreshAffinity() {
        try {
            synchronized (fetchLock) {
                lastAffinityFetchTime = System.currentTimeMillis();
                FetchResponse response = fetch(false, affinityMd5);
                if (response == null || response.code == 304 || response.list == null) {
                    return;
                }
                if (response.list.isEmpty()) {
                    logger.error("fetch sql proxy list empty, skip update");
                    return;
                }
                boolean capable = isAffinityCapable(response.policy);
                boolean capableChanged = capable != affinityCapable;
                this.affinityList = response.list;
                this.affinityMd5 = response.md5;
                this.affinityPolicy = response.policy;
                this.affinityCapable = capable;
                if (capableChanged) {
                    logger.info("sql proxy affinity capable changed, configServer = {}, schema = {}, az = {}, policy = {}, affinityCapable = {}",
                            configServer(), lbDriverUrl.getConfigServerSchema(), lbDriverUrl.getAz(), response.policy, capable);
                }
            }
        } finally {
            //集合与状态统一在锁外收敛：304 或拉取失败也要下发，亲和能力的变化同样影响需要维护的集合
            syncState();
        }
    }

    //刷新兜底集合
    private void refreshFallback() {
        try {
            synchronized (fetchLock) {
                lastFallbackFetchTime = System.currentTimeMillis();
                FetchResponse response = fetch(true, fallbackMd5);
                if (response == null || response.code == 304 || response.list == null) {
                    return;
                }
                if (response.list.isEmpty()) {
                    logger.error("fetch fallback sql proxy list empty, skip update");
                    return;
                }
                //降级边沿命中缓存（304）时也走这里收敛：此时兜底集合来自缓存，但状态已变化，集合必须下发
                this.fallbackList = response.list;
                this.fallbackMd5 = response.md5;
            }
        } finally {
            syncState();
        }
    }

    //状态与集合的一致性收敛：先按当前状态放回生效集合（切换期间不能出现没有任何可用节点的空窗），
    //再下发集合变化，最后再按状态纠正一次（新建的连接池默认参与负载均衡，非生效集合里新增的节点要在这里标回不可用）
    private void syncState() {
        applyState();
        publishDesired();
        applyState();
    }

    //把「driver 需要维护的集合」的当前值下发：先加后摘，避免切换期间出现没有节点的中间态；
    //节点增删会让 ConnectionManager 逐个做阻塞探测，所以必须在锁外下发
    private void publishDesired() {
        List<SqlProxy> added;
        List<SqlProxy> removed;
        synchronized (stateLock) {
            List<SqlProxy> newDesired = desiredSet();
            if (newDesired.isEmpty() || sameSet(newDesired, desiredList)) {
                return;
            }
            added = diff(newDesired, desiredList);
            removed = diff(desiredList, newDesired);
            desiredList = newDesired;
        }
        logger.info("sql proxy set for driver changed, configServer = {}, schema = {}, az = {}, added = {}, removed = {}",
                configServer(), lbDriverUrl.getConfigServerSchema(), lbDriverUrl.getAz(), added, removed);
        pushChange(added, removed);
    }

    //当前 driver 需要维护的集合：兜底集合生效时为「兜底集合 + 本可用区集合」，否则只有本可用区集合。
    //降级期间本可用区集合必须留在池中：健康检查只探测池内节点，摘掉它就再也收不到恢复信号；
    //兜底集合则在恢复后摘掉，下次降级按需重新加入（代价是重新探测与建链，故障是低频事件，可以接受）
    private List<SqlProxy> desiredSet() {
        return fallbackEffective() ? union(affinityList, fallbackList) : new ArrayList<>(affinityList);
    }

    private static boolean sameSet(List<SqlProxy> list1, List<SqlProxy> list2) {
        return list1.size() == list2.size() && new HashSet<>(list1).equals(new HashSet<>(list2));
    }

    //把当前生效状态下发给 ConnectionManager：先放回生效集合，再摘除非生效集合，避免出现空窗。
    //降级期间本可用区集合留在池里但必须摘出负载均衡（仍被探测以判定恢复）；非降级期间 load() 只含本可用区
    //集合，兜底集合根本不在池中，这一半调用是空转——ConnectionManager 对池内不存在的节点直接跳过
    private void applyState() {
        synchronized (stateLock) {
            List<SqlProxy> active = fallbackEffective() ? fallbackList : affinityList;
            List<SqlProxy> inactive = union(affinityList, fallbackList);
            inactive.removeAll(active);
            notifySetUsable(active, true);
            notifySetUsable(inactive, false);
        }
    }

    //集合变化下发，新增节点会被 ConnectionManager 持续探测
    private void pushChange(List<SqlProxy> added, List<SqlProxy> removed) {
        if (!added.isEmpty()) {
            Collections.shuffle(added);
            for (SqlProxyCallback callback : callbacks()) {
                try {
                    callback.add(added);
                } catch (Exception e) {
                    logger.error("SqlProxyCallback add error", e);
                }
            }
        }
        if (!removed.isEmpty()) {
            Collections.shuffle(removed);
            for (SqlProxyCallback callback : callbacks()) {
                try {
                    callback.remove(removed);
                } catch (Exception e) {
                    logger.error("SqlProxyCallback remove error", e);
                }
            }
        }
    }

    //必须在持有 stateLock 时调用
    private void notifySetUsable(List<SqlProxy> list, boolean usable) {
        if (list.isEmpty()) {
            return;
        }
        for (SqlProxyCallback callback : callbacks()) {
            try {
                callback.setUsable(list, usable);
            } catch (Exception e) {
                logger.error("SqlProxyCallback setUsable error, usable = {}", usable, e);
            }
        }
    }

    //降级生效的前提是已经拿到非空的兜底集合；否则继续保持 az 集合可用，避免出现所有集合都被摘除的空窗
    private boolean fallbackEffective() {
        return affinityCapable && fallbackActive && !fallbackList.isEmpty();
    }

    //恢复滞回所需的连续健康检查次数，必须在 stateLock 内调用
    //兜底集合未生效时（拉取失败，或服务端没有默认集合）没有可切换的集合，滞回上限压到 2 次即可：
    //够扛住 az 单轮抖动（降级态不来回翻转、也不反复触发兜底拉取），又不会在 az 真恢复后拖着降级态不放
    private int requiredRecoverCheckCount() {
        int configured = Math.max(1, lbDriverUrl.getRecoverCheckCount());
        return fallbackEffective() ? configured : Math.max(1, Math.min(configured, 2));
    }

    private static boolean hasReachable(List<SqlProxy> list, Set<SqlProxy> reachableSet) {
        for (SqlProxy sqlProxy : list) {
            if (reachableSet.contains(sqlProxy)) {
                return true;
            }
        }
        return false;
    }

    private static List<SqlProxy> diff(List<SqlProxy> source, List<SqlProxy> exclude) {
        List<SqlProxy> result = new ArrayList<>(source);
        result.removeAll(exclude);
        return result;
    }

    private static List<SqlProxy> union(List<SqlProxy> list1, List<SqlProxy> list2) {
        List<SqlProxy> result = new ArrayList<>(list1);
        for (SqlProxy sqlProxy : list2) {
            if (!result.contains(sqlProxy)) {
                result.add(sqlProxy);
            }
        }
        return result;
    }

    private static boolean isAffinityCapable(String policy) {
        return policy != null && !policy.isEmpty() && !POLICY_DEFAULT.equals(policy);
    }

    private static List<SqlProxy> shuffleCopy(List<SqlProxy> list) {
        List<SqlProxy> result = new ArrayList<>(list);
        Collections.shuffle(result);
        return result;
    }

    private long azRefreshIntervalMillis() {
        return Math.max(1, lbDriverUrl.getAzRefreshIntervalSeconds()) * 1000L;
    }

    private List<SqlProxyCallback> callbacks() {
        synchronized (callbackList) {
            return new ArrayList<>(callbackList);
        }
    }

    private String configServer() {
        return lbDriverUrl.getConfigServerHost() + ":" + lbDriverUrl.getConfigServerPort();
    }

    //解析本机地址：显式配置的 ip 优先，否则取连接 config-server 的 socket 本地地址并缓存
    //不把「是否上报 ip」与「是否配置 az」绑定：az 缺省正是依赖服务端按 ip 自动识别可用区的场景，
    //此时若不报本机 ip，可用区只能由请求来源 ip 推断，而 config-server 常部署在 nginx/nlb 之后
    private String resolveIp() {
        String ip = lbDriverUrl.getIp();
        if (ip != null) {
            return ip;
        }
        if (localIpResolved) {
            return localIp;
        }
        synchronized (this) {
            if (localIpResolved) {
                return localIp;
            }
            //解析失败要退避：解析一次会真的连一次 config-server（超时用 configServerTimeout），
            //每次刷新都重试会让刷新线程反复被拖住，日志也会跟着刷屏
            long now = System.currentTimeMillis();
            if (now - lastResolveAttemptTime < azRefreshIntervalMillis()) {
                return localIp;
            }
            lastResolveAttemptTime = now;
            String resolved = LocalAddressResolver.resolve(lbDriverUrl.getConfigServerHost(),
                    lbDriverUrl.getConfigServerPort(), lbDriverUrl.getConfigServerTimeout());
            if (resolved != null) {
                localIp = resolved;
                localIpResolved = true;
                logger.info("resolve local ip success, configServer = {}, schema = {}, ip = {}",
                        configServer(), lbDriverUrl.getConfigServerSchema(), resolved);
            } else {
                //解析失败则本次不发送 ip，由 config-server 用请求来源 ip 兜底；
                //重试按 azRefreshIntervalSeconds 退避（见上方判断），避免每次刷新都连一次 config-server
                logger.warn("resolve local ip error, configServer = {}, schema = {}, ip param will not be sent this time",
                        configServer(), lbDriverUrl.getConfigServerSchema());
            }
        }
        return localIp;
    }

    private FetchResponse fetch(boolean fallback, String md5) {
        ParamBuilder builder = new ParamBuilder();
        if (md5 != null) {
            builder.addParam(PARAM_MD5, md5);
        }
        builder.addParam(PARAM_SCHEMA, lbDriverUrl.getConfigServerSchema());
        //能力等级与版本号恒定携带：等级用于服务端门禁，版本号仅用于服务端日志观测
        builder.addParam(PARAM_CLIENT_FEATURE_LEVEL, Constants.CLIENT_FEATURE_LEVEL);
        builder.addParam(PARAM_CLIENT_VERSION, Constants.CLIENT_VERSION);
        if (fallback) {
            //降级请求显式声明要默认集合，否则服务端会按请求来源 ip 又下发本 az 集合
            builder.addParam(PARAM_POLICY, POLICY_DEFAULT);
        }
        String az = lbDriverUrl.getAz();
        if (az != null) {
            builder.addParam(PARAM_AZ, az);
        }
        String ip = resolveIp();
        if (ip != null) {
            builder.addParam(PARAM_IP, ip);
        }
        String requestParams = builder.build();

        String fullUrl;
        if (lbDriverUrl.getConfigServerPort() == 443) {
            fullUrl = "https://" + configServer() + FETCH_SQL_PROXY_LIST_PATH + "?" + requestParams;
        } else {
            fullUrl = "http://" + configServer() + FETCH_SQL_PROXY_LIST_PATH + "?" + requestParams;
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/json");
            if (lbDriverUrl.getConfigServerApiKey() != null) {
                conn.setRequestProperty("Authorization", "Bearer " + lbDriverUrl.getConfigServerApiKey());
            }
            conn.setConnectTimeout(lbDriverUrl.getConfigServerTimeout());
            conn.setReadTimeout(lbDriverUrl.getConfigServerTimeout());
            conn.setRequestMethod("GET");
            //
            int status = conn.getResponseCode();
            if (status != 200) {
                logger.error("config server error, status code = {}", status);
                return null;
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append(System.lineSeparator());
                }
            }
            Map<String, Object> json = TinyJson.parseObject(response.toString());
            int code = Integer.parseInt(json.get("code").toString());
            if (code == 304) {
                return new FetchResponse(304, null, null, null);
            }
            if (code != 200) {
                logger.error("config server error, response = {}", response);
                return null;
            }
            String md5Response = json.get("md5").toString();
            List<String> list = (List<String>) json.get("data");
            List<SqlProxy> newList = new ArrayList<>();
            for (String string : list) {
                String[] split = string.split(":");
                if (split.length != 2) {
                    throw new IllegalArgumentException("parse sql-proxy error = " + string);
                }
                SqlProxy sqlProxy = new SqlProxy(split[0], Integer.parseInt(split[1]));
                newList.add(sqlProxy);
            }
            String policy = json.get(PARAM_POLICY) != null ? json.get(PARAM_POLICY).toString() : null;
            return new FetchResponse(200, md5Response, newList, policy);
        } catch (Exception e) {
            logger.error("fetch sql proxy list error, configServer = {}, schema = {}, az = {}, fallback = {}",
                    configServer(), lbDriverUrl.getConfigServerSchema(), lbDriverUrl.getAz(), fallback, e);
            return null;
        } finally {
            try {
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e) {
                logger.error("http connection disconnect error", e);
            }
        }
    }

    private static class FetchResponse {
        int code;
        String md5;
        List<SqlProxy> list;
        String policy;

        public FetchResponse(int code, String md5, List<SqlProxy> list, String policy) {
            this.code = code;
            this.md5 = md5;
            this.list = list;
            this.policy = policy;
        }
    }

}
