package me.xunrana.blog.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ArticleStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    @EnumValue
    private final int code;

    @JsonValue
    private final String description;

    public static ArticleStatus fromCode(int code) {
        for (ArticleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的文章状态编码: " + code);
    }
}
