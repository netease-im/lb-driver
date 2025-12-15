package com.netease.nim.lbd.config.server.service;

import com.netease.nim.lbd.config.server.model.SchemaConfig;

import java.util.Map;

/**
 * Created by caojiajun on 2025/12/10
 */
public interface ConfigService {

    void init(Map<String, String> config);

    boolean reload();

    SchemaConfig getSchemaConfig(String schema);

    Map<String, SchemaConfig> getConfigMap();

}
