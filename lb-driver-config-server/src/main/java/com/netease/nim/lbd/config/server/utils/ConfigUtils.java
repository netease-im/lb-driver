package com.netease.nim.lbd.config.server.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.conf.ConfigType;
import com.netease.nim.lbd.config.server.model.Config;
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

    public static Config parse(String content) {
        JSONObject json = JSONObject.parseObject(content);
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

        Map<String, List<String>> proxyConfig = new HashMap<>();

        JSONArray proxyConfigJson = json.getJSONArray("proxy_config");
        for (Object o : proxyConfigJson) {
            JSONObject schemaJson = (JSONObject) o;
            String schema = schemaJson.getString("schema");
            if (schema == null || schema.isEmpty()) {
                throw new IllegalArgumentException("`schema` is empty");
            }
            JSONArray proxyJson = schemaJson.getJSONArray("proxy");
            List<String> proxyList = new ArrayList<>();
            for (Object object : proxyJson) {
                String proxy = object.toString().trim();
                checkProxy(proxy);
                proxyList.add(proxy);
            }
            if (proxyList.isEmpty()) {
                throw new IllegalArgumentException("proxy list is empty, schema = " + schema);
            }
            proxyConfig.put(schema, proxyList);
        }

        if (proxyConfig.isEmpty()) {
            throw new IllegalArgumentException("proxy_config is empty");
        }

        Config config = new Config();
        config.setAuthEnable(authEnable);
        config.setApiKeys(apiKeys);
        config.setProxyConfig(proxyConfig);
        return config;
    }

    private static void checkProxy(String proxy) {
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
            logger.warn("sql-proxy = {} not reachable, please check config", host + ":" + port);
        }
    }

    public static boolean checkHostPort(String host, int port) {
        try (Socket socket = new Socket()) {
            try {
                int timeout = 10000;
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

    public static JSONObject monitorJson(Config config, ConfigType configType) {
        JSONObject monitorJson = new JSONObject();
        JSONArray infoJsonArray = new JSONArray();
        JSONObject info = new JSONObject();
        info.put("configType", configType);
        info.put("authEnable", String.valueOf(config.isAuthEnable()));
        info.put("apiKeySize", config.getApiKeys().size());
        infoJsonArray.add(info);
        monitorJson.put("info", infoJsonArray);

        JSONArray proxyJsonArray = new JSONArray();
        for (Map.Entry<String, List<String>> entry : config.getProxyConfig().entrySet()) {
            JSONObject json = new JSONObject();
            json.put("schema", entry.getKey());
            json.put("size", entry.getValue().size());
            proxyJsonArray.add(json);
        }
        monitorJson.put("proxy", proxyJsonArray);

        JSONArray proxyDetailJsonArray = new JSONArray();
        for (Map.Entry<String, List<String>> entry : config.getProxyConfig().entrySet()) {
            for (String proxy : entry.getValue()) {
                JSONObject json = new JSONObject();
                json.put("schema", entry.getKey());
                json.put("proxy", proxy);
                String[] split = proxy.split(":");
                boolean check = ConfigUtils.checkHostPort(split[0], Integer.parseInt(split[1]));
                json.put("reachable", check ? 1 : 0);
                proxyDetailJsonArray.add(json);
            }
        }
        monitorJson.put("proxy_detail", proxyDetailJsonArray);
        return monitorJson;
    }

}
