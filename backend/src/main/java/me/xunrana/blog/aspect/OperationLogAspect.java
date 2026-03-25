package me.xunrana.blog.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.annotation.OpLog;
import me.xunrana.blog.model.entity.OperationLog;
import me.xunrana.blog.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

/**
 * 操作日志 AOP 切面
 *
 * 拦截所有标注了 @OpLog 注解的方法，自动记录:
 *   - 操作模块 & 操作类型（从注解读取）
 *   - 调用的方法全路径
 *   - 请求参数（JSON 序列化）
 *   - 客户端 IP
 *   - 当前登录用户 ID
 *   - 方法执行耗时
 *
 * 这是 Phase 2 的核心学习文件 — 通过实现以下 TODO 来掌握:
 *   - Spring AOP 切面编程（@Aspect + @Around）
 *   - 自定义注解的反射读取
 *   - SecurityContextHolder 获取当前用户
 *   - HttpServletRequest 获取请求上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
// ============================================================
// TODO 1: 添加 @Aspect 注解，声明这是一个切面类
// 📖 教程: docs/08-AOP操作日志与自定义注解.md 第2节
//
// 提示: import org.aspectj.lang.annotation.Aspect;
// ============================================================
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    // ============================================================
    // TODO 2: 实现 @Around 环绕通知
    // 📖 教程: docs/08-AOP操作日志与自定义注解.md 第3节
    //
    // 方法签名:
    //   @Around("@annotation(opLog)")
    //   public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable
    //
    // 实现步骤:
    //   1. 记录开始时间: long startTime = System.currentTimeMillis()
    //
    //   2. 执行目标方法: Object result = joinPoint.proceed()
    //      ⚠️ 必须调用 proceed() 并返回 result，否则原方法不会执行！
    //
    //   3. 计算耗时: long duration = System.currentTimeMillis() - startTime
    //
    //   4. 构建 OperationLog 实体并填充字段:
    //      a. module / operation → 从 opLog 注解参数读取
    //         opLog.module(), opLog.operation()
    //
    //      b. method → 获取方法全路径
    //         String className = joinPoint.getTarget().getClass().getName();
    //         String methodName = joinPoint.getSignature().getName();
    //         → className + "." + methodName
    //
    //      c. params → 序列化方法参数为 JSON
    //         Object[] args = joinPoint.getArgs();
    //         用 ObjectMapper 将 args 转为 JSON 字符串
    //         ⚠️ 参数中可能有 HttpServletRequest 等不可序列化对象，需要过滤
    //
    //      d. ip → 从当前 HTTP 请求中提取
    //         HttpServletRequest request = ((ServletRequestAttributes)
    //             RequestContextHolder.getRequestAttributes()).getRequest();
    //         IpUtils.getClientIp(request)
    //
    //      e. userId → 从 Spring Security 上下文获取当前用户
    //         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    //         if (auth != null && auth.isAuthenticated()) → auth.getName() 是 userId
    //
    //      f. duration → 耗时（毫秒）
    //      g. createdAt → LocalDateTime.now()
    //
    //   5. 异步保存日志: operationLogService.saveLog(operationLog)
    //      （这里简单同步保存即可，进阶可用 @Async 异步）
    //
    //   6. 返回 result
    //
    // 提示:
    //   - import jakarta.servlet.http.HttpServletRequest;
    //   - import org.springframework.web.context.request.RequestContextHolder;
    //   - import org.springframework.web.context.request.ServletRequestAttributes;
    //   - import org.springframework.security.core.context.SecurityContextHolder;
    //   - import com.fasterxml.jackson.databind.ObjectMapper;
    //   - 过滤不可序列化参数: 跳过 HttpServletRequest、HttpServletResponse 类型
    // ============================================================
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        // TODO: 在这里实现环绕通知

        // 临时实现：直接放行目标方法（你完成 TODO 后替换这行）
        return joinPoint.proceed();
    }
}
