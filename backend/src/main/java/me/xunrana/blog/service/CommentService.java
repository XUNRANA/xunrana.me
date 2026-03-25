package me.xunrana.blog.service;

import me.xunrana.blog.model.dto.CommentDTO;
import me.xunrana.blog.model.vo.CommentVO;

import java.util.List;

public interface CommentService {

    List<CommentVO> getCommentsByArticleId(Long articleId);

    void addComment(CommentDTO dto, String ip);

    void updateCommentStatus(Long id, Integer status);
}
