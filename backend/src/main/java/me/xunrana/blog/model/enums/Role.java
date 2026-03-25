package me.xunrana.blog.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Role {

    USER(0, "普通用户"),
    ADMIN(1, "管理员");

    @EnumValue
    private final int code;

    @JsonValue
    private final String description;

    public static Role fromCode(int code) {
        for (Role role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色编码: " + code);
    }
}
