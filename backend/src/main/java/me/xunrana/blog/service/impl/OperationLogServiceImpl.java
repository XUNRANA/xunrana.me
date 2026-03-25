package me.xunrana.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

    // ============================================================
    // TODO 3: 实现保存操作日志
    // 📖 教程: docs/08-AOP操作日志与自定义注解.md 第4节
    //
    // 实现步骤:
    //   1. 调用 operationLogMapper.insert(operationLog) 插入数据库
    //   2. 记录 debug 日志: "操作日志已保存: module={}, operation={}"
    //
    // 提示:
    //   - 这个方法很简单，但它是 AOP 切面的落地点
    //   - 进阶: 可以用 @Async 异步执行，避免阻塞业务请求
    // ============================================================
    @Override
    public void saveLog(OperationLog operationLog) {
        // TODO: 在这里实现日志保存

    }

    // ============================================================
    // TODO 4: 实现分页查询操作日志
    // 📖 教程: docs/08-AOP操作日志与自定义注解.md 第5节
    //
    // 实现步骤:
    //   1. 创建 MyBatis-Plus 分页对象: new Page<>(page, size)
    //   2. 构建查询条件: 按 created_at 降序排列
    //      LambdaQueryWrapper<OperationLog>
    //          .orderByDesc(OperationLog::getCreatedAt)
    //   3. 执行查询: operationLogMapper.selectPage(page, wrapper)
    //   4. 将 OperationLog 实体转为 OperationLogVO
    //      用 BeanUtil.copyProperties() 或手动映射
    //   5. 返回 PageResult（参考 ArticleServiceImpl 的分页写法）
    //
    // 提示:
    //   - 转换方法: page.convert(entity -> { ... BeanUtil.copyProperties ... })
    //   - 或者用 page.getRecords().stream().map() 手动转换
    // ============================================================
    @Override
    public PageResult<OperationLogVO> getLogPage(int page, int size) {
        // TODO: 在这里实现分页查询

        return null; // 替换为你的实现
    }
}
