package com.netease.nim.lbd;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by caojiajun on 2025/12/3
 */
public class LBContainerFactory {

    private static final LBContainerFactory instance = new LBContainerFactory();

    private final ConcurrentHashMap<String, LBContainer> map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SqlProxyProvider> sqlProxyProviderMap = new ConcurrentHashMap<>();

    private LBContainerFactory() {
    }

    /**
     * get instance
     * @return LBContainerFactory
     */
    public static LBContainerFactory getInstance() {
        return instance;
    }

    /**
     * get LBContainer by lbDriverUrl
     * @param lbDriverUrl lbDriverUrl
     * @return LBContainer
     */
    public LBContainer get(LBDriverUrl lbDriverUrl) {
        LBContainer lbContainer = map.get(lbDriverUrl.getUrl());
        if (lbContainer != null) {
            return lbContainer;
        }
        SqlProxyProvider sqlProxyProvider = initSqlProxyProvider(lbDriverUrl);
        return map.computeIfAbsent(lbDriverUrl.getUrl(), k -> new LBContainer(lbDriverUrl, sqlProxyProvider));
    }

    private SqlProxyProvider initSqlProxyProvider(LBDriverUrl lbDriverUrl) {
        SqlProxyProvider sqlProxyProvider = sqlProxyProviderMap.get(lbDriverUrl.getUrl());
        if (sqlProxyProvider != null) {
            return sqlProxyProvider;
        }
        LBDriverType type = lbDriverUrl.getType();
        return sqlProxyProviderMap.computeIfAbsent(lbDriverUrl.getUrl(), k -> {
            if (type == LBDriverType.local) {
                return new LocalSqlProxyProvider(lbDriverUrl);
            } else if (type == LBDriverType.remote) {
                return new RemoteSqlProxyProvider(lbDriverUrl);
            } else {
                throw new IllegalArgumentException("not support lb driver type");
            }
        });
    }
}
