# 08 - AOP 操作日志与自定义注解

> 本文档讲解 Spring AOP 的核心概念、自定义注解的原理与定义、@Around 环绕通知的实现、反射获取方法信息、以及如何通过 AOP 实现无侵入的操作日志记录。

---

## 目录

1. [什么是 AOP](#1-什么是-aop)
2. [AOP 核心概念](#2-aop-核心概念)
3. [Spring AOP 原理](#3-spring-aop-原理)
4. [自定义注解基础](#4-自定义注解基础)
5. [切入点表达式 (对应 TODO 1)](#5-切入点表达式-对应-todo-1)
6. [@Around 环绕通知详解 (对应 TODO 2)](#6-around-环绕通知详解-对应-todo-2)
7. [反射获取方法信息](#7-反射获取方法信息)
8. [动手实现 (对应 TODO 3, TODO 4, 控制器修改)](#8-动手实现-对应-todo-3-todo-4-控制器修改)
9. [进阶: 异步日志保存](#9-进阶-异步日志保存)
10. [测试验证](#10-测试验证)
11. [面试常见问题](#11-面试常见问题)

---

## 1. 什么是 AOP

### 1.1 完整名称

AOP 的全称是 **Aspect-Oriented Programming**（面向切面编程）。它是一种编程范式，用于将**横切关注点**（cross-cutting concerns）从业务逻辑中分离出来。

### 1.2 核心思想

在一个后端系统中，有些功能不属于任何一个具体的业务模块，但几乎每个模块都需要——比如日志记录、权限校验、事务管理、性能监控。这些功能被称为**横切关注点**，因为它们"横切"了多个业务模块。

**没有 AOP 的世界**：

```java
// ArticleServiceImpl.java
public void createArticle(ArticleDTO dto, Long authorId) {
    long start = System.currentTimeMillis();                    // 重复代码: 计时
    log.info("开始创建文章, 操作人: {}", authorId);               // 重复代码: 日志
    checkPermission(authorId, "ADMIN");                         // 重复代码: 权限检查

    // ↓↓↓ 真正的业务逻辑只有下面这几行 ↓↓↓
    Article article = new Article();
    BeanUtil.copyProperties(dto, article);
    article.setSlug(generateSlug(dto.getTitle()));
    articleMapper.insert(article);
    // ↑↑↑ 业务逻辑结束 ↑↑↑

    long duration = System.currentTimeMillis() - start;         // 重复代码: 计时
    log.info("文章创建完成, 耗时: {}ms", duration);              // 重复代码: 日志
    saveOperationLog("文章管理", "创建", authorId, duration);    // 重复代码: 操作日志
}

// CategoryServiceImpl.java
public void createCategory(CategoryDTO dto) {
    long start = System.currentTimeMillis();                    // 又是一遍...
    log.info("开始创建分类");                                    // 又是一遍...
    checkPermission(getCurrentUserId(), "ADMIN");               // 又是一遍...

    // 业务逻辑...
    categoryMapper.insert(category);

    long duration = System.currentTimeMillis() - start;         // 又是一遍...
    log.info("分类创建完成, 耗时: {}ms", duration);              // 又是一遍...
    saveOperationLog("分类管理", "创建", userId, duration);      // 又是一遍...
}

// TagServiceImpl.java —— 同样的重复代码...
// CommentServiceImpl.java —— 同样的重复代码...
```

**问题清单**：
1. **代码重复**：每个 Service 方法都要写日志、计时、权限检查，违反 DRY 原则
2. **耦合严重**：业务逻辑和日志逻辑混在一起，改日志格式要改 N 个文件
3. **容易遗漏**：新增一个方法时忘记加日志，导致审计缺失
4. **维护困难**：如果要调整操作日志的字段（比如新增 IP 记录），需要修改所有 Service

### 1.3 有了 AOP 的世界

```java
// ArticleServiceImpl.java —— 只有纯粹的业务逻辑
@OperationLog(module = "文章管理", operation = "创建")  // 一个注解搞定！
public void createArticle(ArticleDTO dto, Long authorId) {
    Article article = new Article();
    BeanUtil.copyProperties(dto, article);
    article.setSlug(generateSlug(dto.getTitle()));
    articleMapper.insert(article);
}

// CategoryServiceImpl.java
@OperationLog(module = "分类管理", operation = "创建")  // 一个注解搞定！
public void createCategory(CategoryDTO dto) {
    categoryMapper.insert(category);
}
```

日志记录的逻辑被统一放在一个**切面类**中，通过注解声明式地应用到需要的方法上。业务代码完全不知道日志的存在——这就是 AOP 的威力。

### 1.4 类比理解

把 AOP 想象成机场的**安检机**：

```
乘客（方法调用）  →  安检机（AOP 切面）  →  登机口（业务逻辑）
                        │
                    检查行李（日志记录）
                    核实身份（权限校验）
                    记录时间（性能监控）
```

- 每个乘客（方法调用）都会经过安检机（AOP 切面）
- 安检机不影响乘客的行程（业务逻辑完全不受影响）
- 安检机可以独立升级（修改日志逻辑不需要改业务代码）
- 安检机是统一入口（所有标注了 `@OperationLog` 的方法都会被拦截）

---

## 2. AOP 核心概念

AOP 有一套自己的术语体系，初学时容易混淆。用一个表格梳理清楚：

### 2.1 概念速查表

| 术语 | 英文 | 定义 | 类比 | 本项目中的对应 |
|------|------|------|------|---------------|
| **切面** | Aspect | 包含通知和切入点的类，封装了横切关注点的完整逻辑 | 安检机 | `OperationLogAspect` 类 |
| **切入点** | Pointcut | 定义哪些方法会被拦截的规则（匹配条件） | 安检机拦截规则（哪些乘客需要安检） | `@annotation(OperationLog)` |
| **通知** | Advice | 拦截后要执行的具体逻辑（做什么事） | 安检时具体做什么（检查行李、核实身份） | `@Around` 方法中的日志记录逻辑 |
| **连接点** | JoinPoint | 程序执行中可以被拦截的点（通常是方法执行） | 每一个乘客 | 每一个被调用的方法 |
| **织入** | Weaving | 将切面应用到目标对象的过程（创建代理） | 安装安检机的过程 | Spring 容器启动时自动完成 |

### 2.2 五种通知类型

| 通知类型 | 注解 | 执行时机 | 适用场景 |
|---------|------|---------|---------|
| **前置通知** | `@Before` | 方法执行**之前** | 权限校验、参数预处理 |
| **后置通知** | `@After` | 方法执行**之后**（无论成功或异常） | 资源清理 |
| **返回通知** | `@AfterReturning` | 方法**正常返回**之后 | 记录返回值、结果缓存 |
| **异常通知** | `@AfterThrowing` | 方法**抛出异常**之后 | 异常记录、告警 |
| **环绕通知** | `@Around` | 包围方法执行，可以控制是否执行、何时执行 | **操作日志（本项目使用）**、性能监控、事务管理 |

**为什么我们选择 @Around？**

操作日志需要：
1. 方法执行**前**：记录开始时间、获取方法信息
2. 方法执行**后**：计算耗时、保存日志
3. 方法**异常时**：记录异常信息

只有 `@Around` 能同时覆盖这三个阶段。如果用 `@Before` + `@After` + `@AfterThrowing` 组合，代码会被拆成三个方法，共享数据（如开始时间）很不方便。

### 2.3 概念之间的关系图

```
┌─────────────────────────────────────────────────┐
│                  切面 (Aspect)                    │
│  @Aspect                                         │
│  @Component                                      │
│  public class OperationLogAspect {               │
│                                                   │
│    ┌─────────────────────────────────┐            │
│    │      切入点 (Pointcut)           │            │
│    │  @annotation(OperationLog)      │            │
│    │  "拦截所有标注了 @OperationLog   │            │
│    │   注解的方法"                    │            │
│    └──────────────┬──────────────────┘            │
│                   │                               │
│    ┌──────────────▼──────────────────┐            │
│    │      通知 (Advice)               │            │
│    │  @Around                         │            │
│    │  记录开始时间                      │            │
│    │  → 执行目标方法 ← 连接点(JoinPoint)│            │
│    │  记录结束时间                      │            │
│    │  保存操作日志                      │            │
│    └─────────────────────────────────┘            │
│                                                   │
└─────────────────────────────────────────────────┘
         │
         │ 织入 (Weaving) —— Spring 容器启动时自动完成
         ▼
┌─────────────────────────────────────────────────┐
│                目标对象 (Target)                    │
│  AdminArticleController                           │
│  ├── createArticle()   ← 被拦截（有 @OperationLog）│
│  ├── updateArticle()   ← 被拦截（有 @OperationLog）│
│  ├── deleteArticle()   ← 被拦截（有 @OperationLog）│
│  └── getArticlePage()  ← 不拦截（没有 @OperationLog）│
└─────────────────────────────────────────────────┘
```

---

## 3. Spring AOP 原理

### 3.1 实现方式：动态代理

Spring AOP 基于**动态代理**实现，而不是编译期的字节码织入（那是 AspectJ 的做法）。Spring 在运行时为目标对象创建一个代理对象，客户端调用的实际上是代理对象的方法，代理对象在方法执行前后插入切面逻辑。

### 3.2 两种代理模式

**JDK 动态代理**：

```
条件：目标类实现了接口
原理：java.lang.reflect.Proxy.newProxyInstance()
机制：生成一个实现同一接口的代理类
```

```java
// 接口
public interface ArticleService {
    void createArticle(ArticleDTO dto, Long authorId);
}

// 实现类
public class ArticleServiceImpl implements ArticleService {
    public void createArticle(ArticleDTO dto, Long authorId) { ... }
}

// JDK 动态代理生成（伪代码）
public class $Proxy0 implements ArticleService {
    private ArticleServiceImpl target;  // 持有目标对象引用

    public void createArticle(ArticleDTO dto, Long authorId) {
        // === 前置逻辑（AOP Before） ===
        log.info("方法调用前...");

        target.createArticle(dto, authorId);  // 调用真正的目标方法

        // === 后置逻辑（AOP After） ===
        log.info("方法调用后...");
    }
}
```

**CGLIB 代理**：

```
条件：目标类没有实现接口（或者 Spring Boot 默认配置）
原理：在运行时生成目标类的子类
机制：通过继承和方法重写实现拦截
```

```java
// 目标类（没有接口）
public class ArticleServiceImpl {
    public void createArticle(ArticleDTO dto, Long authorId) { ... }
}

// CGLIB 生成的代理类（伪代码）
public class ArticleServiceImpl$$EnhancerBySpringCGLIB$$abc extends ArticleServiceImpl {

    @Override
    public void createArticle(ArticleDTO dto, Long authorId) {
        // === 前置逻辑 ===
        log.info("方法调用前...");

        super.createArticle(dto, authorId);  // 调用父类（目标类）的方法

        // === 后置逻辑 ===
        log.info("方法调用后...");
    }
}
```

### 3.3 Spring Boot 默认使用 CGLIB

从 Spring Boot 2.0 开始，默认配置 `spring.aop.proxy-target-class=true`，即始终使用 CGLIB 代理，即使目标类实现了接口。原因是 CGLIB 代理更通用、更不容易出问题（不依赖接口）。

### 3.4 代理调用流程

```
Client 调用 controller.createArticle()
    │
    ▼
Spring IoC 容器注入的是代理对象（不是原始对象）
    │
    ▼
代理对象拦截调用
    │
    ▼
┌───────────────────────────────────────┐
│         @Around 通知开始执行            │
│                                        │
│  ① 记录开始时间                         │
│  ② 获取方法签名、注解信息                │
│  ③ joinPoint.proceed()                 │
│     │                                  │
│     ▼                                  │
│  ┌───────────────────────────┐         │
│  │    目标方法执行             │         │
│  │    createArticle()        │         │
│  │    （真正的业务逻辑）       │         │
│  └─────────┬─────────────────┘         │
│            │                           │
│  ④ 计算耗时                             │
│  ⑤ 构建 OperationLog 实体              │
│  ⑥ 保存日志到数据库                     │
│                                        │
└───────────────────────────────────────┘
    │
    ▼
返回结果给 Client
```

### 3.5 自调用问题

**这是 Spring AOP 最常见的"坑"**：在同一个类内部调用自己的方法时，AOP 不会生效。

```java
@Service
public class ArticleServiceImpl implements ArticleService {

    @OperationLog(module = "文章管理", operation = "创建")
    public void createArticle(ArticleDTO dto, Long authorId) {
        // 业务逻辑...
        // AOP 正常工作 ✓
    }

    public void batchCreate(List<ArticleDTO> dtos, Long authorId) {
        for (ArticleDTO dto : dtos) {
            this.createArticle(dto, authorId);  // 自调用！AOP 不生效 ✗
        }
    }
}
```

**为什么？**

因为 `this.createArticle()` 直接调用的是目标对象的方法，不经过代理对象。只有通过 Spring 容器获取的 Bean（即代理对象）调用方法时，AOP 才能拦截。

```
外部调用: proxy.createArticle()  → 经过代理 → AOP 生效 ✓
自调用:   this.createArticle()   → 绕过代理 → AOP 不生效 ✗
```

**解决方案**：

方案一：注入自身代理（推荐）
```java
@Service
@Lazy  // 避免循环依赖
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleService self;  // 注入代理对象

    public void batchCreate(List<ArticleDTO> dtos, Long authorId) {
        for (ArticleDTO dto : dtos) {
            self.createArticle(dto, authorId);  // 通过代理调用 → AOP 生效 ✓
        }
    }
}
```

方案二：从 ApplicationContext 获取
```java
@Autowired
private ApplicationContext applicationContext;

public void batchCreate(List<ArticleDTO> dtos, Long authorId) {
    ArticleService proxy = applicationContext.getBean(ArticleService.class);
    for (ArticleDTO dto : dtos) {
        proxy.createArticle(dto, authorId);  // AOP 生效 ✓
    }
}
```

本项目中 `@OperationLog` 注解加在 Controller 层方法上，Controller 方法由 Spring MVC 调用（外部调用），不存在自调用问题。

---

## 4. 自定义注解基础

### 4.1 Java 注解语法

Java 注解使用 `@interface` 关键字定义（注意不是 `interface`）：

```java
public @interface 注解名 {
    // 注解的属性（方法声明的形式）
    String value() default "";  // 带默认值
    int count();                // 无默认值，使用时必须指定
}
```

### 4.2 元注解详解

Java 提供了 4 个元注解（修饰注解的注解）来控制自定义注解的行为：

**@Target：注解可以用在哪里**

```java
@Target(ElementType.METHOD)           // 只能用在方法上
@Target(ElementType.TYPE)             // 只能用在类/接口上
@Target(ElementType.FIELD)            // 只能用在字段上
@Target(ElementType.PARAMETER)        // 只能用在参数上
@Target({ElementType.METHOD, ElementType.TYPE})  // 方法和类上都可以
```

**@Retention：注解保留到什么阶段**

```java
@Retention(RetentionPolicy.SOURCE)    // 只在源码中存在，编译后丢弃（如 @Override）
@Retention(RetentionPolicy.CLASS)     // 保留到字节码文件，但运行时无法通过反射获取
@Retention(RetentionPolicy.RUNTIME)   // 保留到运行时，可以通过反射获取 ← AOP 必须用这个！
```

**为什么 AOP 必须用 RUNTIME？**

因为 Spring AOP 是在**运行时**通过反射读取注解信息来决定是否拦截方法的。如果注解的 Retention 是 SOURCE 或 CLASS，运行时注解信息已经不存在了，AOP 就无法识别。

**@Documented：是否包含在 Javadoc 中**

```java
@Documented  // 使用了此注解的元素在生成 Javadoc 时会显示注解信息
```

这是一个标记注解，对运行时行为无影响，但对文档生成有帮助。

### 4.3 我们的 @OperationLog 注解定义

```java
package me.xunrana.blog.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * 标注在 Controller 方法上，AOP 切面会自动记录操作日志。
 * 无需修改业务代码，实现无侵入的日志记录。
 *
 * 使用示例：
 * <pre>
 *     @OperationLog(module = "文章管理", operation = "创建")
 *     public Result<Void> createArticle(@Valid @RequestBody ArticleDTO dto) {
 *         // 业务逻辑
 *     }
 * </pre>
 */
@Target(ElementType.METHOD)           // 只能用在方法上
@Retention(RetentionPolicy.RUNTIME)   // 运行时保留（AOP 反射读取需要）
@Documented                           // 包含在 Javadoc 中
public @interface OperationLog {

    /**
     * 操作模块
     * 例如：文章管理、分类管理、标签管理
     */
    String module() default "";

    /**
     * 操作类型
     * 例如：创建、更新、删除
     */
    String operation() default "";
}
```

**代码解读**：

`@Target(ElementType.METHOD)`：限制这个注解只能加在方法上。如果有人尝试加在类或字段上，编译器会报错。这是一种安全限制，防止误用。

`@Retention(RetentionPolicy.RUNTIME)`：注解信息保留到运行时。这是 AOP 能够通过 `method.getAnnotation(OperationLog.class)` 读取注解属性的前提。

`String module() default ""`：定义了一个名为 `module` 的属性，类型是 String，默认值是空字符串。使用时通过 `@OperationLog(module = "文章管理")` 传值。

**为什么注解属性用方法声明的形式？**

这是 Java 语言规范的设计。注解属性本质上是抽象方法，在使用注解时"传参"实际上是在实现这些方法。编译器会自动生成实现类。

---

## 5. 切入点表达式 (对应 TODO 1)

### 5.1 什么是切入点表达式

切入点表达式定义了"**哪些方法需要被 AOP 拦截**"。Spring AOP 支持多种表达式类型：

### 5.2 三种主要类型

**execution()：按方法签名匹配**

```java
// 匹配 me.xunrana.blog.service 包下所有类的所有方法
@Pointcut("execution(* me.xunrana.blog.service.*.*(..))")

// 语法：execution(返回类型 包名.类名.方法名(参数类型))
// *  表示任意返回类型
// *  表示任意类名
// *  表示任意方法名
// .. 表示任意参数

// 更具体的例子：
@Pointcut("execution(void me.xunrana.blog.service.ArticleService.createArticle(..))")
// 只匹配 ArticleService 的 createArticle 方法
```

**@annotation()：按方法上的注解匹配** ← 我们使用这种

```java
// 匹配所有标注了 @OperationLog 注解的方法
@Pointcut("@annotation(me.xunrana.blog.common.annotation.OperationLog)")
```

**within()：按类匹配**

```java
// 匹配 controller.admin 包下所有类的所有方法
@Pointcut("within(me.xunrana.blog.controller.admin.*)")
```

### 5.3 为什么选择 @annotation？

| 对比项 | execution() | @annotation() |
|--------|------------|---------------|
| 匹配方式 | 按方法签名（包名、类名、方法名） | 按方法上是否有特定注解 |
| 精确度 | 要么太宽（整个包）要么太具体（单个方法） | 精确到每个方法，由开发者决定 |
| 灵活性 | 改方法名或包路径就失效 | 注解跟着方法走，不怕重构 |
| 可读性 | 调用者看不出方法是否被拦截 | 方法上有 `@OperationLog` 注解，一目了然 |
| 侵入性 | 完全透明（方法不知道自己被拦截） | 需要加注解（极低侵入，一行代码） |

选择 `@annotation` 的核心理由：**显式优于隐式**。当你在一个方法上看到 `@OperationLog` 注解，立刻就知道这个方法的调用会被记录到操作日志。而用 `execution()` 匹配整个包的话，开发者可能不知道自己的方法被拦截了。

### 5.4 代码实现

```java
package me.xunrana.blog.aspect;

import me.xunrana.blog.common.annotation.OperationLog;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect      // 声明这是一个切面类
@Component   // 注册到 Spring 容器（AOP 切面必须是 Bean 才能生效）
public class OperationLogAspect {

    // ===== TODO 1: 定义切入点 =====

    /**
     * 切入点：匹配所有标注了 @OperationLog 注解的方法
     *
     * 注意：这里写的是注解的全限定类名（包名 + 类名）
     * 如果写错了包路径，AOP 不会报错但也不会匹配任何方法
     */
    @Pointcut("@annotation(me.xunrana.blog.common.annotation.OperationLog)")
    public void operationLogPointcut() {
        // 切入点方法体为空 —— 它只是一个标记，供通知方法引用
        // 方法名 operationLogPointcut 会在 @Around 注解中被引用
    }
}
```

**关键细节**：

1. `@Aspect` 注解来自 AspectJ，但 Spring AOP 只是借用了 AspectJ 的注解语法，底层实现还是动态代理
2. `@Component` 是必须的——切面类必须被 Spring 管理，否则 AOP 不会扫描到它
3. 切入点方法的方法体必须为空，它只是一个"名称引用"，在 `@Around("operationLogPointcut()")` 中使用
4. 注解的全限定类名必须正确，建议从 IDE 中复制，手写容易出错

---

## 6. @Around 环绕通知详解 (对应 TODO 2)

### 6.1 @Around 执行流程

```
@Around 方法开始
│
├── ① 记录开始时间 startTime
├── ② 获取方法签名 MethodSignature
├── ③ 获取 @OperationLog 注解的属性值
│
├── try {
│       ④ result = joinPoint.proceed()   ← 执行目标方法
│       ⑤ 目标方法正常返回
│   } catch (Throwable e) {
│       ⑥ 记录异常信息
│       ⑦ throw e   ← 重新抛出，不吞异常！
│   } finally {
│       ⑧ 计算耗时 duration = now - startTime
│       ⑨ 构建 OperationLog 实体
│       ⑩ 保存日志到数据库
│   }
│
└── return result  ← 将目标方法的返回值传递回去
```

### 6.2 ProceedingJoinPoint 详解

`ProceedingJoinPoint` 是 `@Around` 通知独有的参数（其他通知类型使用 `JoinPoint`）。它代表了被拦截的方法调用，核心方法是 `proceed()`：

```java
// ProceedingJoinPoint 的关键 API
Object proceed();              // 执行目标方法，返回目标方法的返回值
Object proceed(Object[] args); // 执行目标方法（可以替换参数）
Object getTarget();            // 获取目标对象（被代理的原始对象）
Signature getSignature();      // 获取方法签名
Object[] getArgs();            // 获取方法参数值数组
```

**proceed() 的重要性**：

如果 `@Around` 方法中不调用 `proceed()`，目标方法就不会被执行。这既是 `@Around` 的强大之处（可以决定是否执行目标方法），也是需要特别注意的地方（忘记调用就等于"拦截并丢弃"了请求）。

### 6.3 完整代码实现

```java
package me.xunrana.blog.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.annotation.OperationLog;
import me.xunrana.blog.mapper.OperationLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;  // Jackson，Spring Boot 自动注入

    /** 参数 JSON 最大长度，超过截断 */
    private static final int MAX_PARAMS_LENGTH = 2000;

    @Pointcut("@annotation(me.xunrana.blog.common.annotation.OperationLog)")
    public void operationLogPointcut() {
    }

    // ===== TODO 2: 环绕通知实现 =====

    /**
     * 环绕通知：在目标方法执行前后记录操作日志
     *
     * @param joinPoint 连接点，包含目标方法的所有信息
     * @return 目标方法的返回值（原样传递）
     * @throws Throwable 目标方法抛出的异常（原样传递）
     */
    @Around("operationLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // ========== ① 记录开始时间 ==========
        long startTime = System.currentTimeMillis();

        // ========== ② 获取方法签名和注解信息 ==========
        // MethodSignature 是 Signature 的子接口，提供了更丰富的方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取被拦截的 Method 对象
        Method method = signature.getMethod();

        // ========== ③ 读取 @OperationLog 注解的属性值 ==========
        // 通过反射获取方法上的 @OperationLog 注解实例
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        // annotation.module()    → "文章管理"
        // annotation.operation() → "创建"

        // ========== ④ 执行目标方法 ==========
        Object result = null;
        Throwable throwable = null;
        try {
            // proceed() 是执行目标方法的关键调用
            // 如果不调用 proceed()，目标方法就不会被执行！
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // ========== ⑤ 捕获异常（但不吞掉） ==========
            throwable = e;
            throw e;  // 重新抛出异常，让全局异常处理器处理
        } finally {
            // ========== ⑥ 无论成功或失败，都保存操作日志 ==========
            try {
                saveLog(joinPoint, annotation, startTime, throwable);
            } catch (Exception e) {
                // 保存日志失败不影响主流程
                log.error("保存操作日志失败", e);
            }
        }

        return result;
    }

    /**
     * 构建并保存操作日志
     */
    private void saveLog(ProceedingJoinPoint joinPoint, OperationLog annotation,
                         long startTime, Throwable throwable) {
        // 计算方法执行耗时
        long duration = System.currentTimeMillis() - startTime;

        // 获取方法签名：类名.方法名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName()
                + "." + signature.getName();
        // 例如：AdminArticleController.createArticle

        // 获取当前登录用户 ID
        Long userId = getCurrentUserId();

        // 获取请求 IP
        String ip = getRequestIp();

        // 序列化方法参数
        String params = serializeParams(joinPoint.getArgs());

        // 构建日志实体
        me.xunrana.blog.model.entity.OperationLog logEntity =
                new me.xunrana.blog.model.entity.OperationLog();
        logEntity.setUserId(userId);
        logEntity.setModule(annotation.module());       // 从注解获取
        logEntity.setOperation(annotation.operation()); // 从注解获取
        logEntity.setMethod(methodName);
        logEntity.setParams(params);
        logEntity.setIp(ip);
        logEntity.setDuration(duration);
        logEntity.setCreatedAt(LocalDateTime.now());

        // 保存到数据库
        operationLogMapper.insert(logEntity);

        log.info("操作日志已记录: module={}, operation={}, method={}, duration={}ms",
                annotation.module(), annotation.operation(), methodName, duration);
    }

    /**
     * 从 Spring Security 上下文获取当前登录用户 ID
     *
     * JwtAuthFilter 在认证成功后将 userId 存入 Authentication.name
     * 所以这里通过 SecurityContextHolder 获取
     */
    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext()
                    .getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return Long.parseLong(authentication.getName());
            }
        } catch (Exception e) {
            log.warn("获取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 HTTP 请求中获取客户端 IP 地址
     *
     * 优先从 X-Forwarded-For 头获取（经过代理/负载均衡时）
     * 否则从 request.getRemoteAddr() 获取直连 IP
     */
    private String getRequestIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                // 优先从反向代理头获取真实 IP
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                // X-Forwarded-For 可能包含多个 IP（经过多级代理），取第一个
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.warn("获取请求IP失败: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * 将方法参数序列化为 JSON 字符串
     *
     * 注意：
     * 1. 某些参数（如 HttpServletRequest、Authentication）无法序列化，需要跳过
     * 2. 参数 JSON 可能很长（比如文章内容），需要截断
     */
    private String serializeParams(Object[] args) {
        try {
            if (args == null || args.length == 0) {
                return "[]";
            }
            // 过滤掉不可序列化的参数类型
            Object[] serializableArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof HttpServletRequest
                        || args[i] instanceof Authentication) {
                    serializableArgs[i] = args[i].getClass().getSimpleName();
                } else {
                    serializableArgs[i] = args[i];
                }
            }
            String json = objectMapper.writeValueAsString(serializableArgs);
            // 截断过长的参数（如文章内容可能几千字）
            if (json.length() > MAX_PARAMS_LENGTH) {
                json = json.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            log.warn("序列化方法参数失败: {}", e.getMessage());
            return "serialization_error";
        }
    }
}
```

### 6.4 关键代码详解

**为什么 catch 后要 throw？**

```java
try {
    result = joinPoint.proceed();
} catch (Throwable e) {
    throwable = e;
    throw e;  // 必须重新抛出！
}
```

如果不 `throw e`，异常就被"吞掉"了。Controller 方法抛出的 BusinessException 不会到达 GlobalExceptionHandler，客户端会收到正常的 200 响应（但实际上操作失败了）。AOP 的原则是：**记录但不改变方法的行为**。

**为什么保存日志放在 finally 里？**

```java
finally {
    try {
        saveLog(joinPoint, annotation, startTime, throwable);
    } catch (Exception e) {
        log.error("保存操作日志失败", e);
    }
}
```

两层保障：
1. `finally` 保证无论方法成功还是抛异常，日志都会被保存
2. 外层 `try-catch` 保证即使日志保存失败（比如数据库连接断开），也不会影响主流程

**获取当前用户 ID**：

```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
Long userId = Long.parseLong(authentication.getName());
```

回顾 `JwtAuthFilter` 中的代码，认证成功后将 userId 存入了 Authentication 的 principal：

```java
// JwtAuthFilter.java 中的代码
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
//                                             ↑ 这里传入了 userId
```

`authentication.getName()` 就是获取这个 userId（以字符串形式），再 `Long.parseLong()` 转换。

---

## 7. 反射获取方法信息

### 7.1 MethodSignature

`MethodSignature` 是 AOP 中获取被拦截方法信息的核心接口：

```java
MethodSignature signature = (MethodSignature) joinPoint.getSignature();

// 获取 Method 对象（java.lang.reflect.Method）
Method method = signature.getMethod();

// 获取方法名
String methodName = signature.getName();
// 例如: "createArticle"

// 获取方法所在类（声明类）
Class<?> declaringClass = signature.getDeclaringType();
// 例如: AdminArticleController.class

// 获取类的简单名称（不含包名）
String className = signature.getDeclaringType().getSimpleName();
// 例如: "AdminArticleController"

// 获取方法的参数类型数组
Class<?>[] paramTypes = signature.getParameterTypes();
// 例如: [ArticleDTO.class, Authentication.class]

// 获取方法的参数名数组
String[] paramNames = signature.getParameterNames();
// 例如: ["dto", "authentication"]

// 获取方法的返回类型
Class<?> returnType = signature.getReturnType();
// 例如: Result.class
```

### 7.2 Method.getAnnotation()

通过反射读取方法上的注解实例，获取注解属性值：

```java
Method method = signature.getMethod();

// 获取方法上的 @OperationLog 注解实例
OperationLog annotation = method.getAnnotation(OperationLog.class);

// 如果方法没有这个注解，返回 null
if (annotation == null) {
    return;  // 安全检查
}

// 读取注解的属性值
String module = annotation.module();         // 例如: "文章管理"
String operation = annotation.operation();   // 例如: "创建"
```

**原理**：`getAnnotation()` 利用 Java 反射机制，在运行时读取方法上的注解信息。这要求注解的 `@Retention` 必须是 `RUNTIME`，否则运行时注解信息已经被丢弃。

### 7.3 JoinPoint.getArgs()

获取方法被调用时的实际参数值：

```java
Object[] args = joinPoint.getArgs();
// 例如：createArticle(@Valid @RequestBody ArticleDTO dto, Authentication authentication)
// args[0] = ArticleDTO 对象 {"title": "我的文章", "content": "..."}
// args[1] = Authentication 对象

// 遍历参数
for (int i = 0; i < args.length; i++) {
    System.out.println("参数" + i + ": " + args[i]);
}
```

**注意**：`getArgs()` 返回的是方法的**实际参数值**（对象引用），不是参数名。如果需要参数名，要通过 `MethodSignature.getParameterNames()` 获取。

### 7.4 综合使用示例

```java
// 一个完整的例子：获取方法的全部信息
public void logMethodInfo(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();

    // 类名.方法名
    String fullName = signature.getDeclaringType().getSimpleName()
            + "." + signature.getName();
    // → "AdminArticleController.createArticle"

    // 注解信息
    OperationLog annotation = method.getAnnotation(OperationLog.class);
    String module = annotation.module();       // → "文章管理"
    String operation = annotation.operation(); // → "创建"

    // 参数信息
    String[] paramNames = signature.getParameterNames(); // → ["dto", "authentication"]
    Object[] paramValues = joinPoint.getArgs();           // → [ArticleDTO{...}, Auth{...}]

    // 返回值类型
    Class<?> returnType = signature.getReturnType();       // → Result.class

    log.info("[{}] {}.{} 参数: {}", module, signature.getDeclaringType().getSimpleName(),
            signature.getName(), paramValues);
}
```

---

## 8. 动手实现 (对应 TODO 3, TODO 4, 控制器修改)

### 8.1 OperationLogService 接口

```java
package me.xunrana.blog.service;

import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.model.entity.OperationLog;

public interface OperationLogService {

    /**
     * 保存操作日志
     */
    void saveLog(OperationLog log);

    /**
     * 分页查询操作日志（管理端使用）
     *
     * @param page 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<OperationLog> getLogPage(int page, int size);
}
```

### 8.2 OperationLogServiceImpl 实现 (TODO 3)

```java
package me.xunrana.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.mapper.OperationLogMapper;
import me.xunrana.blog.model.entity.OperationLog;
import me.xunrana.blog.service.OperationLogService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    // ===== TODO 3: 保存操作日志 =====
    @Override
    public void saveLog(OperationLog operationLog) {
        // 直接使用 MyBatis-Plus 的 BaseMapper.insert()
        // OperationLog 实体已经在 AOP 切面中构建完毕
        operationLogMapper.insert(operationLog);
        log.debug("操作日志已保存: module={}, operation={}",
                operationLog.getModule(), operationLog.getOperation());
    }

    // ===== TODO 4: 分页查询操作日志 =====
    @Override
    public PageResult<OperationLog> getLogPage(int page, int size) {
        // 构建分页查询，按创建时间倒序（最新的在前面）
        Page<OperationLog> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> result = operationLogMapper.selectPage(pageParam, wrapper);

        return PageResult.from(result);
    }
}
```

**代码说明**：

- `saveLog()` 方法极其简单，只是一行 `mapper.insert()`。这是有意为之——Service 层保持精简，复杂的日志构建逻辑放在 AOP 切面中。
- `getLogPage()` 提供管理端查看操作日志的功能。使用 `LambdaQueryWrapper` 实现类型安全的条件查询，`orderByDesc` 保证最新的日志排在前面。

### 8.3 改造 AOP 切面（使用 Service 替代直接操作 Mapper）

在实际项目中，切面中建议调用 Service 而不是直接调用 Mapper，保持分层架构的一致性：

```java
// 改造前：AOP 中直接注入 Mapper
private final OperationLogMapper operationLogMapper;

// 改造后：AOP 中注入 Service
private final OperationLogService operationLogService;

// saveLog 方法中改为调用 Service
private void saveLog(ProceedingJoinPoint joinPoint, OperationLog annotation,
                     long startTime, Throwable throwable) {
    // ... 构建 logEntity ...

    // 改造前
    // operationLogMapper.insert(logEntity);

    // 改造后
    operationLogService.saveLog(logEntity);
}
```

### 8.4 在 Controller 上添加 @OperationLog 注解

**AdminArticleController**：

```java
package me.xunrana.blog.controller.admin;

import me.xunrana.blog.common.annotation.OperationLog;
// ... 其他导入

@RestController
@RequestMapping("/v1/admin/articles")
@Tag(name = "文章管理")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @PostMapping("")
    @Operation(summary = "创建文章")
    @OperationLog(module = "文章管理", operation = "创建")  // ← 新增
    public Result<Void> createArticle(@Valid @RequestBody ArticleDTO dto,
                                      Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        articleService.createArticle(dto, userId);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新文章")
    @OperationLog(module = "文章管理", operation = "更新")  // ← 新增
    public Result<Void> updateArticle(
            @Parameter(description = "文章ID") @PathVariable Long id,
            @Valid @RequestBody ArticleDTO dto) {
        articleService.updateArticle(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文章")
    @OperationLog(module = "文章管理", operation = "删除")  // ← 新增
    public Result<Void> deleteArticle(
            @Parameter(description = "文章ID") @PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }

    // 注意：getArticlePage() 不加 @OperationLog
    // 查询操作不需要记录操作日志，只记录增删改
    @GetMapping("")
    @Operation(summary = "管理端分页查询文章列表（所有状态）")
    public Result<PageResult<ArticleVO>> getArticlePage(ArticleQueryDTO query) {
        return Result.success(articleService.getArticlePage(query));
    }
}
```

**AdminCategoryController**：

```java
@RestController
@RequestMapping("/v1/admin/categories")
@Tag(name = "分类管理")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping("")
    @Operation(summary = "创建分类")
    @OperationLog(module = "分类管理", operation = "创建")  // ← 新增
    public Result<Void> createCategory(@Valid @RequestBody CategoryDTO dto) {
        categoryService.createCategory(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分类")
    @OperationLog(module = "分类管理", operation = "更新")  // ← 新增
    public Result<Void> updateCategory(
            @Parameter(description = "分类ID") @PathVariable Long id,
            @Valid @RequestBody CategoryDTO dto) {
        categoryService.updateCategory(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    @OperationLog(module = "分类管理", operation = "删除")  // ← 新增
    public Result<Void> deleteCategory(
            @Parameter(description = "分类ID") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
```

**AdminTagController**：

```java
@RestController
@RequestMapping("/v1/admin/tags")
@Tag(name = "标签管理")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    @PostMapping("")
    @Operation(summary = "创建标签")
    @OperationLog(module = "标签管理", operation = "创建")  // ← 新增
    public Result<Void> createTag(@Valid @RequestBody TagDTO dto) {
        tagService.createTag(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新标签")
    @OperationLog(module = "标签管理", operation = "更新")  // ← 新增
    public Result<Void> updateTag(
            @Parameter(description = "标签ID") @PathVariable Long id,
            @Valid @RequestBody TagDTO dto) {
        tagService.updateTag(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    @OperationLog(module = "标签管理", operation = "删除")  // ← 新增
    public Result<Void> deleteTag(
            @Parameter(description = "标签ID") @PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }
}
```

### 8.5 注解放在哪一层？

| 层次 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| **Controller 层** ← 我们的选择 | 可以获取 HttpServletRequest（IP、请求路径）；每个 Controller 方法对应一个 API 端点，日志粒度清晰 | Service 层直接调用不会被记录 | 记录 HTTP 请求级别的操作日志 |
| **Service 层** | 无论从哪里调用（Controller、定时任务、消息队列）都能记录 | 获取 HTTP 请求信息不方便；一个 Service 方法可能被多个 Controller 调用，日志可能重复 | 记录业务逻辑级别的操作日志 |

本项目选择 Controller 层，因为操作日志本质上是记录"谁在什么时候通过 API 做了什么操作"，这是 HTTP 请求级别的概念。

---

## 9. 进阶: 异步日志保存

### 9.1 当前方案的问题

目前操作日志是**同步保存**的：AOP 切面在目标方法返回后，会在同一个线程中执行 `operationLogMapper.insert()`。这意味着每个管理端请求的响应时间 = 业务逻辑耗时 + 日志保存耗时（约 5-10ms）。

```
请求 → AOP 前置 → 业务逻辑(50ms) → 日志保存(5ms) → 返回
                                                     ↑
                                          总耗时 55ms，多了 5ms
```

### 9.2 异步方案

**方案一：@Async 注解**

```java
// 1. 在主启动类开启异步支持
@SpringBootApplication
@EnableAsync       // ← 新增
@EnableScheduling
@MapperScan("me.xunrana.blog.mapper")
public class XunranaBlogApplication { ... }

// 2. 在 Service 方法上标注 @Async
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Async  // ← 新增：方法将在另一个线程中执行
    @Override
    public void saveLog(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}
```

`@Async` 标注的方法会被 Spring 提交到线程池异步执行，调用方不需要等待方法完成。

```
请求 → AOP 前置 → 业务逻辑(50ms) → 提交日志任务到线程池 → 立即返回
                                                        ↑
                                              总耗时 50ms，快了 5ms
                                    线程池中异步执行 → 日志保存(5ms)
```

**方案二：自定义线程池**

默认的 `@Async` 使用 `SimpleAsyncTaskExecutor`（每次创建新线程，没有线程复用）。生产环境建议配置 `ThreadPoolTaskExecutor`：

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("logExecutor")
    public ThreadPoolTaskExecutor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);             // 核心线程数
        executor.setMaxPoolSize(5);              // 最大线程数
        executor.setQueueCapacity(100);          // 等待队列容量
        executor.setThreadNamePrefix("log-");    // 线程名前缀（便于排查日志）
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());  // 队列满时由调用线程执行
        executor.initialize();
        return executor;
    }
}

// 使用时指定线程池
@Async("logExecutor")
public void saveLog(OperationLog operationLog) { ... }
```

### 9.3 权衡

| | 同步保存 | 异步保存 |
|---|---------|---------|
| **延迟** | 增加 5-10ms | 接近 0 |
| **可靠性** | 高（数据库写成功才返回） | 低（线程池/应用崩溃可能丢日志） |
| **复杂度** | 低 | 中（需要配置线程池、处理异常） |
| **一致性** | 强一致（方法返回时日志已保存） | 最终一致（日志可能几毫秒后才保存） |

**本项目暂不使用异步**，理由：
1. 管理端操作频率极低（一天几十次），5ms 的额外延迟完全不影响体验
2. 同步方案简单可靠，不需要考虑线程池配置和异步异常处理
3. 作为学习项目，先掌握同步方案，异步作为"进阶知识"了解即可

---

## 10. 测试验证

### 10.1 验证 AOP 拦截

**创建文章并观察操作日志**：

```bash
# 1. 以管理员身份登录获取 token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'
# 返回: {"code": 200, "data": {"accessToken": "eyJ..."}}

# 2. 创建文章
curl -X POST http://localhost:8080/api/v1/admin/articles \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{
    "title": "测试 AOP 操作日志",
    "content": "这是一篇测试文章",
    "status": 1,
    "categoryId": 1,
    "tagIds": [1, 2]
  }'
# 返回: {"code": 200, "message": "操作成功"}
```

**应用日志中应该看到**：

```
INFO  OperationLogAspect - 操作日志已记录: module=文章管理, operation=创建,
    method=AdminArticleController.createArticle, duration=127ms
```

### 10.2 查看操作日志表

```sql
-- 查看 operation_log 表中的记录
SELECT id, user_id, module, operation, method, ip, duration, created_at
FROM operation_log
ORDER BY created_at DESC
LIMIT 10;

-- 预期输出:
-- +----+---------+----------+-----------+--------------------------------------------+-------+----------+---------------------+
-- | id | user_id | module   | operation | method                                     | ip    | duration | created_at          |
-- +----+---------+----------+-----------+--------------------------------------------+-------+----------+---------------------+
-- |  1 |       1 | 文章管理  | 创建      | AdminArticleController.createArticle       | ::1   |      127 | 2026-03-25 10:30:00 |
-- +----+---------+----------+-----------+--------------------------------------------+-------+----------+---------------------+
```

### 10.3 验证删除操作

```bash
# 删除文章
curl -X DELETE http://localhost:8080/api/v1/admin/articles/1 \
  -H "Authorization: Bearer eyJ..."

# 查看操作日志
mysql> SELECT * FROM operation_log ORDER BY created_at DESC LIMIT 1;
# module=文章管理, operation=删除, method=AdminArticleController.deleteArticle
```

### 10.4 验证 IP 获取

```bash
# 通过 Nginx 代理访问时，X-Forwarded-For 头会包含真实 IP
curl -X POST http://localhost:8080/api/v1/admin/categories \
  -H "Authorization: Bearer eyJ..." \
  -H "X-Forwarded-For: 123.45.67.89" \
  -H "Content-Type: application/json" \
  -d '{"name": "测试分类"}'

# 查看操作日志
mysql> SELECT ip FROM operation_log ORDER BY created_at DESC LIMIT 1;
# ip = 123.45.67.89（应该是 X-Forwarded-For 中的值）
```

### 10.5 验证异常场景

```bash
# 删除不存在的文章
curl -X DELETE http://localhost:8080/api/v1/admin/articles/99999 \
  -H "Authorization: Bearer eyJ..."

# 返回: {"code": 2001, "message": "文章不存在"}

# 查看操作日志 —— 即使操作失败，日志也应该被记录
mysql> SELECT * FROM operation_log ORDER BY created_at DESC LIMIT 1;
# module=文章管理, operation=删除, method=AdminArticleController.deleteArticle
# 注意：日志依然被记录了，因为 finally 块保证了日志保存
```

---

## 11. 面试常见问题

### Q1: Spring AOP 的实现原理是什么？

**答**：Spring AOP 基于**动态代理**实现。Spring 在运行时为目标对象创建代理对象，客户端调用的实际上是代理对象。代理对象在执行目标方法前后插入切面逻辑。具体有两种代理方式：如果目标对象实现了接口，使用 **JDK 动态代理**（基于 `java.lang.reflect.Proxy`）；如果没有实现接口，使用 **CGLIB 代理**（在运行时生成目标类的子类）。Spring Boot 默认使用 CGLIB。这与 AspectJ 的编译期字节码织入不同，Spring AOP 是运行时织入。

### Q2: @Around 和 @Before/@After 的区别？什么场景用哪个？

**答**：`@Before` 只在方法执行前触发，`@After` 只在方法执行后触发，它们是分开的两个方法，共享数据不方便。`@Around` 环绕通知可以包围整个方法执行过程，在一个方法内完成前置和后置逻辑，还能控制目标方法是否执行（通过 `proceed()`）。如果只需要方法执行前的逻辑（如权限检查），用 `@Before`；如果需要同时涉及方法执行前后（如性能监控、操作日志记录耗时），用 `@Around`。`@Around` 最灵活但也最复杂，能做到其他通知类型能做的一切。

### Q3: 什么是 AOP 的自调用问题？如何解决？

**答**：自调用问题是指在同一个类中，方法 A 调用方法 B（`this.B()`），方法 B 上的 AOP 不会生效。原因是 `this.B()` 直接调用了目标对象的方法，绕过了代理对象，而 AOP 切面逻辑是在代理对象中实现的。解决方案有：一是注入自身代理（`@Autowired private MyService self;`，然后调用 `self.B()`）；二是从 `ApplicationContext` 中获取代理 Bean 再调用；三是使用 AspectJ 的编译期织入（不依赖代理机制，但配置复杂）。

### Q4: 如何通过反射获取方法上的注解信息？

**答**：在 AOP 的 `@Around` 通知方法中，通过 `ProceedingJoinPoint.getSignature()` 获取 `MethodSignature`，再调用 `getMethod()` 获取 `java.lang.reflect.Method` 对象。然后通过 `method.getAnnotation(AnnotationClass.class)` 获取注解实例，就可以读取注解的属性值了。前提是注解的 `@Retention` 必须是 `RUNTIME`，否则运行时反射无法获取注解信息。

### Q5: AOP 中如何获取当前登录用户？

**答**：在 Spring Security 环境下，通过 `SecurityContextHolder.getContext().getAuthentication()` 获取当前认证信息。在我们的项目中，`JwtAuthFilter` 认证成功后将 userId 存入了 `Authentication` 的 principal 中（`authentication.getName()` 返回 userId 字符串），所以 AOP 切面中可以直接从 `SecurityContextHolder` 获取。需要注意的是，如果 AOP 在异步线程中执行，`SecurityContextHolder` 默认使用 `ThreadLocal` 存储，异步线程可能无法获取到，需要配置 `SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)`。

### Q6: @Around 通知中如果不调用 proceed() 会怎样？

**答**：如果 `@Around` 方法中不调用 `joinPoint.proceed()`，目标方法将不会被执行。这可以用来实现方法拦截——比如权限校验不通过时直接返回错误结果而不执行业务逻辑。但在操作日志场景中，如果忘记调用 `proceed()`，业务逻辑不会执行，请求会返回 null 或默认值，造成严重的 Bug。所以 `@Around` 方法必须确保在正常流程中调用 `proceed()` 并将其返回值传递回去。

### Q7: 解释 AOP 中切面、切入点、通知之间的关系

**答**：**切面**（Aspect）是一个类，封装了横切关注点的完整逻辑，用 `@Aspect` 标注。**切入点**（Pointcut）定义了"拦截哪些方法"的规则，用 `@Pointcut` 注解和表达式定义（如 `@annotation(OperationLog)` 表示拦截所有标注了 `@OperationLog` 的方法）。**通知**（Advice）是"拦截后做什么事"的具体逻辑，用 `@Before`、`@After`、`@Around` 等注解标注。三者的关系是：一个切面包含一个或多个切入点和通知；切入点负责匹配连接点（即确定拦截范围）；通知引用切入点并定义拦截后的执行逻辑。
