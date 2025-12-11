package com.netease.nim.lbd;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/9
 */
public interface SqlProxyProvider {

    List<SqlProxy> load();

    void addSqlProxyCallback(SqlProxyCallback callback);
}
