package me.xunrana.blog.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    private Long id;

    private Long articleId;

    private Long parentId;

    private String nickname;

    private String content;

    private LocalDateTime createdAt;

    private List<CommentVO> children;
}
