package com.netease.nim.lbd.config.server.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.listener.Listener;
import com.netease.nim.lbd.config.server.model.Config;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import com.netease.nim.lbd.config.server.utils.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by caojiajun on 2025/12/10
 */
public class NacosConfigService implements ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(NacosConfigService.class);

    private static final ExecutorService reloadExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("nacos-config-service"));

    private Config config;

    private String dataId;
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
            this.dataId = nacosProps.getProperty("dataId");
            this.group = nacosProps.getProperty("group");
            if (dataId == null) {
                throw new IllegalArgumentException("missing 'nacos.dataId'");
            }
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
            boolean success = reload();
            if (!success) {
                throw new IllegalStateException("reload from nacos error");
            }
            // Listen config changes
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return reloadExecutor;
                }
                @Override
                public void receiveConfigInfo(String content) {
                    try {
                        logger.info("nacos conf update!");
                        NacosConfigService.this.config = ConfigUtils.parse(content);
                        ConfigService.log(NacosConfigService.this.config);
                    } catch (Exception e) {
                        logger.error("receiveConfigInfo error, content = {}", content);
                    }
                }
            });
            logger.info("NacosConfigService init success, nacosProps = {}", nacosProps);
        } catch (Exception e) {
            logger.info("NacosConfigService init error, nacosProps = {}", nacosProps, e);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean reload() {
        try {
            String content = configService.getConfig(dataId, group, timeoutMs);
            this.config = ConfigUtils.parse(content);
            ConfigService.log(this.config);
            return true;
        } catch (Exception e) {
            logger.error("reload from nacos error, dataId = {}, group = {}, timeouMs = {}", dataId, group, timeoutMs, e);
            return false;
        }
    }

    @Override
    public Config getConfig() {
        return config;
    }
}
