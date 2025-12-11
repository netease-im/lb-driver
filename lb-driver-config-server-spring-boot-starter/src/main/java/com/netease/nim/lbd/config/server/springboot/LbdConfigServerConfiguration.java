package com.netease.nim.lbd.config.server.springboot;


import com.netease.nim.lbd.config.server.conf.ConfigType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "lbd-driver-config-server")
public class LbdConfigServerConfiguration {

    private ConfigType configType;
    private Map<String, String> config;

    public ConfigType getConfigType() {
        return configType;
    }

    public void setConfigType(ConfigType configType) {
        this.configType = configType;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }
}

