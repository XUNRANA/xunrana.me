package me.xunrana.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.ErrorCode;
import me.xunrana.blog.exception.BusinessException;
import me.xunrana.blog.mapper.ArticleTagMapper;
import me.xunrana.blog.mapper.TagMapper;
import me.xunrana.blog.model.dto.TagDTO;
import me.xunrana.blog.model.entity.ArticleTag;
import me.xunrana.blog.model.entity.Tag;
import me.xunrana.blog.model.vo.TagVO;
import me.xunrana.blog.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    public List<TagVO> getAllTags() {
        return tagMapper.selectTagWithCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTag(TagDTO dto) {
        // Check if name already exists
        Long count = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>().eq(Tag::getName, dto.getName()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名称已存在");
        }

        Tag tag = new Tag();
        BeanUtil.copyProperties(dto, tag);

        // Generate slug from name if not provided
        if (StrUtil.isBlank(tag.getSlug())) {
            tag.setSlug(dto.getName().trim().toLowerCase().replace(" ", "-"));
        }

        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        log.info("Tag created: id={}, name={}", tag.getId(), tag.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTag(Long id, TagDTO dto) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }

        // Check if new name conflicts with another tag
        if (StrUtil.isNotBlank(dto.getName()) && !dto.getName().equals(existing.getName())) {
            Long count = tagMapper.selectCount(
                    new LambdaQueryWrapper<Tag>().eq(Tag::getName, dto.getName()));
            if (count > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名称已存在");
            }
        }

        BeanUtil.copyProperties(dto, existing, "id", "createdAt");
        tagMapper.updateById(existing);
        log.info("Tag updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }

        // Check if any articles use this tag
        Long articleCount = articleTagMapper.selectCount(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id));
        if (articleCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该标签下有文章，无法删除");
        }

        tagMapper.deleteById(id);
        log.info("Tag deleted: id={}", id);
    }
}
