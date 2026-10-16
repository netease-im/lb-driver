package com.netease.nim.lbd.config.server.controller;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.conf.ConfigProperties;
import com.netease.nim.lbd.config.server.conf.ConfigType;
import com.netease.nim.lbd.config.server.conf.LogBean;
import com.netease.nim.lbd.config.server.exception.AppException;
import com.netease.nim.lbd.config.server.model.ClientFeatureLevel;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.service.ConfigService;
import com.netease.nim.lbd.config.server.service.EtcdConfigService;
import com.netease.nim.lbd.config.server.service.LocalConfigService;
import com.netease.nim.lbd.config.server.service.NacosConfigService;
import com.netease.nim.lbd.config.server.service.SqlProxyListResolver;
import com.netease.nim.lbd.config.server.utils.ConfigUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class ConfigApiController implements InitializingBean {

    private static final String Authorization = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_FROM_IP = "X-From-IP";

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

    @GetMapping("/fetch_sql_proxy_list")
    public JSONObject fetchSqlProxyList(HttpServletRequest request,
                                        @RequestParam(value = "schema") String schema,
                                        @RequestParam(value = "md5", required = false) String md5,
                                        @RequestParam(value = "az", required = false) String az,
                                        @RequestParam(value = "ip", required = false) String ip,
                                        @RequestParam(value = "policy", required = false) String policy,
                                        @RequestParam(value = "clientFeatureLevel", required = false) String clientFeatureLevel,
                                        @RequestParam(value = "clientVersion", required = false) String clientVersion) {
        LogBean log = LogBean.get();
        log.addProps("md5", md5);
        log.addProps("schema", schema);
        log.addProps("az", az);
        log.addProps("ip", ip);
        log.addProps("policy.request", policy);
        log.addProps("clientVersion", clientVersion);
        int featureLevel = SqlProxyListResolver.parseClientFeatureLevel(clientFeatureLevel);
        log.addProps("clientFeatureLevel", featureLevel);
        log.addProps("clientFeatureLevel.display", ClientFeatureLevel.displayNameOf(featureLevel));
        SchemaConfig schemaConfig = configService.getSchemaConfig(schema);
        if (schemaConfig == null) {
            log.addProps("result", not_found);
            return not_found;
        }
        //
        auth(request, schemaConfig);
        //
        SqlProxyListResolver.Result result = SqlProxyListResolver.resolve(schemaConfig, policy, az, ip,
                clientIps(request), featureLevel);
        log.addProps("policy.applied", result.getPolicy().getValue());
        log.addProps("az.applied", result.getAz());
        //
        List<String> sqlProxyLists = result.getProxyList();
        //摘要必须把 policy（与命中的可用区）一起算进去，否则策略变化而集合未变时客户端会因 304 拿到过期的策略认知；
        //只与本次算出的摘要比对，不拿「最近一次下发的摘要」代替：配置刚变更时后者会给出过期的 304
        //（代价只是每个请求多算一次小 JSON 的 md5，这个接口是 5s~30s 级轮询，不在热路径上）
        String newMd5 = result.digest();
        if (Objects.equals(md5, newMd5)) {
            log.addProps("result", not_modify);
            log.addProps("md5.cache", true);
            return not_modify;
        }
        JSONObject json = new JSONObject();
        json.put("code", 200);
        json.put("md5", newMd5);
        json.put("data", sqlProxyLists);
        json.put("policy", result.getPolicy().getValue());
        log.addProps("result", json);
        return json;
    }

    //请求来源 ip 的候选顺序：X-From-IP → X-Real-IP → X-Forwarded-For 首值 → RemoteAddr
    //前两个由接入层覆盖写入、客户端无法伪造，优先采信；X-Forwarded-For 是调用方可自由构造的
    //本方法只用于推断可用区（性能优化），不作为鉴权或安全边界，授权一律由 apiKey 把关
    private List<String> clientIps(HttpServletRequest request) {
        List<String> list = new ArrayList<>();
        addIfNotEmpty(list, request.getHeader(X_FROM_IP));
        addIfNotEmpty(list, request.getHeader(X_REAL_IP));
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            //取首值即最靠近客户端的地址
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) {
                list.add(first);
            }
        }
        addIfNotEmpty(list, request.getRemoteAddr());
        return list;
    }

    private void addIfNotEmpty(List<String> list, String value) {
        if (value != null && !value.trim().isEmpty()) {
            list.add(value.trim());
        }
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
        //header 由调用方任意构造：不是 Bearer 前缀时直接按鉴权失败处理，
        //不能让 substring 抛越界异常变成 500（500 会被当成服务端故障触发告警）
        if (!authorization.startsWith(BEARER_PREFIX)) {
            LogBean.get().addProps("Authorization.fail", true);
            throw new AppException(403, "illegal authorization header");
        }
        String apiKey = authorization.substring(BEARER_PREFIX.length());
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
