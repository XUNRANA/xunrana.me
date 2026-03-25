package me.xunrana.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.ErrorCode;
import me.xunrana.blog.exception.BusinessException;
import me.xunrana.blog.mapper.UserMapper;
import me.xunrana.blog.model.dto.LoginDTO;
import me.xunrana.blog.model.entity.User;
import me.xunrana.blog.model.vo.LoginVO;
import me.xunrana.blog.model.vo.UserVO;
import me.xunrana.blog.security.JwtTokenProvider;
import me.xunrana.blog.service.AuthService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String BLACKLIST_KEY_PREFIX = "blog:token:blacklist:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 根据用户名查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, loginDTO.getUsername())
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 生成Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 构建UserVO
        UserVO userVO = buildUserVO(user);

        // 构建LoginVO
        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(accessToken);
        loginVO.setRefreshToken(refreshToken);
        loginVO.setTokenType("Bearer");
        loginVO.setExpiresIn(jwtTokenProvider.getAccessExpiration());
        loginVO.setUser(userVO);

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return loginVO;
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        // 验证refreshToken
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "RefreshToken无效或已过期");
        }

        // 验证Token类型
        Claims claims = jwtTokenProvider.parseToken(refreshToken);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token类型错误");
        }

        // 获取用户信息
        Long userId = Long.parseLong(claims.getSubject());
        User user = userMapper.selectById(userId);

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        // 生成新的Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 构建LoginVO
        UserVO userVO = buildUserVO(user);

        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(newAccessToken);
        loginVO.setRefreshToken(newRefreshToken);
        loginVO.setTokenType("Bearer");
        loginVO.setExpiresIn(jwtTokenProvider.getAccessExpiration());
        loginVO.setUser(userVO);

        log.info("Token刷新成功: userId={}", userId);
        return loginVO;
    }

    @Override
    public void logout(String accessToken) {
        try {
            // 解析Token获取过期时间
            Claims claims = jwtTokenProvider.parseToken(accessToken);
            long expiration = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            long remainingTtl = expiration - now;

            if (remainingTtl > 0) {
                // 将Token加入黑名单，过期后自动删除
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + accessToken,
                        "1",
                        remainingTtl,
                        TimeUnit.MILLISECONDS
                );
                log.info("Token已加入黑名单, 剩余TTL={}ms", remainingTtl);
            }
        } catch (Exception e) {
            log.warn("登出处理Token时发生异常: {}", e.getMessage());
        }
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return buildUserVO(user);
    }

    /**
     * 将User实体转换为UserVO
     */
    private UserVO buildUserVO(User user) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(user.getAvatar());
        userVO.setEmail(user.getEmail());
        userVO.setRole(user.getRole());
        userVO.setCreatedAt(user.getCreatedAt());
        return userVO;
    }
}
