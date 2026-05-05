package me.xunrana.blog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.common.annotation.OpLog;
import me.xunrana.blog.model.dto.TagDTO;
import me.xunrana.blog.service.TagService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/tags")
@Tag(name = "标签管理")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    @PostMapping("")
    @Operation(summary = "创建标签")
    @OpLog(module = "标签管理", operation = "创建标签")
    public Result<Void> createTag(@Valid @RequestBody TagDTO dto) {
        tagService.createTag(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新标签")
    @OpLog(module = "标签管理", operation = "更新标签")
    public Result<Void> updateTag(
            @Parameter(description = "标签ID") @PathVariable Long id,
            @Valid @RequestBody TagDTO dto) {
        tagService.updateTag(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    @OpLog(module = "标签管理", operation = "删除标签")
    public Result<Void> deleteTag(
            @Parameter(description = "标签ID") @PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }
}
