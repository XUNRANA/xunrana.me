# 02 - 数据库设计与MyBatis-Plus

> 本文档详细解释数据库表结构设计、索引策略、MyBatis-Plus 的使用方法，以及相关面试高频考点。

---

## 目录

1. [数据库设计原则](#1-数据库设计原则)
2. [ER 关系图](#2-er-关系图)
3. [各表设计详解](#3-各表设计详解)
4. [索引设计策略](#4-索引设计策略)
5. [MyBatis-Plus 基础](#5-mybatis-plus-基础)
6. [自定义 XML Mapper](#6-自定义-xml-mapper)
7. [分页实现](#7-分页实现)
8. [面试常见问题](#8-面试常见问题)

---

## 1. 数据库设计原则

### 1.1 基本规范

| 规范 | 说明 | 本项目示例 |
|------|------|-----------|
| 字符集 | 使用 `utf8mb4`（支持 Emoji） | `DEFAULT CHARSET=utf8mb4` |
| 排序规则 | `utf8mb4_unicode_ci`（不区分大小写） | `COLLATE utf8mb4_unicode_ci` |
| 存储引擎 | InnoDB（支持事务、行级锁、外键） | `ENGINE=InnoDB` |
| 主键 | BIGINT AUTO_INCREMENT | `id BIGINT NOT NULL AUTO_INCREMENT` |
| 时间字段 | DATETIME + 默认值 | `created_at DATETIME DEFAULT CURRENT_TIMESTAMP` |
| 命名规范 | 全小写 + 下划线分隔（snake_case） | `article_tag`、`created_at` |

### 1.2 为什么用 utf8mb4 而不是 utf8？

MySQL 的 `utf8` 编码实际上是 `utf8mb3`，最多只能存储 3 个字节的字符，**不支持 4 字节的 Emoji 表情**（如 😀）。`utf8mb4` 才是真正的 UTF-8 编码，支持完整的 Unicode 字符集。

### 1.3 为什么主键用 BIGINT 而不是 INT？

| 类型 | 字节 | 最大值 | 够用吗？ |
|------|------|--------|---------|
| INT | 4 | 约 21 亿 | 大多数场景够用 |
| BIGINT | 8 | 约 922 京 | 足够应对任何场景 |

虽然个人博客用 INT 绰绰有余，但使用 BIGINT 是**工程实践的好习惯**：
1. **扩展性**：如果项目发展壮大，不需要改表结构
2. **与 Java Long 对应**：Java 的 Long 是 8 字节，与 BIGINT 完美匹配
3. **分布式 ID 兼容**：未来如果使用 Snowflake 雪花算法生成分布式 ID，需要 BIGINT

---

## 2. ER 关系图

```
┌───────────────┐       ┌───────────────┐       ┌───────────────┐
│     user      │       │   category    │       │     tag       │
├───────────────┤       ├───────────────┤       ├───────────────┤
│ PK id         │       │ PK id         │       │ PK id         │
│    username   │       │    name       │       │    name       │
│    password   │       │    slug       │       │    slug       │
│    nickname   │       │    description│       │    created_at │
│    avatar     │       │    sort_order │       └───────┬───────┘
│    email      │       │    created_at │               │
│    role       │       └───────┬───────┘               │
│    status     │               │                       │
│    created_at │               │ 1:N                    │ M:N
│    updated_at │               │                       │
└───────┬───────┘               │                       │
        │                       │               ┌───────┴───────┐
        │ 1:N                   │               │  article_tag  │
        │                       │               ├───────────────┤
        │               ┌──────┴────────┐       │ FK article_id │
        │               │   article     │       │ FK tag_id     │
        │               ├───────────────┤       └───────────────┘
        └──────────────►│ PK id         │◄──────────────┘
                        │    title      │
                        │    slug       │
                        │    summary    │       ┌───────────────┐
                        │    content    │       │   comment     │
                        │    cover_image│       ├───────────────┤
                        │ FK category_id│       │ PK id         │
                        │ FK author_id  │       │ FK article_id │──► article.id
                        │    status     │       │ FK parent_id  │──► comment.id (自引用)
                        │    is_top     │       │    nickname   │
                        │    view_count │       │    email      │
                        │    like_count │       │    content    │
                        │    comment_cnt│       │    status     │
                        │    created_at │       │    ip         │
                        │    updated_at │       │    created_at │
                        │    published_at│      └───────────────┘
                        └───────────────┘

┌───────────────┐
│ operation_log │  （独立表，不与其他表关联）
├───────────────┤
│ PK id         │
│    user_id    │
│    module     │
│    operation  │
│    method     │
│    params     │
│    ip         │
│    duration   │
│    created_at │
└───────────────┘
```

### 表间关系总结

| 关系 | 说明 | 实现方式 |
|------|------|----------|
| user → article | 一个用户可以写多篇文章（1:N） | `article.author_id` 外键 |
| category → article | 一个分类下有多篇文章（1:N） | `article.category_id` 外键 |
| article ↔ tag | 一篇文章有多个标签，一个标签可用于多篇文章（M:N） | `article_tag` 中间表 |
| article → comment | 一篇文章有多条评论（1:N） | `comment.article_id` 外键 |
| comment → comment | 评论可以嵌套回复（自引用 1:N） | `comment.parent_id` 外键 |

---

## 3. 各表设计详解

### 3.1 user 表（用户表）

```sql
CREATE TABLE `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,          -- 登录用户名
    `password`   VARCHAR(255) NOT NULL,          -- BCrypt 加密后的密码
    `nickname`   VARCHAR(50)  NULL,              -- 显示昵称
    `avatar`     VARCHAR(255) NULL,              -- 头像 URL
    `email`      VARCHAR(100) NULL,              -- 邮箱
    `role`       TINYINT      NOT NULL DEFAULT 0, -- 0=普通用户, 1=管理员
    `status`     TINYINT      NOT NULL DEFAULT 1, -- 0=禁用, 1=正常
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),      -- 用户名唯一索引
    UNIQUE KEY `uk_email` (`email`)             -- 邮箱唯一索引
);
```

**设计要点**：
- `password` 用 `VARCHAR(255)` 而不是更短的长度，因为 BCrypt 加密后的字符串长度为 60 字符，预留空间给未来可能更换的加密算法
- `role` 使用 TINYINT 而不是 VARCHAR，节省存储空间且查询更快
- `ON UPDATE CURRENT_TIMESTAMP` 自动更新 `updated_at`，不需要在代码中手动设置

### 3.2 article 表（文章表）

```sql
CREATE TABLE `article` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(200)  NOT NULL,        -- 文章标题
    `slug`          VARCHAR(200)  NOT NULL,        -- URL 友好的标识符
    `summary`       VARCHAR(500)  NULL,            -- 摘要
    `content`       MEDIUMTEXT    NOT NULL,        -- 文章内容（Markdown）
    `cover_image`   VARCHAR(255)  NULL,            -- 封面图 URL
    `category_id`   BIGINT        NULL,            -- 分类 ID（允许为空）
    `author_id`     BIGINT        NOT NULL,        -- 作者 ID
    `status`        TINYINT       NOT NULL DEFAULT 0, -- 0=草稿, 1=已发布
    `is_top`        TINYINT       NOT NULL DEFAULT 0, -- 是否置顶
    `view_count`    INT           NOT NULL DEFAULT 0, -- 浏览量
    `like_count`    INT           NOT NULL DEFAULT 0, -- 点赞数
    `comment_count` INT           NOT NULL DEFAULT 0, -- 评论数
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `published_at`  DATETIME      NULL,            -- 发布时间（草稿时为 NULL）
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE SET NULL,
    FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
);
```

**设计要点**：

- **slug 字段**：用于生成 SEO 友好的 URL（如 `/articles/my-first-blog` 而不是 `/articles/1`）
- **content 用 MEDIUMTEXT**：MEDIUMTEXT 最多存 16MB，普通文章绰绰有余。TEXT 最多 64KB，如果文章包含 Base64 图片可能不够
- **category_id 允许 NULL**：文章可以不属于任何分类，外键设为 `ON DELETE SET NULL`（分类被删时文章不受影响）
- **author_id 不允许 NULL**：每篇文章必须有作者，`ON DELETE CASCADE`（用户被删时关联文章也删除）
- **计数字段冗余存储**：`view_count`、`like_count`、`comment_count` 存在文章表中，避免每次查询都要 COUNT。这是一种**空间换时间**的策略

**VARCHAR 长度选择理由**：

| 字段 | 长度 | 理由 |
|------|------|------|
| title | 200 | 普通标题 20-50 字，预留充足空间 |
| slug | 200 | 与 title 等长，slug 由标题生成 |
| summary | 500 | 摘要约 100-200 字 |
| cover_image | 255 | URL 标准建议不超过 255 字符 |

### 3.3 article_tag 表（文章-标签关联表）

```sql
CREATE TABLE `article_tag` (
    `article_id` BIGINT NOT NULL,
    `tag_id`     BIGINT NOT NULL,
    PRIMARY KEY (`article_id`, `tag_id`),          -- 联合主键
    KEY `idx_tag_id` (`tag_id`),
    FOREIGN KEY (`article_id`) REFERENCES `article` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
);
```

**设计要点**：
- 这是一个**多对多关系的中间表**
- 使用**联合主键** `(article_id, tag_id)` 而不是单独的自增 ID，因为：
  1. 自然唯一：同一篇文章不能重复关联同一个标签
  2. 节省空间：不需要额外的 ID 列
  3. 查询高效：联合主键本身就是索引
- 额外创建了 `idx_tag_id` 索引，因为联合主键 `(article_id, tag_id)` 只能加速以 `article_id` 为前缀的查询，按 `tag_id` 查询需要单独的索引

### 3.4 comment 表（评论表）

```sql
CREATE TABLE `comment` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `article_id` BIGINT       NOT NULL,            -- 所属文章
    `parent_id`  BIGINT       NULL,                -- 父评论（NULL 表示一级评论）
    `nickname`   VARCHAR(50)  NOT NULL,            -- 评论者昵称
    `email`      VARCHAR(100) NULL,                -- 评论者邮箱
    `content`    TEXT         NOT NULL,            -- 评论内容
    `status`     TINYINT      NOT NULL DEFAULT 0,  -- 0=待审核, 1=已通过, 2=已拒绝
    `ip`         VARCHAR(45)  NULL,                -- 评论者 IP
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
);
```

**设计要点**：
- **自引用关系**：`parent_id` 指向自身表的 `id`，实现评论嵌套回复
- **ip 字段用 VARCHAR(45)**：IPv6 地址最长 45 个字符（如 `2001:0db8:0000:0000:0000:0000:0000:0001`）
- **评论审核机制**：`status` 字段控制评论是否可见，防止垃圾评论
- 评论者不需要登录（访客评论），所以使用 `nickname` 而不是关联 user 表

---

## 4. 索引设计策略

### 4.1 什么时候需要加索引？

| 场景 | 是否需要索引 | 原因 |
|------|-------------|------|
| WHERE 条件中的列 | 需要 | 加速查询过滤 |
| JOIN 关联的列 | 需要 | 加速表连接 |
| ORDER BY 排序的列 | 需要 | 避免 filesort |
| 唯一约束的列 | 需要 | UNIQUE KEY 本身就是索引 |
| 数据量很小的表 | 不需要 | 全表扫描可能比走索引还快 |
| 很少用于查询条件的列 | 不需要 | 维护索引有成本 |

### 4.2 本项目的索引设计

```sql
-- article 表的索引
UNIQUE KEY `uk_slug` (`slug`)        -- slug 唯一 + 按 slug 查询文章详情
KEY `idx_category_id` (`category_id`) -- 按分类筛选文章
KEY `idx_author_id` (`author_id`)     -- 按作者筛选文章
KEY `idx_status` (`status`)           -- 按状态筛选（草稿/已发布）
KEY `idx_created_at` (`created_at`)   -- 按创建时间排序

-- user 表的索引
UNIQUE KEY `uk_username` (`username`) -- 登录时按用户名查询
UNIQUE KEY `uk_email` (`email`)       -- 邮箱唯一约束

-- comment 表的索引
KEY `idx_article_id` (`article_id`)   -- 查询某篇文章的所有评论
KEY `idx_parent_id` (`parent_id`)     -- 查询某条评论的所有回复
KEY `idx_status` (`status`)           -- 按审核状态筛选

-- article_tag 表
PRIMARY KEY (`article_id`, `tag_id`)  -- 联合主键 = 联合索引
KEY `idx_tag_id` (`tag_id`)           -- 按标签查文章
```

### 4.3 索引类型

| 类型 | 说明 | 示例 |
|------|------|------|
| **B+Tree 索引** | 默认索引类型，适合范围查询和排序 | `KEY idx_created_at (created_at)` |
| **唯一索引** | B+Tree + 唯一约束 | `UNIQUE KEY uk_slug (slug)` |
| **联合索引** | 多列组合索引，遵循最左前缀原则 | `PRIMARY KEY (article_id, tag_id)` |
| **全文索引** | 用于文本搜索 | `FULLTEXT(title, content)` |

### 4.4 关于全文搜索

目前文章搜索使用 LIKE 模糊匹配：

```sql
WHERE a.title LIKE CONCAT('%', #{keyword}, '%')
   OR a.summary LIKE CONCAT('%', #{keyword}, '%')
```

**LIKE '%keyword%' 的问题**：
- 前缀带 `%` 无法使用索引，会全表扫描
- 对于小数据量（博客文章通常几百到几千篇）完全够用
- 如果数据量大，可以考虑：

| 方案 | 适用场景 | 优缺点 |
|------|---------|--------|
| MySQL FULLTEXT | 中等数据量 | 配置简单，但中文分词需要 ngram |
| Elasticsearch | 大数据量 | 功能强大，但需要额外维护 ES 集群 |
| Meilisearch | 中小数据量 | 轻量级，搜索体验好 |

---

## 5. MyBatis-Plus 基础

### 5.1 BaseMapper 内置 CRUD

MyBatis-Plus 为每个继承了 `BaseMapper<T>` 的 Mapper 接口自动提供以下方法：

```java
// 我们只需要定义接口，不需要写实现
public interface UserMapper extends BaseMapper<User> {
    // BaseMapper 已经提供了以下方法（不需要手动写 SQL）：
}
```

**常用方法一览**：

```java
// ===== 增 =====
int insert(T entity);                          // 插入一条记录

// ===== 删 =====
int deleteById(Serializable id);               // 按 ID 删除
int deleteBatchIds(Collection<? extends Serializable> idList); // 批量删除
int delete(Wrapper<T> queryWrapper);           // 按条件删除

// ===== 改 =====
int updateById(T entity);                      // 按 ID 更新（null 字段不更新）
int update(T entity, Wrapper<T> updateWrapper); // 按条件更新

// ===== 查 =====
T selectById(Serializable id);                 // 按 ID 查询
List<T> selectBatchIds(Collection<? extends Serializable> idList); // 批量查询
T selectOne(Wrapper<T> queryWrapper);          // 查询一条
List<T> selectList(Wrapper<T> queryWrapper);   // 查询列表
Long selectCount(Wrapper<T> queryWrapper);     // 查询总数
IPage<T> selectPage(IPage<T> page, Wrapper<T> queryWrapper); // 分页查询
```

### 5.2 LambdaQueryWrapper 链式查询

LambdaQueryWrapper 是 MyBatis-Plus 提供的**类型安全**查询构造器：

```java
// 传统字符串方式（容易拼错字段名，没有编译检查）
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.eq("username", "admin");  // "username" 是字符串，拼错了编译不报错

// Lambda 方式（推荐！编译期检查字段名）
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "admin");  // 方法引用，拼错了编译报错
```

**常用条件构造方法**：

```java
LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

// 等值条件
wrapper.eq(Article::getStatus, 1);                 // status = 1

// 模糊查询
wrapper.like(Article::getTitle, "Spring");          // title LIKE '%Spring%'

// 范围查询
wrapper.ge(Article::getCreatedAt, startDate);       // created_at >= startDate
wrapper.le(Article::getCreatedAt, endDate);         // created_at <= endDate
wrapper.between(Article::getViewCount, 100, 1000);  // view_count BETWEEN 100 AND 1000

// 排序
wrapper.orderByDesc(Article::getCreatedAt);         // ORDER BY created_at DESC

// IN 查询
wrapper.in(Article::getCategoryId, Arrays.asList(1L, 2L, 3L)); // category_id IN (1,2,3)

// 条件动态拼接（第一个参数为 true 时才拼接）
wrapper.eq(categoryId != null, Article::getCategoryId, categoryId);
wrapper.eq(status != null, Article::getStatus, status);
wrapper.like(keyword != null, Article::getTitle, keyword);

// 查询
List<Article> articles = articleMapper.selectList(wrapper);
```

**实际使用示例（UserDetailsServiceImpl）**：

```java
// 从我们项目中的真实代码：按用户名查询用户
User user = userMapper.selectOne(
    new LambdaQueryWrapper<User>()
        .eq(User::getUsername, username)
);
```

### 5.3 Entity 注解详解

```java
@Data                              // Lombok: Getter + Setter + toString + equals + hashCode
@NoArgsConstructor                 // Lombok: 无参构造函数
@AllArgsConstructor                // Lombok: 全参构造函数
@TableName("article")              // MyBatis-Plus: 映射到 article 表
public class Article {

    @TableId(type = IdType.AUTO)   // 主键，自增策略
    private Long id;

    private String title;          // 字段名与列名相同时，不需要注解

    @TableField("cover_image")     // 字段名与列名不同时，显式映射
    private String coverImage;     // Java 驼峰 → MySQL 下划线

    @TableField("category_id")
    private Long categoryId;

    @TableField(exist = false)     // 标记为非数据库字段（不会参与 SQL）
    private List<Tag> tags;        // 通过关联查询填充，不存在于 article 表中

    @TableField(exist = false)
    private String categoryName;   // 分类名称，来自 JOIN 查询
}
```

**注意**：虽然我们配置了 `map-underscore-to-camel-case: true`（下划线自动转驼峰），但为了代码清晰，我们仍然显式标注了 `@TableField`。

### 5.4 逻辑删除

在 `application-dev.yml` 中配置了逻辑删除：

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted        # 逻辑删除字段
      logic-delete-value: 1              # 删除标记值
      logic-not-delete-value: 0          # 未删除标记值
```

配置后，MyBatis-Plus 会自动处理：
- `deleteById()` → 执行 `UPDATE SET deleted=1` 而不是 `DELETE`
- `selectList()` → 自动追加 `WHERE deleted=0`

> 注：目前我们的表结构中暂未添加 `deleted` 字段，这是为后续扩展预留的配置。

---

## 6. 自定义 XML Mapper

### 6.1 什么时候用 XML，什么时候用 BaseMapper？

| 场景 | 推荐方式 | 理由 |
|------|----------|------|
| 简单 CRUD | BaseMapper 内置方法 | 零代码，效率高 |
| 单表条件查询 | LambdaQueryWrapper | 类型安全，动态条件 |
| 多表 JOIN 查询 | XML Mapper | SQL 复杂，用 XML 更直观 |
| 嵌套子查询 | XML Mapper | 复杂 SQL 逻辑 |
| 自定义 ResultMap | XML Mapper | 一对多、多对多结果映射 |

### 6.2 ArticleMapper.xml 详解

以文章分页查询为例，这是一个典型的多表关联 + 动态条件 + 嵌套查询：

```xml
<!-- ResultMap：定义查询结果如何映射到 Java 对象 -->
<resultMap id="articleVOResultMap" type="me.xunrana.blog.model.vo.ArticleVO">
    <!-- id 标签用于主键字段，result 标签用于普通字段 -->
    <id column="id" property="id"/>
    <result column="title" property="title"/>
    <result column="slug" property="slug"/>
    <result column="cover_image" property="coverImage"/>
    <result column="category_name" property="categoryName"/>
    <result column="author_name" property="authorName"/>
    <!-- ... 其他字段 ... -->

    <!-- collection：一对多关联（一篇文章有多个标签） -->
    <!-- column="id" 表示将当前行的 id 作为参数传给 select 指定的查询 -->
    <!-- select 指定嵌套查询的 statement ID -->
    <collection property="tags"
                ofType="me.xunrana.blog.model.vo.TagVO"
                column="id"
                select="selectTagsByArticleId"/>
</resultMap>
```

**ResultMap 的工作流程**：

```
1. 执行主查询 selectArticlePage
   │  返回 N 条文章记录
   │
   ▼
2. 对每条记录，取出 id 值
   │
   ▼
3. 将 id 作为参数，执行 selectTagsByArticleId 查询
   │  返回该文章的所有标签
   │
   ▼
4. 将标签列表赋值给 ArticleVO.tags 属性
```

**主查询 SQL（动态条件）**：

```xml
<select id="selectArticlePage" resultMap="articleVOResultMap">
    SELECT a.id, a.title, ..., c.name AS category_name, u.nickname AS author_name
    FROM article a
        LEFT JOIN category c ON a.category_id = c.id    -- 关联分类表
        LEFT JOIN user u ON a.author_id = u.id           -- 关联用户表
    <where>
        <!-- 动态条件：只有参数不为 null 时才拼接 -->
        <if test="query.categoryId != null">
            AND a.category_id = #{query.categoryId}
        </if>
        <if test="query.tagId != null">
            AND a.id IN (
                SELECT at2.article_id FROM article_tag at2
                WHERE at2.tag_id = #{query.tagId}
            )
        </if>
        <if test="query.status != null">
            AND a.status = #{query.status}
        </if>
        <if test="query.keyword != null and query.keyword != ''">
            AND (a.title LIKE CONCAT('%', #{query.keyword}, '%')
                 OR a.summary LIKE CONCAT('%', #{query.keyword}, '%'))
        </if>
    </where>
    ORDER BY a.is_top DESC, a.created_at DESC
</select>
```

**关键知识点**：

1. **`<where>` 标签**：智能处理 WHERE 关键字和多余的 AND/OR
   - 如果所有条件都为 null，不会生成 WHERE 子句
   - 自动去除第一个条件前多余的 AND/OR

2. **`<if test="...">`**：条件判断，test 中使用 OGNL 表达式

3. **`#{}`（参数占位符）vs `${}`（字符串替换）**：
   - `#{}` → 使用 PreparedStatement 的 `?` 占位符，**防止 SQL 注入**
   - `${}` → 直接拼接 SQL，有注入风险，只在必须使用时才用（如动态表名）

4. **LEFT JOIN**：使用左连接而不是内连接，确保即使分类/作者被删除，文章仍能查出来

5. **子查询按标签筛选**：通过 `IN (SELECT ...)` 子查询从中间表 article_tag 中查找

### 6.3 嵌套查询 vs 嵌套结果

MyBatis 处理一对多关联有两种方式：

```
方式一：嵌套查询（我们用的方式）
- 主查询查文章，每条记录再发一次子查询查标签
- 优点：SQL 简单，易理解
- 缺点：N+1 问题（N 篇文章就发 N+1 次查询）

方式二：嵌套结果
- 一次 JOIN 查询把文章和标签全查出来
- 使用 ResultMap 的嵌套映射处理重复数据
- 优点：只有一次查询
- 缺点：SQL 复杂，结果集有数据冗余
```

对于博客项目，文章数量通常不大（每页 10 条），N+1 问题影响不大。如果需要优化，可以使用嵌套结果方式或者在 Service 层手动批量查询标签。

---

## 7. 分页实现

### 7.1 分页插件配置

```java
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页拦截器，指定数据库类型为 MySQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 7.2 分页插件工作原理

```
1. Service 层创建 Page 对象
   Page<ArticleVO> page = new Page<>(pageNum, pageSize);
       │
       ▼
2. 调用 Mapper 的分页方法
   articleMapper.selectArticlePage(page, query);
       │
       ▼
3. PaginationInnerInterceptor 拦截 SQL
   │
   │  原始 SQL:
   │  SELECT a.id, ... FROM article a LEFT JOIN ...
   │
   │  拦截器自动修改为两条 SQL：
   │
   │  SQL 1 (查总数):
   │  SELECT COUNT(*) FROM article a LEFT JOIN ... WHERE ...
   │
   │  SQL 2 (查数据，自动添加 LIMIT):
   │  SELECT a.id, ... FROM article a LEFT JOIN ... WHERE ... LIMIT 0, 10
   │
       ▼
4. 将结果封装到 IPage 对象中
   page.getRecords()   → 当前页数据列表
   page.getTotal()     → 总记录数
   page.getCurrent()   → 当前页码
   page.getSize()      → 每页大小
   page.getPages()     → 总页数
```

### 7.3 分页结果封装

```java
// PageResult：我们自定义的分页响应对象
@Data
public class PageResult<T> implements Serializable {
    private List<T> records;    // 当前页数据
    private long total;         // 总记录数
    private long page;          // 当前页码
    private long size;          // 每页大小

    // 从 MyBatis-Plus 的 IPage 转换
    public static <T> PageResult<T> from(IPage<T> page) {
        return PageResult.<T>builder()
                .records(page.getRecords())
                .total(page.getTotal())
                .page(page.getCurrent())
                .size(page.getSize())
                .build();
    }
}
```

**为什么封装 PageResult 而不是直接返回 IPage？**
- `IPage` 是 MyBatis-Plus 的类，直接返回会让 API 与 ORM 框架耦合
- `PageResult` 只包含前端需要的字段，更简洁
- 如果将来更换 ORM 框架，API 返回格式不需要变

---

## 8. 面试常见问题

### Q1: MySQL 索引的底层数据结构是什么？为什么用 B+ 树？

**答**：MySQL InnoDB 的索引底层使用 **B+ 树**。选择 B+ 树而不是其他数据结构的原因：
- **比二叉树/AVL 树/红黑树好**：B+ 树的扇出（每个节点的子节点数）很大，树的高度很低（通常 3-4 层），减少了磁盘 IO 次数
- **比 B 树好**：B+ 树的数据全部存在叶子节点，叶子节点之间用双向链表连接，非常适合范围查询和全表扫描
- **比 Hash 索引好**：Hash 索引只支持等值查询，不支持范围查询和排序

### Q2: 什么是联合索引？最左前缀原则是什么？

**答**：联合索引是在多个列上创建的索引，如 `INDEX(a, b, c)`。最左前缀原则是指：查询条件必须从联合索引的最左列开始，才能利用到索引。
- `WHERE a=1 AND b=2 AND c=3` → 完整使用索引
- `WHERE a=1 AND b=2` → 使用 a、b 部分索引
- `WHERE a=1` → 使用 a 部分索引
- `WHERE b=2 AND c=3` → 不能使用索引（缺少最左列 a）
- `WHERE a=1 AND c=3` → 只使用 a 部分索引（b 断了）

在本项目中，`article_tag` 表的联合主键 `(article_id, tag_id)` 可以加速按 `article_id` 查询，但按 `tag_id` 查询需要额外索引。

### Q3: MyBatis 中 #{} 和 ${} 的区别？

**答**：
- `#{}` 使用 PreparedStatement 的参数占位符 `?`，值在 JDBC 层进行类型转换和转义，**防止 SQL 注入**。
- `${}` 直接将参数值拼接到 SQL 字符串中，有 **SQL 注入风险**。
- 使用场景：绝大多数情况用 `#{}`，只有动态表名、列名、ORDER BY 字段名等必须用 `${}`，且要确保参数来源可信。

### Q4: MyBatis-Plus 的分页是如何实现的？

**答**：通过 `PaginationInnerInterceptor` 拦截器实现。它会拦截 SQL 执行，先执行一条 `SELECT COUNT(*)` 查询总数，再在原 SQL 后自动追加 `LIMIT offset, size` 获取当前页数据。需要在配置类中注册该拦截器并指定数据库类型（不同数据库的分页语法不同）。

### Q5: 为什么文章表的计数字段（view_count、like_count）要冗余存储？

**答**：这是一种**空间换时间**的反规范化设计。如果不冗余存储，每次查询文章列表时都需要关联评论表执行 `COUNT(*)`，在数据量大时会很慢。冗余存储后直接读取字段值即可。代价是需要在评论增删时维护计数的一致性（通过事务或消息队列）。这是面试中**数据库设计取舍**问题的经典答案。

### Q6: 什么是 N+1 查询问题？如何解决？

**答**：N+1 问题是指：查询 N 条主记录后，对每条记录再发起 1 次子查询加载关联数据，总共执行 N+1 次 SQL。在我们的 ArticleMapper.xml 中，使用嵌套查询加载文章标签就存在 N+1 问题。解决方案：
1. **嵌套结果映射**：一次 JOIN 查询获取所有数据
2. **批量查询**：在 Service 层先查文章 ID 列表，再一次性查出所有相关标签，手动组装
3. **MyBatis 延迟加载**：按需加载，减少不必要的查询
4. **缓存**：对标签等变化不频繁的数据使用缓存

### Q7: 什么情况下索引会失效？

**答**：常见的索引失效场景：
1. 对索引列使用函数：`WHERE YEAR(created_at) = 2024`
2. 隐式类型转换：`WHERE varchar_col = 123`（字符串列传了数字）
3. LIKE 左模糊：`WHERE title LIKE '%keyword'`
4. OR 条件中有非索引列
5. 不满足最左前缀原则
6. MySQL 优化器认为全表扫描更快（数据量小或选择性低）

可以使用 `EXPLAIN` 命令查看 SQL 的执行计划，确认是否使用了索引。
