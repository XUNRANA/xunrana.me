package me.xunrana.blog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.common.annotation.OpLog;
import me.xunrana.blog.service.CommentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/comments")
@Tag(name = "评论管理")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    @PutMapping("/{id}/status")
    @Operation(summary = "审核评论（修改评论状态）")
    @OpLog(module = "评论管理", operation = "审核评论")
    public Result<Void> updateCommentStatus(
            @Parameter(description = "评论ID") @PathVariable Long id,
            @Parameter(description = "状态：0=待审核, 1=通过, 2=拒绝") @RequestParam Integer status) {
        commentService.updateCommentStatus(id, status);
        return Result.success();
    }
}
