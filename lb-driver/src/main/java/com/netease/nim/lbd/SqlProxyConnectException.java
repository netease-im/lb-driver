package com.netease.nim.lbd;

import java.sql.SQLException;

/**
 * Created by caojiajun on 2025/12/9
 */
public class SqlProxyConnectException extends SQLException {

    private final SQLException cause;

    public SqlProxyConnectException(SQLException cause) {
        this.cause = cause;
    }

    @Override
    public SQLException getCause() {
        return cause;
    }
}
