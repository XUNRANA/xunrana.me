package me.xunrana.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import me.xunrana.blog.model.dto.ArticleQueryDTO;
import me.xunrana.blog.model.entity.Article;
import me.xunrana.blog.model.vo.ArticleDetailVO;
import me.xunrana.blog.model.vo.ArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * Paginated query for articles with category, author, and tag information.
     * Supports filtering by categoryId, tagId, status, and keyword.
     */
    IPage<ArticleVO> selectArticlePage(IPage<?> page, @Param("query") ArticleQueryDTO query);

    /**
     * Select full article detail by slug, including category name, author info, and tags.
     */
    ArticleDetailVO selectArticleBySlug(@Param("slug") String slug);
}
