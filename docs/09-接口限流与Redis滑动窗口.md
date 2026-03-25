# 09 - 接口限流与 Redis 滑动窗口

> 本文档讲解接口限流的必要性、四种经典限流算法的原理与对比、Redis ZSET 实现滑动窗口的详细过程、自定义 @RateLimit 注解与 AOP 切面的配合，以及限流异常的处理方案。

---

## 目录

1. [为什么需要接口限流](#1-为什么需要接口限流)
2. [四种经典限流算法](#2-四种经典限流算法)
3. [为什么选择滑动窗口](#3-为什么选择滑动窗口)
4. [Redis ZSET 数据结构详解](#4-redis-zset-数据结构详解)
5. [滑动窗口算法详解](#5-滑动窗口算法详解)
6. [自定义 @RateLimit 注解](#6-自定义-ratelimit-注解)
7. [AOP 切面实现](#7-aop-切面实现)
8. [限流异常处理](#8-限流异常处理)
9. [应用到控制器](#9-应用到控制器)
10. [原子性与 Lua 脚本](#10-原子性与-lua-脚本)
11. [测试验证](#11-测试验证)
12. [面试常见问题](#12-面试常见问题)

---

## 1. 为什么需要接口限流

### 1.1 真实场景中的问题

一个没有任何保护措施的 API 接口，在互联网环境中会面临各种恶意或异常流量：

| 场景 | 描述 | 后果 |
|------|------|------|
| 评论刷屏 | 恶意用户用脚本每秒发送上百条评论 | 数据库被垃圾数据淹没，正常用户体验变差 |
| 登录暴力破解 | 攻击者用字典对账号密码进行穷举 | 账号被盗，安全事故 |
| 爬虫滥用 | 爬虫高频请求文章接口 | 服务器负载飙升，正常用户响应变慢 |
| DDoS 攻击 | 大量请求涌入 | 服务完全不可用 |

**以我们的博客系统为例**：

```
假设：评论接口没有限流

攻击者写一个简单的循环脚本：
for i in range(10000):
    requests.post("/api/v1/articles/1/comments", json={
        "nickname": "spam",
        "content": "垃圾评论 " + str(i)
    })

结果：
- 几秒内数据库就写入了上万条垃圾评论
- MySQL 的写入 QPS 被打满，CPU 飙到 100%
- 所有正常用户的请求都超时了
- 博客系统完全瘫痪
```

### 1.2 限流 = "水龙头"

限流的核心思想不是"堵住水"，而是"控制流量"——就像水龙头：

```
没有限流（水管直连）：            有限流（加了水龙头）：

    请求洪流                       请求洪流
    ████████                      ████████
    ████████                      ████████
        │                             │
        │                          ┌──┴──┐
        │                          │水龙头│  ← 限流器：控制流速
        │                          └──┬──┘
        │                             │
        ▼                             ▼
   ████████████                    ··●·●··
   服务器直接崩溃                  服务器稳定运行
```

限流不是拒绝所有请求，而是保证单位时间内只允许合理数量的请求通过。超出限制的请求会收到一个友好的提示："请求过于频繁，请稍后再试"（HTTP 429）。

### 1.3 我们博客中的限流需求

| 接口 | 限流策略 | 原因 |
|------|----------|------|
| `POST /v1/articles/{id}/comments` | 每 IP 每分钟 5 次 | 防止评论刷屏 |
| `POST /v1/auth/login` | 每 IP 每分钟 10 次 | 防止暴力破解密码 |

这两个接口是面向公开网络的（不需要登录就能调用），所以最容易被滥用，是限流的首要目标。

---

## 2. 四种经典限流算法

### 2.1 固定窗口 (Fixed Window)

**原理**：将时间划分为固定大小的窗口（比如每 1 分钟一个窗口），在每个窗口内维护一个计数器。每来一个请求，计数器加一；如果计数器超过阈值，拒绝请求。窗口结束时，计数器清零。

```
时间线:  |--- 窗口1 (0:00~0:59) ---|--- 窗口2 (1:00~1:59) ---|
阈值:    5次/窗口

请求:    ●  ●  ●  ●  ●  ✗  ✗       ●  ●  ●
         1  2  3  4  5  拒绝 拒绝     1  2  3
                                    ↑ 新窗口，计数器清零
```

**实现方式**：

```java
// 伪代码：固定窗口
String key = "rate_limit:" + ip + ":" + method;
long count = redis.incr(key);           // 计数器 +1
if (count == 1) {
    redis.expire(key, 60);              // 第一次请求时设置过期时间
}
if (count > maxRequests) {
    throw new RateLimitException();     // 超过阈值，拒绝
}
```

**致命缺陷——边界突发问题**：

```
时间线:  |--- 窗口1 ---|--- 窗口2 ---|
阈值:    5次/窗口

                 ●●●●●  ●●●●●
                 ↑0:55   ↑1:00
                 窗口1   窗口2

问题：窗口1末尾5个 + 窗口2开头5个 = 10秒内通过了10个请求！
实际流量是阈值的 2 倍！
```

在窗口切换的边界处，短时间内可以通过 2 倍于阈值的请求。这在高流量场景下是不可接受的。

### 2.2 滑动窗口 (Sliding Window) ← 我们的选择

**原理**：窗口不是固定的，而是随着时间"滑动"。每次请求到来时，都以**当前时刻为终点**，往前看固定时间段（比如 60 秒）内的请求数。

```
时间线: ──────────────────────────────────────────→
             [===== 60秒窗口 =====]
                 ↑                  ↑
              窗口起点          当前时刻

每次请求到来，窗口都会"滑动"到以当前时刻为终点：

时刻 T=70:  [====== T=10 ~ T=70 ======]
时刻 T=71:   [====== T=11 ~ T=71 ======]
时刻 T=72:    [====== T=12 ~ T=72 ======]
                                        → 窗口随时间平滑移动
```

**为什么没有边界突发？**

```
时间线:  ──────────────────────────────────────→
阈值:    5次/60秒

                 ●●●●●  ●●●●●
                 ↑0:55   ↑1:00

在 T=1:00 时刻，窗口 = [0:00, 1:00]：
包含 0:55 的 5 个 + 1:00 的 5 个 = 10 个 → 超过阈值 → 拒绝后面的请求！

滑动窗口在任何时刻看到的都是"过去60秒"的完整请求数，不存在边界盲区。
```

### 2.3 令牌桶 (Token Bucket)

**原理**：系统维护一个"令牌桶"，桶以固定速率生成令牌（比如每秒 10 个）。每个请求到来时需要从桶中取一个令牌，取到了才能通过；桶空了就拒绝。桶有容量上限，多余的令牌会被丢弃。

```
令牌桶示意图：

  [令牌生成器] ──→ 每秒放入 10 个令牌
       │
       ▼
  ┌──────────┐   容量上限 = 20
  │ ●●●●●●●● │   当前令牌数 = 8
  │ ●●●●●●●● │
  └────┬─────┘
       │
       ▼
  请求来了 → 取一个令牌 → 通过 ✓
  请求来了 → 桶空了     → 拒绝 ✗
```

**关键特点**：允许突发流量。如果桶中积累了很多令牌（比如低峰期），突然来一波请求可以全部通过。这在某些场景下是期望的（比如秒杀活动开始的一瞬间）。

**典型实现**：
- Google Guava 的 `RateLimiter`（单机限流）
- Nginx 的 `limit_req` 模块
- 阿里巴巴 Sentinel

### 2.4 漏桶 (Leaky Bucket)

**原理**：把请求想象成水滴，倒入一个"漏桶"。桶底有一个固定大小的孔，水（请求）以恒定速率流出。桶满了，新的水（请求）就溢出被丢弃。

```
漏桶示意图：

  请求 ●●●●●●●●  （可能突然来很多）
       │││││││
       ▼▼▼▼▼▼▼
  ┌──────────┐
  │ ~~~~~~~~ │   桶容量 = 20
  │ ~~~~~~~~ │   桶满了 → 溢出（拒绝）
  └────┬─────┘
       │
       ● · · ● · · ● · · ●   恒定速率流出（处理请求）
```

**关键特点**：无论请求来得多猛，处理速度永远是恒定的。这使得漏桶非常适合"流量整形"场景——把不规则的流量变成平滑的输出。

**典型应用**：消息队列的消费速率控制、网络流量整形。

### 2.5 四种算法对比

| 算法 | 平滑性 | 突发流量 | 实现复杂度 | 适用场景 |
|------|--------|----------|-----------|---------|
| 固定窗口 | 差 | 有边界突发 | 简单 | 粗粒度限流，要求不高的场景 |
| **滑动窗口** | **好** | **无突发** | **中等** | **API 限流 ←我们的选择** |
| 令牌桶 | 好 | 允许突发 | 中等 | 需要允许短暂突发的场景 |
| 漏桶 | 最好 | 不允许突发 | 较高 | 流量整形，严格恒定速率 |

---

## 3. 为什么选择滑动窗口

对于我们的博客系统，选择滑动窗口的理由如下：

### 3.1 没有边界突发问题

固定窗口的边界突发是其最大缺陷。滑动窗口在任何时刻看到的都是"过去 N 秒"的请求数，不存在盲区。对于评论接口和登录接口这种安全敏感的场景，我们不能容忍瞬间 2 倍流量的通过。

### 3.2 Redis ZSET 天然适配

Redis 的有序集合（Sorted Set / ZSET）是滑动窗口的天然数据结构：

```
ZSET 特性                          滑动窗口需求
─────────────────                  ─────────────────
每个 member 有一个 score     →     score = 请求时间戳
按 score 排序               →     按时间排序
可以按 score 范围删除        →     删除窗口外的过期请求
可以快速统计成员数量         →     统计窗口内的请求数
```

用 ZSET 实现滑动窗口，只需要 4-5 个 Redis 命令，非常简洁。

### 3.3 不需要令牌桶的突发能力

令牌桶允许突发流量，这对于需要应对瞬间高峰的场景（如秒杀）很有用。但对于评论和登录接口，我们恰恰**不想**允许突发——攻击者的脚本本身就是突发流量。

### 3.4 实现复杂度适中

漏桶虽然平滑性最好，但实现较复杂（需要维护队列和定时调度）。滑动窗口用 Redis ZSET 几行代码就能搞定，非常适合我们的项目规模。

---

## 4. Redis ZSET 数据结构详解

### 4.1 什么是 Sorted Set

Sorted Set（有序集合，简称 ZSET）是 Redis 的五大基本数据类型之一。它是一个**不重复的字符串集合**，每个成员都关联一个 **score（分值）**，集合按 score 排序。

```
ZSET 结构：

Key: "blog:rate_limit:127.0.0.1:CommentController.addComment"

┌──────────────────┬────────────────────┐
│     member       │       score        │
├──────────────────┼────────────────────┤
│ "1711350010123"  │  1711350010123     │  ← 最早的请求
│ "1711350015456"  │  1711350015456     │
│ "1711350020789"  │  1711350020789     │
│ "1711350030012"  │  1711350030012     │
│ "1711350045345"  │  1711350045345     │  ← 最新的请求
└──────────────────┴────────────────────┘
       ↑                   ↑
  请求的唯一标识        请求的时间戳（毫秒）
```

### 4.2 核心命令

在滑动窗口限流中，我们会用到以下 Redis 命令：

**ZADD key score member** —— 添加成员

```bash
# 添加一个请求记录（score=时间戳，member=唯一标识）
ZADD blog:rate_limit:127.0.0.1:addComment 1711350010123 "1711350010123"
# 返回: (integer) 1  ← 新增了1个成员
```

时间复杂度：O(log N)，N 是集合大小。底层是跳表（Skip List），插入效率很高。

**ZCARD key** —— 统计成员数量

```bash
# 统计窗口内有多少个请求
ZCARD blog:rate_limit:127.0.0.1:addComment
# 返回: (integer) 5  ← 当前有5个请求记录
```

时间复杂度：O(1)。ZSET 内部维护了成员计数器，不需要遍历。

**ZREMRANGEBYSCORE key min max** —— 按 score 范围删除

```bash
# 删除所有 score <= 1711350000000 的成员（窗口之前的过期请求）
ZREMRANGEBYSCORE blog:rate_limit:127.0.0.1:addComment 0 1711350000000
# 返回: (integer) 2  ← 删除了2个过期的请求记录
```

时间复杂度：O(log N + M)，N 是集合大小，M 是被删除的成员数。

**EXPIRE key seconds** —— 设置 key 过期时间

```bash
# 设置 key 60 秒后自动删除（防止冷数据占用内存）
EXPIRE blog:rate_limit:127.0.0.1:addComment 60
# 返回: (integer) 1  ← 设置成功
```

时间复杂度：O(1)。

### 4.3 ZSET 的底层数据结构

Redis ZSET 内部使用两种编码方式：

| 条件 | 编码方式 | 说明 |
|------|----------|------|
| 成员数 < 128 且每个成员 < 64 字节 | **ziplist**（压缩列表） | 紧凑存储，省内存 |
| 否则 | **skiplist + hashtable** | 跳表 + 哈希表组合 |

对于限流场景，每个 key 中的成员数通常很少（几个到几十个），所以大多数情况下使用 ziplist 编码，内存效率很高。

### 4.4 为什么用 ZSET 而不是其他数据结构

| 方案 | 问题 |
|------|------|
| `String + INCR` | 只能实现固定窗口，有边界突发问题 |
| `List + LLEN` | 无法按时间范围删除，需要遍历 |
| `Hash` | 无法排序，无法按范围操作 |
| **`ZSET`** | **score=时间戳，完美支持范围删除和计数** |

---

## 5. 滑动窗口算法详解

### 5.1 算法步骤

滑动窗口限流的核心就是 5 个步骤，每次请求到来时依次执行：

```
请求到来
    │
    ▼
Step 1: 清除窗口外的过期记录
    │   ZREMRANGEBYSCORE key 0 (now - windowSize)
    │
    ▼
Step 2: 统计窗口内的请求数
    │   ZCARD key → count
    │
    ▼
Step 3: 判断是否超过阈值
    │   count >= maxRequests ?
    │
    ├── YES → 拒绝请求（抛出 RateLimitException）
    │
    └── NO → 继续
            │
            ▼
        Step 4: 记录本次请求
            │   ZADD key now now
            │
            ▼
        Step 5: 设置 key 过期时间
            │   EXPIRE key windowSize
            │
            ▼
        放行请求，执行业务逻辑
```

### 5.2 图解示例

```
假设: maxRequests = 3, timeWindow = 60秒 (60000毫秒)

时间线 (秒): 0----10----20----30----40----50----60----70
                                                    ↑ 现在 (now)
请求记录:    [R1:10] [R2:25] [R3:40] [R4:55]

窗口范围: [now - 60, now] = [10, 70]

Step 1: ZREMRANGEBYSCORE key 0 10
        → 删除 score ≤ 10 的成员
        → 删除 R1 (score=10)
        → 剩余: R2(25), R3(40), R4(55)

Step 2: ZCARD key
        → 返回 3

Step 3: 3 >= 3 (maxRequests)?
        → YES
        → 拒绝请求！返回 429 Too Many Requests
```

```
另一个场景：只有 R2 和 R3

时间线 (秒): 0----10----20----30----40----50----60----70
                                                    ↑ 现在 (now)
请求记录:         [R2:25] [R3:40]

Step 1: ZREMRANGEBYSCORE key 0 10
        → 没有 score ≤ 10 的成员，不删除

Step 2: ZCARD key
        → 返回 2

Step 3: 2 >= 3?
        → NO
        → 继续处理

Step 4: ZADD key 70 "70"
        → 记录本次请求
        → 现在集合: R2(25), R3(40), 新请求(70)

Step 5: EXPIRE key 60
        → 设置 key 60 秒后过期
        → 防止不活跃的 key 永远占用内存

→ 请求通过，执行业务逻辑
```

### 5.3 为什么 member 用时间戳

在我们的实现中，ZADD 的 member 和 score 都使用时间戳：

```java
ZADD key timestamp timestamp
//       ↑ score   ↑ member
```

- **score**：必须是时间戳，因为要按时间范围查询和删除
- **member**：也用时间戳（或时间戳+随机数），保证唯一性

为什么 member 需要唯一？因为 ZSET 的 member 是不重复的。如果同一毫秒内有两个请求，相同的 member 会覆盖，导致计数不准。在高并发场景下可以加上随机后缀：

```java
// 低并发（我们的博客够用了）
String member = String.valueOf(now);

// 高并发（防止同一毫秒冲突）
String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);
```

---

## 6. 自定义 @RateLimit 注解

### 6.1 注解定义

```java
package me.xunrana.blog.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 基于 Redis ZSET 滑动窗口算法实现
 */
@Target(ElementType.METHOD)       // 只能标注在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时保留（AOP 需要在运行时读取）
public @interface RateLimit {

    /**
     * 时间窗口内最大请求次数
     * 默认 10 次
     */
    int maxRequests() default 10;

    /**
     * 时间窗口大小（秒）
     * 默认 60 秒
     */
    int timeWindow() default 60;

    /**
     * 超出限流时的提示消息
     * 默认使用 ErrorCode.RATE_LIMIT_EXCEEDED 的消息
     */
    String message() default "";
}
```

### 6.2 元注解详解

**@Target(ElementType.METHOD)**

指定这个注解只能标注在方法上。因为限流是针对单个接口的，不需要标注在类或字段上。

```java
// 正确用法：标注在 Controller 方法上
@RateLimit(maxRequests = 5, timeWindow = 60)
@PostMapping("/articles/{articleId}/comments")
public Result<Void> addComment(...) { ... }

// 编译错误：不能标注在类上
@RateLimit  // ← 编译报错！
public class CommentController { ... }
```

**@Retention(RetentionPolicy.RUNTIME)**

指定注解在运行时保留。这是 AOP 切面能够在运行时读取注解信息的前提。如果设置为 `SOURCE` 或 `CLASS`，注解在编译后或类加载后就丢失了，AOP 无法获取。

### 6.3 注解属性的含义

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxRequests` | int | 10 | 时间窗口内允许的最大请求次数 |
| `timeWindow` | int | 60 | 时间窗口大小，单位：秒 |
| `message` | String | "" | 自定义提示消息，为空时使用默认消息 |

### 6.4 使用示例

```java
// 示例 1: 使用默认值（每60秒最多10次）
@RateLimit
public Result<Void> someMethod() { ... }

// 示例 2: 评论接口（每60秒最多5次）
@RateLimit(maxRequests = 5, timeWindow = 60)
public Result<Void> addComment() { ... }

// 示例 3: 登录接口（每60秒最多10次，自定义消息）
@RateLimit(maxRequests = 10, timeWindow = 60, message = "登录尝试过于频繁，请稍后再试")
public Result<LoginVO> login() { ... }
```

---

## 7. AOP 切面实现

### 7.1 什么是 AOP

AOP（Aspect-Oriented Programming，面向切面编程）是 Spring 的核心功能之一。它让我们可以在**不修改业务代码**的情况下，为方法添加额外的功能（如日志、限流、权限检查）。

```
传统方式（侵入式）：                AOP 方式（非侵入式）：

public Result<Void> addComment() {  public Result<Void> addComment() {
    // 限流逻辑 ← 与业务无关           // 只有业务逻辑
    checkRateLimit();                   commentService.addComment(dto, ip);
                                        return Result.success();
    // 业务逻辑                     }
    commentService.addComment(dto, ip);
    return Result.success();        // 限流逻辑在切面中，完全解耦
}
```

AOP 的核心概念：

| 概念 | 说明 | 在我们的限流场景中 |
|------|------|-------------------|
| **切面 (Aspect)** | 横切关注点的模块化 | `RateLimitAspect` 类 |
| **连接点 (Join Point)** | 程序执行的某个点 | 被 `@RateLimit` 标注的方法 |
| **通知 (Advice)** | 在连接点执行的动作 | `@Before` — 在方法执行前检查限流 |
| **切入点 (Pointcut)** | 匹配连接点的表达式 | `@annotation(rateLimit)` |

### 7.2 为什么用 @Before 而不是 @Around

Spring AOP 提供了多种通知类型：

| 通知类型 | 执行时机 | 用途 |
|---------|---------|------|
| `@Before` | 方法执行前 | 前置检查（限流、权限） |
| `@After` | 方法执行后（无论成功失败） | 资源清理 |
| `@AfterReturning` | 方法成功返回后 | 结果处理 |
| `@AfterThrowing` | 方法抛出异常后 | 异常处理 |
| `@Around` | 包裹方法执行（最强大） | 需要控制方法是否执行 |

对于限流，我们选择 `@Before`：

```
@Before 的执行流程：

    请求到来
        │
        ▼
    @Before 切面执行
        │
        ├── 超过限流 → 抛出 RateLimitException → 方法不执行
        │
        └── 未超过 → 通过
                │
                ▼
            目标方法执行（addComment）
```

限流只需要在方法执行前判断"能不能执行"——如果超限就抛异常阻断，如果没超限就放行。不需要像 `@Around` 那样包裹方法执行过程或处理返回值。使用 `@Before` 语义更清晰，代码更简单。

### 7.3 Key 生成策略

限流的 Redis key 需要唯一标识"谁在访问哪个接口"。我们采用 **IP + 类名.方法名** 的组合：

```
Key 格式: blog:rate_limit:{IP}:{ClassName}.{MethodName}

示例:
blog:rate_limit:127.0.0.1:CommentController.addComment
blog:rate_limit:192.168.1.100:AuthController.login
blog:rate_limit:47.96.88.23:CommentController.addComment
```

为什么这样设计？

- **IP 维度**：每个用户独立限流，A 用户的请求不影响 B 用户
- **方法维度**：不同接口的限流策略独立，评论接口 5 次/分钟不影响登录接口 10 次/分钟
- **前缀 `blog:rate_limit:`**：Redis key 的命名规范，避免与其他 key 冲突

### 7.4 获取客户端 IP

在反向代理（Nginx）环境下，不能直接用 `request.getRemoteAddr()`，因为拿到的是 Nginx 的 IP，而不是真实客户端 IP。需要从请求头中获取：

```java
/**
 * 获取客户端真实 IP 地址
 * 优先从代理头中获取，兜底用 getRemoteAddr()
 */
private String getClientIp(HttpServletRequest request) {
    // X-Forwarded-For: 标准的代理头，多级代理时以逗号分隔
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        // X-Real-IP: Nginx 常用的代理头
        ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        // 兜底: 直接从连接中获取（没有代理时）
        ip = request.getRemoteAddr();
    }
    // X-Forwarded-For 可能包含多个 IP（多级代理），取第一个
    if (ip != null && ip.contains(",")) {
        ip = ip.substring(0, ip.indexOf(",")).trim();
    }
    return ip;
}
```

### 7.5 完整切面实现

```java
package me.xunrana.blog.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.annotation.RateLimit;
import me.xunrana.blog.exception.RateLimitException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect          // 标记这是一个切面类
@Component       // 注册为 Spring Bean
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key 前缀
    private static final String RATE_LIMIT_KEY_PREFIX = "blog:rate_limit:";

    /**
     * @Before: 在目标方法执行之前执行
     * @annotation(rateLimit): 切入点——匹配所有标注了 @RateLimit 的方法
     *
     * 参数 rateLimit 就是方法上的 @RateLimit 注解实例，
     * Spring AOP 会自动注入，我们可以从中读取 maxRequests、timeWindow 等属性
     */
    @Before("@annotation(rateLimit)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        // ===== TODO 1: 构建 Redis Key =====
        // 获取当前 HTTP 请求
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return; // 非 HTTP 请求环境（如测试），跳过限流
        }
        HttpServletRequest request = attributes.getRequest();

        // 获取客户端 IP
        String ip = getClientIp(request);

        // 获取目标方法的类名和方法名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 拼接 Redis Key: blog:rate_limit:127.0.0.1:CommentController.addComment
        String key = RATE_LIMIT_KEY_PREFIX + ip + ":" + className + "." + methodName;

        // ===== TODO 2: 滑动窗口限流判断 =====
        long now = System.currentTimeMillis();
        long windowStart = now - rateLimit.timeWindow() * 1000L;

        // Step 1: 删除窗口外的过期请求记录
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // Step 2: 统计窗口内的请求数
        Long count = redisTemplate.opsForZSet().zCard(key);

        // Step 3: 判断是否超过阈值
        if (count != null && count >= rateLimit.maxRequests()) {
            log.warn("接口限流: key={}, count={}, max={}", key, count, rateLimit.maxRequests());

            // ===== TODO 3: 超过限流，抛出异常 =====
            String message = rateLimit.message().isEmpty()
                    ? "请求过于频繁，请稍后再试"
                    : rateLimit.message();
            throw new RateLimitException(message);
        }

        // Step 4: 记录本次请求（score=当前时间戳，member=当前时间戳字符串）
        redisTemplate.opsForZSet().add(key, String.valueOf(now), now);

        // Step 5: 设置 key 过期时间（自动清理不活跃的 key）
        redisTemplate.expire(key, rateLimit.timeWindow(), TimeUnit.SECONDS);
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        return ip;
    }
}
```

### 7.6 代码逐行解析

**@Aspect + @Component**

```java
@Aspect      // 告诉 Spring：这是一个 AOP 切面
@Component   // 告诉 Spring：把这个类注册为 Bean（AOP 切面必须是 Spring Bean 才能生效）
```

两个注解缺一不可。`@Aspect` 定义了切面身份，`@Component` 让 Spring 扫描和管理它。

**@Before("@annotation(rateLimit)")**

```java
@Before("@annotation(rateLimit)")
public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
```

- `@Before`：在目标方法执行之前执行
- `@annotation(rateLimit)`：切入点表达式，含义是"匹配所有标注了 @RateLimit 注解的方法"
- 参数名 `rateLimit` 与切入点表达式中的 `rateLimit` **必须一致**，Spring 会自动将注解实例注入到这个参数中

**JoinPoint**

```java
JoinPoint joinPoint  // 连接点，包含目标方法的信息
```

通过 JoinPoint 可以获取：
- `joinPoint.getTarget().getClass().getSimpleName()` → 目标类名，如 `CommentController`
- `joinPoint.getSignature().getName()` → 方法名，如 `addComment`
- `joinPoint.getArgs()` → 方法参数

**RequestContextHolder**

```java
ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
HttpServletRequest request = attributes.getRequest();
```

AOP 切面不是 Controller，不能直接注入 `HttpServletRequest`。`RequestContextHolder` 是 Spring 提供的工具类，可以在任何地方获取当前线程绑定的 HTTP 请求。

**RedisTemplate 操作**

```java
// opsForZSet() 返回 ZSetOperations，提供 ZSET 相关操作
redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);  // ZREMRANGEBYSCORE
redisTemplate.opsForZSet().zCard(key);                                // ZCARD
redisTemplate.opsForZSet().add(key, String.valueOf(now), now);        // ZADD
redisTemplate.expire(key, rateLimit.timeWindow(), TimeUnit.SECONDS);  // EXPIRE
```

Spring Data Redis 的 `RedisTemplate` 将 Redis 命令封装为 Java 方法，`opsForZSet()` 对应 ZSET 操作。

---

## 8. 限流异常处理

### 8.1 自定义 RateLimitException

限流异常是一种特殊的业务异常。我们让它继承 `BusinessException`，这样可以复用现有的异常处理架构：

```java
package me.xunrana.blog.exception;

import me.xunrana.blog.common.ErrorCode;

/**
 * 限流异常
 * 当请求超过限流阈值时抛出
 */
public class RateLimitException extends BusinessException {

    public RateLimitException(String message) {
        // 使用 RATE_LIMIT_EXCEEDED 错误码（4001），自定义消息
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message);
    }
}
```

继承关系：

```
RuntimeException
    └── BusinessException
            └── RateLimitException
```

### 8.2 GlobalExceptionHandler 中添加处理方法

在已有的 `GlobalExceptionHandler` 中新增一个专门处理 `RateLimitException` 的方法：

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ===== 新增：限流异常处理 =====
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)  // HTTP 429
    public Result<Void> handleRateLimitException(RateLimitException e) {
        log.warn("接口限流: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    // ===== 原有的异常处理方法保持不变 =====

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    // ... 其他异常处理方法
}
```

### 8.3 为什么需要单独处理 RateLimitException

虽然 `RateLimitException` 继承自 `BusinessException`，已有的 `handleBusinessException` 也能捕获它，但我们单独处理的原因：

1. **HTTP 状态码不同**：限流应该返回 **429 Too Many Requests**，而一般业务异常返回 200
2. **日志级别/内容不同**：限流日志需要记录更具体的信息（哪个 IP、哪个接口）
3. **语义更清晰**：明确区分"业务规则不满足"和"请求频率超限"

### 8.4 异常匹配优先级

`@ExceptionHandler` 遵循"就近原则"——优先匹配最具体的异常类型：

```
抛出 RateLimitException
    ↓ 检查 handleRateLimitException(RateLimitException.class) → 精确匹配 ✓
    ↓ 不会继续检查 handleBusinessException

抛出 BusinessException (非 RateLimitException)
    ↓ 检查 handleRateLimitException → 不匹配（BusinessException 不是 RateLimitException 的子类）
    ↓ 检查 handleBusinessException(BusinessException.class) → 匹配 ✓
```

### 8.5 完整的限流响应

```
客户端: POST /api/v1/articles/1/comments (第6次请求，60秒内)
    │
    ▼
Spring Security Filter Chain
    │ 公开接口，无需认证
    ▼
DispatcherServlet
    │ 路由到 CommentController.addComment()
    ▼
AOP 切面 RateLimitAspect.checkRateLimit()
    │ 检查 Redis: 窗口内已有 5 个请求
    │ 5 >= 5 → 超过限流阈值
    │ 抛出 RateLimitException("请求过于频繁，请稍后再试")
    ▼
GlobalExceptionHandler.handleRateLimitException()
    │ 记录日志: WARN "接口限流: code=4001, message=请求过于频繁，请稍后再试"
    ▼
返回 HTTP 429:
{
    "code": 4001,
    "message": "请求过于频繁，请稍后再试",
    "data": null
}
```

---

## 9. 应用到控制器

### 9.1 评论接口限流

评论接口面向匿名用户开放，最容易被刷屏。设置每 IP 每分钟最多 5 次评论：

```java
@RestController
@RequestMapping("/v1")
@Tag(name = "评论模块")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/articles/{articleId}/comments")
    @Operation(summary = "获取文章评论列表（树形结构）")
    public Result<List<CommentVO>> getComments(
            @Parameter(description = "文章ID") @PathVariable Long articleId) {
        return Result.success(commentService.getCommentsByArticleId(articleId));
    }

    @PostMapping("/articles/{articleId}/comments")
    @Operation(summary = "发表评论")
    @RateLimit(maxRequests = 5, timeWindow = 60)  // ← 每IP每60秒最多5条评论
    public Result<Void> addComment(
            @Parameter(description = "文章ID") @PathVariable Long articleId,
            @Valid @RequestBody CommentDTO dto,
            HttpServletRequest request) {
        dto.setArticleId(articleId);
        String ip = getClientIp(request);
        commentService.addComment(dto, ip);
        return Result.success();
    }

    // ... getClientIp 方法
}
```

注意：`@RateLimit` 只加在 `addComment` 上（写操作），不加在 `getComments` 上（读操作）。读接口通常不需要限流（除非有特殊的爬虫防护需求）。

### 9.2 登录接口限流

登录接口是暴力破解的主要目标。设置每 IP 每分钟最多 10 次登录尝试：

```java
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证模块")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    @RateLimit(maxRequests = 10, timeWindow = 60, message = "登录尝试过于频繁，请稍后再试")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        LoginVO loginVO = authService.login(loginDTO);
        return Result.success(loginVO);
    }

    // ... 其他方法不变
}
```

### 9.3 为什么不给所有接口加限流

```
需要限流的接口：                     不需要限流的接口：
─────────────────                   ─────────────────
POST /comments (匿名写入)           GET /articles (公开读取)
POST /auth/login (安全敏感)         GET /categories (公开读取)
POST /admin/files/upload (资源消耗) GET /tags (公开读取)
```

限流的原则是**保护高风险接口**，而不是给所有接口都加。读接口的压力可以通过 Redis 缓存来缓解，不需要限流。如果给所有接口都加限流，反而会增加 Redis 的负担（每次请求都要执行 5 个 Redis 命令）。

---

## 10. 原子性与 Lua 脚本

### 10.1 当前实现的问题

我们的滑动窗口实现使用了 5 个独立的 Redis 命令：

```java
// 这 5 个命令是分别发送给 Redis 的，不是原子操作
redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);  // 命令 1
Long count = redisTemplate.opsForZSet().zCard(key);                   // 命令 2
// 判断 count...
redisTemplate.opsForZSet().add(key, member, now);                     // 命令 3
redisTemplate.expire(key, timeWindow, TimeUnit.SECONDS);              // 命令 4
```

在高并发场景下，可能出现竞态条件（Race Condition）：

```
线程A                          线程B
─────                          ─────
ZREMRANGEBYSCORE → count=4
                               ZREMRANGEBYSCORE → count=4
判断 4 < 5 → 通过
                               判断 4 < 5 → 通过
ZADD (count变成5)
                               ZADD (count变成6!)  ← 超过阈值了！
```

两个线程同时读到 count=4，都认为没超限，都放行了。最终窗口内有 6 个请求，超过了 maxRequests=5 的限制。

### 10.2 Lua 脚本解决方案

Redis 支持通过 Lua 脚本在服务端原子地执行多个命令。整个 Lua 脚本在 Redis 中是**单线程执行**的，不会被其他命令打断。

```lua
-- rate_limit.lua
-- 滑动窗口限流 Lua 脚本（原子操作版本）
-- KEYS[1] = 限流 key
-- ARGV[1] = maxRequests (最大请求数)
-- ARGV[2] = timeWindow (时间窗口，毫秒)
-- ARGV[3] = now (当前时间戳，毫秒)
-- 返回: 1 = 允许, 0 = 拒绝

local key = KEYS[1]
local max = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Step 1: 删除窗口外的过期记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Step 2: 统计窗口内的请求数
local count = redis.call('ZCARD', key)

-- Step 3: 判断是否超过阈值
if count >= max then
    return 0  -- 拒绝
end

-- Step 4: 记录本次请求
redis.call('ZADD', key, now, now)

-- Step 5: 设置过期时间（秒）
redis.call('EXPIRE', key, math.ceil(window / 1000))

return 1  -- 允许
```

使用 Spring Data Redis 执行 Lua 脚本：

```java
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    // 加载 Lua 脚本（只加载一次，Redis 会缓存编译后的脚本）
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local key = KEYS[1] " +
            "local max = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
            "local count = redis.call('ZCARD', key) " +
            "if count >= max then return 0 end " +
            "redis.call('ZADD', key, now, now) " +
            "redis.call('EXPIRE', key, math.ceil(window / 1000)) " +
            "return 1"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        // ... 构建 key（同前）...

        long now = System.currentTimeMillis();
        long windowMillis = rateLimit.timeWindow() * 1000L;

        // 执行 Lua 脚本（原子操作）
        Long allowed = redisTemplate.execute(
            RATE_LIMIT_SCRIPT,
            Collections.singletonList(key),  // KEYS
            rateLimit.maxRequests(),          // ARGV[1]
            windowMillis,                     // ARGV[2]
            now                              // ARGV[3]
        );

        if (allowed == null || allowed == 0L) {
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }
    }
}
```

### 10.3 对我们项目的建议

**当前阶段：不需要 Lua 脚本**。

原因：

1. **个人博客流量极小**：日均访问量可能只有几十到几百，不存在高并发竞态条件
2. **非原子操作的误差可接受**：即使偶尔多放过一两个请求（比如 6 个而不是 5 个），对博客系统也没有影响
3. **代码简洁性**：非 Lua 版本更易读、易维护，适合学习

**什么时候需要 Lua 脚本**：

- 高并发系统（QPS > 1000）
- 对限流精度要求严格的场景（如支付接口、秒杀接口）
- 分布式环境下多实例部署时

在面试中，能说出"当前实现不是原子的，高并发下有竞态条件，可以用 Lua 脚本优化"就是加分项。

---

## 11. 测试验证

### 11.1 使用 curl 测试评论限流

启动项目后，在终端中执行以下命令，快速发送 7 个评论请求：

```bash
for i in $(seq 1 7); do
  curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/v1/articles/1/comments \
    -H "Content-Type: application/json" \
    -d '{"nickname":"test","content":"hello"}'
  echo " - Request $i"
done
```

**预期结果**：

```
200 - Request 1    ← 第1次：通过
200 - Request 2    ← 第2次：通过
200 - Request 3    ← 第3次：通过
200 - Request 4    ← 第4次：通过
200 - Request 5    ← 第5次：通过（达到上限）
429 - Request 6    ← 第6次：被限流！
429 - Request 7    ← 第7次：被限流！
```

### 11.2 查看 Redis 中的数据

```bash
# 查看限流 key 中的所有记录（member 和 score）
redis-cli ZRANGE blog:rate_limit:127.0.0.1:CommentController.addComment 0 -1 WITHSCORES

# 预期输出类似：
# 1) "1711350010123"
# 2) "1711350010123"
# 3) "1711350010456"
# 4) "1711350010456"
# 5) "1711350010789"
# 6) "1711350010789"
# ...
# 每两行一组：member 和 score（都是时间戳）

# 查看 key 的 TTL（剩余过期时间）
redis-cli TTL blog:rate_limit:127.0.0.1:CommentController.addComment
# 预期：小于 60 的正整数

# 查看 key 中有多少条记录
redis-cli ZCARD blog:rate_limit:127.0.0.1:CommentController.addComment
# 预期：5（达到上限后不再新增）
```

### 11.3 测试登录限流

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrongpwd"}'
  echo " - Request $i"
done
```

**预期结果**：前 10 次返回 200（密码错误的业务响应），第 11 和 12 次返回 429。

### 11.4 等待窗口过期后重试

```bash
# 等待 60 秒后再次请求
sleep 60

curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8080/api/v1/articles/1/comments \
  -H "Content-Type: application/json" \
  -d '{"nickname":"test","content":"hello after wait"}'

# 预期：HTTP Status: 200（窗口过期后可以重新请求）
```

### 11.5 查看应用日志

当请求被限流时，应用日志中会输出 WARN 级别的日志：

```
WARN  m.x.b.aspect.RateLimitAspect - 接口限流: key=blog:rate_limit:127.0.0.1:CommentController.addComment, count=5, max=5
WARN  m.x.b.exception.GlobalExceptionHandler - 接口限流: code=4001, message=请求过于频繁，请稍后再试
```

---

## 12. 面试常见问题

### Q1: 常见的限流算法有哪些？各有什么优缺点？

**答**：常见的限流算法有四种：

1. **固定窗口**：将时间划分为固定大小的窗口，每个窗口维护一个计数器。实现简单，但存在**边界突发问题**——在窗口切换的边界处，短时间内可以通过 2 倍于阈值的请求。
2. **滑动窗口**：窗口随时间滑动，每次请求都看"过去 N 秒"的请求数。解决了固定窗口的边界突发问题，适合 API 级别的限流。可以用 Redis ZSET 实现，score 存时间戳。
3. **令牌桶**：以固定速率往桶里放令牌，请求需要拿到令牌才能通过。允许突发流量（桶中可以积累令牌），Guava 的 `RateLimiter` 就是令牌桶实现。
4. **漏桶**：请求进入桶中，以恒定速率流出。能把不规则的流量变平滑，但不允许任何突发，适合流量整形场景。

### Q2: 滑动窗口和固定窗口的区别？为什么滑动窗口更好？

**答**：固定窗口把时间切成不重叠的固定区间（比如每分钟一个），在窗口切换的边界处存在盲区——两个窗口的末尾和开头可以集中通过 2 倍的流量。滑动窗口则以当前时刻为终点，往前看固定时间段，窗口随时间平滑移动，任何时刻看到的都是"过去 N 秒"的完整请求数，不存在边界突发问题。在对限流精度有要求的场景（如防止暴力破解、防刷屏），滑动窗口明显更好。

### Q3: 如何用 Redis 实现滑动窗口限流？

**答**：用 Redis 的 ZSET（有序集合）实现。核心思路是把每个请求记录为 ZSET 的一个成员，score 为请求的时间戳。每次新请求到来时执行：(1) `ZREMRANGEBYSCORE` 删除窗口外的过期记录；(2) `ZCARD` 统计窗口内的请求数；(3) 如果超过阈值就拒绝；(4) 否则 `ZADD` 记录本次请求；(5) `EXPIRE` 设置 key 过期时间防止内存泄漏。key 的格式一般是 `prefix:IP:method`，这样每个用户每个接口独立限流。如果并发量大，还需要用 Lua 脚本保证这些操作的原子性。

### Q4: Redis ZSET 的底层数据结构是什么？时间复杂度？

**答**：ZSET 在 Redis 中有两种编码方式：当成员数少于 128 且每个成员小于 64 字节时，使用 **ziplist（压缩列表）**，内存效率高；否则使用 **skiplist（跳表）+ hashtable（哈希表）** 的组合。跳表保证了按 score 排序和范围查询的效率，哈希表保证了按 member 查找的效率。

核心操作时间复杂度：`ZADD` O(logN)、`ZCARD` O(1)、`ZREMRANGEBYSCORE` O(logN + M)（M 为被删除的成员数）、`ZRANGEBYSCORE` O(logN + M)。对于限流场景，N 通常很小（几个到几十个），性能完全没有问题。

### Q5: 你的限流方案有什么问题？如何改进？

**答**：目前的实现有几个可以改进的地方：

1. **非原子操作**：5 个 Redis 命令是分别执行的，高并发下可能出现竞态条件（两个请求同时读到未超限，同时放行）。改进方案是用 Lua 脚本把 5 个命令合并为原子操作。
2. **单节点 Redis**：如果 Redis 宕机，限流就失效了。改进方案是使用 Redis Sentinel 或 Redis Cluster 提高可用性。
3. **仅基于 IP**：如果攻击者使用代理池更换 IP，基于 IP 的限流就失效了。可以结合用户 ID、设备指纹等维度做多级限流。
4. **没有全局限流**：当前是每个接口独立限流，没有全局的 QPS 保护。可以加一层网关级别的全局限流（如 Nginx 的 `limit_req`）。

### Q6: 在微服务架构中，限流应该放在哪一层？

**答**：限流可以放在多个层次，各有优劣：

1. **API Gateway（网关层）**：如 Spring Cloud Gateway、Nginx。适合全局限流（总 QPS 控制）、IP 黑名单。优点是所有流量都经过网关，统一管控；缺点是粒度较粗，难以针对具体业务接口做细粒度限流。
2. **Service（服务层）**：如我们的 AOP 切面。适合业务级别的限流（如某个接口每用户每分钟 N 次）。优点是可以结合业务逻辑做精细控制；缺点是每个服务都要引入限流组件。
3. **最佳实践**：两层都做。网关层做粗粒度的全局保护（防 DDoS），服务层做细粒度的业务保护（防刷接口）。这就是**分层限流**的思想。

### Q7: 令牌桶和漏桶的区别？各适合什么场景？

**答**：

- **令牌桶**：以固定速率生成令牌，请求消耗令牌。桶中可以积累令牌（有上限），所以允许突发流量——如果桶中有 20 个令牌，突然来 20 个请求可以全部通过。适合场景：允许短暂突发但长期平均速率受限的场景，如秒杀活动开始瞬间。Guava `RateLimiter` 就是令牌桶实现。
- **漏桶**：请求进入桶中排队，以恒定速率流出。无论输入多猛，输出永远是匀速的。不允许任何突发。适合场景：需要严格控制处理速率的场景，如消息队列的消费速率控制、网络流量整形。

一句话区别：**令牌桶控制的是"平均速率"，允许瞬间突发；漏桶控制的是"瞬时速率"，绝对匀速**。
