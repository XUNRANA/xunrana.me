# 05 - RESTful API 设计规范

> 本文档讲解 REST 架构风格的核心原则、本项目的 API URL 设计规范、版本控制策略、请求/响应约定、分页设计，以及 Swagger/OpenAPI 自动化文档。

---

## 目录

1. [REST 核心原则](#1-rest-核心原则)
2. [API URL 设计规范](#2-api-url-设计规范)
3. [HTTP 方法与语义](#3-http-方法与语义)
4. [API 版本控制策略](#4-api-版本控制策略)
5. [请求与响应约定](#5-请求与响应约定)
6. [分页约定](#6-分页约定)
7. [Swagger/OpenAPI 文档](#7-swaggeropenapi-文档)
8. [API 安全设计](#8-api-安全设计)
9. [API 测试方法](#9-api-测试方法)
10. [面试常见问题](#10-面试常见问题)

---

## 1. REST 核心原则

REST（Representational State Transfer，表述性状态转移）是一种软件架构风格，由 Roy Fielding 在 2000 年的博士论文中提出。RESTful API 是遵循 REST 原则设计的 Web API。

### 1.1 六大约束

| 约束 | 说明 | 在本项目中的体现 |
|------|------|------------------|
| **客户端-服务器** | 前后端分离 | Vue 3 前端 + Spring Boot 后端 |
| **无状态** | 每个请求包含全部信息 | JWT Token 携带用户身份 |
| **可缓存** | 响应可标记为可缓存 | GET 请求的文章列表可缓存 |
| **统一接口** | 资源标识、表述操作、自描述消息 | URL 标识资源，HTTP 方法标识操作 |
| **分层系统** | 客户端不知道中间层 | Nginx → Spring Boot 的代理架构 |
| **按需代码（可选）** | 服务端可返回可执行代码 | 不适用 |

### 1.2 资源导向设计

REST 的核心思想是：**一切皆资源**。URL 标识资源，HTTP 方法标识对资源的操作。

```
以"文章"这个资源为例：

资源集合:  /api/v1/articles           ← 所有文章
单个资源:  /api/v1/articles/{slug}    ← 特定文章
子资源:    /api/v1/articles/{id}/comments  ← 某文章的评论

操作:
GET    /api/v1/articles          → 获取文章列表
GET    /api/v1/articles/my-blog  → 获取文章详情
POST   /api/v1/admin/articles    → 创建文章
PUT    /api/v1/admin/articles/1  → 更新文章
DELETE /api/v1/admin/articles/1  → 删除文章
```

---

## 2. API URL 设计规范

### 2.1 URL 结构

```
https://xunrana.me/api/v1/articles?page=1&size=10&categoryId=1
│                   │   │  │        │
│                   │   │  │        └── 查询参数（过滤/分页）
│                   │   │  └── 资源名（名词，复数形式）
│                   │   └── API 版本号
│                   └── API 前缀（context-path）
└── 域名
```

### 2.2 命名规则

| 规则 | 正确示例 | 错误示例 | 说明 |
|------|----------|----------|------|
| 使用名词 | `/articles` | `/getArticles` | URL 标识资源，不是动作 |
| 使用复数 | `/articles` | `/article` | 集合用复数 |
| 使用小写 | `/categories` | `/Categories` | URL 统一小写 |
| 使用连字符 | `/operation-logs` | `/operation_logs` | kebab-case |
| 不要动词 | `DELETE /articles/1` | `/deleteArticle?id=1` | HTTP 方法表达动作 |
| 层级关系 | `/articles/1/comments` | `/articleComments?articleId=1` | 用路径表达从属 |

### 2.3 本项目的 API 路径设计

```
/api                                   ← context-path（所有 API 的前缀）
 └── /v1                               ← 版本号
      │
      ├── /auth                        ← 认证模块（公开）
      │    ├── POST /login             ← 登录
      │    ├── POST /refresh           ← 刷新 Token
      │    └── POST /logout            ← 登出（需认证）
      │
      ├── /articles                    ← 文章模块（公开读取）
      │    ├── GET /                   ← 文章分页列表
      │    └── GET /{slug}             ← 文章详情
      │
      ├── /categories                  ← 分类模块（公开读取）
      │    └── GET /                   ← 分类列表
      │
      ├── /tags                        ← 标签模块（公开读取）
      │    └── GET /                   ← 标签列表
      │
      ├── /comments                    ← 评论模块（公开）
      │    ├── GET /{articleId}        ← 某文章的评论列表
      │    └── POST /                  ← 发表评论
      │
      └── /admin                       ← 管理后台（需要 ADMIN 角色）
           ├── /articles
           │    ├── POST /             ← 创建文章
           │    ├── PUT /{id}          ← 更新文章
           │    └── DELETE /{id}       ← 删除文章
           ├── /categories
           │    ├── POST /             ← 创建分类
           │    ├── PUT /{id}          ← 更新分类
           │    └── DELETE /{id}       ← 删除分类
           ├── /tags
           │    ├── POST /             ← 创建标签
           │    ├── PUT /{id}          ← 更新标签
           │    └── DELETE /{id}       ← 删除标签
           └── /comments
                └── PUT /{id}/status   ← 审核评论
```

### 2.4 公开与管理员接口分离

我们将 API 分为两组：
- **公开接口**（`/v1/articles`、`/v1/categories` 等）：任何人都可以访问的读取接口
- **管理接口**（`/v1/admin/*`）：需要 ADMIN 角色的写入/管理接口

这种分离的好处：
1. **安全规则清晰**：SecurityConfig 中一条 `.requestMatchers("/v1/admin/**").hasRole("ADMIN")` 搞定
2. **职责分离**：公开 Controller 和 Admin Controller 分开，代码结构清晰
3. **后续扩展**：可以分别对公开接口和管理接口做不同的限流策略

---

## 3. HTTP 方法与语义

### 3.1 CRUD 映射

| HTTP 方法 | CRUD 操作 | 语义 | 是否幂等 | 请求体 |
|-----------|----------|------|---------|--------|
| **GET** | Read | 获取资源 | 是 | 无 |
| **POST** | Create | 创建资源 | 否 | 有 |
| **PUT** | Update | 全量更新 | 是 | 有 |
| **PATCH** | Update | 部分更新 | 否 | 有 |
| **DELETE** | Delete | 删除资源 | 是 | 无 |

### 3.2 幂等性解释

**幂等**（Idempotent）：同一操作执行多次，结果和执行一次相同。

```
GET /articles/1     → 多次请求，每次都返回同一篇文章 ✓ 幂等
DELETE /articles/1  → 第一次删除成功，后续请求返回 404，但资源状态不变 ✓ 幂等
PUT /articles/1     → 多次执行全量更新，最终状态一致 ✓ 幂等
POST /articles      → 每次执行都创建一篇新文章 ✗ 非幂等
```

### 3.3 HTTP 状态码使用

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| **200 OK** | 请求成功 | GET、PUT、DELETE 成功 |
| **201 Created** | 资源创建成功 | POST 创建成功 |
| **204 No Content** | 成功但无返回内容 | DELETE 成功 |
| **400 Bad Request** | 请求参数错误 | 参数校验失败 |
| **401 Unauthorized** | 未认证 | 未提供 Token 或 Token 过期 |
| **403 Forbidden** | 权限不足 | 非 ADMIN 访问管理接口 |
| **404 Not Found** | 资源不存在 | 文章/分类不存在 |
| **500 Internal Error** | 服务器错误 | 未预期的异常 |

> 在我们的项目中，为了简化前端处理，大部分情况 HTTP 状态码都是 200，具体的错误信息通过 `Result.code` 传达。但参数校验、权限不足等情况会返回对应的 HTTP 状态码。

---

## 4. API 版本控制策略

### 4.1 为什么需要版本控制？

API 发布后，客户端（前端/移动端/第三方）会依赖它。如果直接修改 API 导致不兼容，所有客户端都会崩溃。版本控制允许新旧版本共存，客户端可以按自己的节奏迁移。

### 4.2 常见版本控制方案

| 方案 | 示例 | 优点 | 缺点 |
|------|------|------|------|
| **URL 路径**（我们用的） | `/api/v1/articles` | 直观、易理解、易实现 | URL 变长 |
| 查询参数 | `/api/articles?version=1` | 不影响 URL 结构 | 容易遗漏 |
| HTTP Header | `Accept: application/vnd.blog.v1+json` | URL 简洁 | 不直观、难调试 |
| 自定义 Header | `X-API-Version: 1` | 灵活 | 非标准 |

**我们选择 URL 路径版本的理由**：
1. 最直观，一眼就能看出版本号
2. 浏览器地址栏可以直接看到和测试
3. Nginx 路由方便（可以将 v1 和 v2 转发到不同的服务）
4. 国内大厂普遍采用（如微信开放平台 `/sns/oauth2/v2/access_token`）

### 4.3 版本升级策略

```
v1 发布：POST /api/v1/articles  Body: {title, content}

需求变更：文章需要支持 markdown 和 html 两种格式

v2 发布：POST /api/v2/articles  Body: {title, content, contentType}

v1 保持不变，默认 contentType 为 markdown
旧客户端继续使用 v1，不受影响
新客户端使用 v2，享受新功能
```

---

## 5. 请求与响应约定

### 5.1 请求约定

| 约定 | 说明 | 示例 |
|------|------|------|
| Content-Type | 请求体使用 JSON | `Content-Type: application/json` |
| 认证方式 | Bearer Token | `Authorization: Bearer eyJ...` |
| 字段命名 | camelCase（驼峰命名） | `categoryId`、`isTop` |
| 分页参数 | URL 查询参数 | `?page=1&size=10` |
| 过滤参数 | URL 查询参数 | `?categoryId=1&status=1&keyword=Spring` |
| 路径参数 | 资源标识 | `/articles/{slug}`、`/admin/articles/{id}` |

### 5.2 响应约定

**统一响应格式**：

```json
{
  "code": 200,           // 业务状态码
  "message": "操作成功",  // 提示信息
  "data": { ... }        // 业务数据（类型灵活）
}
```

**数据类型约定**：

| 数据类型 | JSON 表示 | 示例 |
|----------|----------|------|
| 日期时间 | ISO 8601 字符串 | `"2024-03-24T10:30:00"` |
| 布尔值 | 0/1 整数 | `"isTop": 1` |
| 枚举 | 整数编码 | `"status": 0` (草稿) |
| 空值 | null | `"data": null` |
| 列表 | JSON 数组 | `"tags": [{"id": 1, ...}]` |

### 5.3 请求示例

```bash
# 创建文章
POST /api/v1/admin/articles
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "title": "Spring Boot 入门教程",
  "summary": "从零开始学习 Spring Boot",
  "content": "# Spring Boot\n\n这是文章内容...",
  "coverImage": "/uploads/cover-spring-boot.jpg",
  "categoryId": 1,
  "status": 1,
  "isTop": 0,
  "tagIds": [1, 3, 5]
}
```

```bash
# 查询文章列表（带过滤和分页）
GET /api/v1/articles?page=1&size=10&categoryId=1&keyword=Spring
```

---

## 6. 分页约定

### 6.1 请求参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | Integer | 1 | 当前页码（从 1 开始） |
| `size` | Integer | 10 | 每页条数 |
| `categoryId` | Long | null | 按分类筛选（可选） |
| `tagId` | Long | null | 按标签筛选（可选） |
| `status` | Integer | null | 按状态筛选（可选） |
| `keyword` | String | null | 关键词搜索（可选） |

请求参数封装在 `ArticleQueryDTO` 中：

```java
@Data
public class ArticleQueryDTO {
    private Integer page = 1;      // 默认第一页
    private Integer size = 10;     // 默认每页 10 条
    private Long categoryId;       // 可选筛选条件
    private Long tagId;
    private Integer status;
    private String keyword;
}
```

### 6.2 响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [                   // 当前页数据
      {
        "id": 10,
        "title": "Spring Boot 入门教程",
        "slug": "spring-boot-tutorial",
        "summary": "从零开始学习...",
        "categoryName": "后端开发",
        "authorName": "xunrana",
        "tags": [
          {"id": 1, "name": "Java"},
          {"id": 3, "name": "Spring"}
        ],
        "viewCount": 156,
        "createdAt": "2024-03-20T10:00:00",
        "publishedAt": "2024-03-20T10:30:00"
      }
      // ...更多文章
    ],
    "total": 25,                   // 总记录数
    "page": 1,                     // 当前页码
    "size": 10                     // 每页条数
  }
}
```

前端分页组件使用：

```javascript
// 计算总页数
const totalPages = Math.ceil(data.total / data.size);  // 25 / 10 = 3 页

// Element Plus 分页组件直接绑定
<el-pagination :total="data.total" :page-size="data.size" :current-page="data.page" />
```

---

## 7. Swagger/OpenAPI 文档

### 7.1 什么是 Swagger/OpenAPI？

- **OpenAPI**：一个 API 描述规范（JSON/YAML 格式），定义了如何描述 RESTful API
- **Swagger**：一套基于 OpenAPI 规范的工具集
- **Swagger UI**：将 OpenAPI 规范渲染为可交互的网页文档
- **SpringDoc**：Spring Boot 3 下的 OpenAPI 实现（替代已停更的 Springfox）

### 7.2 SpringDoc 自动文档生成原理

```
Spring Boot 启动
    │
    ▼
SpringDoc 扫描所有 @RestController
    │
    ▼
分析 @GetMapping/@PostMapping 等注解 → 提取 URL、HTTP 方法
分析 @RequestBody/@PathVariable 等 → 提取请求参数
分析方法返回类型 → 提取响应结构
分析 @Valid + 校验注解 → 提取参数约束
    │
    ▼
生成 OpenAPI 3.0 规范（JSON）
    路径：/api/v3/api-docs
    │
    ▼
Swagger UI 渲染为网页
    路径：/api/swagger-ui.html
```

### 7.3 如何访问

项目启动后直接访问：

```
Swagger UI:  http://localhost:8080/api/swagger-ui.html
OpenAPI JSON: http://localhost:8080/api/v3/api-docs
```

在 Swagger UI 中可以：
1. 查看所有 API 的路径、方法、参数
2. 直接在页面上发送测试请求
3. 查看请求和响应的 JSON 格式
4. 添加 Authorization Header（Bearer Token）

### 7.4 安全配置

我们在 SecurityConfig 中将 Swagger 相关路径设为公开：

```java
.requestMatchers(HttpMethod.GET,
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/swagger-ui.html")
.permitAll()
```

> 生产环境建议关闭 Swagger 访问，可以通过配置 `springdoc.api-docs.enabled=false` 禁用。

---

## 8. API 安全设计

### 8.1 接口权限分级

```
┌─────────────────────────────────────────────────────────┐
│                     公开接口 (permitAll)                   │
│                                                          │
│  POST /v1/auth/login          用户登录                    │
│  POST /v1/auth/refresh        刷新 Token                  │
│  GET  /v1/articles/**         文章列表/详情                │
│  GET  /v1/categories/**       分类列表                    │
│  GET  /v1/tags/**             标签列表                    │
│  POST /v1/comments            发表评论                    │
│  GET  /v1/comments/{id}       评论列表                    │
│  GET  /swagger-ui/**          API 文档                    │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                  需认证接口 (authenticated)                │
│                                                          │
│  POST /v1/auth/logout         用户登出                    │
│  GET  /v1/auth/me             获取当前用户信息              │
│                                                          │
├─────────────────────────────────────────────────────────┤
│               管理员接口 (hasRole("ADMIN"))                │
│                                                          │
│  POST   /v1/admin/articles     创建文章                   │
│  PUT    /v1/admin/articles/{id} 更新文章                  │
│  DELETE /v1/admin/articles/{id} 删除文章                  │
│  POST   /v1/admin/categories    创建分类                  │
│  PUT    /v1/admin/categories/{id} 更新分类                │
│  DELETE /v1/admin/categories/{id} 删除分类                │
│  POST   /v1/admin/tags          创建标签                  │
│  PUT    /v1/admin/tags/{id}     更新标签                  │
│  DELETE /v1/admin/tags/{id}     删除标签                  │
│  PUT    /v1/admin/comments/{id}/status 审核评论            │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 8.2 安全最佳实践

| 实践 | 说明 | 本项目实现 |
|------|------|-----------|
| HTTPS | 传输加密 | 生产环境 Nginx 配置 SSL |
| 参数校验 | 防止非法输入 | @Valid + @NotBlank/@Size 等 |
| SQL 注入防护 | 使用参数化查询 | MyBatis #{} 占位符 |
| XSS 防护 | 过滤/转义用户输入 | 前端渲染时转义（后续增加） |
| CSRF 防护 | JWT 方案天然免疫 | 禁用 CSRF（无状态 API） |
| 密码加密 | 不可逆哈希 | BCrypt 加密存储 |
| Token 黑名单 | 登出时使 Token 失效 | Redis 黑名单 |
| 隐藏错误详情 | 不暴露堆栈信息 | GlobalExceptionHandler |
| 限流 | 防止暴力攻击 | 计划使用 Redis 滑动窗口 |

---

## 9. API 测试方法

### 9.1 使用 curl

```bash
# 登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 获取文章列表
curl http://localhost:8080/api/v1/articles?page=1&size=10

# 创建文章（需要 Token）
curl -X POST http://localhost:8080/api/v1/admin/articles \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "title": "测试文章",
    "content": "这是内容",
    "categoryId": 1,
    "status": 0,
    "tagIds": [1]
  }'
```

### 9.2 使用 Swagger UI

1. 访问 `http://localhost:8080/api/swagger-ui.html`
2. 找到 `POST /v1/auth/login` 接口
3. 点击 "Try it out"
4. 输入请求体 `{"username":"admin","password":"admin123"}`
5. 点击 "Execute"
6. 复制返回的 accessToken
7. 点击页面顶部的 "Authorize" 按钮
8. 输入 `Bearer {你的token}`
9. 现在可以测试需要认证的接口了

### 9.3 使用 Postman

1. 创建一个 Environment，添加变量 `baseUrl = http://localhost:8080/api`
2. 创建登录请求，在 Tests 中自动保存 Token：
```javascript
const data = pm.response.json();
pm.environment.set("accessToken", data.data.accessToken);
```
3. 其他请求的 Authorization 设为 `Bearer {{accessToken}}`

---

## 10. 面试常见问题

### Q1: RESTful API 的核心特征是什么？

**答**：RESTful API 的核心特征包括：
1. **资源导向**：URL 标识资源（名词），不使用动词
2. **HTTP 方法语义化**：GET 获取、POST 创建、PUT 更新、DELETE 删除
3. **无状态**：每个请求包含完整的信息，服务器不保存客户端状态
4. **统一接口**：使用标准的 HTTP 协议，响应格式统一
5. **层次化**：客户端不需要知道是直连服务器还是通过代理

### Q2: PUT 和 PATCH 的区别？

**答**：PUT 是**全量更新**——客户端需要发送资源的完整数据，服务器用新数据完全替换旧数据；PATCH 是**部分更新**——客户端只发送需要修改的字段。PUT 是幂等的（多次执行结果相同），PATCH 不保证幂等。在实际项目中，很多团队简化处理，对更新操作统一使用 PUT，忽略未传的字段（如 MyBatis-Plus 的 `updateById` 默认跳过 null 字段）。

### Q3: 如何设计 API 版本控制？

**答**：常见的方案有 URL 路径版本（`/api/v1/`）、Header 版本（`Accept: application/vnd.api.v1+json`）、查询参数版本（`?version=1`）。我们使用 URL 路径版本，因为它最直观、最易调试，也是国内大厂的主流做法。版本升级时，旧版本保持不变，新版本在新路径下发布，给客户端迁移时间。

### Q4: HTTP 状态码 401 和 403 的区别？

**答**：401 Unauthorized 表示**未认证**——客户端未提供有效的身份凭证（没有 Token 或 Token 过期），需要先登录。403 Forbidden 表示**未授权**——客户端已认证（身份已确认），但没有访问该资源的权限（如普通用户访问管理员接口）。

### Q5: 什么是幂等性？哪些 HTTP 方法是幂等的？

**答**：幂等性是指同一操作执行多次，其效果与执行一次相同。GET、PUT、DELETE、HEAD、OPTIONS 是幂等的。POST 不是幂等的——每次调用 POST 创建资源会产生新的资源。幂等性在分布式系统中很重要，比如网络超时导致的请求重试，幂等接口不会产生副作用。

### Q6: 前后端分离项目中，如何处理跨域问题？

**答**：前后端分离时，前端（如 `localhost:3000`）和后端（如 `localhost:8080`）域名或端口不同，浏览器的同源策略会阻止跨域请求。解决方案有：
1. **后端配置 CORS**（我们的做法）：通过 `CorsConfig` 设置允许的源、方法、头信息
2. **Nginx 反向代理**：前后端使用同一个域名，Nginx 根据路径转发
3. **前端代理**：开发时使用 Vite 的 proxy 配置

生产环境推荐使用 Nginx 反向代理，前后端使用同一域名（如 `xunrana.me`），从根本上避免跨域问题。
