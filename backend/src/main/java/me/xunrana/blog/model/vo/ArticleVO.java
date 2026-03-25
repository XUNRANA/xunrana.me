package me.xunrana.blog.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVO {

    private Long id;

    private String title;

    private String slug;

    private String summary;

    private String coverImage;

    private Long categoryId;

    private String categoryName;

    private Long authorId;

    private String authorName;

    private Integer status;

    private Integer isTop;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private List<TagVO> tags;
}
