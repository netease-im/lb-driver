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

    public LBContainer(LBDriverUrl lbDriverUrl) {
        this.lbDriverUrl = lbDriverUrl;
        if (lbDriverUrl.getType() == LBDriverType.local) {
            this.connectionManager = new ConnectionManager(lbDriverUrl, new LocalSqlProxyProvider(lbDriverUrl));
        } else if (lbDriverUrl.getType() == LBDriverType.remote) {
            this.connectionManager = new ConnectionManager(lbDriverUrl, new DefaultSqlProxyProvider(lbDriverUrl));
        } else {
            throw new IllegalArgumentException("not support lb driver type");
        }
        if (lbDriverUrl.isLogStats()) {
            scheduler.scheduleAtFixedRate(this::logStats, 1, 1, TimeUnit.MINUTES);
        }
    }

    public Connection connect() throws SQLException {
        return new LBConnection(connectionManager, lbDriverUrl);
    }

    private void logStats() {
        try {
            LbdStats stats = connectionManager.stats();
            statsLogger.info("lbd stats, sql-proxy count = {}", stats.getStatsList().size());
            for (LbdStats.SqlProxyStats sqlProxyStats : stats.getStatsList()) {
                statsLogger.info("sql-proxy={},online={},reachable={},using={},idle={}",
                        sqlProxyStats.getSqlProxy(), sqlProxyStats.isOnline(), sqlProxyStats.isReachable(), sqlProxyStats.getUsing(), sqlProxyStats.getIdle());
            }
        } catch (Exception e) {
            logger.error("lbd stats error", e);
        }
    }
}
