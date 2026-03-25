package me.xunrana.blog.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.RedisConstants;
import me.xunrana.blog.common.annotation.RateLimit;
import me.xunrana.blog.exception.RateLimitException;
import org.aspectj.lang.JoinPoint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 接口限流 AOP 切面
 *
 * 基于 Redis ZSET（有序集合）实现滑动窗口限流算法。
 *
 * 算法原理:
 *   - 每次请求用当前时间戳作为 score 和 member 存入 ZSET
 *   - 清除窗口外的旧记录 (ZREMRANGEBYSCORE)
 *   - 统计窗口内的请求数 (ZCARD)
 *   - 如果超过阈值则拒绝请求
 *
 * 滑动窗口时间线:
 *   |-------- timeWindow --------|
 *   |  过期请求(删除)  | 有效请求  | → 当前时间
 *
 * 这是 Phase 2 的核心学习文件 — 通过实现以下 TODO 来掌握:
 *   - Redis ZSET 数据结构操作
 *   - 滑动窗口限流算法
 *   - @Before 前置通知（在方法执行前拦截）
 *   - 组合使用 AOP + Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
// ============================================================
// TODO 1: 添加 @Aspect 注解
// 📖 教程: docs/09-接口限流与Redis滑动窗口.md 第2节
// ============================================================
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    // ============================================================
    // TODO 2: 定义 @Before 前置通知 + 切入点
    // 📖 教程: docs/09-接口限流与Redis滑动窗口.md 第3节
    //
    // 方法签名:
    //   @Before("@annotation(rateLimit)")
    //   public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit)
    //
    // 注意:
    //   - 用 @Before 而不是 @Around，因为限流只需要在方法执行前拦截
    //   - 如果超过限流阈值，直接抛出 RateLimitException，方法不会执行
    // ============================================================

    // ============================================================
    // TODO 3: 实现 Redis ZSET 滑动窗口限流算法
    // 📖 教程: docs/09-接口限流与Redis滑动窗口.md 第4节
    //
    // 在上面的 checkRateLimit 方法中实现以下步骤:
    //
    //   1. 构造限流 Key:
    //      String methodName = joinPoint.getSignature().getDeclaringTypeName()
    //          + "." + joinPoint.getSignature().getName();
    //      获取客户端 IP:
    //        HttpServletRequest request = ((ServletRequestAttributes)
    //            RequestContextHolder.getRequestAttributes()).getRequest();
    //        String ip = IpUtils.getClientIp(request);
    //      完整 Key = RedisConstants.RATE_LIMIT_KEY + methodName + ":" + ip
    //
    //   2. 获取当前时间戳和窗口起始时间:
    //      long now = System.currentTimeMillis();
    //      long windowStart = now - rateLimit.timeWindow() * 1000L;
    //
    //   3. 清除窗口外的过期记录:
    //      redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
    //      → 删除 score 在 [0, windowStart] 范围内的所有成员
    //
    //   4. 统计窗口内的请求数:
    //      Long count = redisTemplate.opsForZSet().zCard(key);
    //
    //   5. 判断是否超过阈值:
    //      if (count != null && count >= rateLimit.maxRequests()) {
    //          throw new RateLimitException("请求过于频繁，请稍后再试");
    //      }
    //
    //   6. 记录当前请求:
    //      redisTemplate.opsForZSet().add(key, now + "-" + UUID, now);
    //      → member 用 "时间戳-UUID" 保证唯一性，score 用时间戳
    //      ⚠️ member 必须唯一！如果两个请求时间戳相同，用 UUID 区分
    //
    //   7. 设置 Key 过期时间（兜底清理）:
    //      redisTemplate.expire(key, rateLimit.timeWindow() + 1, TimeUnit.SECONDS);
    //      → 比窗口多 1 秒，确保窗口内的 Key 不会提前过期
    //
    // 提示:
    //   - import java.util.UUID;
    //   - import java.util.concurrent.TimeUnit;
    //   - import org.springframework.web.context.request.RequestContextHolder;
    //   - import org.springframework.web.context.request.ServletRequestAttributes;
    //   - import me.xunrana.blog.common.utils.IpUtils;
    // ============================================================
}
