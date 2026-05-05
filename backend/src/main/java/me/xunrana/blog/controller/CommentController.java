package me.xunrana.blog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.common.annotation.RateLimit;
import me.xunrana.blog.model.dto.CommentDTO;
import me.xunrana.blog.model.vo.CommentVO;
import me.xunrana.blog.service.CommentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
@Tag(name = "评论模块")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/articles/{articleId}/comments")
    @Operation(summary = "获取文章评论列表（树形结构）")
    public Result<List<CommentVO>> getComments(
            @Parameter(description = "文章ID") @PathVariable Long articleId) {
        return Result.success(commentService.getCommentsByArticleId(articleId));
    }

    @PostMapping("/articles/{articleId}/comments")
    @Operation(summary = "发表评论")
    @RateLimit(maxRequests = 5, timeWindow = 60)
    public Result<Void> addComment(
            @Parameter(description = "文章ID") @PathVariable Long articleId,
            @Valid @RequestBody CommentDTO dto,
            HttpServletRequest request) {
        dto.setArticleId(articleId);
        String ip = getClientIp(request);
        commentService.addComment(dto, ip);
        return Result.success();
    }

    /**
     * Extract the real client IP from the request, considering proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // If multiple IPs (X-Forwarded-For can be comma-separated), take the first
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        return ip;
    }
}
