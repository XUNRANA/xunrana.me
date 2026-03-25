package me.xunrana.blog.exception;

/**
 * 限流异常
 *
 * 当接口请求频率超过 @RateLimit 注解设定的阈值时抛出。
 * GlobalExceptionHandler 会捕获此异常并返回 HTTP 429 (Too Many Requests)。
 *
 * @see me.xunrana.blog.common.annotation.RateLimit
 * @see me.xunrana.blog.aspect.RateLimitAspect
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
