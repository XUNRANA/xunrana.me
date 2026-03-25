package me.xunrana.blog.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * 标注在 Controller 方法上，AOP 切面会自动记录：
 *   - 操作模块 (module)
 *   - 操作类型 (operation)
 *   - 请求参数、IP 地址、执行耗时
 *   - 当前登录用户 ID
 *
 * 使用示例:
 * <pre>
 *   {@code @OpLog(module = "文章管理", operation = "创建文章")}
 *   @PostMapping
 *   public Result<Void> createArticle(@RequestBody ArticleDTO dto) { ... }
 * </pre>
 *
 * @see me.xunrana.blog.aspect.OperationLogAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /**
     * 操作模块（如 "文章管理"、"分类管理"）
     */
    String module() default "";

    /**
     * 操作类型（如 "创建文章"、"删除文章"）
     */
    String operation() default "";
}
