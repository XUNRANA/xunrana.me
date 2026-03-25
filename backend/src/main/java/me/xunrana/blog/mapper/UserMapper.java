package me.xunrana.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.xunrana.blog.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
