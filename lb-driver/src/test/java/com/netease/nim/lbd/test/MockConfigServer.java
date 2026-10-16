package com.netease.nim.lbd.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用的 mock config-server，只实现 /fetch_sql_proxy_list
 * <p>
 * 按是否携带 policy=default 区分两个请求变体，各自维护一份 md5，并记录收到的请求便于断言
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class MockConfigServer {

    public static class Request {
        private final Map<String, String> params;

        Request(Map<String, String> params) {
            this.params = params;
        }

        public String get(String name) {
            return params.get(name);
        }

        public boolean isFallback() {
            return "default".equals(params.get("policy"));
        }
    }

    private final HttpServer httpServer;
    private final AtomicInteger fallbackRequestCount = new AtomicInteger();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private volatile List<String> affinityList = new ArrayList<>();
    private volatile List<String> fallbackList = new ArrayList<>();
    private volatile String affinityPolicyField = "az";
    private volatile String fallbackPolicyField = "default";
    private volatile String affinityMd5;
    private volatile String fallbackMd5;
    private volatile long fallbackDelayMillis = 0;

    public MockConfigServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/fetch_sql_proxy_list", this::handle);
        httpServer.setExecutor(null);
        httpServer.start();
    }

    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    public void stop() {
        httpServer.stop(0);
    }

    public void setAffinityList(String... hosts) {
        this.affinityList = Arrays.asList(hosts);
    }

    public void setFallbackList(String... hosts) {
        this.fallbackList = Arrays.asList(hosts);
    }

    public void setAffinityPolicyField(String policyField) {
        this.affinityPolicyField = policyField;
    }

    public void setFallbackDelayMillis(long fallbackDelayMillis) {
        this.fallbackDelayMillis = fallbackDelayMillis;
    }

    public int getFallbackRequestCount() {
        return fallbackRequestCount.get();
    }

    public List<Request> getRequests() {
        return requests;
    }

    public List<Request> getRequests(boolean fallback) {
        List<Request> list = new ArrayList<>();
        for (Request request : requests) {
            if (request.isFallback() == fallback) {
                list.add(request);
            }
        }
        return list;
    }

    public String getAffinityMd5() {
        return affinityMd5;
    }

    public String getFallbackMd5() {
        return fallbackMd5;
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        Request request = new Request(params);
        requests.add(request);
        boolean fallback = request.isFallback();
        if (fallback) {
            fallbackRequestCount.incrementAndGet();
        }
        //可选的响应延迟：用于验证 driver 不会在调用线程上等待兜底拉取
        long delayMillis = fallback ? fallbackDelayMillis : 0;
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<String> list = fallback ? fallbackList : affinityList;
        String policyField = fallback ? fallbackPolicyField : affinityPolicyField;
        String variant = fallback ? "fallback" : "affinity";
        String md5 = md5(variant, list, policyField);
        String currentMd5 = fallback ? fallbackMd5 : affinityMd5;
        String response;
        if (md5.equals(currentMd5) && md5.equals(params.get("md5"))) {
            response = "{\"code\":304}";
        } else {
            if (fallback) {
                fallbackMd5 = md5;
            } else {
                affinityMd5 = md5;
            }
            StringBuilder data = new StringBuilder();
            for (String string : list) {
                if (data.length() > 0) {
                    data.append(",");
                }
                data.append("\"").append(string).append("\"");
            }
            response = "{\"code\":200,\"md5\":\"" + md5 + "\",\"data\":[" + data + "]"
                    + (policyField != null ? ",\"policy\":\"" + policyField + "\"" : "") + "}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String param : rawQuery.split("&")) {
            String[] nameAndValue = param.split("=");
            if (nameAndValue.length != 2) {
                continue;
            }
            map.put(URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String md5(String variant, List<String> list, String policyField) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((variant + "|" + list + "|" + policyField).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
