package me.xunrana.blog.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    @NotBlank(message = "Category name must not be blank")
    @Size(max = 50, message = "Category name must not exceed 50 characters")
    private String name;

    private String slug;

    private String description;

    private Integer sortOrder;
}
