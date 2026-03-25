package me.xunrana.blog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.model.dto.LoginDTO;
import me.xunrana.blog.model.vo.LoginVO;
import me.xunrana.blog.model.vo.UserVO;
import me.xunrana.blog.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证模块")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        LoginVO loginVO = authService.login(loginDTO);
        return Result.success(loginVO);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token")
    public Result<LoginVO> refresh(@RequestParam String refreshToken) {
        LoginVO loginVO = authService.refreshToken(refreshToken);
        return Result.success(loginVO);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authService.logout(token);
        return Result.success();
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public Result<UserVO> info(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        UserVO userVO = authService.getCurrentUser(userId);
        return Result.success(userVO);
    }
}
