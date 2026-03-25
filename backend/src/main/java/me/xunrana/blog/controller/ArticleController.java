package me.xunrana.blog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.model.dto.ArticleQueryDTO;
import me.xunrana.blog.model.vo.ArticleDetailVO;
import me.xunrana.blog.model.vo.ArticleVO;
import me.xunrana.blog.service.ArticleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/articles")
@Tag(name = "文章模块")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("")
    @Operation(summary = "分页查询文章列表")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO query) {
        // Public endpoint only shows published articles
        query.setStatus(1);
        return Result.success(articleService.getArticlePage(query));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "根据slug获取文章详情")
    public Result<ArticleDetailVO> getArticleBySlug(
            @Parameter(description = "文章slug") @PathVariable String slug) {
        return Result.success(articleService.getArticleBySlug(slug));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索文章")
    public Result<PageResult<ArticleVO>> searchArticles(ArticleQueryDTO query) {
        // Search only published articles
        query.setStatus(1);
        return Result.success(articleService.getArticlePage(query));
    }

    @GetMapping("/archives")
    @Operation(summary = "获取文章归档列表")
    public Result<List<ArticleVO>> getArchives() {
        return Result.success(articleService.getArchives());
    }
}
