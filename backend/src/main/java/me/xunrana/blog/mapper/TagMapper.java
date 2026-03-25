package me.xunrana.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.xunrana.blog.model.entity.Tag;
import me.xunrana.blog.model.vo.TagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * Select all tags with the count of published articles using each tag.
     */
    List<TagVO> selectTagWithCount();

    /**
     * Select all tags associated with a given article.
     */
    List<Tag> selectTagsByArticleId(@Param("articleId") Long articleId);
}
