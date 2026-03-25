package me.xunrana.blog.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    USERNAME_EXIST(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "密码错误"),

    ARTICLE_NOT_FOUND(2001, "文章不存在"),
    CATEGORY_NOT_FOUND(2002, "分类不存在"),
    TAG_NOT_FOUND(2003, "标签不存在"),

    FILE_UPLOAD_ERROR(3001, "文件上传失败"),

    RATE_LIMIT_EXCEEDED(4001, "请求过于频繁");

    private final int code;
    private final String message;
}
