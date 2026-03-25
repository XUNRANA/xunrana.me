package me.xunrana.blog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.xunrana.blog.common.ErrorCode;
import me.xunrana.blog.common.Result;
import me.xunrana.blog.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（无状态API不需要）
                .csrf(AbstractHttpConfigurer::disable)

                // 会话管理：无状态
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 请求授权规则
                .authorizeHttpRequests(auth -> auth
                        // 认证相关接口
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh").permitAll()
                        // 公开的文章、分类、标签接口
                        .requestMatchers(HttpMethod.GET, "/v1/articles/**", "/v1/categories/**", "/v1/tags/**").permitAll()
                        // 发表评论（允许匿名评论）
                        .requestMatchers(HttpMethod.POST, "/v1/articles/*/comments").permitAll()
                        // Swagger文档
                        .requestMatchers(HttpMethod.GET, "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // 上传文件访问
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // 管理员接口
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )

                // JWT过滤器
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // 认证异常处理
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(401);
                            ObjectMapper objectMapper = new ObjectMapper();
                            Result<Void> result = Result.error(ErrorCode.UNAUTHORIZED);
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(403);
                            ObjectMapper objectMapper = new ObjectMapper();
                            Result<Void> result = Result.error(ErrorCode.FORBIDDEN);
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
