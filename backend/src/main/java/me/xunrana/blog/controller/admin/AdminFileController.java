package me.xunrana.blog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.common.annotation.OpLog;
import me.xunrana.blog.common.annotation.RateLimit;
import me.xunrana.blog.model.vo.FileUploadVO;
import me.xunrana.blog.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传 Controller（管理员接口）
 *
 * 串联 Phase 2 的三个能力：
 *   1. RateLimitAspect 在方法前拦截，防止刷上传
 *   2. OperationLogAspect 环绕记录上传操作
 *   3. FileService 执行实际的图片落盘
 */
@RestController
@RequestMapping("/v1/admin/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传相关接口")
public class AdminFileController {

    private final FileService fileService;

    @PostMapping("/upload/image")
    @Operation(summary = "上传图片")
    @OpLog(module = "文件管理", operation = "上传图片")
    @RateLimit(maxRequests = 10, timeWindow = 60)
    public Result<FileUploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file));
    }
}
