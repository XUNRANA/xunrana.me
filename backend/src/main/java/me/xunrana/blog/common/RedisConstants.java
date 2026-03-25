package me.xunrana.blog.common;

/**
 * Redis Key 常量定义
 *
 * 统一管理所有 Redis 缓存的 Key 前缀和过期时间，避免硬编码和 Key 冲突。
 *
 * Key 设计规范:
 *   - 使用 "blog:" 作为项目级前缀
 *   - 使用 ":" 分隔层级（如 blog:article:detail:slug-xxx）
 *   - 常量名用大写+下划线
 */
public class RedisConstants {

    private RedisConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 文章缓存 ====================

    /** 文章详情缓存 Key 前缀，完整 Key = ARTICLE_DETAIL_KEY + slug */
    public static final String ARTICLE_DETAIL_KEY = "blog:article:detail:";

    /** 文章详情缓存过期时间（分钟） */
    public static final long CACHE_TTL_ARTICLE = 30;

    /** 文章列表缓存 Key 前缀，完整 Key = ARTICLE_PAGE_KEY + page:size:其他参数 */
    public static final String ARTICLE_PAGE_KEY = "blog:articles:page:";

    /** 文章列表缓存过期时间（分钟） */
    public static final long CACHE_TTL_ARTICLE_PAGE = 10;

    // ==================== 浏览量统计 ====================

    /** 文章浏览量 Hash Key（所有文章共用一个 Hash，field = articleId） */
    public static final String ARTICLE_VIEW_COUNT_KEY = "blog:article:views";

    /** 浏览量同步到 MySQL 的定时任务间隔描述（5 分钟） */
    public static final String VIEW_COUNT_SYNC_CRON = "0 */5 * * * ?";

    // ==================== 分类/标签缓存 ====================

    /** 分类列表缓存 Key */
    public static final String CATEGORY_LIST_KEY = "blog:categories";

    /** 标签列表缓存 Key */
    public static final String TAG_LIST_KEY = "blog:tags";

    /** 分类/标签缓存过期时间（分钟） */
    public static final long CACHE_TTL_CATEGORY_TAG = 60;

    // ==================== Token 黑名单 ====================

    /** Token 黑名单 Key 前缀（已在 JwtAuthFilter 中使用） */
    public static final String TOKEN_BLACKLIST_KEY = "blog:token:blacklist:";

    // ==================== 接口限流 ====================

    /** 限流 Key 前缀，完整 Key = RATE_LIMIT_KEY + 接口标识 + ":" + IP/UserId */
    public static final String RATE_LIMIT_KEY = "blog:rate_limit:";
}
