package me.xunrana.blog.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private Integer role;

    private LocalDateTime createdAt;
}
