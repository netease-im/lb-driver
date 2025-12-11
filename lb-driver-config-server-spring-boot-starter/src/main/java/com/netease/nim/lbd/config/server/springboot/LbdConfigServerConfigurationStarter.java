package com.netease.nim.lbd.config.server.springboot;

import com.netease.nim.lbd.config.server.conf.ConfigProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LbdConfigServerConfiguration.class})
public class LbdConfigServerConfigurationStarter {

    @Bean
    public ConfigProperties configConfiguration(LbdConfigServerConfiguration configuration) {
        ConfigProperties dashboardProperties = new ConfigProperties();
        dashboardProperties.setConfig(configuration.getConfig());
        dashboardProperties.setConfigType(configuration.getConfigType());
        return dashboardProperties;
    }
}
