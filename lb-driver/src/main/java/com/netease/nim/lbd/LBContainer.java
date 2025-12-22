package com.netease.nim.lbd;

import com.netease.nim.lbd.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created by caojiajun on 2025/12/3
 */
public class LBContainer {

    private static final Logger logger = LoggerFactory.getLogger(LBContainer.class);
    private static final Logger statsLogger = LoggerFactory.getLogger("lbd-stats");

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("lbd-log-stats"));


    private final LBDriverUrl lbDriverUrl;
    private final ConnectionManager connectionManager;

    public LBContainer(LBDriverUrl lbDriverUrl, SqlProxyProvider sqlProxyProvider) {
        this.lbDriverUrl = lbDriverUrl;
        this.connectionManager = new ConnectionManager(lbDriverUrl, sqlProxyProvider);
        if (lbDriverUrl.isLogStats()) {
            scheduler.scheduleAtFixedRate(this::logStats, 1, 1, TimeUnit.MINUTES);
        }
    }

    /**
     * connect
     * @return LBConnection
     * @throws SQLException exception
     */
    public Connection connect() throws SQLException {
        return new LBConnection(connectionManager, lbDriverUrl);
    }

    //打印日志
    private void logStats() {
        try {
            LbdStats stats = connectionManager.stats();
            statsLogger.info("lbd stats, sql-proxy-count={}, logic-connect-count={}, total-connect-count={}, using-connect-count={}",
                    stats.getStatsList().size(), stats.getLogicalCount(), stats.getTotalCount(), stats.getUsingCount());
            for (LbdStats.SqlProxyStats sqlProxyStats : stats.getStatsList()) {
                statsLogger.info("sql-proxy={},online={},reachable={},using={},idle={},create={},reuse={},close={}",
                        sqlProxyStats.getSqlProxy(), sqlProxyStats.isOnline(), sqlProxyStats.isReachable(),
                        sqlProxyStats.getUsing(), sqlProxyStats.getIdle(), sqlProxyStats.getCreate(), sqlProxyStats.getReuse(), sqlProxyStats.getClose());
            }
        } catch (Exception e) {
            logger.error("lbd stats error", e);
        }
    }
}
