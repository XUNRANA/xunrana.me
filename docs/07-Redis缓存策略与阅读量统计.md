# 07 - Redis 缓存策略与阅读量统计

> 本文档讲解 Redis 缓存的核心概念、Cache Aside 模式实现、文章浏览量的 Redis 计数方案、定时同步机制、以及缓存穿透/击穿/雪崩的防护策略。

---

## 目录

1. [为什么需要缓存](#1-为什么需要缓存)
2. [Redis 基础回顾](#2-redis-基础回顾)
3. [常见缓存策略对比](#3-常见缓存策略对比)
4. [我们项目中的缓存设计](#4-我们项目中的缓存设计)
5. [Cache Aside 实现详解 (对应 TODO 1)](#5-cache-aside-实现详解-对应-todo-1)
6. [浏览量 Redis 计数 (对应 TODO 2)](#6-浏览量-redis-计数-对应-todo-2)
7. [定时同步机制 (对应 TODO 3)](#7-定时同步机制-对应-todo-3)
8. [缓存失效策略 (对应 TODO 4)](#8-缓存失效策略-对应-todo-4)
9. [缓存三大问题及防护](#9-缓存三大问题及防护)
10. [测试验证](#10-测试验证)
11. [面试常见问题](#11-面试常见问题)

---

## 1. 为什么需要缓存

### 1.1 没有缓存的问题

在没有缓存的架构中，每一次用户请求都会直接访问数据库。数据库作为持久化存储，其 I/O 操作（磁盘读写、网络传输、SQL 解析与执行）远比内存操作慢。当并发量上来后，数据库会成为整个系统的性能瓶颈：

```
用户 A ──→ Controller ──→ Service ──→ MySQL ──→ 磁盘读取（50ms）
用户 B ──→ Controller ──→ Service ──→ MySQL ──→ 磁盘读取（50ms）
用户 C ──→ Controller ──→ Service ──→ MySQL ──→ 磁盘读取（50ms）
...
用户 N ──→ Controller ──→ Service ──→ MySQL ──→ 连接池耗尽，超时！
```

**问题清单**：
1. **响应慢**：每次查询都走数据库，涉及 SQL 解析、索引查找、磁盘 I/O，单次耗时 30-100ms
2. **数据库压力大**：高并发场景下数据库连接池被占满，后续请求排队甚至超时
3. **资源浪费**：博客文章内容短时间内不会变，重复查询完全相同的数据毫无意义
4. **扩展困难**：数据库的横向扩展（读写分离、分库分表）成本远高于缓存

### 1.2 博客场景分析

博客系统是典型的**读多写少**场景：

| 操作 | 频率 | 特点 |
|------|------|------|
| 查看文章详情 | 极高（每次访问） | 读操作，同一文章被反复查看 |
| 查看分类列表 | 高（每次导航） | 读操作，分类很少变化 |
| 创建/更新文章 | 低（管理员操作） | 写操作，一天可能只有几次 |
| 创建/更新分类 | 极低 | 写操作，一周可能只有一次 |

**用具体数字说明**：假设博客日均 1000 PV（页面浏览量），其中 80% 是文章详情页：
- **无缓存**：800 次/天 x 50ms/次 = 40,000ms 的数据库查询时间，还要加上连接建立、关闭的开销
- **有缓存**：第一次查询走数据库（50ms），后续 799 次直接从 Redis 返回（1ms/次） = 50ms + 799ms = 849ms

性能提升约 **47 倍**，数据库负载降低到 1/800。

### 1.3 引入缓存后的架构

```
                          ┌──────────┐
                     ┌───→│  Redis   │───→ 命中？直接返回（1ms）
                     │    │ (内存缓存) │
                     │    └──────────┘
                     │         │ 未命中
用户请求 → Controller → Service │
                     │         ▼
                     │    ┌──────────┐
                     └───→│  MySQL   │───→ 查询结果写入 Redis → 返回
                          │ (持久存储) │
                          └──────────┘
```

缓存的本质就是：**用内存的速度换取数据库的压力，用空间换时间**。

---

## 2. Redis 基础回顾

### 2.1 Redis 是什么

Redis（**Re**mote **Di**ctionary **S**erver）是一个开源的、基于内存的键值存储数据库。它的核心特点：

1. **速度极快**：数据存储在内存中，读写操作 O(1)，QPS 可达 10 万+
2. **数据结构丰富**：不只是简单的 Key-Value，支持 String、Hash、List、Set、ZSet 五种核心结构
3. **持久化支持**：虽然是内存数据库，但支持 RDB 快照和 AOF 日志两种持久化方式
4. **原子操作**：所有单条命令都是原子的，天然线程安全
5. **支持 TTL**：键可以设置过期时间，到期自动删除

### 2.2 五种核心数据结构

| 数据结构 | 描述 | 典型应用场景 | 本项目中的用途 |
|----------|------|-------------|---------------|
| **String** | 最简单的键值对，值可以是字符串、数字、JSON | 缓存对象（JSON序列化）、计数器、分布式锁 | 缓存文章详情 JSON、分类列表 JSON |
| **Hash** | 键值对的集合，类似 Java 的 HashMap | 存储对象的多个字段、计数器集合 | 存储所有文章的浏览量（field=articleId, value=count） |
| **List** | 有序的字符串列表，支持头尾插入 | 消息队列、最新文章列表、时间线 | 暂未使用 |
| **Set** | 无序的字符串集合，元素唯一 | 标签、点赞用户集合、共同关注 | 暂未使用 |
| **ZSet** | 有序集合，每个元素有一个分数（score）用于排序 | 排行榜、延迟队列 | 暂未使用（后续可做热门文章排行） |

### 2.3 Key 命名规范

Redis 的 Key 就是一个字符串，但在实际项目中需要建立统一的命名规范。业界通用的约定是**冒号分隔的层级命名**：

```
项目名:模块:标识符
```

**为什么用冒号 `:`？**
- Redis 官方文档推荐的分隔符，GUI 工具（如 RedisInsight、Another Redis Desktop Manager）会根据冒号自动将 Key 分组为树形结构，方便查看和管理
- 不用 `.` 或 `/` 是因为冒号在 URL 和 Java 包名中没有特殊含义，不会引发歧义

**我们项目的 Key 命名**：

```
blog:article:detail:{slug}     → 文章详情缓存
blog:article:views             → 所有文章的浏览量（Hash）
blog:categories                → 分类列表缓存
blog:token:blacklist:{token}   → JWT 黑名单（已在 JwtAuthFilter 中使用）
```

### 2.4 TTL（Time To Live）过期机制

TTL 是 Redis Key 的"保质期"——设定一个时间后，Key 会自动被删除：

```bash
# 设置 Key 并指定 30 分钟后过期
SET blog:article:detail:my-first-post "{...}" EX 1800

# 查看 Key 剩余存活时间（秒）
TTL blog:article:detail:my-first-post
# 返回: 1795（表示还剩 1795 秒）

# 手动删除 Key
DEL blog:article:detail:my-first-post
```

**TTL 单位**：
- `EX`：秒（seconds）
- `PX`：毫秒（milliseconds）
- Spring 中使用 `java.time.Duration`：`Duration.ofMinutes(30)`

**为什么缓存必须设 TTL？**
1. 防止缓存和数据库数据长期不一致
2. 自动清理不再访问的冷数据，节省内存
3. 为缓存雪崩防护留出余地（后面会讲随机 TTL）

### 2.5 常用命令速查

| 命令 | 语法 | 说明 | 示例 |
|------|------|------|------|
| `SET` | `SET key value [EX seconds]` | 设置字符串值 | `SET name "xunrana" EX 60` |
| `GET` | `GET key` | 获取字符串值 | `GET name` |
| `DEL` | `DEL key [key ...]` | 删除一个或多个 Key | `DEL name age` |
| `EXPIRE` | `EXPIRE key seconds` | 设置过期时间 | `EXPIRE name 300` |
| `TTL` | `TTL key` | 查看剩余存活时间 | `TTL name` |
| `EXISTS` | `EXISTS key` | 检查 Key 是否存在 | `EXISTS name` |
| `HSET` | `HSET key field value` | 设置 Hash 的某个字段 | `HSET user:1 name "xunrana"` |
| `HGET` | `HGET key field` | 获取 Hash 的某个字段 | `HGET user:1 name` |
| `HGETALL` | `HGETALL key` | 获取 Hash 的所有字段 | `HGETALL user:1` |
| `HINCRBY` | `HINCRBY key field increment` | Hash 字段原子递增 | `HINCRBY blog:article:views 1 1` |
| `KEYS` | `KEYS pattern` | 按模式查找 Key（生产慎用） | `KEYS blog:article:*` |

**Java 中使用 RedisTemplate 对应关系**：

```java
// String 操作
redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(30));  // SET key value EX 1800
redisTemplate.opsForValue().get(key);                                  // GET key
redisTemplate.delete(key);                                             // DEL key

// Hash 操作
redisTemplate.opsForHash().put(key, field, value);         // HSET key field value
redisTemplate.opsForHash().get(key, field);                // HGET key field
redisTemplate.opsForHash().entries(key);                   // HGETALL key
redisTemplate.opsForHash().increment(key, field, delta);   // HINCRBY key field delta
```

---

## 3. 常见缓存策略对比

缓存策略解决的核心问题是：**数据在缓存和数据库之间如何同步？** 不同的策略在一致性、性能、复杂度之间做不同的取舍。

### 3.1 Cache Aside（旁路缓存）

**读流程**：
1. 先查缓存 → 命中则直接返回
2. 缓存未命中 → 查数据库 → 将结果写入缓存 → 返回

**写流程**：
1. 先更新数据库
2. 再删除缓存（注意：是**删除**，不是更新）

```java
// 读流程伪代码
public Article getArticle(String slug) {
    // 1. 先查缓存
    Article article = redis.get("blog:article:detail:" + slug);
    if (article != null) {
        return article;  // 缓存命中
    }
    // 2. 缓存未命中，查数据库
    article = db.selectBySlug(slug);
    // 3. 写入缓存
    if (article != null) {
        redis.set("blog:article:detail:" + slug, article, 30, TimeUnit.MINUTES);
    }
    return article;
}

// 写流程伪代码
public void updateArticle(Long id, ArticleDTO dto) {
    // 1. 先更新数据库
    db.update(id, dto);
    // 2. 再删除缓存（而不是更新缓存）
    redis.delete("blog:article:detail:" + slug);
}
```

**为什么写操作是删除缓存而不是更新缓存？**

考虑并发场景：
- 线程 A 更新文章标题为 "V2"
- 线程 B 同时更新文章标题为 "V3"

如果"更新缓存"：A 更新DB → A 更新缓存为V2 → B 更新DB → B 更新缓存为V3，看起来没问题。但如果网络延迟导致 A 更新缓存的操作晚于 B：A 更新DB → B 更新DB → B 更新缓存为V3 → A 更新缓存为V2，此时缓存是 V2，但数据库是 V3，**不一致**！

如果"删除缓存"：A 更新DB → A 删缓存 → B 更新DB → B 删缓存，无论顺序如何，缓存都被删除了，下次读取时会从数据库加载最新数据，**最终一致**。

### 3.2 Read Through（读穿透）

**特点**：应用程序只与缓存交互，缓存层自己负责在缓存未命中时查询数据库并回填。

```
应用程序 ──→ 缓存层 ──→ 命中？返回
                │ 未命中
                ├──→ 查数据库
                ├──→ 回填缓存
                └──→ 返回
```

**实现方式**：需要缓存中间件支持（如 Caffeine + Spring Cache 抽象），缓存框架会自动处理 DB 查询。

### 3.3 Write Through（写穿透/同步写）

**特点**：应用程序只写缓存，缓存层同步将数据写入数据库。写操作保证缓存和数据库同时更新。

```
应用程序 ──→ 缓存层 ──→ 写缓存 + 同步写数据库 ──→ 返回
```

**优点**：缓存和数据库强一致
**缺点**：写操作延迟增大（两次写操作串行），需要缓存框架支持

### 3.4 Write Behind（写回/异步写）

**特点**：应用程序只写缓存，缓存层**异步、批量**将数据写入数据库。

```
应用程序 ──→ 缓存层 ──→ 写缓存 → 立即返回
                              │
                              └──→ 异步批量写数据库（定时/攒够一批）
```

**优点**：写操作极快（只写内存），适合高写入场景
**缺点**：数据丢失风险（缓存挂了但还没同步到数据库）、实现复杂

### 3.5 对比总结

| 策略 | 一致性 | 性能 | 复杂度 | 适用场景 |
|------|--------|------|--------|---------|
| **Cache Aside** | 最终一致 | 读快，写正常 | 低 | 通用场景，大厂最常用 |
| **Read Through** | 最终一致 | 读快 | 中（需缓存框架） | 读多写少，有缓存框架时 |
| **Write Through** | 强一致 | 写慢 | 中 | 对一致性要求高的场景 |
| **Write Behind** | 弱一致 | 写极快 | 高 | 日志、计数等允许丢数据的场景 |

### 3.6 为什么本项目选择 Cache Aside？

1. **简单直观**：代码逻辑清晰，读和写的缓存操作由开发者显式控制，不依赖缓存框架的黑盒行为
2. **无框架依赖**：只需要 `RedisTemplate`，不需要引入 Spring Cache、Caffeine 等额外抽象层
3. **面试高频**：Cache Aside 是国内大厂（阿里、美团、字节）面试最常考的缓存策略，手写实现对面试有直接帮助
4. **灵活可控**：可以针对不同数据设置不同 TTL、不同的失效策略，精细化控制

---

## 4. 我们项目中的缓存设计

### 4.1 缓存对象一览

| 缓存对象 | Redis 数据结构 | Key 格式 | TTL | 失效策略 |
|----------|---------------|---------|-----|---------|
| 文章详情 | String (JSON) | `blog:article:detail:{slug}` | 30min + 随机偏移 | 文章增删改时主动删除 |
| 文章浏览量 | Hash | `blog:article:views` | 不设 TTL（定时同步后删除） | 定时任务同步到 MySQL 后清空 |
| 分类列表 | String (JSON) | `blog:categories` | 60min + 随机偏移 | 分类增删改时主动删除 |

**设计考量**：
- **文章详情**用 String 存储 JSON 序列化后的 `ArticleDetailVO`，因为每次都是整体读取，不需要部分字段更新
- **浏览量**用 Hash 而不是 String，是因为所有文章的浏览量可以集中在一个 Key 中管理，用 `HINCRBY` 原子递增
- **TTL 添加随机偏移**是为了防止多个 Key 在同一时刻过期，导致缓存雪崩

### 4.2 RedisConstants 常量类

将所有 Redis Key 前缀和 TTL 配置集中管理，避免魔法字符串散落在代码各处：

```java
package me.xunrana.blog.common;

/**
 * Redis 键名和过期时间常量
 *
 * 集中管理的好处：
 * 1. 修改 Key 前缀只需改一处
 * 2. IDE 可以全局搜索引用，知道哪些地方用了缓存
 * 3. Code Review 时方便检查 Key 命名是否规范
 */
public class RedisConstants {

    // ===== Key 前缀 =====

    /** 文章详情缓存 Key 前缀，完整 Key 为 blog:article:detail:{slug} */
    public static final String ARTICLE_DETAIL_KEY = "blog:article:detail:";

    /** 文章浏览量 Hash Key，field 为 articleId，value 为浏览次数 */
    public static final String ARTICLE_VIEWS_KEY = "blog:article:views";

    /** 分类列表缓存 Key */
    public static final String CATEGORIES_KEY = "blog:categories";

    // ===== 过期时间（秒） =====

    /** 文章详情缓存基础 TTL：30 分钟 */
    public static final long ARTICLE_DETAIL_TTL = 30 * 60;

    /** 分类列表缓存基础 TTL：60 分钟 */
    public static final long CATEGORIES_TTL = 60 * 60;

    /** 随机偏移范围上限（秒）：5 分钟 */
    public static final int TTL_RANDOM_OFFSET = 5 * 60;

    /** 空值缓存 TTL：1 分钟（防穿透） */
    public static final long NULL_TTL = 60;

    private RedisConstants() {
        // 工具类禁止实例化
    }
}
```

**为什么要集中管理常量？**

如果不集中管理，代码中会出现这样的情况：

```java
// ArticleServiceImpl.java
redisTemplate.opsForValue().set("blog:article:detail:" + slug, ...);  // 手写字符串

// ArticleCacheEvictService.java
redisTemplate.delete("blog:artcile:detail:" + slug);  // 拼写错误！少了一个 i
// 这个 Bug 在编译期发现不了，只有运行时才能发现缓存没删掉
```

使用常量后：

```java
// 所有地方都引用同一个常量，不会拼错
redisTemplate.opsForValue().set(RedisConstants.ARTICLE_DETAIL_KEY + slug, ...);
redisTemplate.delete(RedisConstants.ARTICLE_DETAIL_KEY + slug);
```

---

## 5. Cache Aside 实现详解 (对应 TODO 1)

### 5.1 完整流程图

```
请求 GET /api/v1/articles/{slug}
           │
           ▼
     ArticleController.getArticleBySlug(slug)
           │
           ▼
     ArticleServiceImpl.getArticleBySlug(slug)
           │
           ▼
     ┌──── 从 Redis 查缓存 ────┐
     │ Key: blog:article:      │
     │      detail:{slug}      │
     └────────┬────────────────┘
              │
     ┌────────┴────────┐
     │                 │
     ▼                 ▼
   缓存命中          缓存未命中
     │                 │
     ▼                 ▼
  直接返回         查询数据库
  （1ms）          （50ms）
                       │
              ┌────────┴────────┐
              │                 │
              ▼                 ▼
           查到数据          未查到数据
              │                 │
              ▼                 ▼
     写入 Redis 缓存      写入空值到 Redis
     TTL = 30min +        TTL = 1min
     random(0~5min)       （防缓存穿透）
              │                 │
              ▼                 ▼
           返回数据          返回 null
                          （或抛业务异常）
```

### 5.2 代码实现

在 `ArticleServiceImpl` 中改造 `getArticleBySlug` 方法：

```java
package me.xunrana.blog.service.impl;

import me.xunrana.blog.common.RedisConstants;
// ... 其他导入

@Service
@Slf4j
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;  // 注入 RedisTemplate

    // ===== TODO 1: Cache Aside 实现 =====
    @Override
    public ArticleDetailVO getArticleBySlug(String slug) {
        // ========== 第一步：查询缓存 ==========
        String cacheKey = RedisConstants.ARTICLE_DETAIL_KEY + slug;
        // 从 Redis 中获取缓存值
        // 注意：这里返回的是 Object，因为我们的 RedisTemplate 配置了 Jackson 序列化器
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        // ========== 第二步：缓存命中判断 ==========
        if (cached != null) {
            // 如果缓存的是空值标记（防穿透），返回业务异常
            if ("NULL".equals(cached)) {
                throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
            }
            // 缓存命中，直接返回（这里需要类型转换）
            log.debug("缓存命中: key={}", cacheKey);
            return (ArticleDetailVO) cached;
        }

        // ========== 第三步：缓存未命中，查询数据库 ==========
        log.debug("缓存未命中，查询数据库: slug={}", slug);
        ArticleDetailVO detail = articleMapper.selectArticleBySlug(slug);

        // ========== 第四步：数据库结果处理 ==========
        if (detail == null) {
            // 数据库也没有 → 缓存空值，防止缓存穿透
            // 空值 TTL 设置很短（1分钟），避免长期占用内存
            redisTemplate.opsForValue().set(cacheKey, "NULL",
                    Duration.ofSeconds(RedisConstants.NULL_TTL));
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        // 数据库查到了 → 写入缓存
        // TTL = 基础时间 + 随机偏移，防止缓存雪崩
        long ttl = RedisConstants.ARTICLE_DETAIL_TTL
                + ThreadLocalRandom.current().nextInt(RedisConstants.TTL_RANDOM_OFFSET);
        redisTemplate.opsForValue().set(cacheKey, detail, Duration.ofSeconds(ttl));
        log.debug("写入缓存: key={}, ttl={}s", cacheKey, ttl);

        // ========== 第五步：浏览量递增（见 TODO 2） ==========
        // incrementViewCount(detail.getId());

        // 获取上一篇/下一篇文章（这部分不缓存，因为文章顺序可能变化）
        ArticleVO prevArticle = findAdjacentArticle(detail.getId(), true);
        detail.setPrevArticle(prevArticle);
        ArticleVO nextArticle = findAdjacentArticle(detail.getId(), false);
        detail.setNextArticle(nextArticle);

        return detail;
    }

    // ... 其他方法
}
```

### 5.3 逐行解读

**第一步：构建缓存 Key**

```java
String cacheKey = RedisConstants.ARTICLE_DETAIL_KEY + slug;
// 例如：slug = "my-first-post-1711234567"
// cacheKey = "blog:article:detail:my-first-post-1711234567"
```

使用常量 + slug 拼接，保证每篇文章有独立的缓存 Key。

**第二步：查询缓存并判断**

```java
Object cached = redisTemplate.opsForValue().get(cacheKey);
```

`opsForValue()` 返回 `ValueOperations`，对应 Redis 的 String 类型操作。`get()` 如果 Key 不存在返回 `null`。

**关键判断：区分"Key 不存在"和"Key 存在但值为空标记"**

```java
if (cached != null) {
    if ("NULL".equals(cached)) {
        // 这是我们主动写入的空值标记，说明这个 slug 对应的文章确实不存在
        throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
    }
    return (ArticleDetailVO) cached;
}
```

这里的 `"NULL"` 字符串是我们自定义的空值标记，用来区分两种情况：
- `cached == null`：Key 不存在，可能是过期了或者从未缓存，需要查数据库
- `cached.equals("NULL")`：Key 存在，但我们之前已经确认过数据库里没有这条数据，直接返回"不存在"

**第四步：随机 TTL**

```java
long ttl = RedisConstants.ARTICLE_DETAIL_TTL
        + ThreadLocalRandom.current().nextInt(RedisConstants.TTL_RANDOM_OFFSET);
```

`ThreadLocalRandom` 是 Java 7 引入的线程安全随机数生成器，比 `Math.random()` 在多线程下性能更好。

如果 `ARTICLE_DETAIL_TTL = 1800`（30分钟），`TTL_RANDOM_OFFSET = 300`（5分钟），那么每个 Key 的实际 TTL 在 1800~2100 秒之间随机分布。这样即使多个缓存在同一时间创建，它们也不会在同一时刻过期。

### 5.4 为什么要缓存空值？

假设有人恶意请求不存在的文章 slug：

```
GET /api/v1/articles/non-exist-slug-1
GET /api/v1/articles/non-exist-slug-2
GET /api/v1/articles/non-exist-slug-3
... 大量不存在的 slug
```

如果不缓存空值，每次请求都会查数据库（因为缓存里没有，每次都是 miss），数据库承受大量无效查询。

缓存空值后，第一次查数据库发现不存在 → 写入 `"NULL"` 到 Redis（TTL=1分钟） → 后续 1 分钟内的相同请求直接从 Redis 返回"不存在"，不再查数据库。

---

## 6. 浏览量 Redis 计数 (对应 TODO 2)

### 6.1 原始方案的问题

当前代码中，每次查看文章详情都会直接更新数据库：

```java
// 现有代码 —— 直接 SQL UPDATE
articleMapper.update(null, new LambdaUpdateWrapper<Article>()
        .eq(Article::getId, detail.getId())
        .setSql("view_count = view_count + 1"));
```

**问题分析**：

| 问题 | 说明 |
|------|------|
| **行锁竞争** | `UPDATE article SET view_count = view_count + 1 WHERE id = ?` 会对该行加排他锁（X Lock），高并发时大量线程排队等锁 |
| **数据库压力** | 每次浏览都是一次写操作，1000 PV/天 = 1000 次 UPDATE |
| **不必要的持久化** | 浏览量不是关键数据，没必要每次都持久化，丢失几次浏览量完全可接受 |
| **性能浪费** | 一次 UPDATE 操作耗时约 5-10ms（涉及事务日志写入），远大于 Redis HINCRBY 的 0.1ms |

### 6.2 Redis Hash 计数方案

**数据结构**：

```
Key:    blog:article:views   （Hash 类型）
Field:  1                     → Value: 156     （文章 ID=1 的浏览量）
Field:  2                     → Value: 89      （文章 ID=2 的浏览量）
Field:  3                     → Value: 234     （文章 ID=3 的浏览量）
```

**为什么用 Hash 而不是独立的 String Key？**

方案一：每篇文章一个 String Key

```
blog:article:views:1  →  156
blog:article:views:2  →  89
blog:article:views:3  →  234
```

方案二：一个 Hash Key，每篇文章一个 Field（我们选择这个）

```
blog:article:views
  field=1  →  156
  field=2  →  89
  field=3  →  234
```

对比：

| 对比项 | 独立 String Key | 一个 Hash Key |
|--------|----------------|--------------|
| Key 数量 | N 个（文章数量） | 1 个 |
| 内存占用 | 每个 Key 有独立的元数据开销（约 40 字节） | 只有一个 Key 的元数据开销 |
| 批量读取 | 需要 MGET 或 Pipeline | HGETALL 一次读取所有 |
| 定时同步 | 需要遍历所有 Key | HGETALL 一次获取，方便批量同步 |
| 原子递增 | INCR | HINCRBY（同样原子） |

Hash 在"同步到数据库"场景中优势明显：`HGETALL blog:article:views` 一条命令获取所有文章的浏览增量，然后批量 UPDATE 到 MySQL。

### 6.3 代码实现

```java
// ===== TODO 2: Redis 浏览量计数 =====

/**
 * 使用 Redis Hash 的 HINCRBY 命令原子递增浏览量
 *
 * HINCRBY 的特点：
 * 1. 原子操作：多线程并发调用也不会丢失计数
 * 2. O(1) 复杂度：无论 Hash 有多少 field，递增操作都是常数时间
 * 3. 自动创建：如果 Key 或 Field 不存在，会自动创建并初始化为 0 再递增
 */
private void incrementViewCount(Long articleId) {
    redisTemplate.opsForHash().increment(
            RedisConstants.ARTICLE_VIEWS_KEY,     // Key:   blog:article:views
            articleId.toString(),                  // Field: 文章 ID
            1                                     // Delta: 每次 +1
    );
    // 对应 Redis 命令: HINCRBY blog:article:views {articleId} 1
}
```

**在 `getArticleBySlug` 中调用**：

```java
@Override
public ArticleDetailVO getArticleBySlug(String slug) {
    // ... 缓存逻辑（TODO 1）

    // 浏览量递增 —— 无论是否命中缓存都要执行
    // 注意：这行代码要放在缓存判断之外，因为命中缓存的请求也算一次浏览
    incrementViewCount(detail.getId());

    return detail;
}
```

**重要细节**：浏览量递增应该在缓存命中和未命中时都执行，因为无论数据从哪里返回，用户确实浏览了一次。调整后的完整代码结构：

```java
@Override
public ArticleDetailVO getArticleBySlug(String slug) {
    String cacheKey = RedisConstants.ARTICLE_DETAIL_KEY + slug;
    Object cached = redisTemplate.opsForValue().get(cacheKey);

    ArticleDetailVO detail;

    if (cached != null) {
        if ("NULL".equals(cached)) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        detail = (ArticleDetailVO) cached;
    } else {
        detail = articleMapper.selectArticleBySlug(slug);
        if (detail == null) {
            redisTemplate.opsForValue().set(cacheKey, "NULL",
                    Duration.ofSeconds(RedisConstants.NULL_TTL));
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        long ttl = RedisConstants.ARTICLE_DETAIL_TTL
                + ThreadLocalRandom.current().nextInt(RedisConstants.TTL_RANDOM_OFFSET);
        redisTemplate.opsForValue().set(cacheKey, detail, Duration.ofSeconds(ttl));
    }

    // 无论缓存是否命中，都递增浏览量
    incrementViewCount(detail.getId());

    // 上一篇/下一篇
    detail.setPrevArticle(findAdjacentArticle(detail.getId(), true));
    detail.setNextArticle(findAdjacentArticle(detail.getId(), false));

    return detail;
}
```

---

## 7. 定时同步机制 (对应 TODO 3)

### 7.1 为什么需要定时同步

浏览量数据存储在 Redis 中，但 Redis 是内存数据库，有数据丢失风险（虽然有 RDB/AOF 持久化，但不如 MySQL 可靠）。此外，前端可能直接查询数据库中的 `view_count` 字段做排序、统计等。因此需要定期将 Redis 中的浏览量增量同步到 MySQL。

**数据流**：

```
用户浏览 → Redis HINCRBY +1 → 每 5 分钟 → 批量 UPDATE MySQL → 清空 Redis Hash
```

### 7.2 @Scheduled 定时任务基础

Spring 提供了 `@Scheduled` 注解实现定时任务，需要配合 `@EnableScheduling` 使用。

**启用定时任务**：在主启动类上添加 `@EnableScheduling`：

```java
@SpringBootApplication
@MapperScan("me.xunrana.blog.mapper")
@EnableScheduling  // 开启定时任务支持
public class XunranaBlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(XunranaBlogApplication.class, args);
    }
}
```

**@Scheduled 的三种调度方式**：

| 属性 | 含义 | 示例 | 说明 |
|------|------|------|------|
| `fixedRate` | 固定频率（毫秒） | `@Scheduled(fixedRate = 300000)` | 每 5 分钟执行一次（从上次开始计时） |
| `fixedDelay` | 固定延迟（毫秒） | `@Scheduled(fixedDelay = 300000)` | 上次执行**完成后**等 5 分钟再执行 |
| `cron` | Cron 表达式 | `@Scheduled(cron = "0 */5 * * * ?")` | 每 5 分钟的第 0 秒执行 |

**fixedRate vs fixedDelay 的区别**：

```
fixedRate = 5 分钟：
|--任务1(2min)--|--等3min--|--任务2(2min)--|--等3min--|
                ↑ 5 分钟后    ↑ 5 分钟后

fixedDelay = 5 分钟：
|--任务1(2min)--|--等5min--|--任务2(2min)--|--等5min--|
               完成后等5min  完成后等5min
```

本项目用 `fixedRate`：即使同步任务执行较慢，也按固定频率触发，确保数据及时同步。

### 7.3 同步算法

```
┌──────────────────────────────────────────┐
│ 定时任务触发（每 5 分钟）                    │
│                                           │
│ 1. HGETALL blog:article:views             │
│    → 获取所有文章的浏览增量                   │
│    → {1: 15, 3: 8, 7: 23}                │
│                                           │
│ 2. 遍历每个 entry:                         │
│    UPDATE article                          │
│    SET view_count = view_count + {delta}   │
│    WHERE id = {articleId}                  │
│                                           │
│ 3. DEL blog:article:views                 │
│    → 清空 Redis Hash，重新计数              │
└──────────────────────────────────────────┘
```

### 7.4 代码实现

创建定时任务类 `ViewCountSyncTask`：

```java
package me.xunrana.blog.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.RedisConstants;
import me.xunrana.blog.mapper.ArticleMapper;
import me.xunrana.blog.model.entity.Article;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 定时同步 Redis 浏览量到 MySQL
 *
 * 为什么不在每次浏览时直接写 MySQL？
 * 1. 每次浏览都 UPDATE 一次太频繁，数据库压力大
 * 2. 浏览量不是关键数据，最终一致即可
 * 3. 批量 UPDATE 比逐条 UPDATE 效率高很多
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ViewCountSyncTask {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ArticleMapper articleMapper;

    /**
     * 每 5 分钟执行一次浏览量同步
     *
     * fixedRate = 300000 表示每 300 秒（5 分钟）触发一次
     * 不使用 cron 表达式是因为固定频率更简单，且对精确时间点无要求
     */
    @Scheduled(fixedRate = 300000)
    public void syncViewCounts() {
        log.info("开始同步浏览量到数据库...");

        // ========== 第一步：获取 Redis 中所有浏览量增量 ==========
        Map<Object, Object> viewCounts = redisTemplate.opsForHash()
                .entries(RedisConstants.ARTICLE_VIEWS_KEY);
        // 对应命令: HGETALL blog:article:views
        // 返回: {1=15, 3=8, 7=23}

        // 如果没有数据，说明这段时间没有浏览，直接返回
        if (viewCounts.isEmpty()) {
            log.info("无浏览量需要同步");
            return;
        }

        // ========== 第二步：批量更新 MySQL ==========
        int syncCount = 0;
        for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
            try {
                Long articleId = Long.parseLong(entry.getKey().toString());
                Integer delta = Integer.parseInt(entry.getValue().toString());

                // UPDATE article SET view_count = view_count + {delta} WHERE id = {articleId}
                articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                        .eq(Article::getId, articleId)
                        .setSql("view_count = view_count + " + delta));

                syncCount++;
            } catch (NumberFormatException e) {
                log.warn("浏览量数据格式异常: key={}, value={}", entry.getKey(), entry.getValue());
            }
        }

        // ========== 第三步：清空 Redis Hash ==========
        redisTemplate.delete(RedisConstants.ARTICLE_VIEWS_KEY);
        // 对应命令: DEL blog:article:views

        log.info("浏览量同步完成: 更新了 {} 篇文章", syncCount);
    }
}
```

### 7.5 逐行解读

**获取所有浏览增量**：

```java
Map<Object, Object> viewCounts = redisTemplate.opsForHash()
        .entries(RedisConstants.ARTICLE_VIEWS_KEY);
```

`entries()` 对应 Redis 的 `HGETALL` 命令，返回 Hash 中所有的 field-value 对。返回类型是 `Map<Object, Object>`，因为 RedisTemplate 的泛型配置决定了序列化方式。

**批量更新 MySQL**：

```java
articleMapper.update(null, new LambdaUpdateWrapper<Article>()
        .eq(Article::getId, articleId)
        .setSql("view_count = view_count + " + delta));
```

使用 MyBatis-Plus 的 `setSql()` 方法写原生 SQL 片段，实现 `view_count = view_count + N` 的原子递增。不能用 `set(Article::getViewCount, newValue)` 是因为我们不知道当前数据库中的值，直接设置可能覆盖其他线程的更新。

**清空 Redis Hash**：

```java
redisTemplate.delete(RedisConstants.ARTICLE_VIEWS_KEY);
```

同步完成后删除整个 Hash Key，下一个周期重新从 0 开始计数。

### 7.6 竞态条件分析

**问题**：在执行 `HGETALL` 和 `DEL` 之间，可能有新的浏览请求执行了 `HINCRBY`，这些新增的浏览量会在 `DEL` 时被一起删除，导致丢失。

```
时间线：
T1: HGETALL → 获取 {1: 15}
T2: 用户浏览文章 1 → HINCRBY → Hash 变为 {1: 16}
T3: UPDATE MySQL → view_count += 15
T4: DEL → 删除 Hash
结果: 丢失了 T2 的 1 次浏览量
```

**我们的选择：接受这个不精确**

理由：
1. 浏览量不是金融数据，丢失几次计数完全可接受
2. 5 分钟窗口内的丢失量极小（可能只有 1-3 次）
3. 使用 Lua 脚本可以实现原子的"读取并删除"，但增加了复杂度，对博客场景来说不值得

如果确实需要精确计数（如电商库存），可以使用 Redis Lua 脚本保证原子性：

```lua
-- 原子性地获取所有数据并删除 Key（仅供参考，本项目不实现）
local data = redis.call('HGETALL', KEYS[1])
redis.call('DEL', KEYS[1])
return data
```

---

## 8. 缓存失效策略 (对应 TODO 4)

### 8.1 什么时候需要主动删除缓存

当文章或分类数据被**增、删、改**时，数据库中的数据已经变了，但 Redis 中的缓存还是旧数据。如果不主动删除缓存，用户在 TTL 过期前看到的都是旧数据。

| 操作 | 需要删除的缓存 |
|------|--------------|
| 创建文章 | 无需删除（新文章还没有缓存） |
| 更新文章 | 删除该文章的详情缓存 `blog:article:detail:{slug}` |
| 删除文章 | 删除该文章的详情缓存 `blog:article:detail:{slug}` |
| 创建分类 | 删除分类列表缓存 `blog:categories` |
| 更新分类 | 删除分类列表缓存 `blog:categories` |
| 删除分类 | 删除分类列表缓存 `blog:categories` |

### 8.2 简单删除 vs 延迟双删

**简单删除**（我们使用的方案）：

```java
// 1. 更新数据库
articleMapper.updateById(article);
// 2. 删除缓存
redisTemplate.delete(RedisConstants.ARTICLE_DETAIL_KEY + slug);
```

**延迟双删**（大厂高并发场景使用）：

```java
// 1. 先删缓存
redisTemplate.delete(cacheKey);
// 2. 更新数据库
articleMapper.updateById(article);
// 3. 延迟一段时间（通常 500ms ~ 1s）
Thread.sleep(500);
// 4. 再删一次缓存
redisTemplate.delete(cacheKey);
```

为什么需要"双删"？考虑以下并发场景（简单删除的问题）：

```
线程 A（更新操作）         线程 B（读操作）
     │                      │
 更新数据库（V2）            │
     │                  查询缓存（miss）
     │                  查询数据库（读到 V2... 但如果主从延迟读到 V1）
     │                  写入缓存（V1 ← 旧数据！）
 删除缓存                   │
     │                      │
```

在读写分离（主从架构）场景中，从库可能有毫秒级延迟，线程 B 可能从从库读到旧数据并写入缓存。延迟双删的第二次删除就是为了清除这种"脏数据"。

**本项目使用简单删除的理由**：
1. 博客系统没有主从读写分离，不存在主从延迟问题
2. 写操作极少（管理员操作），并发写+读的概率极低
3. 即使偶尔不一致，TTL 到期后也会自动修复
4. 实现简单，易于理解和维护

### 8.3 代码实现

创建缓存失效工具方法，并在 Service 中调用：

```java
// ===== TODO 4: 缓存失效方法 =====

/**
 * 删除文章详情缓存
 *
 * @param slug 文章的 slug（URL 标识）
 */
private void evictArticleCache(String slug) {
    String cacheKey = RedisConstants.ARTICLE_DETAIL_KEY + slug;
    Boolean deleted = redisTemplate.delete(cacheKey);
    log.info("删除文章缓存: key={}, result={}", cacheKey, deleted);
}

/**
 * 删除分类列表缓存
 */
private void evictCategoriesCache() {
    Boolean deleted = redisTemplate.delete(RedisConstants.CATEGORIES_KEY);
    log.info("删除分类列表缓存: result={}", deleted);
}
```

**在 ArticleServiceImpl 中调用**：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateArticle(Long id, ArticleDTO dto) {
    Article existing = articleMapper.selectById(id);
    if (existing == null) {
        throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
    }

    BeanUtil.copyProperties(dto, existing, "id");
    existing.setUpdatedAt(LocalDateTime.now());

    if (Integer.valueOf(1).equals(dto.getStatus()) && existing.getPublishedAt() == null) {
        existing.setPublishedAt(LocalDateTime.now());
    }

    articleMapper.updateById(existing);
    log.info("Article updated: id={}", id);

    // 重建 article-tag 关系
    articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
            .eq(ArticleTag::getArticleId, id));
    saveArticleTags(id, dto.getTagIds());

    // ===== 新增：删除缓存 =====
    evictArticleCache(existing.getSlug());
}

@Override
@Transactional(rollbackFor = Exception.class)
public void deleteArticle(Long id) {
    // 先查出 slug 用于删除缓存
    Article article = articleMapper.selectById(id);

    articleMapper.deleteById(id);
    articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
            .eq(ArticleTag::getArticleId, id));
    log.info("Article deleted: id={}", id);

    // ===== 新增：删除缓存 =====
    if (article != null) {
        evictArticleCache(article.getSlug());
    }
}
```

**在 CategoryServiceImpl 中调用**：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void createCategory(CategoryDTO dto) {
    // ... 创建逻辑
    categoryMapper.insert(category);

    // ===== 新增：删除分类列表缓存 =====
    evictCategoriesCache();
}

@Override
@Transactional(rollbackFor = Exception.class)
public void updateCategory(Long id, CategoryDTO dto) {
    // ... 更新逻辑
    categoryMapper.updateById(existing);

    // ===== 新增：删除分类列表缓存 =====
    evictCategoriesCache();
}

@Override
@Transactional(rollbackFor = Exception.class)
public void deleteCategory(Long id) {
    // ... 删除逻辑
    categoryMapper.deleteById(id);

    // ===== 新增：删除分类列表缓存 =====
    evictCategoriesCache();
}
```

### 8.4 注意事项

**1. 先更新数据库，再删缓存**

```java
// 正确顺序
articleMapper.updateById(existing);    // ① 先更新 DB
evictArticleCache(existing.getSlug()); // ② 再删缓存

// 错误顺序
evictArticleCache(existing.getSlug()); // ① 先删缓存
articleMapper.updateById(existing);    // ② 再更新 DB
// 问题：①和②之间如果有读请求，会把旧数据重新写入缓存
```

**2. 缓存删除失败不应影响主流程**

缓存是加速层，不是数据源。如果 Redis 挂了或网络抖动导致删除失败，主流程（数据库更新）不应该回滚：

```java
private void evictArticleCache(String slug) {
    try {
        String cacheKey = RedisConstants.ARTICLE_DETAIL_KEY + slug;
        redisTemplate.delete(cacheKey);
        log.info("删除文章缓存: key={}", cacheKey);
    } catch (Exception e) {
        // 缓存删除失败只记录日志，不抛出异常
        // 最坏情况：用户在 TTL 过期前看到旧数据
        log.warn("删除文章缓存失败: slug={}, error={}", slug, e.getMessage());
    }
}
```

---

## 9. 缓存三大问题及防护

这三个问题是 Redis 面试的必考题，也是生产环境中真实会遇到的问题。

### 9.1 缓存穿透 (Cache Penetration)

**什么是缓存穿透？**

请求的数据在缓存和数据库中**都不存在**。由于缓存中没有，每次请求都会穿过缓存直接打到数据库。

```
恶意请求: GET /api/v1/articles/non-exist-slug-12345
    │
    ▼
  查缓存 → miss（不存在）
    │
    ▼
  查数据库 → null（也不存在）
    │
    ▼
  不缓存（因为没有数据可缓存）
    │
    ▼
  下一个请求又来了... 重复上述过程
```

如果攻击者大量请求不存在的 slug，数据库会承受巨大压力。

**解决方案一：缓存空值**（我们使用的方案）

```java
if (detail == null) {
    // 数据库查不到，也往缓存里写一个标记
    redisTemplate.opsForValue().set(cacheKey, "NULL",
            Duration.ofSeconds(RedisConstants.NULL_TTL));  // TTL = 1 分钟
    throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
}
```

**优点**：实现简单，一行代码搞定
**缺点**：如果攻击者使用不同的 slug 请求，每个都会在 Redis 中创建一个 Key，浪费内存。不过 TTL 只有 1 分钟，影响有限。

**解决方案二：布隆过滤器 (Bloom Filter)**

布隆过滤器是一个概率性数据结构，可以快速判断一个元素是否"可能存在"或"一定不存在"。

```
请求 slug → 查布隆过滤器
    │
    ├── 一定不存在 → 直接返回 404（不查缓存也不查数据库）
    │
    └── 可能存在 → 查缓存 → 查数据库 → 正常流程
```

**优点**：内存占用极小（1 亿条数据只需约 100MB），拦截效果好
**缺点**：实现复杂，存在误判率（可能存在但实际不存在），新增数据需要同步更新过滤器

本项目暂不实现布隆过滤器，缓存空值已经足够应对博客场景。

### 9.2 缓存击穿 (Cache Breakdown)

**什么是缓存击穿？**

某个**热点 Key** 在缓存过期的瞬间，大量并发请求同时到达，全部穿过缓存打到数据库。

```
时刻 T: 热点文章缓存到期（TTL 过期）
    │
并发请求 1 → 查缓存 miss → 查 DB
并发请求 2 → 查缓存 miss → 查 DB
并发请求 3 → 查缓存 miss → 查 DB
...
并发请求 100 → 查缓存 miss → 查 DB
    │
数据库瞬间承受 100 个相同查询！
```

**解决方案一：互斥锁 (Mutex Lock)**

```java
// 只有一个线程查数据库，其他线程等待
public ArticleDetailVO getWithMutex(String slug) {
    String cacheKey = "blog:article:detail:" + slug;
    String lockKey = "blog:article:lock:" + slug;

    Object cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return (ArticleDetailVO) cached;
    }

    // 尝试获取分布式锁
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 获取锁成功，查数据库并回填缓存
            ArticleDetailVO detail = articleMapper.selectArticleBySlug(slug);
            redisTemplate.opsForValue().set(cacheKey, detail, Duration.ofMinutes(30));
            return detail;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // 获取锁失败，短暂等待后重试
        Thread.sleep(50);
        return getWithMutex(slug);  // 递归重试
    }
}
```

**解决方案二：永不过期 + 后台刷新**

缓存不设 TTL，在缓存中额外存储一个逻辑过期时间。后台线程定期检查并刷新即将过期的热点数据。

**我们的方案：随机 TTL 偏移**

对于博客场景，并发量不会太高（不会有上万人同时访问同一篇文章），随机 TTL 偏移已经足够：

```java
long ttl = RedisConstants.ARTICLE_DETAIL_TTL
        + ThreadLocalRandom.current().nextInt(RedisConstants.TTL_RANDOM_OFFSET);
```

不同文章的缓存在不同时刻过期，大大降低了同一时刻多个缓存失效的概率。

### 9.3 缓存雪崩 (Cache Avalanche)

**什么是缓存雪崩？**

大量 Key 在**同一时刻过期**（或 Redis 宕机），导致海量请求同时打到数据库。

与击穿的区别：
- **击穿**：一个热点 Key 过期
- **雪崩**：大量 Key 同时过期

```
时刻 T:
  blog:article:detail:post-1  过期
  blog:article:detail:post-2  过期
  blog:article:detail:post-3  过期
  blog:categories             过期
  ... 所有缓存几乎同时过期
      │
      ▼
  数据库瞬间承受所有查询 → 数据库崩溃
```

**为什么会同时过期？** 常见场景：
1. 系统初始化时批量预热缓存，所有 Key 的 TTL 相同，到期时间也相同
2. Redis 重启后缓存全部丢失，相当于所有 Key 同时"过期"

**解决方案一：随机 TTL 偏移**（我们使用的方案）

```java
// 基础 TTL = 30 分钟，随机偏移 = 0~5 分钟
long ttl = 1800 + ThreadLocalRandom.current().nextInt(300);
// 实际 TTL 范围: 1800~2100 秒
// 不同 Key 在不同时刻过期，错开了失效时间
```

**解决方案二：多级缓存**

```
请求 → 本地缓存（Caffeine，进程内） → Redis → 数据库
```

即使 Redis 挂了，本地缓存还能顶一段时间。

**解决方案三：服务降级 / 熔断**

当检测到数据库压力过大时，直接返回默认值或错误提示，而不是继续请求数据库。可以使用 Sentinel 或 Resilience4j 实现。

### 9.4 三大问题总结对比

| | 缓存穿透 | 缓存击穿 | 缓存雪崩 |
|---|----------|---------|---------|
| **本质** | 查询不存在的数据 | 热点 Key 过期 | 大量 Key 同时过期 |
| **影响范围** | 单个不存在的 Key | 单个热点 Key | 大量 Key |
| **攻击可能** | 可被恶意利用 | 一般是自然发生 | 一般是设计缺陷 |
| **解决方案** | 缓存空值、布隆过滤器 | 互斥锁、永不过期 | 随机 TTL、多级缓存 |
| **本项目方案** | 缓存空值（TTL=1min） | 随机 TTL 偏移 | 随机 TTL 偏移 |

---

## 10. 测试验证

### 10.1 启动 Redis Monitor

在 Redis CLI 中开启监控模式，可以实时看到所有执行的命令：

```bash
# 连接 Redis 并开启监控
redis-cli MONITOR

# 输出示例：
# 1711234567.123456 [0 127.0.0.1:62345] "GET" "blog:article:detail:my-first-post"
# 1711234567.234567 [0 127.0.0.1:62345] "SET" "blog:article:detail:my-first-post" "..." "EX" "1923"
```

### 10.2 验证缓存读写

**第一次请求（缓存未命中）**：

```bash
# 调用文章详情接口
curl http://localhost:8080/api/v1/articles/my-first-post

# Redis MONITOR 应该看到：
# GET blog:article:detail:my-first-post       → 查缓存（返回 nil）
# SET blog:article:detail:my-first-post {...}  → 写缓存（带 TTL）
# HINCRBY blog:article:views 1 1              → 浏览量 +1

# 应用日志应该看到：
# DEBUG - 缓存未命中，查询数据库: slug=my-first-post
# DEBUG - 写入缓存: key=blog:article:detail:my-first-post, ttl=1856s
```

**第二次请求（缓存命中）**：

```bash
# 再次调用相同接口
curl http://localhost:8080/api/v1/articles/my-first-post

# Redis MONITOR 应该看到：
# GET blog:article:detail:my-first-post       → 查缓存（命中）
# HINCRBY blog:article:views 1 1              → 浏览量 +1
# 注意：没有 SET 命令（不需要写缓存）

# 应用日志应该看到：
# DEBUG - 缓存命中: key=blog:article:detail:my-first-post
# 注意：没有 SQL 查询日志（不查数据库）
```

### 10.3 验证浏览量

```bash
# 查看 Redis 中的浏览量
redis-cli HGETALL blog:article:views

# 输出示例：
# 1) "1"     ← articleId
# 2) "2"     ← 浏览了 2 次

# 查看特定文章的浏览量
redis-cli HGET blog:article:views 1
# 输出: "2"
```

### 10.4 验证定时同步

```bash
# 等待 5 分钟（或临时把 fixedRate 改为 30000 = 30 秒），观察日志：
# INFO - 开始同步浏览量到数据库...
# INFO - 浏览量同步完成: 更新了 1 篇文章

# 再次查看 Redis（应该已被清空）
redis-cli HGETALL blog:article:views
# (empty list or set)

# 查看 MySQL 中的 view_count（应该增加了）
mysql> SELECT id, title, view_count FROM article WHERE id = 1;
# +----+------------------+------------+
# | id | title            | view_count |
# +----+------------------+------------+
# |  1 | 我的第一篇文章     |          2 |
# +----+------------------+------------+
```

### 10.5 验证缓存失效

```bash
# 更新文章（需要 admin token）
curl -X PUT http://localhost:8080/api/v1/admin/articles/1 \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"title": "更新后的标题", "content": "更新后的内容"}'

# Redis MONITOR 应该看到：
# DEL blog:article:detail:my-first-post  → 缓存被删除

# 再次访问文章（应该重新从数据库加载）
curl http://localhost:8080/api/v1/articles/my-first-post
# 看到更新后的内容
```

---

## 11. 面试常见问题

### Q1: Cache Aside 模式的读写流程？

**答**：Cache Aside 模式的**读流程**是：先查缓存，命中则直接返回；未命中则查数据库，将结果写入缓存后返回。**写流程**是：先更新数据库，再删除缓存（注意是删除而不是更新）。删除而非更新是因为在并发场景下，更新缓存可能导致数据不一致。Cache Aside 是最常用的缓存策略，适合读多写少的场景。

### Q2: 如何保证缓存和数据库的一致性？

**答**：常见的做法是"先更新数据库，再删除缓存"。这种方式在极端并发场景下仍可能出现短暂不一致（比如读操作在删除之前查到旧值），但通过设置合理的 TTL 可以保证最终一致性。如果需要更强的一致性保证，可以使用"延迟双删"方案：先删缓存、再更新数据库、延迟一段时间后再次删除缓存，这样可以清除主从延迟导致的脏数据。另外也可以通过订阅数据库 Binlog（如 Canal）异步删除缓存来保证一致性。

### Q3: 什么是缓存穿透？如何解决？

**答**：缓存穿透是指请求的数据在缓存和数据库中都不存在，导致每次请求都穿透缓存直达数据库。恶意攻击者可以利用这个特点大量请求不存在的数据来打垮数据库。解决方案有两种：一是**缓存空值**，将不存在的结果也缓存起来（设置较短的 TTL，如 1 分钟）；二是使用**布隆过滤器**，在缓存层之前加一层过滤，快速判断数据是否存在，拦截掉不存在的请求。

### Q4: 什么是缓存雪崩？如何解决？

**答**：缓存雪崩是指大量缓存 Key 在同一时刻过期，导致海量请求同时涌入数据库，造成数据库瞬间压力过大甚至崩溃。解决方案包括：给缓存的 TTL 加上随机偏移量，避免同时过期；使用多级缓存（本地缓存 + Redis）；对数据库做限流和熔断保护；对热点数据做缓存预热和永不过期策略。最常用的是随机 TTL，实现简单且效果好。

### Q5: 什么是缓存击穿？和穿透有什么区别？

**答**：缓存击穿是指某个**热点 Key** 在缓存过期的瞬间，有大量并发请求同时到来，全部穿过缓存查询数据库。和穿透的区别在于：穿透是查询**不存在**的数据，击穿是查询**存在但缓存刚好过期**的数据。解决击穿可以使用互斥锁（setnx）保证只有一个线程去查数据库回填缓存，或者使用逻辑过期策略让热点数据永不过期，通过后台线程异步刷新。

### Q6: Redis 有哪些数据结构？分别适合什么场景？

**答**：Redis 有五种核心数据结构。**String** 是最基本的键值对，适合缓存 JSON 对象、计数器、分布式锁。**Hash** 是字段-值的映射集合，适合存储对象的多个属性或一组相关的计数器。**List** 是有序链表，适合消息队列、最新动态列表。**Set** 是无序集合（元素唯一），适合标签、共同好友、去重统计。**ZSet**（有序集合）在 Set 基础上给每个元素加了分数用于排序，适合排行榜、延迟队列。

### Q7: 为什么用 Redis Hash 存浏览量而不是 String？

**答**：如果用 String，每篇文章需要一个独立的 Key（如 `blog:article:views:1`、`blog:article:views:2`），Key 数量等于文章数量，每个 Key 都有独立的元数据开销（约 40 字节）。而用 Hash，只需一个 Key（`blog:article:views`），所有文章的浏览量作为 Hash 的不同 Field 存储，内存利用率更高。更重要的是，定时同步时需要一次性获取所有文章的浏览增量，`HGETALL` 一条命令就能完成，而 String 方案需要用 `KEYS` 遍历或事先知道所有文章 ID。

### Q8: 定时同步浏览量的方案，如何处理同步期间新增的浏览量？

**答**：定时同步的流程是：先 `HGETALL` 获取所有浏览增量，然后批量 UPDATE 到 MySQL，最后 `DEL` 清空 Hash。在 `HGETALL` 和 `DEL` 之间如果有新的浏览请求执行了 `HINCRBY`，这些新增的浏览量会在 `DEL` 时被一起删除，导致丢失。对于博客场景，这是可接受的最终一致性——浏览量不是金融数据，丢失几次计数无关紧要。如果需要精确计数，可以使用 Redis Lua 脚本将"读取并删除"做成原子操作，或者使用 `HGETALL` 后逐个 `HDEL` 已同步的 field 而不是整体删除。
