package me.xunrana.blog.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDTO {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String summary;

    @NotBlank(message = "Content must not be blank")
    private String content;

    private String coverImage;

    private Long categoryId;

    private Integer status;

    private Integer isTop;

    private List<Long> tagIds;
}
