package me.xunrana.blog.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.annotation.OpLog;
import me.xunrana.blog.common.utils.IpUtils;
import me.xunrana.blog.model.entity.OperationLog;
import me.xunrana.blog.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志 AOP 切面
 *
 * 拦截 @OpLog 标注的方法，环绕记录：模块、操作、方法、参数(JSON)、IP、用户ID、耗时。
 *
 * 关键点：
 *   - @Around 必须 proceed() 并返回结果，否则原方法不会执行。
 *   - 参数序列化前需过滤 HttpServletRequest/Response/MultipartFile 等不可序列化对象。
 *   - SecurityContextHolder 在异步线程中默认丢失，因此本切面在原线程同步保存日志。
 */
@Aspect
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    /** 最多保存的参数 JSON 长度（避免大对象灌爆 DB） */
    private static final int MAX_PARAMS_LENGTH = 2000;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Throwable error = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            try {
                saveOperationLog(joinPoint, opLog, duration, error);
            } catch (Exception logErr) {
                // 日志记录失败不能影响业务
                log.warn("操作日志保存失败: {}", logErr.getMessage());
            }
        }
    }

    private void saveOperationLog(ProceedingJoinPoint joinPoint, OpLog opLog, long duration, Throwable error) {
        OperationLog logEntity = new OperationLog();
        logEntity.setModule(opLog.module());
        logEntity.setOperation(opLog.operation());

        // 方法全路径
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        logEntity.setMethod(className + "." + methodName);

        // 参数 JSON（过滤不可序列化对象）
        logEntity.setParams(serializeArgs(joinPoint.getArgs(), error));

        // 客户端 IP
        HttpServletRequest request = currentRequest();
        if (request != null) {
            logEntity.setIp(IpUtils.getClientIp(request));
        }

        // 当前登录用户 ID
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            try {
                logEntity.setUserId(Long.parseLong(auth.getName()));
            } catch (NumberFormatException ignored) {
                // principal 可能不是 userId 数字，忽略
            }
        }

        logEntity.setDuration(duration);
        logEntity.setCreatedAt(LocalDateTime.now());

        operationLogService.saveLog(logEntity);
    }

    private String serializeArgs(Object[] args, Throwable error) {
        try {
            Object[] safeArgs = Arrays.stream(args)
                    .map(arg -> {
                        if (arg == null) return null;
                        if (arg instanceof HttpServletRequest
                                || arg instanceof HttpServletResponse
                                || arg instanceof MultipartFile) {
                            return arg.getClass().getSimpleName();
                        }
                        return arg;
                    })
                    .toArray();
            String json = objectMapper.writeValueAsString(safeArgs);
            if (error != null) {
                json = "{\"args\":" + json + ",\"error\":\"" + error.getClass().getSimpleName()
                        + ": " + safe(error.getMessage()) + "\"}";
            }
            if (json.length() > MAX_PARAMS_LENGTH) {
                json = json.substring(0, MAX_PARAMS_LENGTH) + "...[truncated]";
            }
            return json;
        } catch (Exception e) {
            return "[serialize error: " + e.getMessage() + "]";
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
