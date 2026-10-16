package com.netease.nim.lbd.config.server.service;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.model.ClientFeatureLevel;
import com.netease.nim.lbd.config.server.model.ProxyPolicy;
import com.netease.nim.lbd.config.server.model.SchemaConfig;
import com.netease.nim.lbd.config.server.utils.MD5Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 本次请求应该下发哪份集合的判定逻辑，与 web 层解耦，便于单测
 * <p>
 * 优先级：客户端显式声明 `policy=default` → 未启用 AZ 或能力等级不足 → `az` 参数 → `ip` 参数 → 请求来源 ip → 默认集合
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class SqlProxyListResolver {

    private static final Logger logger = LoggerFactory.getLogger(SqlProxyListResolver.class);

    public static class Result {

        private final ProxyPolicy policy;
        private final String az;
        private final List<String> proxyList;

        Result(ProxyPolicy policy, String az, List<String> proxyList) {
            this.policy = policy;
            this.az = az;
            this.proxyList = proxyList;
        }

        /**
         * getPolicy
         * @return 本次实际依据的下发策略，取值来自 `proxy.policy` 的可用模式
         */
        public ProxyPolicy getPolicy() {
            return policy;
        }

        /**
         * getAz
         * @return 本次命中的可用区，未命中为 null
         */
        public String getAz() {
            return az;
        }

        /**
         * getProxyList
         * @return 本次下发的 sql-proxy 列表（已排序）
         */
        public List<String> getProxyList() {
            return proxyList;
        }

        /**
         * digest
         * @return 响应摘要，必须把 policy（以及命中的可用区）一起算进去，
         *         否则策略或可用区变化而集合恰好相同时，客户端会因 304 拿到过期的策略认知
         */
        public String digest() {
            JSONObject json = new JSONObject();
            json.put("az", az);
            json.put("data", proxyList);
            json.put("policy", policy.getValue());
            return MD5Util.md5(json.toJSONString());
        }
    }

    /**
     * resolve
     * @param schemaConfig schemaConfig
     * @param policyParam 请求参数 policy，客户端降级时会显式传 default
     * @param azParam 请求参数 az
     * @param ipParam 请求参数 ip
     * @param clientIps 请求来源 ip 候选列表（按可信度从高到低，如 X-Forwarded-For 首值、RemoteAddr）
     * @param clientFeatureLevel 客户端能力等级，字段缺失或非法时按 legacy 处理
     * @return Result
     */
    public static Result resolve(SchemaConfig schemaConfig, String policyParam, String azParam, String ipParam,
                                 List<String> clientIps, int clientFeatureLevel) {
        List<String> defaultProxyList = sorted(schemaConfig.getProxyList());
        if (ProxyPolicy.DEFAULT == ProxyPolicy.parse(policyParam)) {
            //客户端明确要默认集合：降级路径，直接给默认集合
            return new Result(ProxyPolicy.DEFAULT, null, defaultProxyList);
        }
        if (!schemaConfig.isAzPolicy()) {
            return new Result(ProxyPolicy.DEFAULT, null, defaultProxyList);
        }
        if (clientFeatureLevel < ClientFeatureLevel.MIN_LEVEL_FOR_AZ) {
            //老客户端拿不到兜底能力，只能下发默认集合，否则本可用区故障时它会退化成全部失败
            logger.info("client feature level too low for az policy, level = {}, schema = {}, azParam = {}",
                    clientFeatureLevel, schemaConfig.getSchema(), azParam);
            return new Result(ProxyPolicy.DEFAULT, null, defaultProxyList);
        }
        //
        String az = null;
        String azParamTrim = trim(azParam);
        if (azParamTrim != null && schemaConfig.getAzNames().contains(azParamTrim)) {
            az = azParamTrim;
        }
        if (az == null) {
            az = schemaConfig.azOf(trim(ipParam));
        }
        if (az == null && clientIps != null) {
            for (String clientIp : clientIps) {
                az = schemaConfig.azOf(trim(clientIp));
                if (az != null) {
                    break;
                }
            }
        }
        if (az == null) {
            //未命中可用区：下发默认集合，与「未启用 AZ」的语义一致
            return new Result(ProxyPolicy.DEFAULT, null, defaultProxyList);
        }
        List<String> azProxyList = sorted(schemaConfig.getAzProxyList(az));
        if (azProxyList.isEmpty()) {
            logger.warn("az = {} has no sql-proxy config, fallback to default list, schema = {}",
                    az, schemaConfig.getSchema());
            return new Result(ProxyPolicy.DEFAULT, null, defaultProxyList);
        }
        return new Result(ProxyPolicy.AZ, az, azProxyList);
    }

    /**
     * parseClientFeatureLevel
     * @param value 请求参数 clientFeatureLevel
     * @return 等级，字段缺失或非法时按 legacy 等级处理
     */
    public static int parseClientFeatureLevel(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return ClientFeatureLevel.LEGACY.getLevel();
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (Exception e) {
            logger.warn("illegal clientFeatureLevel = {}, treat as legacy", value);
            return ClientFeatureLevel.LEGACY.getLevel();
        }
    }

    private static List<String> sorted(Collection<String> list) {
        List<String> result = new ArrayList<>();
        if (list != null) {
            result.addAll(list);
        }
        Collections.sort(result);
        return result;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
