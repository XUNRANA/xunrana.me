package me.xunrana.blog.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {

    @NotNull(message = "Article ID must not be null")
    private Long articleId;

    private Long parentId;

    @NotBlank(message = "Nickname must not be blank")
    @Size(max = 50, message = "Nickname must not exceed 50 characters")
    private String nickname;

    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Content must not be blank")
    @Size(max = 500, message = "Content must not exceed 500 characters")
    private String content;
}
