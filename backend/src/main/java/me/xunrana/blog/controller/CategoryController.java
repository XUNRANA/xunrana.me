package me.xunrana.blog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.model.vo.CategoryVO;
import me.xunrana.blog.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
@Tag(name = "分类模块")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("")
    @Operation(summary = "获取所有分类（含文章数）")
    public Result<List<CategoryVO>> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }
}
