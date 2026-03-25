package me.xunrana.blog.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_tag")
public class ArticleTag {

    @TableField("article_id")
    private Long articleId;

    @TableField("tag_id")
    private Long tagId;
}
