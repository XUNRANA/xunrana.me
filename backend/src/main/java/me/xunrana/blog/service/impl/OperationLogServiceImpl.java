package me.xunrana.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.PageResult;
import me.xunrana.blog.mapper.OperationLogMapper;
import me.xunrana.blog.model.entity.OperationLog;
import me.xunrana.blog.model.vo.OperationLogVO;
import me.xunrana.blog.service.OperationLogService;
import org.springframework.stereotype.Service;

/**
 * 操作日志 Service 实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public void saveLog(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
        log.debug("操作日志已保存: module={}, operation={}, userId={}, ip={}, duration={}ms",
                operationLog.getModule(), operationLog.getOperation(),
                operationLog.getUserId(), operationLog.getIp(), operationLog.getDuration());
    }

    @Override
    public PageResult<OperationLogVO> getLogPage(int page, int size) {
        Page<OperationLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreatedAt);
        Page<OperationLog> result = operationLogMapper.selectPage(pageParam, wrapper);

        Page<OperationLogVO> voPage = result.convert(entity -> {
            OperationLogVO vo = new OperationLogVO();
            BeanUtil.copyProperties(entity, vo);
            return vo;
        });
        return PageResult.from(voPage);
    }
}
