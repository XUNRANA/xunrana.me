package me.xunrana.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.mapper.ArticleMapper;
import me.xunrana.blog.mapper.CommentMapper;
import me.xunrana.blog.model.dto.CommentDTO;
import me.xunrana.blog.model.entity.Article;
import me.xunrana.blog.model.entity.Comment;
import me.xunrana.blog.model.vo.CommentVO;
import me.xunrana.blog.service.CommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<CommentVO> getCommentsByArticleId(Long articleId) {
        // Query all approved comments for the article
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getArticleId, articleId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getCreatedAt));

        // Convert to VO list
        List<CommentVO> allVOs = comments.stream().map(comment -> {
            CommentVO vo = new CommentVO();
            BeanUtil.copyProperties(comment, vo);
            return vo;
        }).toList();

        // Build tree structure
        return buildCommentTree(allVOs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(CommentDTO dto, String ip) {
        Comment comment = new Comment();
        BeanUtil.copyProperties(dto, comment);
        comment.setStatus(1); // Auto approve for now
        comment.setIp(ip);
        comment.setCreatedAt(LocalDateTime.now());

        commentMapper.insert(comment);
        log.info("Comment added: id={}, articleId={}", comment.getId(), comment.getArticleId());

        // Increment article comment count
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, dto.getArticleId())
                .setSql("comment_count = comment_count + 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCommentStatus(Long id, Integer status) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setStatus(status);
        commentMapper.updateById(comment);
        log.info("Comment status updated: id={}, status={}", id, status);
    }

    /**
     * Build a tree structure from a flat list of CommentVOs.
     * Root comments have parentId == null, children are nested under their parent.
     */
    private List<CommentVO> buildCommentTree(List<CommentVO> allComments) {
        // Group comments by parentId
        Map<Long, List<CommentVO>> childrenMap = allComments.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CommentVO::getParentId));

        // Find root comments and attach children recursively
        List<CommentVO> roots = new ArrayList<>();
        for (CommentVO comment : allComments) {
            if (comment.getParentId() == null) {
                attachChildren(comment, childrenMap);
                roots.add(comment);
            }
        }

        return roots;
    }

    /**
     * Recursively attach child comments to the parent.
     */
    private void attachChildren(CommentVO parent, Map<Long, List<CommentVO>> childrenMap) {
        List<CommentVO> children = childrenMap.get(parent.getId());
        if (children != null) {
            for (CommentVO child : children) {
                attachChildren(child, childrenMap);
            }
            parent.setChildren(children);
        }
    }
}
