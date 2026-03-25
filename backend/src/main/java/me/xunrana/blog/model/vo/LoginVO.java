package me.xunrana.blog.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String accessToken;

    private String refreshToken;

    private String tokenType = "Bearer";

    private Long expiresIn;

    private UserVO user;
}
