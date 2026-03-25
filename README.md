# Xunrana Blog - 个人博客系统

> 一个基于 Spring Boot 3.2 + Vue 3 的前后端分离个人博客系统，涵盖企业级开发的核心技术栈。

## 项目简介

本项目是一个完整的个人博客平台，采用前后端分离架构，后端基于 Spring Boot 3.2 构建 RESTful API，前端使用 Vue 3 + TypeScript。项目涵盖了用户认证、文章管理、分类标签、评论系统等核心功能，同时集成了 Spring Security + JWT 安全认证、Redis 缓存、Docker 容器化部署等企业级技术方案。

**在线访问**: [https://xunrana.me](https://xunrana.me)

---

## 系统架构

```
                         ┌─────────────────────────────────────────┐
                         │              Internet                    │
                         └────────────────┬────────────────────────┘
                                          │
                                          ▼
                         ┌─────────────────────────────────────────┐
                         │          Nginx (反向代理)                 │
                         │   - 静态资源托管 (Vue 3 SPA)             │
                         │   - SSL/TLS 证书管理                     │
                         │   - 请求转发 /api → Spring Boot          │
                         │   - Gzip 压缩                           │
                         └──────────┬──────────────┬───────────────┘
                                    │              │
                          静态资源    │              │  /api/*
                          (HTML/JS)  │              │
                                    ▼              ▼
                   ┌────────────────┐   ┌──────────────────────────┐
                   │   Vue 3 SPA    │   │    Spring Boot 3.2       │
                   │   前端应用      │   │    后端 API 服务          │
                   │                │   │                          │
                   │ - TypeScript   │   │ ┌──────────────────────┐ │
                   │ - Element Plus │   │ │  Spring Security 6   │ │
                   │ - Vite 5       │   │ │  + JWT 认证过滤器     │ │
                   │ - Pinia        │   │ └──────────┬───────────┘ │
                   └────────────────┘   │            │             │
                                        │            ▼             │
                                        │ ┌──────────────────────┐ │
                                        │ │    Controller 层      │ │
                                        │ │    (REST API 入口)    │ │
                                        │ └──────────┬───────────┘ │
                                        │            │             │
                                        │            ▼             │
                                        │ ┌──────────────────────┐ │
                                        │ │    Service 层         │ │
                                        │ │    (业务逻辑处理)      │ │
                                        │ └──────────┬───────────┘ │
                                        │            │             │
                                        │            ▼             │
                                        │ ┌──────────────────────┐ │
                                        │ │    Mapper 层          │ │
                                        │ │    (MyBatis-Plus)     │ │
                                        │ └──────────┬───────────┘ │
                                        └────────────┼─────────────┘
                                                     │
                                    ┌────────────────┼────────────────┐
                                    │                │                │
                                    ▼                ▼                ▼
                           ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
                           │  MySQL 8.0   │ │  Redis 7     │ │  文件存储     │
                           │  持久化存储   │ │  缓存/黑名单  │ │  uploads/    │
                           └──────────────┘ └──────────────┘ └──────────────┘
```

---

## 技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **后端框架** | Spring Boot | 3.2.3 | 核心框架，自动配置，内嵌 Tomcat |
| **安全认证** | Spring Security 6 | 6.2.x | 认证授权框架 |
| **令牌方案** | JWT (jjwt) | 0.12.5 | 无状态认证，双 Token 设计 |
| **ORM 框架** | MyBatis-Plus | 3.5.5 | 增强版 MyBatis，内置 CRUD |
| **数据库** | MySQL | 8.0 | 关系型数据库，InnoDB 引擎 |
| **缓存** | Redis | 7.x | Token 黑名单，热点数据缓存 |
| **API 文档** | SpringDoc OpenAPI | 2.3.0 | 自动生成 Swagger UI 文档 |
| **工具库** | Hutool | 5.8.25 | Java 工具类库 |
| **简化代码** | Lombok | - | 注解生成 Getter/Setter/Builder |
| **前端框架** | Vue 3 + TypeScript | 3.x | 组合式 API，类型安全 |
| **前端 UI** | Element Plus | - | Vue 3 组件库 |
| **构建工具** | Vite | 5.x | 下一代前端构建工具 |
| **容器化** | Docker + Compose | - | 一键部署，环境一致性 |
| **反向代理** | Nginx | - | 静态资源托管，请求转发，SSL |
| **CI/CD** | GitHub Actions | - | 自动构建、测试、部署 |

---

## 功能特性

### 已完成

- [x] **用户认证** - JWT 双 Token 认证（AccessToken + RefreshToken）
- [x] **权限控制** - 基于角色的访问控制（RBAC: Admin / User）
- [x] **文章管理** - CRUD、分页查询、关键词搜索、状态管理（草稿/已发布）
- [x] **分类管理** - 文章分类的增删改查，支持排序
- [x] **标签管理** - 文章标签的增删改查，多对多关联
- [x] **评论系统** - 评论发布、嵌套回复、审核管理
- [x] **统一响应** - `Result<T>` 统一返回格式 + 全局异常处理
- [x] **参数校验** - JSR 380 注解校验 + 自定义错误提示
- [x] **API 文档** - SpringDoc OpenAPI 自动生成 Swagger UI
- [x] **跨域支持** - CORS 全局配置
- [x] **数据库设计** - 完整的表结构、索引、外键约束

### 开发中（Phase 2 — 框架已搭建，核心 TODO 待实现）

- [ ] **Redis 缓存** - Cache Aside 模式 + 阅读量 Redis 计数 + 定时同步 MySQL
- [ ] **操作日志** - AOP 切面 + @OpLog 自定义注解记录管理员操作
- [ ] **接口限流** - @RateLimit 注解 + Redis ZSET 滑动窗口算法
- [ ] **文件上传** - 图片安全校验 + UUID 命名 + 按日期分目录

### 规划中

- [ ] **前端页面** - Vue 3 + Element Plus 管理后台 & 博客前台
- [ ] **全文搜索** - MySQL FULLTEXT / Elasticsearch
- [ ] **Docker 部署** - Docker Compose 一键部署
- [ ] **CI/CD** - GitHub Actions 自动化流水线
- [ ] **HTTPS** - Let's Encrypt 免费 SSL 证书
- [ ] **SEO 优化** - SSR / 预渲染、Sitemap

---

## 项目结构

```
xunrana.me/
├── README.md                          # 项目说明文档
├── docs/                              # 开发文档（学习笔记）
│   ├── 01-项目初始化与架构设计.md
│   ├── 02-数据库设计与MyBatis-Plus.md
│   ├── 03-Spring-Security与JWT认证.md
│   ├── 04-统一响应与异常处理.md
│   ├── 05-RESTful-API设计规范.md
│   ├── 06-本地开发环境搭建.md
│   ├── 07-Redis缓存策略与阅读量统计.md
│   ├── 08-AOP操作日志与自定义注解.md
│   ├── 09-接口限流与Redis滑动窗口.md
│   └── 10-文件上传与静态资源服务.md
│
├── backend/                           # 后端 Spring Boot 项目
│   ├── pom.xml                        # Maven 依赖管理
│   └── src/main/
│       ├── java/me/xunrana/blog/
│       │   ├── XunranaBlogApplication.java   # 启动类
│       │   ├── config/                # 配置类
│       │   │   ├── SecurityConfig.java       # Spring Security 配置
│       │   │   ├── RedisConfig.java          # Redis 序列化配置
│       │   │   ├── MyBatisPlusConfig.java    # 分页插件配置
│       │   │   ├── CorsConfig.java           # 跨域配置
│       │   │   └── WebMvcConfig.java         # 静态资源映射（/uploads/）
│       │   ├── security/              # 安全认证模块
│       │   │   ├── JwtTokenProvider.java     # JWT 令牌生成与验证
│       │   │   ├── JwtAuthFilter.java        # JWT 认证过滤器
│       │   │   └── UserDetailsServiceImpl.java # 用户信息加载
│       │   ├── common/                # 公共组件
│       │   │   ├── Result.java               # 统一返回结果
│       │   │   ├── PageResult.java           # 分页返回结果
│       │   │   ├── ErrorCode.java            # 错误码枚举
│       │   │   ├── RedisConstants.java       # Redis Key 常量定义
│       │   │   ├── annotation/        # 自定义注解
│       │   │   │   ├── OpLog.java            # 操作日志注解
│       │   │   │   └── RateLimit.java        # 接口限流注解
│       │   │   └── utils/
│       │   │       └── IpUtils.java          # IP 地址提取工具
│       │   ├── aspect/                # AOP 切面
│       │   │   ├── OperationLogAspect.java   # 操作日志切面 ⭐TODO
│       │   │   └── RateLimitAspect.java      # 接口限流切面 ⭐TODO
│       │   ├── exception/             # 异常处理
│       │   │   ├── BusinessException.java    # 自定义业务异常
│       │   │   ├── RateLimitException.java   # 限流异常（HTTP 429）
│       │   │   └── GlobalExceptionHandler.java # 全局异常处理器
│       │   ├── model/                 # 数据模型
│       │   │   ├── entity/            # 数据库实体（与表一一对应）
│       │   │   ├── dto/               # 请求参数对象
│       │   │   ├── vo/                # 响应视图对象
│       │   │   └── enums/             # 枚举类
│       │   ├── mapper/                # MyBatis Mapper 接口
│       │   └── service/               # 业务逻辑接口
│       │       └── impl/
│       │           ├── ArticleCacheService.java  # Redis 缓存 ⭐TODO
│       │           ├── FileServiceImpl.java      # 文件上传 ⭐TODO
│       │           └── OperationLogServiceImpl.java # 操作日志 ⭐TODO
│       └── resources/
│           ├── application.yml               # 主配置
│           ├── application-dev.yml           # 开发环境配置
│           ├── application-prod.yml          # 生产环境配置
│           └── mapper/                       # MyBatis XML 映射文件
│               ├── ArticleMapper.xml
│               ├── CategoryMapper.xml
│               └── TagMapper.xml
│
├── deploy/                            # 部署相关
│   └── mysql/
│       └── init.sql                   # 数据库初始化脚本
│
└── .gitignore                         # Git 忽略规则
```

---

## 快速开始

### 环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 运行环境 |
| Maven | 3.9+ | 项目构建与依赖管理 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 7.0+ | 缓存与 Token 黑名单 |
| Git | 2.x | 版本控制 |

### 本地运行

```bash
# 1. 克隆项目
git clone https://github.com/你的用户名/xunrana.me.git
cd xunrana.me

# 2. 启动 MySQL 和 Redis（推荐使用 Docker）
docker run -d --name blog-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  mysql:8.0

docker run -d --name blog-redis \
  -p 6379:6379 \
  redis:7-alpine

# 3. 初始化数据库
mysql -h 127.0.0.1 -u root -proot123 < deploy/mysql/init.sql

# 4. 启动后端
cd backend
mvn spring-boot:run

# 5. 访问
#    - API 文档: http://localhost:8080/api/swagger-ui.html
#    - 登录测试: POST http://localhost:8080/api/v1/auth/login
#      Body: {"username": "admin", "password": "admin123"}
```

### Docker 一键部署（规划中）

```bash
# 使用 Docker Compose 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f blog-backend
```

---

## API 文档

启动后端后，访问 Swagger UI 查看完整 API 文档：

**本地开发**: [http://localhost:8080/api/swagger-ui.html](http://localhost:8080/api/swagger-ui.html)

### 核心 API 概览

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/v1/auth/login` | 用户登录 | 公开 |
| POST | `/api/v1/auth/refresh` | 刷新 Token | 公开 |
| POST | `/api/v1/auth/logout` | 用户登出 | 认证 |
| GET | `/api/v1/articles` | 文章分页列表 | 公开 |
| GET | `/api/v1/articles/{slug}` | 文章详情 | 公开 |
| POST | `/api/v1/admin/articles` | 创建文章 | 管理员 |
| PUT | `/api/v1/admin/articles/{id}` | 更新文章 | 管理员 |
| DELETE | `/api/v1/admin/articles/{id}` | 删除文章 | 管理员 |
| GET | `/api/v1/categories` | 分类列表 | 公开 |
| GET | `/api/v1/tags` | 标签列表 | 公开 |
| POST | `/api/v1/comments` | 发表评论 | 公开 |
| GET | `/api/v1/comments/{articleId}` | 文章评论列表 | 公开 |

---

## 部署架构

```
VPS (Ubuntu 24.04, 2.5GB RAM)
├── Docker
│   ├── Nginx        → 反向代理 + 静态资源
│   ├── Spring Boot  → 后端 API (8080)
│   ├── MySQL 8.0    → 数据库 (3306)
│   └── Redis 7      → 缓存 (6379)
└── GitHub Actions   → CI/CD 自动部署
```

**域名**: xunrana.me
**服务器**: RackNerd VPS, Ubuntu 24.04, 2.5GB RAM, 45GB SSD

---

## 开发文档

本项目配套详细的开发文档，记录了每个模块的设计思路和实现细节，适合学习参考：

| 文档 | 内容 |
|------|------|
| [01-项目初始化与架构设计](docs/01-项目初始化与架构设计.md) | 技术选型、分层架构、配置文件详解 |
| [02-数据库设计与MyBatis-Plus](docs/02-数据库设计与MyBatis-Plus.md) | 表结构设计、索引策略、ORM 使用 |
| [03-Spring-Security与JWT认证](docs/03-Spring-Security与JWT认证.md) | JWT 原理、双 Token 设计、过滤器链 |
| [04-统一响应与异常处理](docs/04-统一响应与异常处理.md) | 统一返回格式、全局异常捕获、参数校验 |
| [05-RESTful-API设计规范](docs/05-RESTful-API设计规范.md) | REST 规范、API 版本控制、Swagger |
| [06-本地开发环境搭建](docs/06-本地开发环境搭建.md) | 环境安装、项目运行、常见问题排查 |
| [07-Redis缓存策略与阅读量统计](docs/07-Redis缓存策略与阅读量统计.md) | Cache Aside 模式、缓存穿透/击穿/雪崩、Redis 计数 |
| [08-AOP操作日志与自定义注解](docs/08-AOP操作日志与自定义注解.md) | AOP 原理、@Aspect 切面、反射获取注解 |
| [09-接口限流与Redis滑动窗口](docs/09-接口限流与Redis滑动窗口.md) | 限流算法对比、ZSET 滑动窗口、@Before 通知 |
| [10-文件上传与静态资源服务](docs/10-文件上传与静态资源服务.md) | multipart 原理、安全校验、静态资源映射 |

---

## License

MIT License - 详见 [LICENSE](LICENSE) 文件

---

> 如果觉得项目有帮助，欢迎 Star 支持！
