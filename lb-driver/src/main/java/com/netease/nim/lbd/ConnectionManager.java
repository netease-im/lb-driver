package com.netease.nim.lbd;

import com.netease.nim.lbd.util.AdjustCount;
import com.netease.nim.lbd.util.AutoAdjustQueue;
import com.netease.nim.lbd.util.CloseUtils;
import com.netease.nim.lbd.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ConnectionManager
 * Created by caojiajun on 2025/12/3
 */
public class ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final LBDriverUrl lbDriverUrl;

    private final SqlProxyProvider sqlProxyProvider;

    private final Lock lock = new ReentrantLock();

    private final Map<SqlProxy, SqlProxyConnectionPool> poolMap = new ConcurrentHashMap<>();
    private final Map<SqlProxy, AtomicInteger> connectErrorCountMap = new ConcurrentHashMap<>();

    private final AutoAdjustQueue<SqlProxyConnectionPool> borrowQueue = new AutoAdjustQueue<>();
    private final AutoAdjustQueue<SqlProxyConnectionPool> balanceQueue = new AutoAdjustQueue<>();

    private int logicalCount = 0;
    private int totalCount = 0;
    private int usingCount = 0;

    private final AtomicBoolean healthCheckStatus = new AtomicBoolean(false);
    private final AtomicBoolean rebalanceStatus = new AtomicBoolean(false);

    /**
     * ConnectionManager
     * @param lbDriverUrl lbDriverUrl
     * @param sqlProxyProvider sqlProxyProvider
     */
    public ConnectionManager(LBDriverUrl lbDriverUrl, SqlProxyProvider sqlProxyProvider) {
        this.lbDriverUrl = lbDriverUrl;
        this.sqlProxyProvider = sqlProxyProvider;
        init();
        logger.info("lbd connection manager init success, url = {}", lbDriverUrl.getUrl());
    }

    private void init() {
        List<SqlProxy> list = sqlProxyProvider.load();
        if (list == null || list.isEmpty()) {
            throw new IllegalStateException("sql proxy list is empty");
        }
        lock.lock();
        try {
            for (SqlProxy sqlProxy : list) {
                SqlProxyConnectionPool pool = new SqlProxyConnectionPool(sqlProxy);
                poolMap.put(sqlProxy, pool);
            }
        } finally {
            lock.unlock();
        }
        sqlProxyProvider.addSqlProxyCallback(new SqlProxyCallback() {
            @Override
            public void add(List<SqlProxy> list) {
                addSqlProxy(list);
            }

            @Override
            public void remove(List<SqlProxy> list) {
                removeSqlProxy(list);
            }

            @Override
            public void setUsable(List<SqlProxy> list, boolean usable) {
                setSqlProxyUsable(list, usable);
            }
        });
        //首轮健康检查标记为启动轮：provider 可在该轮回调里同步收敛（例如降级所需的兜底集合是重新
        //向 config-server 拉取的，运行期不能阻塞业务线程、只能异步），启动成功与否取决于这一轮结束时
        //有没有可用节点，所以启动轮必须让它收敛完再判定
        checkStartupReachable();
        if (!hasUsableSqlProxy()) {
            throw new IllegalArgumentException("There is no reachable sql proxy");
        }
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory("lbd-connection-manager"));
        scheduler.scheduleAtFixedRate(this::checkBalance, lbDriverUrl.getCheckBalanceIntervalSeconds(), lbDriverUrl.getCheckBalanceIntervalSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkReachable, lbDriverUrl.getCheckHealthIntervalSeconds(), lbDriverUrl.getCheckHealthIntervalSeconds(), TimeUnit.SECONDS);
    }


    /**
     * 新建一个逻辑连接
     * 如果实际连接数小于逻辑连接数，则此时就应该把实际连接建立起来，如果建立失败，直接报错
     * 允许重试1次
     * @throws SQLException 异常
     */
    public void registerLogicalConnection() throws SQLException {
        Set<SqlProxyConnectionPool> excludes = new HashSet<>();
        RealConnection connection = null;
        SQLException ex = null;
        int retry = 2;
        for (int i=0; i<retry; i++) {
            lock.lock();
            SqlProxyConnectionPool pool;
            try {
                if (totalCount >= logicalCount + 1) {
                    logicalCount ++;
                    return;
                }
                pool = balanceQueue.peekHeadExcludeRandomly(excludes);
                if (pool == null) {
                    throw new SQLException("There is no reachable sql proxy");
                }
                connection = pool.getConnection(true, false);
                excludes.add(pool);
            } finally {
                lock.unlock();
            }
            try {
                syncCreating(pool, connection);
                break;
            } catch (SQLException e) {
                ex = e;
            }
        }
        if (connection.isHealthy()) {
            lock.lock();
            try {
                logicalCount ++;
            } finally {
                lock.unlock();
            }
            return;
        }
        if (ex != null) {
            throw ex;
        }
        throw new SQLException("There is no reachable sql proxy");
    }

    /**
     * 销毁一个逻辑连接
     * 如果实际连接数大于逻辑连接数，则需要销毁实际连接
     * @param lbConnection 逻辑连接
     */
    public void unregisterLogicalConnection(LBConnection lbConnection) {
        RealConnection realConnection = lbConnection.getRealConnection();
        boolean needClose;
        lock.lock();
        try {
            logicalCount --;
            if (realConnection != null) {//如果逻辑连接中包含实际连接，处理该实际连接即可
                SqlProxy sqlProxy = realConnection.getSqlProxy();
                SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                if (pool == null) {
                    needClose = true;
                } else {
                    boolean closeIdleConnection = logicalCount < totalCount;
                    needClose = pool.returnConnection(realConnection, closeIdleConnection);
                }
            } else {//如果逻辑连接中不包含实际连接，则处理连接池中的连接
                boolean closeIdleConnection = logicalCount < totalCount;
                if (closeIdleConnection) {
                    Set<SqlProxyConnectionPool> excludes = new HashSet<>();
                    while (true) {
                        SqlProxyConnectionPool pool = balanceQueue.peekTailExclude(excludes);
                        if (pool == null) {
                            break;
                        }
                        realConnection = pool.getConnection2Close();
                        if (realConnection != null) {
                            break;
                        }
                        excludes.add(pool);
                    }
                }
                needClose = closeIdleConnection;
            }
        } finally {
            lock.unlock();
        }
        if (needClose && realConnection != null) {
            realConnection.close();
        }
    }

    /**
     * 归还一个实际连接
     * @param realConnection 实际连接
     */
    public void returnConnection(RealConnection realConnection) {
        boolean needClose;
        lock.lock();
        try {
            SqlProxyConnectionPool pool = poolMap.get(realConnection.getSqlProxy());
            if (pool != null) {
                needClose = pool.returnConnection(realConnection, false);
            } else {
                needClose = true;
            }
        } finally {
            lock.unlock();
        }
        if (needClose) {
            realConnection.close();
        }
    }

    /**
     * 获取一个实际连接
     * 如果实际连接的已使用数量小于总数量，则从已有连接中获取
     * 允许重试1次
     * @return RealConnection
     * @throws SQLException 异常
     */
    public RealConnection getConnection() throws SQLException {
        RealConnection realConnection;
        Set<SqlProxyConnectionPool> excludes = new HashSet<>();
        SQLException ex = null;
        int retry = 2;
        for (int i=0; i<retry; i++) {
            SqlProxyConnectionPool pool;
            lock.lock();
            try {
                boolean useIdleConnection = usingCount < totalCount;
                pool = borrowQueue.peekHeadExclude(excludes);
                if (pool == null) {
                    throw new SQLException("There is no reachable sql proxy");
                }
                realConnection = pool.getConnection(!useIdleConnection, true);
                excludes.add(pool);
            } finally {
                lock.unlock();
            }
            try {
                syncCreating(pool, realConnection);
                return realConnection;
            } catch (SQLException e) {
                ex = e;
            }
        }
        throw ex;
    }

    /**
     * 统计数据
     * @return stats
     */
    public LbdStats stats() {
        LbdStats lbdStats = new LbdStats();
        List<LbdStats.SqlProxyStats > statsList = new ArrayList<>();
        lock.lock();
        try {
            lbdStats.setLogicalCount(logicalCount);
            lbdStats.setTotalCount(totalCount);
            lbdStats.setUsingCount(usingCount);
            for (Map.Entry<SqlProxy, SqlProxyConnectionPool> entry : poolMap.entrySet()) {
                statsList.add(entry.getValue().stats());
            }
        } finally {
            lock.unlock();
        }
        lbdStats.setStatsList(statsList);
        return lbdStats;
    }

    private void syncCreating(SqlProxyConnectionPool pool, RealConnection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalStateException("connection is null");
        }
        try {
            boolean connectSuccess = connection.syncCreating();
            if (connectSuccess) {
                getConnectErrorCount(pool.getSqlProxy()).set(0);
            }
        } catch (SQLException e) {
            boolean needClose;
            lock.lock();
            try {
                needClose = pool.returnConnection(connection, true);
            } finally {
                lock.unlock();
            }
            if (needClose) {
                connection.close();
            }
            //连续2次失败，则标记为不可达
            if (e instanceof SqlProxyConnectException) {
                AtomicInteger counter = getConnectErrorCount(pool.getSqlProxy());
                int count = counter.incrementAndGet();
                if (count >= 2) {
                    Set<RealConnection> toClose = new HashSet<>();
                    lock.lock();
                    counter.set(0);
                    try {
                        pool.setUnreachable();
                        //不可达后池被摘出负载均衡，池内已有的空闲连接不会再被任何路径回收
                        pool.drainIdleConnections(toClose);
                    } finally {
                        lock.unlock();
                    }
                    closeConnections(toClose);
                    //把最新存活快照回传给 provider：az 集合整体不可达时立即降级，不等待下一轮健康检查
                    notifyReachabilityCheck(currentReachableList(), false);
                }
                throw ((SqlProxyConnectException) e).getCause();
            }
            throw e;
        }
    }

    private AtomicInteger getConnectErrorCount(SqlProxy sqlProxy) {
        AtomicInteger count = connectErrorCountMap.get(sqlProxy);
        if (count == null) {
            count = connectErrorCountMap.computeIfAbsent(sqlProxy, k -> new AtomicInteger(0));
        }
        return count;
    }

    //启动阶段的首轮检查
    private void checkStartupReachable() {
        checkReachable(true);
    }

    //定时检查所有的sql-proxy是否可达
    private void checkReachable() {
        checkReachable(false);
    }

    private void checkReachable(boolean startup) {
        if (healthCheckStatus.compareAndSet(false, true)) {
            try {
                List<SqlProxy> reachableList = new ArrayList<>();
                Set<SqlProxy> set = new HashSet<>(poolMap.keySet());
                for (SqlProxy sqlProxy : set) {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null) {
                        continue;
                    }
                    if (!pool.isOnline()) {
                        continue;
                    }
                    boolean reachable = checkReachable0(sqlProxy, pool);
                    if (reachable) {
                        reachableList.add(sqlProxy);
                    }
                    Set<RealConnection> toClose = new HashSet<>();
                    lock.lock();
                    try {
                        if (reachable && !pool.isReachable()) {
                            pool.setReachable();
                            logger.info("sql proxy = {}, reachable = false -> true", sqlProxy);
                        } else if (!reachable && pool.isReachable()) {
                            pool.setUnreachable();
                            //不可达后池被摘出负载均衡，池内已有的空闲连接不会再被任何路径回收
                            pool.drainIdleConnections(toClose);
                            logger.info("sql proxy = {}, reachable = true -> false", sqlProxy);
                        }
                    } finally {
                        lock.unlock();
                    }
                    closeConnections(toClose);
                }
                //存活快照回传给 provider，降级与恢复都由 provider 决定，本类只上报事实
                notifyReachabilityCheck(reachableList, startup);
            } catch (Exception e) {
                logger.error("checkReachable error, url = {}", lbDriverUrl.getUrl(), e);
            } finally {
                healthCheckStatus.compareAndSet(true, false);
            }
        }
    }

    //把存活快照回传给 provider，必须在 lock 外调用
    private void notifyReachabilityCheck(List<SqlProxy> reachableList, boolean startup) {
        try {
            sqlProxyProvider.onReachabilityCheck(reachableList, startup);
        } catch (Exception e) {
            logger.error("onReachabilityCheck error, url = {}", lbDriverUrl.getUrl(), e);
        }
    }

    //不重新做健康检查，只按当前池状态汇总存活节点
    private List<SqlProxy> currentReachableList() {
        List<SqlProxy> list = new ArrayList<>();
        lock.lock();
        try {
            for (Map.Entry<SqlProxy, SqlProxyConnectionPool> entry : poolMap.entrySet()) {
                SqlProxyConnectionPool pool = entry.getValue();
                if (pool.isOnline() && pool.isReachable()) {
                    list.add(entry.getKey());
                }
            }
        } finally {
            lock.unlock();
        }
        return list;
    }

    //检查sql proxy是否可达
    private boolean checkReachable0(SqlProxy sqlProxy, SqlProxyConnectionPool pool) {
        RealConnection connection;
        if (pool == null) {
            connection = new RealConnection(sqlProxy, LBDriverEnv.getRealDriver(), lbDriverUrl);
        } else {
            connection = pool.checkReadableConnection;
            if (connection == null) {
                connection = new RealConnection(sqlProxy, LBDriverEnv.getRealDriver(), lbDriverUrl);
                pool.checkReadableConnection = connection;
            }
        }
        ResultSet rs = null;
        Statement stmt = null;
        boolean reachable;
        try {
            connection.syncCreating();
            stmt = connection.getPhysicalConnection().createStatement();
            rs = stmt.executeQuery(Constants.VALIDATION_QUERY);
            reachable = rs.next();
        } catch (Exception e) {
            reachable = false;
        }
        if (!reachable) {
            logger.warn("sql proxy = {} not reachable", sqlProxy);
        }
        CloseUtils.close(rs);
        CloseUtils.close(stmt);
        if (pool == null) {
            CloseUtils.close(connection);
        } else {
            if (!reachable) {
                CloseUtils.close(connection);
                pool.checkReadableConnection = null;
            }
        }
        return reachable;
    }

    //定时检查是否连接数均衡
    private void checkBalance() {
        if (rebalanceStatus.compareAndSet(false, true)) {
            try {
                checkSqlProxyListFromProvider();
                rebalance();
                removeOfflineSqlProxy();
            } catch (Exception e) {
                logger.error("rebalance error, url = {}", lbDriverUrl.getUrl(), e);
            } finally {
                rebalanceStatus.compareAndSet(true, false);
            }
        }
    }

    //负载均衡
    //平衡sql-proxy节点之间的连接数
    private void rebalance() {
        try {
            SqlProxyConnectionPool pool = null;
            RealConnection target = null;
            boolean needDrop;
            boolean needRaise;
            Map<String, String> infoMap = new HashMap<>();
            lock.lock();
            try {
                infoMap.put("logicCount", String.valueOf(logicalCount));
                infoMap.put("totalCount", String.valueOf(totalCount));

                needDrop = logicalCount < totalCount;
                needRaise = false;
                if (!needDrop) {
                    needRaise = logicalCount > totalCount || !balanceQueue.isCountBalanced();
                    infoMap.put("isCountBalanced", String.valueOf(balanceQueue.isCountBalanced()));
                }
                if (needDrop) {
                    pool = balanceQueue.peekTail();
                    if (pool != null) {
                        target = pool.getConnection2Close();
                    }
                } else if (needRaise) {
                    Set<SqlProxyConnectionPool> excludes = new HashSet<>();
                    while (true) {
                        pool = balanceQueue.peekHeadExclude(excludes);
                        if (pool == null) {
                            break;
                        }
                        if (pool.isReachable()) {
                            break;
                        }
                        excludes.add(pool);
                    }
                    if (pool != null) {
                        target = pool.getConnection(true, false);
                    }
                }
            } finally {
                lock.unlock();
            }
            if (needDrop) {
                if (target != null) {
                    logger.info("rebalance, drop connection = {}, info = {}", target.getSqlProxy(), infoMap);
                    target.close();
                }
            } else if (needRaise) {
                if (target != null) {
                    logger.info("rebalance, raise connection = {}, info = {}", target.getSqlProxy(), infoMap);
                    syncCreating(pool, target);
                }
            }
        } catch (Exception e) {
            logger.error("rebalance error", e);
        }
    }

    //周期校准：按 provider 当前需要维护的完整集合补齐/摘除节点
    //基线用池内实际节点（poolMap.keySet()），不另存一份「期望集合」：池里已下线但还持有在用连接的节点
    //会重复出现在 removed 里，重复下线是幂等的；好处是被守卫 skip 掉的移除能在下一轮重试
    private void checkSqlProxyListFromProvider() {
        try {
            List<SqlProxy> newList = sqlProxyProvider.load();
            if (newList == null) {
                return;
            }
            if (newList.isEmpty()) {
                return;
            }
            List<SqlProxy> oldList = new ArrayList<>(poolMap.keySet());
            List<SqlProxy> added = new ArrayList<>(newList);
            added.removeAll(oldList);
            List<SqlProxy> removed = new ArrayList<>(oldList);
            removed.removeAll(newList);
            if (!added.isEmpty()) {
                Collections.shuffle(added);
                addSqlProxy(added);
            }
            if (!removed.isEmpty()) {
                Collections.shuffle(removed);
                removeSqlProxy(removed);
            }
        } catch (Exception e) {
            logger.error("checkSqlProxyListFromProvider error", e);
        }
    }

    //彻底删除下线sql-proxy，必须没有连接才可以操作
    private void removeOfflineSqlProxy() {
        try {
            Set<SqlProxy> set = new HashSet<>(poolMap.keySet());
            Set<RealConnection> toClosedConnection = new HashSet<>();
            Set<SqlProxy> offlinedSqlProxySet = new HashSet<>();
            for (SqlProxy sqlProxy : set) {
                lock.lock();
                try {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool != null && !pool.isOnline() && pool.isConnectionZero()) {
                        poolMap.remove(sqlProxy);
                        connectErrorCountMap.remove(sqlProxy);
                        RealConnection removed = pool.checkReadableConnection;
                        if (removed != null) {
                            toClosedConnection.add(removed);
                        }
                        offlinedSqlProxySet.add(sqlProxy);
                    }
                } catch (Exception e) {
                    logger.error("remove offline sql proxy error, sql proxy = {}", sqlProxy, e);
                } finally {
                    lock.unlock();
                }
            }
            for (SqlProxy sqlProxy : offlinedSqlProxySet) {
                logger.info("offline sql proxy = {} removed for connection 0", sqlProxy);
            }
            for (RealConnection realConnection : toClosedConnection) {
                CloseUtils.close(realConnection);
            }
        } catch (Exception e) {
            logger.error("remove offline sql proxy error", e);
        }
    }

    private void addSqlProxy(List<SqlProxy> list) {
        try {
            if (list == null || list.isEmpty()) {
                return;
            }
            for (SqlProxy sqlProxy : list) {
                boolean reachable = checkReachable0(sqlProxy, null);
                Set<RealConnection> toClose = new HashSet<>();
                lock.lock();
                try {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null) {
                        pool = new SqlProxyConnectionPool(sqlProxy);
                    }
                    boolean log = false;
                    if (reachable && !pool.isReachable()) {
                        pool.setReachable();
                        logger.info("sql proxy = {} online, reachable = true", sqlProxy);
                        log = true;
                    } else if (!reachable && pool.isReachable()) {
                        pool.setUnreachable();
                        logger.info("sql proxy = {} online, reachable = false", sqlProxy);
                        log = true;
                    }
                    if (!pool.isOnline()) {
                        pool.setOnline();
                        if (!log) {
                            logger.info("sql proxy = {} online, reachable = {}", sqlProxy, reachable);
                        }
                    }
                    pool.refreshUsableStatus();
                    poolMap.put(sqlProxy, pool);
                    if (!pool.isReachable() || !pool.isUsable()) {
                        //被摘出负载均衡的池不允许留空闲连接
                        pool.drainIdleConnections(toClose);
                    }
                } catch (Exception e) {
                    logger.error("add sql proxy = {} error", sqlProxy, e);
                } finally {
                    lock.unlock();
                }
                closeConnections(toClose);
            }
        } catch (Exception e) {
            logger.error("add sql proxy error, list = {}", list, e);
        }
    }

    //调整节点是否参与负载均衡：节点仍在池中继续被探测，只是不再承接新连接；已建立的连接不主动断开
    private void setSqlProxyUsable(List<SqlProxy> list, boolean usable) {
        try {
            if (list == null || list.isEmpty()) {
                return;
            }
            List<SqlProxy> changed = new ArrayList<>();
            Set<RealConnection> toClose = new HashSet<>();
            lock.lock();
            try {
                for (SqlProxy sqlProxy : list) {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null || pool.isUsable() == usable) {
                        continue;
                    }
                    pool.setUsable(usable);
                    if (!usable) {
                        //摘出负载均衡后池内已有的空闲连接不会再被任何路径回收
                        pool.drainIdleConnections(toClose);
                    }
                    changed.add(sqlProxy);
                }
            } finally {
                lock.unlock();
            }
            closeConnections(toClose);
            if (!changed.isEmpty()) {
                logger.info("sql proxy usable changed, list = {}, usable = {}, url = {}",
                        changed, usable, lbDriverUrl.getUrl());
            }
        } catch (Exception e) {
            logger.error("set sql proxy usable error, list = {}, usable = {}", list, usable, e);
        }
    }

    private void removeSqlProxy(List<SqlProxy> list) {
        try {
            if (list == null || list.isEmpty()) {
                return;
            }
            //provider 只会下发真正不再需要的节点，这里可以整体下线
            List<SqlProxy> toRemove = new ArrayList<>(list);
            boolean allSqlProxyRemoving = true;
            for (SqlProxy sqlProxy : poolMap.keySet()) {
                if (!toRemove.contains(sqlProxy)) {
                    allSqlProxyRemoving = false;
                    break;
                }
            }
            if (allSqlProxyRemoving) {
                logger.error("try remove all sql proxy, skip");
                return;
            }
            boolean allSqlProxyOfflineOrUnreachable = true;
            for (SqlProxy sqlProxy : poolMap.keySet()) {
                if (toRemove.contains(sqlProxy)) {
                    continue;
                }
                SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                if (pool == null) {
                    continue;
                }
                if (pool.isOnline() && pool.isReachable()) {
                    allSqlProxyOfflineOrUnreachable = false;
                    break;
                }
            }
            if (allSqlProxyOfflineOrUnreachable) {
                logger.error("try remove all reachable sql proxy, skip");
                return;
            }
            for (SqlProxy sqlProxy : toRemove) {
                Set<RealConnection> toCloseSet = new HashSet<>();
                lock.lock();
                try {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null) {
                        continue;
                    }
                    //标记下线
                    if (pool.isOnline()) {
                        pool.setOffline();
                        logger.info("sql proxy = {} offline", sqlProxy);
                    }
                    //直接关闭空闲连接
                    while (true) {
                        RealConnection connection2Close = pool.getConnection2Close();
                        if (connection2Close == null) {
                            break;
                        }
                        toCloseSet.add(connection2Close);
                    }
                    //等所有连接都关闭了，再从poolMap中移除
                } catch (Exception e) {
                    logger.error("remove sql proxy = {} error", sqlProxy, e);
                } finally {
                    lock.unlock();
                }
                if (!toCloseSet.isEmpty()) {
                    for (RealConnection connection : toCloseSet) {
                        try {
                            connection.close();
                        } catch (Exception e) {
                            logger.error("close idle connection error, sql proxy = {}", sqlProxy, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("remove sql proxy error, list = {}", list, e);
        }
    }

    //锁外关闭连接：关闭一个坏连接可能阻塞（例如等 read timeout），不能放在 lock 内
    private void closeConnections(Set<RealConnection> connections) {
        for (RealConnection connection : connections) {
            CloseUtils.close(connection);
        }
    }

    private boolean hasUsableSqlProxy() {
        for (SqlProxyConnectionPool pool : poolMap.values()) {
            if (pool.isOnline() && pool.isReachable() && pool.isUsable()) {
                return true;
            }
        }
        return false;
    }

    private class SqlProxyConnectionPool {

        private final SqlProxy sqlProxy;

        private AdjustCount<SqlProxyConnectionPool> hostActiveCount = null;
        private AdjustCount<SqlProxyConnectionPool> hostTotalCount = null;

        private boolean reachable = true;//是否可达
        private boolean online = true;//是否在线
        private volatile boolean usable = true;//是否参与负载均衡
        private final AtomicLong createCount = new AtomicLong(0);
        private final AtomicLong closeCount = new AtomicLong(0);
        private final AtomicLong reuseCount = new AtomicLong(0);

        //用于检测健康检查的链接
        private RealConnection checkReadableConnection;

        //空闲的连接
        private final Deque<RealConnection> idleConnections = new ArrayDeque<>();
        //使用中的连接
        private final Set<RealConnection> usingConnections = new HashSet<>();

        public SqlProxyConnectionPool(SqlProxy sqlProxy) {
            this.sqlProxy = sqlProxy;
            this.hostActiveCount = borrowQueue.createCountElement(this);
            this.hostTotalCount = balanceQueue.createCountElement(this);
        }

        public SqlProxy getSqlProxy() {
            return sqlProxy;
        }

        public boolean isConnectionZero() {
            return idleConnections.isEmpty() && usingConnections.isEmpty();
        }

        public boolean returnConnection(RealConnection connection, boolean close) {
            if (connection == null) {
                throw new IllegalStateException("return internal connection can not be null");
            }
            boolean needClose = close;
            if (close || !online || !reachable || !usable || !connection.isHealthy()) {
                if (usingConnections.remove(connection)) {
                    hostTotalCount.decrease();
                    hostActiveCount.decrease();
                    usingCount--;
                    totalCount--;
                } else if (idleConnections.remove(connection)) {
                    hostTotalCount.decrease();
                    totalCount--;
                }
                needClose = true;
            } else {
                if (usingConnections.remove(connection)) {
                    hostActiveCount.decrease();
                    idleConnections.add(connection);
                    usingCount--;
                }
            }
            if (needClose) {
                closeCount.incrementAndGet();
            }
            return needClose;//是否需要关闭，在上层关闭
        }

        public RealConnection getConnection(boolean create, boolean use) {
            RealConnection connection = create ? null : idleConnections.poll();
            if (connection == null) {
                connection = new RealConnection(sqlProxy, LBDriverEnv.getRealDriver(), lbDriverUrl);
                hostTotalCount.increase();
                totalCount++;
                createCount.incrementAndGet();
            } else {
                reuseCount.incrementAndGet();
            }
            if (use) {
                usingConnections.add(connection);
                hostActiveCount.increase();
                usingCount++;
            } else {
                idleConnections.add(connection);
            }
            return connection;
        }

        public RealConnection getConnection2Close() {
            RealConnection connection = idleConnections.poll();
            if (connection != null) {
                totalCount--;
                closeCount.incrementAndGet();
                hostTotalCount.decrease();
                return connection;
            } else {
                return null;
            }
        }

        //收掉池内已有的空闲连接。池被摘出负载均衡后（不可达或不参与负载均衡）这些连接业务取不到
        //（池已不在 borrowQueue），rebalance 也回收不到（池已不在 balanceQueue），留着只会让 totalCount 虚高：
        //rebalance 会据此误判"物理连接富余"（logicalCount < totalCount），转而从活跃池回收空闲连接，
        //结果是活跃池留不住空闲连接、每次请求重新建连。调用方必须持有 lock，返回的连接在锁外关闭
        public void drainIdleConnections(Set<RealConnection> toClose) {
            while (true) {
                RealConnection connection = idleConnections.poll();
                if (connection == null) {
                    break;
                }
                totalCount--;
                closeCount.incrementAndGet();
                hostTotalCount.decrease();
                toClose.add(connection);
            }
        }

        public void setUnreachable() {
            reachable = false;
            refreshUsableStatus();
        }

        public void setReachable() {
            reachable = true;
            refreshUsableStatus();
        }

        public void setOffline() {
            online = false;
            refreshUsableStatus();
        }

        public void setOnline() {
            online = true;
            refreshUsableStatus();
        }

        //节点参与负载均衡的条件：在线 && 可达 && provider 判定其当前参与负载均衡
        public void refreshUsableStatus() {
            if (online && reachable && usable) {
                hostActiveCount.active();
                hostTotalCount.active();
            } else {
                hostActiveCount.inactive();
                hostTotalCount.inactive();
            }
        }

        public boolean isOnline() {
            return online;
        }

        public boolean isReachable() {
            return reachable;
        }

        public boolean isUsable() {
            return usable;
        }

        public void setUsable(boolean usable) {
            this.usable = usable;
            refreshUsableStatus();
        }

        public LbdStats.SqlProxyStats stats() {
            LbdStats.SqlProxyStats sqlProxyStats = new LbdStats.SqlProxyStats();
            sqlProxyStats.setSqlProxy(sqlProxy);
            sqlProxyStats.setOnline(online);
            sqlProxyStats.setReachable(reachable);
            sqlProxyStats.setUsable(usable);
            sqlProxyStats.setUsing(usingConnections.size());
            sqlProxyStats.setIdle(idleConnections.size());
            sqlProxyStats.setCreate(createCount.getAndSet(0));
            sqlProxyStats.setReuse(reuseCount.getAndSet(0));
            sqlProxyStats.setClose(closeCount.getAndSet(0));
            return sqlProxyStats;
        }
    }
}
