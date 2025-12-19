package com.netease.nim.lbd.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.ConnectionProxyImpl;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by caojiajun on 2025/12/19
 */
public class LbdDruidFilter extends FilterAdapter {

    @Override
    public ConnectionProxy connection_connect(FilterChain chain, Properties info) throws SQLException {
        DataSourceProxy dataSource = chain.getDataSource();
        Driver driver = dataSource.getRawDriver();
        String url = dataSource.getRawJdbcUrl();

        Connection nativeConnection = driver.connect(url, info);

        if (nativeConnection == null) {
            return null;
        }

        return new ConnectionProxyImpl(dataSource, nativeConnection, info, dataSource.createConnectionId());
    }
}
