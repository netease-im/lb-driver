package com.netease.nim.lbd.config.server.bootstrap;

import com.netease.nim.lbd.config.server.LbdConfigServerScanBase;
import com.netease.nim.lbd.config.server.springboot.EnableLbdConfigServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Created by caojiajun on 2025/12/10
 */
@SpringBootApplication
@EnableLbdConfigServer
@ComponentScan(basePackageClasses = {LbdConfigServerScanBase.class, Application.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
