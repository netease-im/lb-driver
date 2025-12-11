package com.netease.nim.lbd.util;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class ParamBuilder {

    private final Map<String, String> map = new HashMap<>();

    public ParamBuilder() {
    }

    public ParamBuilder addParam(Object key, Object value) {
        map.put(String.valueOf(key), String.valueOf(value));
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            builder.append(URLEncoder.encode(entry.getKey())).append("=")
                    .append(URLEncoder.encode(entry.getValue())).append("&");
        }
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }
}
