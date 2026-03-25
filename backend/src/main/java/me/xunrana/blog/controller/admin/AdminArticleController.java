package me.xunrana.blog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.model.dto.ArticleDTO;
import me.xunrana.blog.model.dto.ArticleQueryDTO;
import me.xunrana.blog.model.vo.ArticleVO;
import me.xunrana.blog.service.ArticleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/articles")
@Tag(name = "文章管理")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @GetMapping("")
    @Operation(summary = "管理端分页查询文章列表（所有状态）")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO query) {
        return Result.success(articleService.getArticlePage(query));
    }

    @PostMapping("")
    @Operation(summary = "创建文章")
    public Result<Void> createArticle(@Valid @RequestBody ArticleDTO dto,
                                      Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        articleService.createArticle(dto, userId);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新文章")
    public Result<Void> updateArticle(
            @Parameter(description = "文章ID") @PathVariable Long id,
            @Valid @RequestBody ArticleDTO dto) {
        articleService.updateArticle(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文章")
    public Result<Void> deleteArticle(
            @Parameter(description = "文章ID") @PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }
}
