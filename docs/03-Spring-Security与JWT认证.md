# 03 - Spring Security 与 JWT 认证

> 本文档深入讲解 HTTP 无状态认证的背景、JWT 原理与结构、双 Token 设计、Spring Security 过滤器链机制，以及我们项目中的具体实现。这是面试中高频考察的知识点。

---

## 目录

1. [HTTP 无状态与认证方案](#1-http-无状态与认证方案)
2. [Session vs JWT 对比](#2-session-vs-jwt-对比)
3. [JWT 结构详解](#3-jwt-结构详解)
4. [双 Token 设计](#4-双-token-设计)
5. [Spring Security 过滤器链](#5-spring-security-过滤器链)
6. [SecurityConfig 逐行解析](#6-securityconfig-逐行解析)
7. [JwtAuthFilter 认证流程](#7-jwtauthfilter-认证流程)
8. [Token 黑名单与登出](#8-token-黑名单与登出)
9. [RBAC 权限控制](#9-rbac-权限控制)
10. [面试常见问题](#10-面试常见问题)

---

## 1. HTTP 无状态与认证方案

### 1.1 什么是 HTTP 无状态？

HTTP 协议是**无状态的**（Stateless），即服务器不会记住两次请求之间的关系。每次请求都是独立的，服务器不知道"你是谁"。

```
客户端                     服务器
  │                          │
  │─── 请求1: 登录成功 ──────►│   服务器：好的，验证通过
  │                          │
  │─── 请求2: 获取文章 ──────►│   服务器：你是谁？我不认识你
  │                          │
```

所以我们需要一种机制，让客户端在每次请求中**携带身份凭证**，告诉服务器"我是谁"。

### 1.2 主流认证方案

| 方案 | 原理 | 适用场景 |
|------|------|----------|
| **Cookie + Session** | 服务器存储会话，客户端携带 SessionID | 传统 MVC 项目（如 JSP） |
| **JWT Token** | 客户端携带自包含的 Token | 前后端分离、微服务 |
| **OAuth 2.0** | 第三方授权（如微信登录） | 第三方登录、开放平台 |
| **API Key** | 固定密钥 | 服务间通信、简单 API |

---

## 2. Session vs JWT 对比

### 2.1 Cookie + Session 方案

```
客户端                          服务器
  │                               │
  │── POST /login ───────────────►│
  │   {username, password}        │  1. 验证用户名密码
  │                               │  2. 创建 Session 对象，存入内存/Redis
  │                               │  3. 返回 SessionID (通过 Set-Cookie)
  │◄── Set-Cookie: JSESSIONID=xxx│
  │                               │
  │── GET /articles ─────────────►│
  │   Cookie: JSESSIONID=xxx     │  4. 根据 SessionID 查找 Session
  │                               │  5. 确认用户身份
  │◄── 200 OK {articles: [...]} ─│
```

### 2.2 JWT Token 方案

```
客户端                          服务器
  │                               │
  │── POST /login ───────────────►│
  │   {username, password}        │  1. 验证用户名密码
  │                               │  2. 生成 JWT Token（包含用户信息）
  │◄── {accessToken: "eyJ..."} ──│  3. Token 直接返回，服务器不存储
  │                               │
  │── GET /articles ─────────────►│
  │   Authorization: Bearer eyJ.. │  4. 解析 Token，验证签名
  │                               │  5. 从 Token 中提取用户信息
  │◄── 200 OK {articles: [...]} ─│
```

### 2.3 详细对比

| 特性 | Session | JWT |
|------|---------|-----|
| **存储位置** | 服务器端（内存/Redis） | 客户端（localStorage/Cookie） |
| **服务器状态** | 有状态（需要存储 Session） | 无状态（不存储任何东西） |
| **分布式支持** | 需要 Session 共享（Sticky Session/Redis） | 天然支持（任何服务器都能验证） |
| **性能** | 需要查询 Session 存储 | 无需查询，直接解析 Token |
| **安全性** | SessionID 泄露即身份冒用 | Token 泄露即身份冒用 |
| **过期控制** | 服务端控制，可随时失效 | Token 一旦签发，到期前无法失效（需借助黑名单） |
| **跨域支持** | Cookie 有同源策略限制 | Token 放在 Header 中，无跨域问题 |
| **适用场景** | 传统 MVC 应用 | 前后端分离、微服务、移动端 |

**我们选择 JWT 的理由**：
1. 项目是前后端分离架构，JWT 不受跨域限制
2. 无状态设计，服务器不需要存储会话信息
3. 将来扩展为微服务时，JWT 天然支持分布式认证

---

## 3. JWT 结构详解

### 3.1 JWT 组成

JWT（JSON Web Token）由三部分组成，用 `.` 分隔：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGUiOjEsImlhdCI6MTcxMTI3NjgwMCwiZXhwIjoxNzExMjc4NjAwfQ.X7Vy3pGqD5EwKjJxLzYBm2xHXQJkN3hFZRdK8pMvF4E
│                      │                                                                                              │
│     Header           │                          Payload                                                              │  Signature
│  (Base64URL编码)     │                       (Base64URL编码)                                                         │ (签名)
```

### 3.2 三部分详解

**Header（头部）**：

```json
{
  "alg": "HS256"    // 签名算法：HMAC-SHA256
}
```
- 声明使用的签名算法
- 经过 Base64URL 编码后成为 JWT 第一段

**Payload（载荷）**：

```json
{
  "sub": "1",              // Subject: 用户 ID（标准声明）
  "username": "admin",     // 自定义声明：用户名
  "role": 1,               // 自定义声明：角色（1=管理员）
  "iat": 1711276800,       // Issued At: 签发时间（Unix 时间戳）
  "exp": 1711278600        // Expiration: 过期时间（Unix 时间戳）
}
```
- 包含用户信息和元数据
- **注意**：Payload 只是 Base64 编码，**不是加密**！任何人都可以解码看到内容
- **绝对不要**在 Payload 中存放密码等敏感信息

**Signature（签名）**：

```
HMAC-SHA256(
    base64UrlEncode(header) + "." + base64UrlEncode(payload),
    secretKey
)
```
- 使用密钥对 Header 和 Payload 进行签名
- 作用：防止 Token 被篡改
- 服务端收到 Token 后，使用相同密钥重新计算签名，对比是否一致

### 3.3 JWT 验证流程

```
客户端发送 Token: Header.Payload.Signature
          │
          ▼
服务端接收到 Token，拆分三部分
          │
          ▼
使用密钥重新计算签名：
  newSignature = HMAC-SHA256(Header + "." + Payload, secretKey)
          │
          ▼
对比签名：
  newSignature == Signature ?
          │
    ┌─────┴─────┐
    │           │
   Yes         No
    │           │
    ▼           ▼
检查过期时间   拒绝：Token 被篡改
  exp > now?
    │
  ┌─┴─┐
  │   │
 Yes  No
  │   │
  ▼   ▼
验证通过  拒绝：Token 已过期
```

---

## 4. 双 Token 设计

### 4.1 为什么需要双 Token？

如果只使用一个 Token：
- **设置短过期时间**（如 30 分钟）→ 用户频繁需要重新登录，体验差
- **设置长过期时间**（如 7 天）→ Token 泄露后风险窗口太长

双 Token 方案平衡了安全性和用户体验。

### 4.2 AccessToken + RefreshToken

| 特性 | AccessToken | RefreshToken |
|------|------------|--------------|
| **用途** | 访问受保护的 API | 获取新的 AccessToken |
| **过期时间** | 短（30 分钟） | 长（7 天） |
| **携带信息** | 用户 ID、用户名、角色 | 仅用户 ID + type 标识 |
| **存储位置** | 客户端内存/localStorage | 客户端 localStorage |

配置值：

```yaml
jwt:
  access-expiration: 1800000     # 30 分钟 = 30 * 60 * 1000 毫秒
  refresh-expiration: 604800000  # 7 天 = 7 * 24 * 60 * 60 * 1000 毫秒
```

### 4.3 双 Token 工作流程

```
                   客户端                                    服务端
                     │                                        │
  ┌──────────────────┤                                        │
  │ 1. 用户登录      │    POST /api/v1/auth/login             │
  │                  │──────{username, password}──────────────►│
  │                  │                                        │── 验证密码
  │                  │    {                                    │── 生成 AccessToken (30min)
  │                  │◄─────accessToken: "eyJ...",            │── 生成 RefreshToken (7days)
  │                  │      refreshToken: "eyJ...",           │
  │  保存两个 Token  │      expiresIn: 1800000                │
  │                  │    }                                   │
  └──────────────────┤                                        │
                     │                                        │
  ┌──────────────────┤                                        │
  │ 2. 正常访问 API  │    GET /api/v1/admin/articles           │
  │                  │    Authorization: Bearer {accessToken} │
  │                  │──────────────────────────────────────► │── 验证 AccessToken
  │                  │                                        │── 提取用户信息
  │                  │◄──── 200 OK {articles: [...]}          │── 返回数据
  └──────────────────┤                                        │
                     │                                        │
  ┌──────────────────┤                                        │
  │ 3. AccessToken   │    GET /api/v1/admin/articles           │
  │    过期了 (30min) │    Authorization: Bearer {过期Token}    │
  │                  │──────────────────────────────────────► │── 验证失败：Token 过期
  │                  │◄──── 401 Unauthorized                  │
  │                  │                                        │
  │ 4. 用 Refresh    │    POST /api/v1/auth/refresh            │
  │    Token 续期    │──────{refreshToken: "eyJ..."}─────────►│── 验证 RefreshToken
  │                  │                                        │── 生成新 AccessToken
  │                  │◄──── {newAccessToken, newRefreshToken}  │── 生成新 RefreshToken
  │                  │                                        │
  │  更新本地 Token  │                                        │
  └──────────────────┤                                        │
                     │                                        │
  ┌──────────────────┤                                        │
  │ 5. 用户登出      │    POST /api/v1/auth/logout             │
  │                  │    Authorization: Bearer {accessToken} │
  │                  │──────────────────────────────────────► │── 将 Token 加入 Redis 黑名单
  │                  │◄──── 200 OK                            │
  │  清除本地 Token  │                                        │
  └──────────────────┤                                        │
```

### 4.4 JwtTokenProvider 代码实现

```java
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;     // 30 分钟

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;    // 7 天

    private SecretKey secretKey;

    @PostConstruct  // Bean 初始化后执行
    public void init() {
        // 将字符串密钥转换为 SecretKey 对象
        // HMAC-SHA256 要求密钥至少 256 位（32 字节）
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 生成 AccessToken：包含用户 ID、用户名、角色
    public String generateAccessToken(Long userId, String username, Integer role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .subject(userId.toString())       // sub: 用户 ID
                .claim("username", username)       // 自定义声明
                .claim("role", role)               // 自定义声明
                .issuedAt(now)                     // iat: 签发时间
                .expiration(expiryDate)            // exp: 过期时间
                .signWith(secretKey)               // 使用密钥签名
                .compact();                        // 构建并序列化
    }

    // 生成 RefreshToken：只包含用户 ID 和类型标识
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")    // 标识这是 RefreshToken
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    // 解析并验证 Token（签名验证 + 过期检查）
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)      // 设置验证密钥
                .build()
                .parseSignedClaims(token)   // 解析并验证签名
                .getPayload();              // 获取 Payload
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token) {
        try {
            parseToken(token);   // 如果签名无效或 Token 过期，会抛异常
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## 5. Spring Security 过滤器链

### 5.1 过滤器链概念

Spring Security 的核心是一条**过滤器链**（Filter Chain）。每个 HTTP 请求都要经过这条链上的所有过滤器。

```
HTTP 请求
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                  SecurityFilterChain                      │
│                                                          │
│  ┌─────────────────┐                                    │
│  │  CorsFilter     │  ← 处理跨域请求                     │
│  └────────┬────────┘                                    │
│           │                                              │
│  ┌────────▼────────┐                                    │
│  │  CsrfFilter     │  ← CSRF 防护（我们禁用了）          │
│  └────────┬────────┘                                    │
│           │                                              │
│  ┌────────▼────────┐                                    │
│  │  JwtAuthFilter  │  ← 我们自定义的 JWT 过滤器          │
│  │  (自定义)        │     提取 Token → 验证 → 设置认证    │
│  └────────┬────────┘                                    │
│           │                                              │
│  ┌────────▼──────────────────┐                          │
│  │  UsernamePasswordAuth     │  ← 表单登录过滤器         │
│  │  Filter (默认)            │     我们不用它             │
│  └────────┬──────────────────┘                          │
│           │                                              │
│  ┌────────▼────────┐                                    │
│  │  Authorization   │  ← 权限检查                        │
│  │  Filter          │     检查 URL 是否有访问权限         │
│  └────────┬────────┘                                    │
│           │                                              │
│  ┌────────▼────────┐                                    │
│  │  ExceptionTranslation │  ← 异常处理                  │
│  │  Filter               │     认证失败 → 401            │
│  └────────┬──────────────┘     授权失败 → 403            │
│           │                                              │
└───────────┼──────────────────────────────────────────────┘
            │
            ▼
      DispatcherServlet
      → Controller → Service → ...
```

### 5.2 我们的 JWT 过滤器位置

```java
// 关键配置：将 JwtAuthFilter 添加到 UsernamePasswordAuthenticationFilter 之前
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

**为什么放在 UsernamePasswordAuthenticationFilter 之前？**

因为 `UsernamePasswordAuthenticationFilter` 是处理表单登录（username + password）的过滤器，而我们使用 JWT 认证，不需要表单登录。我们的 `JwtAuthFilter` 在它之前就完成了认证（从 Header 中提取 Token 并验证），后续过滤器只需检查 `SecurityContext` 中是否有认证信息即可。

---

## 6. SecurityConfig 逐行解析

```java
@Configuration            // 标记为 Spring 配置类
@EnableWebSecurity        // 启用 Spring Security 的 Web 安全功能
@RequiredArgsConstructor  // Lombok：为 final 字段生成构造函数（替代 @Autowired）
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;  // 注入我们的 JWT 过滤器

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ===== 1. 禁用 CSRF 防护 =====
            // CSRF（跨站请求伪造）防护在传统表单提交中很重要
            // 但 JWT 方案中 Token 放在 Authorization Header 中，不受 CSRF 攻击
            // 所以可以安全地禁用
            .csrf(AbstractHttpConfigurer::disable)

            // ===== 2. 会话管理：无状态 =====
            // 告诉 Spring Security 不要创建 HttpSession
            // 因为我们使用 JWT 进行认证，不需要服务端存储会话
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ===== 3. 请求授权规则 =====
            .authorizeHttpRequests(auth -> auth
                // 3.1 认证接口：所有人都可以访问
                .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh")
                    .permitAll()

                // 3.2 公开的 GET 接口：文章、分类、标签列表
                .requestMatchers(HttpMethod.GET,
                    "/v1/articles/**", "/v1/categories/**", "/v1/tags/**")
                    .permitAll()

                // 3.3 Swagger 文档：开发时需要访问
                .requestMatchers(HttpMethod.GET,
                    "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                    .permitAll()

                // 3.4 上传文件的访问路径
                .requestMatchers(HttpMethod.GET, "/uploads/**")
                    .permitAll()

                // 3.5 管理员接口：需要 ADMIN 角色
                .requestMatchers("/v1/admin/**")
                    .hasRole("ADMIN")
                // hasRole("ADMIN") 实际匹配的权限是 "ROLE_ADMIN"
                // Spring Security 自动添加 "ROLE_" 前缀

                // 3.6 其他所有请求：需要认证（登录）
                .anyRequest().authenticated()
            )

            // ===== 4. 添加 JWT 过滤器 =====
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // ===== 5. 自定义异常处理 =====
            .exceptionHandling(exception -> exception
                // 5.1 认证失败（401）：未登录或 Token 过期
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.setStatus(401);
                    ObjectMapper objectMapper = new ObjectMapper();
                    Result<Void> result = Result.error(ErrorCode.UNAUTHORIZED);
                    response.getWriter().write(objectMapper.writeValueAsString(result));
                })
                // 5.2 权限不足（403）：已登录但角色不够
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

    // ===== 密码编码器 =====
    // BCrypt：自适应哈希算法，每次生成不同的盐值
    // 即使两个用户密码相同，加密后的字符串也不同
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ===== 认证管理器 =====
    // AuthenticationManager 用于验证用户名+密码
    // 内部会调用 UserDetailsService 加载用户信息，然后对比密码
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
```

### 权限规则匹配顺序

Spring Security 的规则匹配是**从上到下、第一个匹配生效**：

```
请求: POST /api/v1/auth/login
  ↓ 匹配第一条规则: POST /v1/auth/login → permitAll() ✓

请求: GET /api/v1/articles/my-first-blog
  ↓ 匹配第二条规则: GET /v1/articles/** → permitAll() ✓

请求: POST /api/v1/admin/articles
  ↓ 不匹配前面的规则
  ↓ 匹配第五条规则: /v1/admin/** → hasRole("ADMIN")
  ↓ 如果没有 ROLE_ADMIN → 403 Forbidden

请求: POST /api/v1/auth/logout
  ↓ 不匹配前面的规则
  ↓ 匹配最后一条: anyRequest() → authenticated()
  ↓ 如果没有有效 Token → 401 Unauthorized
```

> 注意：配置中的路径不包含 context-path（`/api`），因为 Spring Security 在 Servlet 层面工作，此时请求路径已经去掉了 context-path。

---

## 7. JwtAuthFilter 认证流程

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    // OncePerRequestFilter：确保每个请求只经过一次此过滤器
    // （避免请求转发时重复执行）

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_KEY_PREFIX = "blog:token:blacklist:";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Step 1: 从请求头提取 Token
            String token = extractToken(request);

            // Step 2: 如果没有 Token，直接放行（后续过滤器会处理权限检查）
            if (!StringUtils.hasText(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Step 3: 检查 Token 是否在黑名单中（用户已登出）
            Boolean isBlacklisted = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token);
            if (Boolean.TRUE.equals(isBlacklisted)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Step 4: 验证 Token 并设置认证信息
            if (jwtTokenProvider.validateToken(token)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String username = jwtTokenProvider.getUsernameFromToken(token);
                Integer role = jwtTokenProvider.getRoleFromToken(token);

                // 构建权限列表
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                if (role != null && role == 1) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }

                // 创建认证对象并放入 SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userId.toString(),  // principal（主体）
                        null,               // credentials（凭证，设为 null）
                        authorities         // 权限列表
                    );
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // 关键！将认证信息设置到 SecurityContext
                // 后续的 AuthorizationFilter 会检查这里的认证信息
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Token 解析失败不抛异常，而是让请求以"未认证"状态继续
            // 后续 AuthorizationFilter 会根据 URL 权限配置决定是否放行
        }

        // 无论认证成功与否，都继续过滤器链
        filterChain.doFilter(request, response);
    }

    // 从 Authorization 请求头中提取 Bearer Token
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
```

### 认证流程图

```
HTTP 请求到达 JwtAuthFilter
        │
        ▼
提取 Authorization Header
        │
        ├── 没有 Header 或不以 "Bearer " 开头
        │       │
        │       ▼
        │   token = null → 直接放行
        │   （公开接口不需要 Token）
        │
        ├── 提取到 Token
        │       │
        │       ▼
        │   检查 Redis 黑名单
        │       │
        │       ├── Token 在黑名单中 → 放行（视为未认证）
        │       │
        │       ├── Token 不在黑名单
        │       │       │
        │       │       ▼
        │       │   validateToken()
        │       │       │
        │       │       ├── 验证失败（过期/签名无效） → 放行（视为未认证）
        │       │       │
        │       │       ├── 验证成功
        │       │       │       │
        │       │       │       ▼
        │       │       │   从 Token 中提取 userId, username, role
        │       │       │       │
        │       │       │       ▼
        │       │       │   构建 Authentication 对象
        │       │       │       │
        │       │       │       ▼
        │       │       │   设置到 SecurityContextHolder
        │       │       │       │
        │       │       │       ▼
        │       │       │   filterChain.doFilter() → 继续后续过滤器
        │       │       │
```

---

## 8. Token 黑名单与登出

### 8.1 JWT 登出的挑战

JWT 是**自包含的无状态 Token**，一旦签发就无法使其失效（除非等到过期）。这在用户登出时是个问题——用户点击"退出登录"后，Token 理论上仍然有效。

### 8.2 Redis 黑名单方案

```
用户请求登出
    │
    ▼
将当前 Token 存入 Redis
    Key:   blog:token:blacklist:{token字符串}
    Value: true (或任意值)
    TTL:   Token 的剩余有效时间
    │
    ▼
后续请求携带此 Token
    │
    ▼
JwtAuthFilter 检查 Redis 黑名单
    redisTemplate.hasKey("blog:token:blacklist:" + token)
    │
    ├── true → Token 已被拉黑，视为未认证
    └── false → 正常验证 Token
```

**为什么 TTL 设为 Token 的剩余有效时间？**

Token 过期后，即使不在黑名单中也会验证失败。所以黑名单记录只需要保留到 Token 自然过期即可，避免 Redis 中积累大量过期数据。

### 8.3 其他失效方案对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Redis 黑名单**（我们用的） | 实现简单，性能好 | 需要每次请求查 Redis |
| 修改密钥 | 所有 Token 立即失效 | 影响所有用户 |
| 版本号方案 | 可以精确控制单用户 | 需要额外存储和查询 |
| 短过期时间 + RefreshToken | 减小风险窗口 | 无法立即失效 |

---

## 9. RBAC 权限控制

### 9.1 RBAC 模型

RBAC（Role-Based Access Control，基于角色的访问控制）是最常见的权限控制模型：

```
用户 ── 拥有 ──► 角色 ── 拥有 ──► 权限

例如：
admin 用户 ── ADMIN 角色 ── 管理文章、管理评论、管理分类...
普通用户   ── USER 角色  ── 查看文章、发表评论...
```

### 9.2 我们的简化实现

本项目采用简化版 RBAC，只有两种角色：

```java
public enum Role {
    USER(0, "普通用户"),   // 可以查看公开内容
    ADMIN(1, "管理员");    // 可以管理所有内容
}
```

权限控制通过两个层面实现：

**层面一：URL 级别**（SecurityConfig 中配置）

```java
.requestMatchers("/v1/admin/**").hasRole("ADMIN")  // 管理接口需要 ADMIN
.anyRequest().authenticated()                       // 其他接口需要登录
```

**层面二：角色赋予**（JwtAuthFilter 中设置）

```java
List<SimpleGrantedAuthority> authorities = new ArrayList<>();
authorities.add(new SimpleGrantedAuthority("ROLE_USER"));  // 所有登录用户都有 USER 角色
if (role != null && role == 1) {
    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN")); // 管理员额外有 ADMIN 角色
}
```

---

## 10. 面试常见问题

### Q1: JWT 和 Session 的区别？各自的优缺点？

**答**：Session 是有状态的，将会话信息存储在服务端（内存或 Redis），客户端通过 Cookie 携带 SessionID；JWT 是无状态的，将用户信息编码在 Token 中，客户端通过 Authorization Header 携带。

Session 的优点是可以随时在服务端销毁实现即时失效，缺点是需要服务端存储且在分布式环境需要共享。JWT 的优点是无状态、天然支持分布式、不占服务端内存，缺点是签发后无法主动失效（需要借助黑名单机制），且 Token 体积较大（因为携带了用户信息）。

### Q2: JWT Token 被盗了怎么办？

**答**：
1. **缩短 AccessToken 有效期**（我们设为 30 分钟），减小风险窗口
2. **使用 HTTPS**，防止中间人攻击窃取 Token
3. **Token 黑名单机制**，用户发现异常可以主动登出使 Token 失效
4. **检测异常行为**，如 IP 突然变化时要求重新认证
5. **不在 Token 中存储敏感信息**，即使被解码也不会泄露密码

### Q3: 为什么要双 Token（AccessToken + RefreshToken）？

**答**：单 Token 面临安全性和用户体验的矛盾——短过期时间安全但频繁登录，长过期时间方便但泄露风险大。双 Token 方案中，AccessToken 短期（30 分钟）用于访问 API，RefreshToken 长期（7 天）用于刷新 AccessToken。即使 AccessToken 泄露，攻击窗口只有 30 分钟；RefreshToken 只在刷新时使用，暴露面小。

### Q4: Spring Security 的认证流程是怎样的？

**答**：在我们的 JWT 方案中，认证流程是：请求到达 → JwtAuthFilter 从 Authorization Header 中提取 Bearer Token → 检查 Token 是否在 Redis 黑名单中 → 使用密钥验证 Token 签名和有效期 → 从 Token 中提取用户 ID、角色等信息 → 构建 Authentication 对象放入 SecurityContextHolder → 后续的 AuthorizationFilter 根据 URL 权限配置检查 SecurityContext 中的认证信息和角色。

### Q5: BCrypt 加密有什么特点？

**答**：BCrypt 是一种自适应哈希算法，特点是：
1. **自带盐值**：每次加密自动生成随机盐值，即使相同密码也会产生不同的哈希值
2. **可调节成本因子**（work factor），默认为 10，表示 2^10 = 1024 次迭代，可以通过增加成本因子来抵抗硬件升级带来的暴力破解能力提升
3. **不可逆**：无法从哈希值还原明文密码
4. **验证方式**：将明文密码与存储的哈希值传入 BCrypt 验证函数，函数会从哈希值中提取盐值重新加密后对比

### Q6: 如果不用 Spring Security，你会怎么实现认证鉴权？

**答**：可以使用 Servlet Filter 或 Spring 的 HandlerInterceptor 实现：
1. 编写一个过滤器/拦截器，拦截所有请求
2. 从请求头中提取 Token 并验证
3. 将用户信息放入 ThreadLocal（类似 SecurityContextHolder 的作用）
4. 在需要权限控制的地方（Controller 或 AOP 切面）检查用户角色
5. Spring Security 的优势在于提供了完整的安全框架，包括密码编码、CSRF 防护、权限表达式等，减少自己造轮子的风险

### Q7: Token 存在客户端的哪里？localStorage vs Cookie？

**答**：
- **localStorage**：不会自动随请求发送，需要手动通过 JS 读取并放入 Authorization Header，不受 CSRF 攻击，但可能被 XSS 攻击获取
- **Cookie**（httpOnly）：自动随请求发送，设置 httpOnly 后 JS 无法读取（防 XSS），但容易受 CSRF 攻击
- 我们的方案是前端将 Token 放在 Authorization Header 中（通常前端框架使用 axios 拦截器统一添加），结合 HTTPS 传输加密，是目前前后端分离项目的主流做法
