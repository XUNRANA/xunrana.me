package me.xunrana.blog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.model.vo.TagVO;
import me.xunrana.blog.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/tags")
@Tag(name = "标签模块")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("")
    @Operation(summary = "获取所有标签（含文章数）")
    public Result<List<TagVO>> getAllTags() {
        return Result.success(tagService.getAllTags());
    }
}
