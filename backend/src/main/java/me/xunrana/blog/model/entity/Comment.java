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
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("parent_id")
    private Long parentId;

    private String nickname;

    private String email;

    private String content;

    /**
     * 0=pending, 1=approved, 2=rejected
     */
    private Integer status;

    private String ip;

    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * Transient field: child comments (replies)
     */
    @TableField(exist = false)
    private List<Comment> children;
}
