package com.netease.nim.lbd.config.server.conf;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.config.server.controller.HealthStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;


public class LogInterceptor implements HandlerInterceptor {

    private static final Logger staticsLogger = LoggerFactory.getLogger("stats");

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o) {
        LogBean logBean = LogBean.init();
        logBean.setUri(httpServletRequest.getRequestURI());
        logBean.setStartTime(System.currentTimeMillis());
        String ip = getRequestIp(httpServletRequest);
        logBean.setIp(ip);
        return true;
    }

    private String getRequestIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (checkIp(ip)) {
            return ip;
        }
        ip = request.getHeader("X-Forwarded-For");
        if (checkIp(ip)) {
            return ip;
        }
        ip = request.getHeader("Proxy-Client-IP");
        if (checkIp(ip)) {
            return ip;
        }
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (checkIp(ip)) {
            return ip;
        }
        ip = request.getRemoteAddr();
        return ip;
    }

    private boolean checkIp(String ip) {
        return ip != null && !ip.trim().isEmpty() && !ip.equalsIgnoreCase("unknown");
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
        LogBean logBean = LogBean.get();
        logBean.setSpendTime();

        logBean.setCode(httpServletResponse.getStatus());
        logBean.setMethod(httpServletRequest.getMethod());
        String uri = logBean.getUri();
        JSONObject logBeanJson = logBean.toJson();
        int code = logBean.getCode();
        if (code == 200) {
            if (uri.startsWith("/health")) {
                if (staticsLogger.isDebugEnabled()) {
                    staticsLogger.debug(logBeanJson.toString());
                }
            } else {
                if (logBean.getSpendTime() > 1000) {
                    if (staticsLogger.isWarnEnabled()) {
                        staticsLogger.warn(logBeanJson.toString());
                    }
                } else {
                    if (staticsLogger.isInfoEnabled()) {
                        staticsLogger.info(logBeanJson.toString());
                    }
                }
            }
        } else if (code == 500) {
            if (staticsLogger.isErrorEnabled()) {
                staticsLogger.error(logBeanJson.toString());
            }
        } else {
            if (staticsLogger.isWarnEnabled()) {
                staticsLogger.warn(logBeanJson.toString());
            }
        }
        if (!uri.startsWith("/health")) {
            HealthStatus.updateRequestTimestamp();
        }
    }
}
