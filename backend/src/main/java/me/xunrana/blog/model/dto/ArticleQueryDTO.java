package me.xunrana.blog.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleQueryDTO {

    private Integer page = 1;

    private Integer size = 10;

    private Long categoryId;

    private Long tagId;

    private Integer status;

    private String keyword;
}
