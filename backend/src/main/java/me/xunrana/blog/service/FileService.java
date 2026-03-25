package me.xunrana.blog.service;

import me.xunrana.blog.model.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传 Service 接口
 */
public interface FileService {

    /**
     * 上传图片文件
     *
     * 校验规则:
     *   - 文件不能为空
     *   - 仅允许 jpg/jpeg/png/gif/webp 格式
     *   - 大小不超过 5MB（由 application.yml 配置）
     *
     * 存储规则:
     *   - 使用 UUID 重命名，防止文件名冲突
     *   - 按日期分目录存储（如 uploads/2026/03/25/xxx.png）
     *
     * @param file 上传的文件
     * @return 文件信息 VO（含访问 URL）
     */
    FileUploadVO uploadImage(MultipartFile file);
}
