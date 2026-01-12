package com.netease.nim.lbd;

import com.netease.nim.lbd.util.LBDriverUrlParser;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * LBDriver
 * Created by caojiajun on 2025/12/3
 */
public class LBDriver implements Driver {

    static {
        LBDriverEnv.init();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, info);
        if (lbDriverUrl == null) {
            return null;
        }
        return LBContainerFactory.getInstance().get(lbDriverUrl).connect();
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith(Constants.LB_DRIVER_URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        LBDriverUrl lbDriverUrl = LBDriverUrlParser.parseUrl(url, info);
        if (lbDriverUrl == null) {
            return null;
        }
        return LBDriverEnv.getRealDriver().getPropertyInfo(Constants.MYSQL_DRIVER_URL_PREFIX + "127.0.0.1:3306" + "/", lbDriverUrl.getInfo());
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("no logging");
    }
}
