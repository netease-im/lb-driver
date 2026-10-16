package com.netease.nim.lbd.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class ParamBuilder {

    //显式指定 UTF-8：不依赖平台默认字符集，避免参数含非 ASCII 字符时在不同机器上编码结果不一致
    private static final String CHARSET = "UTF-8";

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
            builder.append(encode(entry.getKey())).append("=")
                    .append(encode(entry.getValue())).append("&");
        }
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, CHARSET);
        } catch (UnsupportedEncodingException e) {
            //UTF-8 必然存在，兜底走平台默认字符集
            return URLEncoder.encode(value);
        }
    }
}
