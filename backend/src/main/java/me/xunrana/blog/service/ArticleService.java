package me.xunrana.blog.service;

import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.model.dto.ArticleDTO;
import me.xunrana.blog.model.dto.ArticleQueryDTO;
import me.xunrana.blog.model.vo.ArticleDetailVO;
import me.xunrana.blog.model.vo.ArticleVO;

import java.util.List;

public interface ArticleService {

    PageResult<ArticleVO> getArticlePage(ArticleQueryDTO query);

    ArticleDetailVO getArticleBySlug(String slug);

    List<ArticleVO> getArchives();

    void createArticle(ArticleDTO dto, Long authorId);

    void updateArticle(Long id, ArticleDTO dto);

    void deleteArticle(Long id);
}
