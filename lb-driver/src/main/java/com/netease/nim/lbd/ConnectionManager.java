package com.netease.nim.lbd;

import com.netease.nim.lbd.util.AdjustCount;
import com.netease.nim.lbd.util.AutoAdjustQueue;
import com.netease.nim.lbd.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
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
        checkReachable();
        int reachableCount = 0;
        for (Map.Entry<SqlProxy, SqlProxyConnectionPool> entry : poolMap.entrySet()) {
            boolean reachable = entry.getValue().isReachable();
            if (reachable) {
                reachableCount ++;
            }
        }
        if (reachableCount <= 0) {
            throw new IllegalArgumentException("There is no reachable sql proxy");
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
        });
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory("lbd-connection-manager"));
        scheduler.scheduleAtFixedRate(this::checkBalance, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkReachable, 5, 5, TimeUnit.SECONDS);
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
        List<LbdStats.SqlProxyStats > statsList = new ArrayList<>();
        lock.lock();
        try {
            for (Map.Entry<SqlProxy, SqlProxyConnectionPool> entry : poolMap.entrySet()) {
                statsList.add(entry.getValue().stats());
            }
        } finally {
            lock.unlock();
        }
        LbdStats lbdStats = new LbdStats();
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
                    lock.lock();
                    counter.set(0);
                    try {
                        pool.setUnreachable();
                    } finally {
                        lock.unlock();
                    }
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

    //定时检查所有的sql-proxy是否可达
    private void checkReachable() {
        if (healthCheckStatus.compareAndSet(false, true)) {
            try {
                Set<SqlProxy> set = new HashSet<>(poolMap.keySet());
                for (SqlProxy sqlProxy : set) {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null) {
                        continue;
                    }
                    if (!pool.isOnline()) {
                        continue;
                    }
                    boolean reachable = checkReachable0(sqlProxy);
                    lock.lock();
                    try {
                        if (reachable && !pool.isReachable()) {
                            pool.setReachable();
                            logger.info("sql proxy = {}, reachable = false -> true", sqlProxy);
                        } else if (!reachable && pool.isReachable()) {
                            pool.setUnreachable();
                            logger.info("sql proxy = {}, reachable = true -> false", sqlProxy);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Exception e) {
                logger.error("checkReachable error, url = {}", lbDriverUrl.getUrl(), e);
            } finally {
                healthCheckStatus.compareAndSet(true, false);
            }
        }
    }

    //检查sql proxy是否可达
    private boolean checkReachable0(SqlProxy sqlProxy) {
        RealConnection realConnection = new RealConnection(sqlProxy, LBDriverEnv.getRealDriver(), lbDriverUrl);
        try {
            realConnection.syncCreating();
            return true;
        } catch (Exception e) {
            logger.warn("sql proxy = {} not reachable", sqlProxy);
            return false;
        } finally {
            realConnection.close();
        }
    }

    //定时检查是否连接数均衡
    private void checkBalance() {
        if (rebalanceStatus.compareAndSet(false, true)) {
            try {
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
            lock.lock();
            try {
                needDrop = logicalCount < totalCount;
                needRaise = false;
                if (!needDrop) {
                    needRaise = logicalCount > totalCount || !balanceQueue.isCountBalanced();
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
                    target.close();
                }
            } else if (needRaise) {
                if (target != null) {
                    syncCreating(pool, target);
                }
            }
        } catch (Exception e) {
            logger.error("rebalance error", e);
        }
    }

    //彻底删除下线sql-proxy，必须没有连接才可以操作
    private void removeOfflineSqlProxy() {
        try {
            Set<SqlProxy> set = new HashSet<>(poolMap.keySet());
            for (SqlProxy sqlProxy : set) {
                lock.lock();
                try {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool != null && !pool.isOnline() && pool.isConnectionZero()) {
                        poolMap.remove(sqlProxy);
                        connectErrorCountMap.remove(sqlProxy);
                        logger.info("offline sql proxy = {} removed for connection 0", sqlProxy);
                    }
                } catch (Exception e) {
                    logger.error("remove offline sql proxy error, sql proxy = {}", sqlProxy, e);
                } finally {
                    lock.unlock();
                }
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
                boolean reachable = checkReachable0(sqlProxy);
                lock.lock();
                try {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null) {
                        pool = new SqlProxyConnectionPool(sqlProxy);
                    }
                    if (reachable) {
                        pool.setReachable();
                    } else {
                        pool.setUnreachable();
                    }
                    pool.setOnline();
                    poolMap.put(sqlProxy, pool);
                } catch (Exception e) {
                    logger.error("add sql proxy = {} error", sqlProxy, e);
                } finally {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            logger.error("add sql proxy error, list = {}", list, e);
        }
    }

    private void removeSqlProxy(List<SqlProxy> list) {
        try {
            Set<SqlProxy> set = new HashSet<>(poolMap.keySet());
            list.forEach(set::remove);
            if (set.isEmpty()) {
                logger.error("try remove all sql proxy, skip");
                return;
            }
            boolean allSqlProxyOfflineOrUnreachable = true;
            for (SqlProxy sqlProxy : set) {
                SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                if (pool == null) {
                    continue;
                }
                if (pool.isOnline() || pool.isReachable()) {
                    allSqlProxyOfflineOrUnreachable = false;
                }
            }
            if (allSqlProxyOfflineOrUnreachable) {
                logger.error("try remove all reachable sql proxy, skip");
                return;
            }
            for (SqlProxy sqlProxy : list) {
                Set<RealConnection> toCloseSet = new HashSet<>();
                lock.lock();
                try {
                    SqlProxyConnectionPool pool = poolMap.get(sqlProxy);
                    if (pool == null) {
                        continue;
                    }
                    //标记下线
                    pool.setOffline();
                    logger.info("sql proxy = {} offline", sqlProxy);
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

    private class SqlProxyConnectionPool {

        private final SqlProxy sqlProxy;

        private AdjustCount<SqlProxyConnectionPool> hostActiveCount = null;
        private AdjustCount<SqlProxyConnectionPool> hostTotalCount = null;

        private boolean reachable = true;//是否可达
        private boolean online = true;//是否在线

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
            if (close || !online || !reachable || !connection.isHealthy()) {
                if (usingConnections.remove(connection)) {
                    hostTotalCount.decreaseAndGet();
                    hostActiveCount.decreaseAndGet();
                    usingCount--;
                    totalCount--;
                } else if (idleConnections.remove(connection)) {
                    hostTotalCount.decreaseAndGet();
                    totalCount--;
                }
                needClose = true;
            } else {
                if (usingConnections.remove(connection)) {
                    hostActiveCount.decreaseAndGet();
                    idleConnections.add(connection);
                    usingCount--;
                }
            }
            return needClose;//是否需要关闭，在上层关闭
        }

        public RealConnection getConnection(boolean create, boolean use) {
            RealConnection connection = create ? null : idleConnections.poll();
            if (connection == null) {
                connection = new RealConnection(sqlProxy, LBDriverEnv.getRealDriver(), lbDriverUrl);
                hostTotalCount.increaseAndGet();
                totalCount++;
            }
            if (use) {
                usingConnections.add(connection);
                hostActiveCount.increaseAndGet();
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
                return connection;
            } else {
                return null;
            }
        }

        public void setUnreachable() {
            reachable = false;
            hostActiveCount.inactive();
            hostTotalCount.inactive();
        }

        public void setReachable() {
            reachable = true;
            hostActiveCount.active();
            hostTotalCount.active();
        }

        public void setOffline() {
            online = false;
            hostActiveCount.inactive();
            hostTotalCount.inactive();
        }

        public void setOnline() {
            online = true;
            hostActiveCount.active();
            hostTotalCount.active();
        }

        public boolean isOnline() {
            return online;
        }

        public boolean isReachable() {
            return reachable;
        }

        public LbdStats.SqlProxyStats stats() {
            LbdStats.SqlProxyStats sqlProxyStats = new LbdStats.SqlProxyStats();
            sqlProxyStats.setSqlProxy(sqlProxy);
            sqlProxyStats.setOnline(online);
            sqlProxyStats.setReachable(reachable);
            sqlProxyStats.setUsing(usingConnections.size());
            sqlProxyStats.setIdle(idleConnections.size());
            return sqlProxyStats;
        }
    }
}
