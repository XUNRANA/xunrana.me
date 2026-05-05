package me.xunrana.blog.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.RedisConstants;
import me.xunrana.blog.common.annotation.RateLimit;
import me.xunrana.blog.common.utils.IpUtils;
import me.xunrana.blog.exception.RateLimitException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流 AOP 切面（基于 Redis ZSET 滑动窗口）。
 *
 * 算法：
 *   - ZSET 中每个请求记一个成员，score 为请求时间戳
 *   - 进入方法前先 ZREMRANGEBYSCORE 清掉窗口外的旧记录
 *   - 用 ZCARD 数当前窗口内的请求数；超阈值则抛 RateLimitException
 *   - 否则 ZADD 当前请求并设置 key 过期时间（兜底）
 *
 * 限流维度：方法全路径 + 客户端 IP
 */
@Aspect
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        String methodName = joinPoint.getSignature().getDeclaringTypeName()
                + "." + joinPoint.getSignature().getName();
        String ip = currentIp();
        String key = RedisConstants.RATE_LIMIT_KEY + methodName + ":" + ip;

        long now = System.currentTimeMillis();
        long windowStart = now - rateLimit.timeWindow() * 1000L;

        // 1. 删除窗口外的过期成员（score ∈ [0, windowStart]）
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 2. 当前窗口请求数
        Long count = redisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= rateLimit.maxRequests()) {
            log.warn("限流触发: method={}, ip={}, count={}, max={}/{}s",
                    methodName, ip, count, rateLimit.maxRequests(), rateLimit.timeWindow());
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }

        // 3. 记录当前请求；member 用 "时间戳-UUID" 保证唯一性
        String member = now + "-" + UUID.randomUUID();
        redisTemplate.opsForZSet().add(key, member, now);

        // 4. 兜底过期时间，避免冷门 key 永远占内存
        redisTemplate.expire(key, rateLimit.timeWindow() + 1L, TimeUnit.SECONDS);
    }

    private String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            return IpUtils.getClientIp(req);
        }
        return "unknown";
    }
}
