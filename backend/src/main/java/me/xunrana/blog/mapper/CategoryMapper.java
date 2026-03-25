package me.xunrana.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.xunrana.blog.model.entity.Category;
import me.xunrana.blog.model.vo.CategoryVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * Select all categories with the count of published articles in each.
     */
    List<CategoryVO> selectCategoryWithCount();
}
