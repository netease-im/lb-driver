package com.netease.nim.lbd.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by caojiajun on 2025/12/10
 */
@SpringBootApplication
@MapperScan("com.netease.nim.lbd.example.dao")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
