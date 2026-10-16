package com.netease.nim.lbd.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 本机地址解析
 * <p>
 * 取「连接目标地址的 socket 的本地地址」，而不是 InetAddress.getLocalHost()，在容器与多网卡环境下更可靠
 * <p>
 * 注意：每次调用都会真实发起一次 TCP 连接（失败时最长阻塞 timeoutMillis），调用方需要自行做失败退避
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public class LocalAddressResolver {

    /**
     * 通过连接目标地址解析本机地址
     * @param host 目标地址
     * @param port 目标端口
     * @param timeoutMillis 连接超时，单位毫秒
     * @return 本机地址，解析失败返回 null
     */
    public static String resolve(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            InetAddress localAddress = socket.getLocalAddress();
            if (localAddress == null) {
                return null;
            }
            return localAddress.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }
}
