package me.xunrana.blog.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 地址提取工具类
 *
 * 在反向代理（Nginx）环境下，客户端真实 IP 不是 remoteAddr，
 * 而是通过 X-Forwarded-For 等 Header 传递。
 *
 * 优先级: X-Forwarded-For → X-Real-IP → Proxy-Client-IP → remoteAddr
 */
public class IpUtils {

    private IpUtils() {
        // 工具类，禁止实例化
    }

    private static final String UNKNOWN = "unknown";

    /**
     * 从 HttpServletRequest 中获取客户端真实 IP
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // X-Forwarded-For 可能包含多个 IP（经过多层代理），取第一个
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
