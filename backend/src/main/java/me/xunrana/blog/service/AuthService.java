package me.xunrana.blog.service;

import me.xunrana.blog.model.dto.LoginDTO;
import me.xunrana.blog.model.vo.LoginVO;
import me.xunrana.blog.model.vo.UserVO;

public interface AuthService {

    /**
     * 用户登录
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 刷新Token
     */
    LoginVO refreshToken(String refreshToken);

    /**
     * 用户登出
     */
    void logout(String accessToken);

    /**
     * 获取当前用户信息
     */
    UserVO getCurrentUser(Long userId);
}
