package me.xunrana.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.RedisConstants;
import me.xunrana.blog.mapper.ArticleMapper;
import me.xunrana.blog.model.entity.Article;
import me.xunrana.blog.model.vo.ArticleDetailVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文章缓存 Service
 *
 * 负责文章详情的 Redis 缓存管理和浏览量统计。
 * 实现要点:
 *   - Cache Aside 读模式：先查缓存，未命中再查 DB 并回填
 *   - 防穿透：DB 查不到时缓存空值短 TTL
 *   - 浏览量计数：Redis Hash HINCRBY 原子自增，定时批量同步到 MySQL
 *   - 写后失效：更新/删除文章后删除对应缓存（Cache Aside 写模式）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArticleCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;
    private final ObjectMapper objectMapper;

    /** DB 查不到时缓存空值的 TTL（秒），用于防穿透 */
    private static final long EMPTY_CACHE_TTL_SECONDS = 60L;

    /**
     * Cache Aside 读模式：根据 slug 获取文章详情。
     *
     * 流程：
     *   1. 拼接 Redis Key
     *   2. 命中且非空 → 反序列化返回
     *   3. 命中但为空字符串 → 已知不存在，直接抛业务异常上层 / 返回 null
     *   4. 未命中 → 查 DB → 回填缓存（DB 查到则缓存对象 30min；查不到缓存空值 60s）
     */
    public ArticleDetailVO getArticleBySlug(String slug) {
        String key = RedisConstants.ARTICLE_DETAIL_KEY + slug;

        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached.isEmpty()) {
                // 命中空值（防穿透）
                log.debug("缓存命中(空值): slug={}", slug);
                return null;
            }
            try {
                log.debug("缓存命中: slug={}", slug);
                return objectMapper.readValue(cached, ArticleDetailVO.class);
            } catch (JsonProcessingException e) {
                log.warn("缓存反序列化失败，删除脏缓存: slug={}, err={}", slug, e.getMessage());
                redisTemplate.delete(key);
            }
        }

        // 未命中 → 查 DB
        ArticleDetailVO detail = articleMapper.selectArticleBySlug(slug);
        if (detail == null) {
            // 缓存空值，防止恶意 slug 反复打穿到 DB
            redisTemplate.opsForValue().set(key, "", EMPTY_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return null;
        }

        try {
            String json = objectMapper.writeValueAsString(detail);
            redisTemplate.opsForValue().set(key, json, RedisConstants.CACHE_TTL_ARTICLE, TimeUnit.MINUTES);
            log.debug("回填缓存: slug={}, ttl={}min", slug, RedisConstants.CACHE_TTL_ARTICLE);
        } catch (JsonProcessingException e) {
            log.warn("缓存序列化失败，跳过回填: slug={}, err={}", slug, e.getMessage());
        }
        return detail;
    }

    /**
     * 浏览量自增（Hash HINCRBY，原子操作）。
     * Key = blog:article:views, Field = articleId, Value = 浏览量
     */
    public void incrementViewCount(Long articleId) {
        if (articleId == null) {
            return;
        }
        Long count = redisTemplate.opsForHash()
                .increment(RedisConstants.ARTICLE_VIEW_COUNT_KEY, articleId.toString(), 1L);
        log.debug("浏览量+1: articleId={}, current={}", articleId, count);
    }

    /**
     * 定时同步浏览量到 MySQL。
     *
     * 策略：先 update DB（用 SQL 表达式 view_count = view_count + delta），再 delete Redis field。
     * 顺序保证：万一 update 后宕机未删除，下轮重复同步同一份增量 → 最多重复计数，不会漏。
     */
    @Scheduled(cron = RedisConstants.VIEW_COUNT_SYNC_CRON)
    public void syncViewCountsToDb() {
        Map<Object, Object> entries = redisTemplate.opsForHash()
                .entries(RedisConstants.ARTICLE_VIEW_COUNT_KEY);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        log.info("开始同步文章浏览量到数据库，待同步条数: {}", entries.size());

        int success = 0;
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                Long articleId = Long.parseLong(entry.getKey().toString());
                long delta = Long.parseLong(entry.getValue().toString());
                if (delta <= 0) {
                    continue;
                }
                int rows = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                        .eq(Article::getId, articleId)
                        .setSql("view_count = view_count + " + delta));
                if (rows > 0) {
                    redisTemplate.opsForHash()
                            .delete(RedisConstants.ARTICLE_VIEW_COUNT_KEY, entry.getKey());
                    success++;
                }
            } catch (NumberFormatException e) {
                log.warn("浏览量字段解析失败: key={}, value={}", entry.getKey(), entry.getValue());
            }
        }
        log.info("浏览量同步完成: 成功={}/{}", success, entries.size());
    }

    /**
     * 删除文章详情缓存（Cache Aside 写模式：先更新 DB，再删除缓存）。
     * 调用时机：文章 update / delete 后。
     */
    public void evictArticleCache(String slug) {
        if (slug == null || slug.isEmpty()) {
            return;
        }
        String key = RedisConstants.ARTICLE_DETAIL_KEY + slug;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("删除文章缓存: slug={}, deleted={}", slug, deleted);
    }
}
