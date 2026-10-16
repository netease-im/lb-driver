package com.netease.nim.lbd.config.server.utils;

/**
 * IPv4 网段，用于按客户端 ip 反推可用区
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class Cidr {

    private final String source;
    private final long network;
    private final int prefix;
    private final long mask;

    private Cidr(String source, long network, int prefix) {
        this.source = source;
        this.network = network;
        this.prefix = prefix;
        this.mask = prefix == 0 ? 0L : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
    }

    /**
     * parse
     * @param cidr 形如 10.1.0.0/16，或单个地址 10.1.1.1（等价 /32）
     * @return Cidr
     */
    public static Cidr parse(String cidr) {
        if (cidr == null) {
            throw new IllegalArgumentException("cidr is null");
        }
        String value = cidr.trim();
        String ipPart;
        int prefix;
        int index = value.indexOf('/');
        if (index >= 0) {
            ipPart = value.substring(0, index).trim();
            String prefixPart = value.substring(index + 1).trim();
            try {
                prefix = Integer.parseInt(prefixPart);
            } catch (Exception e) {
                throw new IllegalArgumentException("cidr prefix parse error, cidr = " + cidr);
            }
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("cidr prefix should be in [0, 32], cidr = " + cidr);
            }
        } else {
            ipPart = value;
            prefix = 32;
        }
        long ip = parseIp(ipPart);
        long mask = prefix == 0 ? 0L : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return new Cidr(value, ip & mask, prefix);
    }

    /**
     * contains
     * @param ip ip
     * @return 该 ip 是否在网段内，ip 非法时返回 false
     */
    public boolean contains(String ip) {
        try {
            return (parseIp(ip.trim()) & mask) == network;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 两个网段是否存在重叠
     * @param other other
     * @return 是否重叠
     */
    public boolean overlaps(Cidr other) {
        //掩码更长的网段一定被更短的包含，因此只需要拿短掩码去判断长掩码所属网段的网络号是否落在短网段内
        if (prefix <= other.prefix) {
            return (other.network & mask) == network;
        }
        return (network & other.mask) == other.network;
    }

    @Override
    public String toString() {
        return source;
    }

    //仅支持 ipv4：可用区网段是内网 v4 地址，ipv6 直接报错而不是静默不匹配
    private static long parseIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("ip is empty");
        }
        String[] split = ip.split("\\.");
        if (split.length != 4) {
            throw new IllegalArgumentException("not a ipv4 address, ip = " + ip);
        }
        long result = 0;
        for (String part : split) {
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (Exception e) {
                throw new IllegalArgumentException("not a ipv4 address, ip = " + ip);
            }
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("not a ipv4 address, ip = " + ip);
            }
            result = (result << 8) | value;
        }
        return result;
    }
}
