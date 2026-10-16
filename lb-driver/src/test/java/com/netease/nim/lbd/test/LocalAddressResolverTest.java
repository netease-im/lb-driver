package com.netease.nim.lbd.test;

import com.netease.nim.lbd.util.LocalAddressResolver;
import org.junit.Assert;
import org.junit.Test;

import java.net.ServerSocket;

/**
 * Created by caojiajun on 2026/10/15
 */
public class LocalAddressResolverTest {

    @Test
    public void testResolveSuccess() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            String ip = LocalAddressResolver.resolve("127.0.0.1", serverSocket.getLocalPort(), 1000);
            Assert.assertEquals("127.0.0.1", ip);
        }
    }

    @Test
    public void testResolveFailure() throws Exception {
        int port;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
        }
        //端口已关闭，解析失败返回 null，不抛异常
        Assert.assertNull(LocalAddressResolver.resolve("127.0.0.1", port, 500));
    }
}
