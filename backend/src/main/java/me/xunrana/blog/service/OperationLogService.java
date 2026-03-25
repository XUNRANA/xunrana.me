package me.xunrana.blog.service;

import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.model.entity.OperationLog;
import me.xunrana.blog.model.vo.OperationLogVO;

/**
 * 操作日志 Service 接口
 */
public interface OperationLogService {

    /**
     * 保存操作日志
     *
     * @param operationLog 日志实体
     */
    void saveLog(OperationLog operationLog);

    /**
     * 分页查询操作日志
     *
     * @param page 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<OperationLogVO> getLogPage(int page, int size);
}
