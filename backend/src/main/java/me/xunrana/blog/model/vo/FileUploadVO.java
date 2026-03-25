package me.xunrana.blog.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    /** 文件名（UUID 重命名后的） */
    private String fileName;

    /** 访问 URL（如 /uploads/2026/03/25/uuid.png） */
    private String url;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小（字节） */
    private Long size;
}
