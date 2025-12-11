package com.netease.nim.lbd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by caojiajun on 2025/12/9
 */
public class LocalSqlProxyProvider implements SqlProxyProvider {

    private final LBDriverUrl lbDriverUrl;

    public LocalSqlProxyProvider(LBDriverUrl lbDriverUrl) {
        this.lbDriverUrl = lbDriverUrl;
    }

    @Override
    public List<SqlProxy> load() {
        List<SqlProxy> list = new ArrayList<>(lbDriverUrl.getSqlProxyList());
        Collections.shuffle(list);
        return list;
    }

    @Override
    public void addSqlProxyCallback(SqlProxyCallback callback) {

    }
}
