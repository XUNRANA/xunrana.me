package me.xunrana.blog.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.model.vo.FileUploadVO;
import me.xunrana.blog.service.FileService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传 Controller（管理员接口）
 *
 * 这是 Phase 2 的核心学习文件 — 通过实现以下 TODO 来掌握:
 *   - Spring MVC 文件上传 (@RequestParam MultipartFile)
 *   - 组合使用 @OpLog + @RateLimit 注解
 */
@RestController
@RequestMapping("/v1/admin/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传相关接口")
public class AdminFileController {

    private final FileService fileService;

    // ============================================================
    // TODO 2: 实现图片上传接口
    // 📖 教程: docs/10-文件上传与静态资源服务.md 第3节
    //
    // 方法签名:
    //   @PostMapping("/upload/image")
    //   @Operation(summary = "上传图片")
    //   public Result<FileUploadVO> uploadImage(@RequestParam("file") MultipartFile file)
    //
    // 实现步骤:
    //   1. 调用 fileService.uploadImage(file)
    //   2. 返回 Result.success(fileUploadVO)
    //
    // 这个接口本身很简单，重点在于:
    //   - 理解 @RequestParam("file") MultipartFile 如何接收上传文件
    //   - 前端用 FormData + Content-Type: multipart/form-data 发送
    //
    // 提示: 用 import org.springframework.web.bind.annotation.PostMapping;
    // ============================================================

    // ============================================================
    // TODO 3: 给上传接口加上 @OpLog 和 @RateLimit 注解
    // 📖 教程: docs/10-文件上传与静态资源服务.md 第4节
    //
    // 这一步串联了 Phase 2 的前三个功能:
    //   @OpLog(module = "文件管理", operation = "上传图片")  → 记录操作日志
    //   @RateLimit(maxRequests = 10, timeWindow = 60)       → 60秒内最多10次上传
    //
    // 完成后，一次文件上传会:
    //   1. 先经过 RateLimitAspect 检查请求频率
    //   2. 再经过 OperationLogAspect 记录操作日志
    //   3. 最后执行 uploadImage 业务逻辑
    //
    // 提示:
    //   - import me.xunrana.blog.common.annotation.OpLog;
    //   - import me.xunrana.blog.common.annotation.RateLimit;
    // ============================================================
}
