package me.xunrana.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置
 *
 * 主要功能: 将 /uploads/** 请求映射到本地文件目录，
 * 使上传的图片可以通过 URL 直接访问。
 *
 * 例如: GET /uploads/2026/03/25/abc.png → 读取 ./uploads/2026/03/25/abc.png 文件
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** URL 映射到本地文件系统目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir);
    }
}
