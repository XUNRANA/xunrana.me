package me.xunrana.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.ErrorCode;
import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.exception.BusinessException;
import me.xunrana.blog.mapper.ArticleMapper;
import me.xunrana.blog.mapper.ArticleTagMapper;
import me.xunrana.blog.mapper.CategoryMapper;
import me.xunrana.blog.mapper.TagMapper;
import me.xunrana.blog.model.dto.ArticleDTO;
import me.xunrana.blog.model.dto.ArticleQueryDTO;
import me.xunrana.blog.model.entity.Article;
import me.xunrana.blog.model.entity.ArticleTag;
import me.xunrana.blog.model.vo.ArticleDetailVO;
import me.xunrana.blog.model.vo.ArticleVO;
import me.xunrana.blog.service.ArticleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;

    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^\\w\\u4e00-\\u9fa5-]");
    private static final Pattern MULTI_DASH_PATTERN = Pattern.compile("-{2,}");

    @Override
    public PageResult<ArticleVO> getArticlePage(ArticleQueryDTO query) {
        Page<ArticleVO> page = new Page<>(query.getPage(), query.getSize());
        page = articleMapper.selectArticlePage(page, query);
        return PageResult.from(page);
    }

    @Override
    public ArticleDetailVO getArticleBySlug(String slug) {
        ArticleDetailVO detail = articleMapper.selectArticleBySlug(slug);
        if (detail == null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        // Increment view count asynchronously via direct SQL update
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, detail.getId())
                .setSql("view_count = view_count + 1"));

        // Get previous article (published, id < current, ordered by id DESC)
        ArticleVO prevArticle = findAdjacentArticle(detail.getId(), true);
        detail.setPrevArticle(prevArticle);

        // Get next article (published, id > current, ordered by id ASC)
        ArticleVO nextArticle = findAdjacentArticle(detail.getId(), false);
        detail.setNextArticle(nextArticle);

        return detail;
    }

    @Override
    public List<ArticleVO> getArchives() {
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .orderByDesc(Article::getCreatedAt));

        return articles.stream().map(article -> {
            ArticleVO vo = new ArticleVO();
            BeanUtil.copyProperties(article, vo);
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createArticle(ArticleDTO dto, Long authorId) {
        Article article = new Article();
        BeanUtil.copyProperties(dto, article);

        // Generate slug from title
        article.setSlug(generateSlug(dto.getTitle()));
        article.setAuthorId(authorId);
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setCommentCount(0);
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());

        // If published, set publishedAt
        if (Integer.valueOf(1).equals(dto.getStatus())) {
            article.setPublishedAt(LocalDateTime.now());
        }

        // Default values
        if (article.getIsTop() == null) {
            article.setIsTop(0);
        }
        if (article.getStatus() == null) {
            article.setStatus(0);
        }

        articleMapper.insert(article);
        log.info("Article created: id={}, title={}", article.getId(), article.getTitle());

        // Batch insert article-tag relations
        saveArticleTags(article.getId(), dto.getTagIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticle(Long id, ArticleDTO dto) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        }

        BeanUtil.copyProperties(dto, existing, "id");
        existing.setUpdatedAt(LocalDateTime.now());

        // If status changed to published and publishedAt not set yet
        if (Integer.valueOf(1).equals(dto.getStatus()) && existing.getPublishedAt() == null) {
            existing.setPublishedAt(LocalDateTime.now());
        }

        articleMapper.updateById(existing);
        log.info("Article updated: id={}", id);

        // Rebuild article-tag relations
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                .eq(ArticleTag::getArticleId, id));
        saveArticleTags(id, dto.getTagIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id) {
        articleMapper.deleteById(id);
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                .eq(ArticleTag::getArticleId, id));
        log.info("Article deleted: id={}", id);
    }

    /**
     * Find the previous or next published article relative to the given article ID.
     *
     * @param currentId the current article ID
     * @param previous  true for previous (id < current), false for next (id > current)
     * @return the adjacent ArticleVO or null if none exists
     */
    private ArticleVO findAdjacentArticle(Long currentId, boolean previous) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .select(Article::getId, Article::getTitle, Article::getSlug, Article::getCreatedAt);

        if (previous) {
            wrapper.lt(Article::getId, currentId).orderByDesc(Article::getId);
        } else {
            wrapper.gt(Article::getId, currentId).orderByAsc(Article::getId);
        }
        wrapper.last("LIMIT 1");

        Article article = articleMapper.selectOne(wrapper);
        if (article == null) {
            return null;
        }

        ArticleVO vo = new ArticleVO();
        BeanUtil.copyProperties(article, vo);
        return vo;
    }

    /**
     * Generate a URL-friendly slug from the title.
     * Converts to lowercase, replaces spaces with dashes, removes special chars,
     * and appends a timestamp suffix for uniqueness.
     */
    private String generateSlug(String title) {
        if (StrUtil.isBlank(title)) {
            return "article-" + System.currentTimeMillis();
        }
        String slug = title.trim().toLowerCase();
        slug = slug.replace(" ", "-");
        slug = NON_WORD_PATTERN.matcher(slug).replaceAll("-");
        slug = MULTI_DASH_PATTERN.matcher(slug).replaceAll("-");
        slug = StrUtil.removeSuffix(slug, "-");
        slug = StrUtil.removePrefix(slug, "-");

        // Append timestamp for uniqueness
        slug = slug + "-" + System.currentTimeMillis();
        return slug;
    }

    /**
     * Batch insert article-tag relations.
     */
    private void saveArticleTags(Long articleId, List<Long> tagIds) {
        if (CollUtil.isNotEmpty(tagIds)) {
            for (Long tagId : tagIds) {
                ArticleTag articleTag = new ArticleTag();
                articleTag.setArticleId(articleId);
                articleTag.setTagId(tagId);
                articleTagMapper.insert(articleTag);
            }
        }
    }
}
