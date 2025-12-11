package com.netease.nim.lbd.config.server.service;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.netease.nim.lbd.config.server.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by caojiajun on 2025/12/10
 */
public interface ConfigService {

    Logger logger = LoggerFactory.getLogger(ConfigService.class);

    void init(Map<String, String> config);

    boolean reload();

    Config getConfig();

    static void log(Config config) {
        logger.info("config updated:\n{}", JSONObject.toJSONString(config, JSONWriter.Feature.PrettyFormat));
    }
}
