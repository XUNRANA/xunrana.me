package me.xunrana.blog.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagDTO {

    @NotBlank(message = "Tag name must not be blank")
    @Size(max = 50, message = "Tag name must not exceed 50 characters")
    private String name;

    private String slug;
}
