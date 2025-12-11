package com.netease.nim.lbd.config.server.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebInterceptorConfiguration implements WebMvcConfigurer {

    @Bean
    public LogInterceptor LogInterceptor() {
        return new LogInterceptor();
    }

    public void addInterceptors(InterceptorRegistry registry) {
        LogInterceptor logInterceptor = LogInterceptor();
        registry.addInterceptor(logInterceptor);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setPathMatcher(new AntPathMatcher());
    }
}
