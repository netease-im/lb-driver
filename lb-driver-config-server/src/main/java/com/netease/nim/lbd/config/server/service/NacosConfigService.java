package com.netease.nim.lbd.config.server.service;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.listener.Listener;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import com.netease.nim.lbd.config.server.utils.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by caojiajun on 2025/12/10
 */
public class NacosConfigService implements ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(NacosConfigService.class);

    private static final ExecutorService reloadExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("nacos-config-service"));

    private final Map<String, SchemaConfig> schemaConfigMap = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    private String group;
    private long timeoutMs;
    private com.alibaba.nacos.api.config.ConfigService configService;

    @Override
    public void init(Map<String, String> config) {
        Properties nacosProps = new Properties();
        try {
            // Get nacos config by prefix.
            String prefix = "nacos.";
            for (Map.Entry<String, String> entry : config.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.startsWith(prefix)) {
                    key = key.substring(prefix.length());
                    nacosProps.put(key, value);
                }
            }
            this.configService = NacosFactory.createConfigService(nacosProps);
            this.group = nacosProps.getProperty("group");
            if (group == null) {
                throw new IllegalArgumentException("missing 'nacos.group'");
            }
            String timeoutMsStr = nacosProps.getProperty("timeoutMs");
            if (timeoutMsStr == null) {
                this.timeoutMs = 10000L;
            } else {
                try {
                    this.timeoutMs = Long.parseLong(timeoutMsStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("illegal 'nacos.timeoutMs'");
                }
            }
            logger.info("NacosConfigService init success, nacosProps = {}", nacosProps);
        } catch (Exception e) {
            logger.error("NacosConfigService init error, nacosProps = {}", nacosProps, e);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean reload() {
        try {
            for (Map.Entry<String, SchemaConfig> entry : schemaConfigMap.entrySet()) {
                String schema = entry.getKey();
                try {
                    reload0(schema);
                } catch (Exception e) {
                    logger.error("reload error, schema = {}", schema, e);
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("reload from nacos error", e);
            return false;
        }
    }

    @Override
    public SchemaConfig getSchemaConfig(String schema) {
        SchemaConfig schemaConfig = schemaConfigMap.get(schema);
        if (schemaConfig != null) {
            return schemaConfig;
        }
        initSchema(schema);
        return schemaConfigMap.get(schema);
    }

    private void initSchema(String schema) {
        lock.lock();
        try {
            SchemaConfig schemaConfig = schemaConfigMap.get(schema);
            if (schemaConfig != null) {
                return;
            }
            //
            configService.addListener(schema, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return reloadExecutor;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    try {
                        reload0(schema);
                    } catch (Exception e) {
                        logger.error("reload error, schema = {}", schema, e);
                    }
                }
            });
            //
            reload0(schema);
            //
            schemaConfig = schemaConfigMap.get(schema);
            logger.info("schema init success, schema = {}, schemaConfig = {}", schema, JSONObject.toJSONString(schemaConfig));
        } catch (Exception e) {
            logger.error("init schema config error, schema = {}", schema, e);
            throw new IllegalArgumentException("init schema config error, schema = " + schema);
        } finally {
            lock.unlock();
        }
    }

    private void reload0(String schema) {
        try {
            String config = configService.getConfig(schema, group, timeoutMs);
            SchemaConfig schemaConfig = ConfigUtils.parse(config);
            if (!Objects.equals(schemaConfig.getSchema(), schema)) {
                throw new IllegalArgumentException("illegal schema config, schema = " + schema);
            }
            schemaConfigMap.put(schema, schemaConfig);
        } catch (Exception e) {
            logger.error("init schema config error, schema = {}", schema, e);
            throw new IllegalArgumentException("init schema config error, schema = " + schema);
        }
    }

    @Override
    public Map<String, SchemaConfig> getConfigMap() {
        return new HashMap<>(schemaConfigMap);
    }
}
