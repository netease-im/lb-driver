package com.netease.nim.lbd;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by caojiajun on 2025/12/3
 */
public class LBContainerFactory {

    private static final LBContainerFactory instance = new LBContainerFactory();

    private final ConcurrentHashMap<String, LBContainer> map = new ConcurrentHashMap<>();

    private LBContainerFactory() {
    }

    public static LBContainerFactory getInstance() {
        return instance;
    }

    public LBContainer get(LBDriverUrl lbDriverUrl) {
        LBContainer lbContainer = map.get(lbDriverUrl.getUrl());
        if (lbContainer == null) {
            synchronized (LBContainerFactory.class) {
                lbContainer = map.computeIfAbsent(lbDriverUrl.getUrl(), k -> new LBContainer(lbDriverUrl));
            }
        }
        return lbContainer;
    }
}
