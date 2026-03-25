package me.xunrana.blog.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVO {

    private Long id;

    private String name;

    private String slug;

    private String description;

    private Integer sortOrder;

    private Integer articleCount;

    private LocalDateTime createdAt;
}
