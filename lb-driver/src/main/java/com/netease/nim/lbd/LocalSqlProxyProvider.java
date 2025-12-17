package com.netease.nim.lbd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by caojiajun on 2025/12/9
 */
public class LocalSqlProxyProvider implements SqlProxyProvider {

    private static final Logger logger = LoggerFactory.getLogger(LocalSqlProxyProvider.class);

    private final LBDriverUrl lbDriverUrl;

    public LocalSqlProxyProvider(LBDriverUrl lbDriverUrl) {
        this.lbDriverUrl = lbDriverUrl;
        logger.info("lbd local sql proxy provider init success, sqlProxyList = {}", lbDriverUrl.getSqlProxyList());
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
