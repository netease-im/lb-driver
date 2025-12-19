package com.netease.nim.lbd.example;

import com.netease.nim.lbd.druid.LbdDruidFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Created by caojiajun on 2025/12/10
 */
@SpringBootApplication
@MapperScan("com.netease.nim.lbd.example.dao")
public class Application {

    @Bean
    public LbdDruidFilter lbdDruidFilter() {
        return new LbdDruidFilter();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
