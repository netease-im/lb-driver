package com.netease.nim.lbd.config.server.conf;


import java.util.Map;

public class ConfigProperties {

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

