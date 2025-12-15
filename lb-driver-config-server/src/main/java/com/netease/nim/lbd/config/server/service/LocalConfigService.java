package com.netease.nim.lbd.config.server.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import com.netease.nim.lbd.config.server.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by caojiajun on 2025/12/10
 */
public class LocalConfigService implements ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(LocalConfigService.class);

    private Map<String, String> initConfig;
    private Map<String, SchemaConfig> schemaConfigMap = new ConcurrentHashMap<>();

    @Override
    public void init(Map<String, String> config) {
        this.initConfig = config;
        boolean success = reload();
        if (!success) {
            throw new IllegalArgumentException("init fail");
        }
    }

    @Override
    public boolean reload() {
        try {
            String configFilePath = initConfig.get("local.config.file.path");
            JSONArray jsonArray;
            if (configFilePath != null) {
                FileUtils.FileInfo fileInfo = FileUtils.readByFilePath(configFilePath);
                if (fileInfo == null) {
                    throw new IllegalArgumentException(configFilePath + " read failed");
                }
                String fileContent = fileInfo.getFileContent();
                jsonArray = JSONArray.parseArray(fileContent);
            } else {
                String configFile = initConfig.get("local.config.file");
                if (configFile == null) {
                    throw new IllegalArgumentException("missing 'local.config.file'");
                }
                FileUtils.FileInfo fileInfo = FileUtils.readByFileName(configFile);
                if (fileInfo == null) {
                    throw new IllegalArgumentException(configFile + " read failed");
                }
                String fileContent = fileInfo.getFileContent();
                jsonArray = JSONArray.parseArray(fileContent);
            }
            Map<String, SchemaConfig> schemaConfigMap = new ConcurrentHashMap<>();
            for (Object o : jsonArray) {
                JSONObject json = (JSONObject) o;
                SchemaConfig schemaConfig = ConfigUtils.parse(json.toString());
                if (schemaConfigMap.containsKey(schemaConfig.getSchema())) {
                    throw new IllegalArgumentException("duplicate schema = " + schemaConfig.getSchema());
                }
                schemaConfigMap.put(schemaConfig.getSchema(), schemaConfig);
                logger.info("schema init success, schema = {}, schemaConfig = {}", schemaConfig.getSchema(), JSONObject.toJSONString(schemaConfig));
            }
            this.schemaConfigMap = schemaConfigMap;
            return true;
        } catch (Exception e) {
            logger.error("reload error", e);
            return false;
        }
    }

    @Override
    public SchemaConfig getSchemaConfig(String schema) {
        return schemaConfigMap.get(schema);
    }

    @Override
    public Map<String, SchemaConfig> getConfigMap() {
        return new HashMap<>(schemaConfigMap);
    }
}
