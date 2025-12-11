package com.netease.nim.lbd.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by caojiajun on 2025/12/9
 */
public final class TinyJson {
    private final String text;
    private int pos = 0;

    public static Object parse(String json) {
        return new TinyJson(json).parseValue();
    }

    public static Map<String, Object> parseObject(String json) {
        return (Map<String, Object>) parse(json);
    }
    public static List<Object> parseArray(String json) {
        return (List<Object>) parse(json);
    }

    private TinyJson(String text) { this.text = text; }

    private Object parseValue() {
        skipBlank();
        char c = text.charAt(pos);
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': case 'f': return parseBoolean();
            case 'n':           return parseNull();
            default:            return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipBlank();
        if (peek() == '}') { pos++; return map; }
        do {
            skipBlank();
            String key = parseString();
            skipBlank();
            expect(':');
            map.put(key, parseValue());
            skipBlank();
        } while (consume(','));
        expect('}');
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipBlank();
        if (peek() == ']') {
            pos++;
            return list;
        }
        do {
            list.add(parseValue());
            skipBlank();
        } while (consume(','));
        expect(']');
        return list;
    }

    private String parseString() {
        expect('"');
        int start = pos;
        while (pos < text.length() && text.charAt(pos) != '"') {
            if (text.charAt(pos) == '\\') pos += 2;
            else pos++;
        }
        String str = text.substring(start, pos);
        expect('"');
        return str;
    }

    private Number parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String num = text.substring(start, pos);
        if (num.contains(".")) {
            return Double.parseDouble(num);
        } else {
            return Long.parseLong(num);
        }
    }

    private Boolean parseBoolean() {
        if (text.startsWith("true", pos)) {
            pos += 4;
            return true;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return false;
        }
        throw err("boolean");
    }

    private Object parseNull() {
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw err("null");
    }

    private void skipBlank() {
        while (pos < text.length() && Character.isWhitespace(peek())) {
            pos++;
        }
    }

    private char peek() {
        return text.charAt(pos);
    }

    private boolean consume(char c) {
        skipBlank();
        if (peek() == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        skipBlank();
        if (peek() != c) {
            throw err("'" + c + "'");
        }
        pos++;
    }

    private IllegalArgumentException err(String expect) {
        return new IllegalArgumentException("Expected " + expect + " at " + pos);
    }

}
