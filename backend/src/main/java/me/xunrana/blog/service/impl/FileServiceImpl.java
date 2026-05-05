package me.xunrana.blog.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.ErrorCode;
import me.xunrana.blog.exception.BusinessException;
import me.xunrana.blog.model.vo.FileUploadVO;
import me.xunrana.blog.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传 Service 实现
 *
 * 安全策略（双重校验）：
 *   1. 扩展名白名单：避免上传 .exe / .sh
 *   2. MIME 类型白名单：避免攻击者改名绕过扩展名校验
 *   3. UUID 重命名：避免原始文件名碰撞和路径穿越（../ 等）
 *   4. 按日期分目录：单目录文件数过多会拖慢文件系统遍历
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public FileUploadVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件名非法");
        }

        String extension = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "不支持的文件格式");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件类型不合法");
        }

        String datePath = LocalDate.now().format(DATE_FORMATTER);
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path targetDir = Paths.get(uploadDir, datePath);
        Path targetPath = targetDir.resolve(newFileName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            log.error("文件写入失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件保存失败");
        }

        String url = "/uploads/" + datePath + "/" + newFileName;
        log.info("文件上传成功: original={}, url={}, size={}B", originalName, url, file.getSize());

        return FileUploadVO.builder()
                .fileName(newFileName)
                .url(url)
                .originalName(originalName)
                .size(file.getSize())
                .build();
    }
}
