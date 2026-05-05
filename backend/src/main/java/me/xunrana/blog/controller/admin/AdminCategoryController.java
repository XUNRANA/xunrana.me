package me.xunrana.blog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.common.annotation.OpLog;
import me.xunrana.blog.model.dto.CategoryDTO;
import me.xunrana.blog.service.CategoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/categories")
@Tag(name = "分类管理")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping("")
    @Operation(summary = "创建分类")
    @OpLog(module = "分类管理", operation = "创建分类")
    public Result<Void> createCategory(@Valid @RequestBody CategoryDTO dto) {
        categoryService.createCategory(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分类")
    @OpLog(module = "分类管理", operation = "更新分类")
    public Result<Void> updateCategory(
            @Parameter(description = "分类ID") @PathVariable Long id,
            @Valid @RequestBody CategoryDTO dto) {
        categoryService.updateCategory(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    @OpLog(module = "分类管理", operation = "删除分类")
    public Result<Void> deleteCategory(
            @Parameter(description = "分类ID") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
