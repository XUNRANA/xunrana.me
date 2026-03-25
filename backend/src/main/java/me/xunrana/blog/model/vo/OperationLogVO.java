package me.xunrana.blog.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志查询 VO
 */
@Data
public class OperationLogVO {

    private Long id;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作模块 */
    private String module;

    /** 操作类型 */
    private String operation;

    /** 调用方法 */
    private String method;

    /** 请求参数（JSON） */
    private String params;

    /** 客户端 IP */
    private String ip;

    /** 执行耗时（毫秒） */
    private Long duration;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
