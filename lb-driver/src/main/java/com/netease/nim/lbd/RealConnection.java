package com.netease.nim.lbd;

import com.netease.nim.lbd.util.MySqlUrlUtils;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;

/**
 * 实际连接的一个封装，方便状态处理
 */
public class RealConnection {

    private final Driver realDriver;
    private final SqlProxy sqlProxy;
    private final LBDriverUrl lbDriverUrl;

    private SQLException lastException = null;

    private volatile Connection physicalConnection;
    private volatile boolean isHealthy = true;
    private volatile boolean isClosed = false;

    public RealConnection(SqlProxy sqlProxy, Driver realDriver, LBDriverUrl lbDriverUrl) {
        this.sqlProxy = sqlProxy;
        this.realDriver = realDriver;
        this.lbDriverUrl = lbDriverUrl;
    }

    public boolean syncCreating() throws SQLException {
        if (physicalConnection != null) {
            return false;
        }
        if (!isHealthy) {
            throw lastException != null ? lastException : new SQLException("sync creating connection failed.");
        }
        synchronized (this) {
            if (physicalConnection == null) {
                try {
                    physicalConnection = realDriver.connect(MySqlUrlUtils.url(sqlProxy, lbDriverUrl.getSchemaName()), lbDriverUrl.getInfo());
                    return true;
                } catch (SQLException e) {
                    lastException = e;
                    isHealthy = false;
                    throw new SqlProxyConnectException(e);
                }
            }
        }
        return false;
    }

    public Connection getPhysicalConnection() {
    	if (physicalConnection == null) {
            throw new IllegalStateException("Use syncCreating() before getPhysicalConnection()");
        }
		return physicalConnection;
    }

    public SqlProxy getSqlProxy() {
        return sqlProxy;
    }

    public void markUnhealthy() {
        this.isHealthy = false;
    }

    public boolean isHealthy() {
        return isHealthy;
    }

    public boolean isClosed() {
        return isClosed;
    }
    
    public void close() {
    	synchronized (this) {
	    	isClosed = true;
	    	if (physicalConnection != null) {
                try {
                    physicalConnection.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
    	}
    }

}
