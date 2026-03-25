package me.xunrana.blog.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * 基于 Redis ZSET 滑动窗口算法实现。标注在 Controller 方法上，
 * AOP 切面会在方法执行前检查请求频率，超过阈值则抛出 RateLimitException (HTTP 429)。
 *
 * 限流维度: 按 IP 地址（未登录）或 用户ID（已登录）
 *
 * 使用示例:
 * <pre>
 *   // 60 秒内最多 5 次请求
 *   {@code @RateLimit(maxRequests = 5, timeWindow = 60)}
 *   @PostMapping("/comments")
 *   public Result<Void> createComment(...) { ... }
 * </pre>
 *
 * @see me.xunrana.blog.aspect.RateLimitAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 时间窗口内最大请求数
     */
    int maxRequests() default 10;

    /**
     * 时间窗口大小（秒）
     */
    int timeWindow() default 60;
}
