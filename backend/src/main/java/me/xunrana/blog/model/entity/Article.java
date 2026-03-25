package me.xunrana.blog.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String slug;

    private String summary;

    private String content;

    @TableField("cover_image")
    private String coverImage;

    @TableField("category_id")
    private Long categoryId;

    @TableField("author_id")
    private Long authorId;

    /**
     * 0=draft, 1=published
     */
    private Integer status;

    @TableField("is_top")
    private Integer isTop;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * Transient field: tags associated with this article
     */
    @TableField(exist = false)
    private List<Tag> tags;

    /**
     * Transient field: category name
     */
    @TableField(exist = false)
    private String categoryName;
}
