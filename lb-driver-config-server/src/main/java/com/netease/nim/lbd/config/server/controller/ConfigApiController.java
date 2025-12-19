package com.netease.nim.lbd.config.server.controller;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.conf.ConfigProperties;
import com.netease.nim.lbd.config.server.conf.ConfigType;
import com.netease.nim.lbd.config.server.conf.LogBean;
import com.netease.nim.lbd.config.server.exception.AppException;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.service.ConfigService;
import com.netease.nim.lbd.config.server.service.EtcdConfigService;
import com.netease.nim.lbd.config.server.service.LocalConfigService;
import com.netease.nim.lbd.config.server.service.NacosConfigService;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import com.netease.nim.lbd.config.server.utils.MD5Util;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class ConfigApiController implements InitializingBean {

    private static final String Authorization = "Authorization";

    private static final JSONObject not_modify = new JSONObject();
    private static final JSONObject not_found = new JSONObject();
    private static final JSONObject ok = new JSONObject();
    private static final JSONObject error = new JSONObject();
    static {
        not_modify.put("code", 304);
        not_found.put("code", 404);
        ok.put("code", 200);
        error.put("code", 500);
    }

    @Autowired
    private ConfigProperties configProperties;

    private ConfigService configService;

    private final ConcurrentHashMap<String, String> md5CacheMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> md5CacheUpdateTimeMap = new ConcurrentHashMap<>();

    @GetMapping("/fetch_sql_proxy_list")
    public JSONObject fetchSqlProxyList(HttpServletRequest request,
                                        @RequestParam(value = "schema") String schema,
                                        @RequestParam(value = "md5", required = false) String md5) {
        LogBean.get().addProps("md5", md5);
        LogBean.get().addProps("schema", schema);
        SchemaConfig schemaConfig = configService.getSchemaConfig(schema);
        if (schemaConfig == null) {
            LogBean.get().addProps("result", not_found);
            return not_found;
        }
        //
        auth(request, schemaConfig);
        //
        String md5Cache = md5CacheMap.get(schema);
        Long md5CacheUpdateTime = md5CacheUpdateTimeMap.get(schema);
        if (md5 != null && md5Cache != null && md5CacheUpdateTime != null && Objects.equals(md5, md5Cache)
                && System.currentTimeMillis() - md5CacheUpdateTime < 1000) {
            LogBean.get().addProps("result", not_modify);
            LogBean.get().addProps("md5.cache", true);
            return not_modify;
        }
        //
        List<String> sqlProxyLists = new ArrayList<>(schemaConfig.getProxyList());
        Collections.sort(sqlProxyLists);
        String newMd5 = MD5Util.md5(JSONObject.toJSONString(sqlProxyLists));
        md5CacheMap.put(schema, newMd5);
        md5CacheUpdateTimeMap.put(schema, System.currentTimeMillis());
        if (Objects.equals(md5, newMd5)) {
            LogBean.get().addProps("result", not_modify);
            return not_modify;
        }
        JSONObject json = new JSONObject();
        json.put("code", 200);
        json.put("md5", newMd5);
        json.put("data", sqlProxyLists);
        LogBean.get().addProps("result", json);
        return json;
    }

    @RequestMapping("/reload")
    public JSONObject reload() {
        boolean success = configService.reload();
        if (success) {
            return ok;
        } else {
            return error;
        }
    }

    @RequestMapping("/monitor")
    public JSONObject monitor() {
        Map<String, SchemaConfig> configMap = configService.getConfigMap();
        return ConfigUtils.monitorJson(configMap, configProperties.getConfigType());
    }

    private void auth(HttpServletRequest request, SchemaConfig schemaConfig) {
        boolean enable = schemaConfig.isAuthEnable();
        if (!enable) {
            return;
        }
        String authorization = request.getHeader(Authorization);
        if (authorization == null) {
            LogBean.get().addProps("Authorization.missing", true);
            throw new AppException(403, "missing api key");
        }
        String apiKey = authorization.substring("Bearer ".length());
        if (!schemaConfig.getApiKeys().contains(apiKey)) {
            LogBean.get().addProps("Authorization.fail", true);
            throw new AppException(403, "illegal api key");
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ConfigType configType = configProperties.getConfigType();
        if (configType == ConfigType.etcd) {
            configService = new EtcdConfigService();
        } else if (configType == ConfigType.nacos) {
            configService = new NacosConfigService();
        } else if (configType == ConfigType.local) {
            configService = new LocalConfigService();
        } else if (configType == ConfigType.custom) {
            String className = configProperties.getConfig().get("config.class.name");
            if (className == null) {
                throw new IllegalArgumentException("custom type should provide `config.class.name`");
            }
            configService = (ConfigService) Class.forName(className).getConstructor().newInstance();
        } else {
            throw new IllegalArgumentException("unknown config-type");
        }
        configService.init(configProperties.getConfig());
    }
}
