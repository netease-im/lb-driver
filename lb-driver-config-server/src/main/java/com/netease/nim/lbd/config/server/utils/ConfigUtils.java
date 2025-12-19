package com.netease.nim.lbd.config.server.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.conf.ConfigType;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

/**
 * Created by caojiajun on 2025/12/10
 */
public class ConfigUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);

    public static JSONObject toJson(SchemaConfig schemaConfig) {
        JSONObject json = new JSONObject();
        json.put("schema", schemaConfig.getSchema());
        json.put("auth.enable", schemaConfig.isAuthEnable());
        json.put("api.keys", schemaConfig.getApiKeys());
        json.put("proxy", schemaConfig.getProxyList());
        return json;
    }

    public static SchemaConfig parse(String content) {
        JSONObject json = JSONObject.parseObject(content);

        String schema = json.getString("schema");
        if (schema == null) {
            throw new IllegalArgumentException("missing 'schema'");
        }

        boolean authEnable = json.getBooleanValue("auth.enable");
        Set<String> apiKeys = new HashSet<>();
        JSONArray jsonArray = json.getJSONArray("api.keys");
        if (jsonArray != null) {
            for (Object o : jsonArray) {
                String apiKey = String.valueOf(o);
                apiKeys.add(apiKey);
            }
        }
        if (authEnable && apiKeys.isEmpty()) {
            throw new IllegalArgumentException("`auth.enable` is true but `api.keys` is empty");
        }

        JSONArray proxyJson = json.getJSONArray("proxy");
        List<String> proxyList = new ArrayList<>();
        for (Object object : proxyJson) {
            String proxy = object.toString().trim();
            checkProxy(schema, proxy);
            proxyList.add(proxy);
        }
        if (proxyList.isEmpty()) {
            throw new IllegalArgumentException("proxy list is empty, schema = " + schema);
        }

        SchemaConfig schemaConfig = new SchemaConfig();
        schemaConfig.setSchema(schema);
        schemaConfig.setAuthEnable(authEnable);
        schemaConfig.setApiKeys(apiKeys);
        schemaConfig.setProxyList(proxyList);
        return schemaConfig;
    }

    private static void checkProxy(String schema, String proxy) {
        String[] split = proxy.split(":");
        if (split.length != 2) {
            throw new IllegalArgumentException("proxy parse error, proxy = " + proxy);
        }
        String host;
        int port;
        try {
            host = split[0];
            port = Integer.parseInt(split[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("proxy parse error, proxy = " + proxy);
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("proxy parse error, host is empty, proxy = " + proxy);
        }
        if (port <= 0) {
            throw new IllegalArgumentException("proxy parse error, port should > 0, proxy = " + proxy);
        }
        boolean success = checkHostPort(host, port);
        if (!success) {
            logger.warn("schema = {}, sql-proxy = {} not reachable, please check config", schema, host + ":" + port);
        }
    }

    public static boolean checkHostPort(String host, int port) {
        try (Socket socket = new Socket()) {
            try {
                int timeout = 1000;
                socket.connect(new InetSocketAddress(host, port), timeout);
                return true;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            logger.error("checkHostPort error, host = {}, port = {}", host, port, e);
            return false;
        }
    }

    public static JSONObject monitorJson(Map<String, SchemaConfig> configMap, ConfigType configType) {
        JSONObject monitorJson = new JSONObject();
        JSONArray infoJsonArray = new JSONArray();
        JSONObject info = new JSONObject();
        info.put("configType", configType);
        info.put("schemaSize", configMap.size());
        infoJsonArray.add(info);
        monitorJson.put("info", infoJsonArray);

        JSONArray proxyJsonArray = new JSONArray();
        for (Map.Entry<String, SchemaConfig> entry : configMap.entrySet()) {
            SchemaConfig schemaConfig = entry.getValue();
            JSONObject json = new JSONObject();
            json.put("schema", schemaConfig.getSchema());
            json.put("authEnable", schemaConfig.isAuthEnable() ? 1 : 0);
            json.put("apiKeySize", schemaConfig.getApiKeys().size());
            json.put("proxySize", schemaConfig.getProxyList().size());
            proxyJsonArray.add(json);
        }
        monitorJson.put("schemaInfo", proxyJsonArray);

        JSONArray proxyDetailJsonArray = new JSONArray();
        for (Map.Entry<String, SchemaConfig> entry : configMap.entrySet()) {
            SchemaConfig schemaConfig = entry.getValue();
            for (String proxy : schemaConfig.getProxyList()) {
                JSONObject json = new JSONObject();
                json.put("schema", entry.getKey());
                json.put("proxy", proxy);
                String[] split = proxy.split(":");
                boolean check = ConfigUtils.checkHostPort(split[0], Integer.parseInt(split[1]));
                json.put("reachable", check ? 1 : 0);
                proxyDetailJsonArray.add(json);
            }
        }
        monitorJson.put("proxyDetail", proxyDetailJsonArray);
        return monitorJson;
    }

}
