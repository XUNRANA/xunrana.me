package me.xunrana.blog.service;

import me.xunrana.blog.model.dto.TagDTO;
import me.xunrana.blog.model.vo.TagVO;

import java.util.List;

public interface TagService {

    List<TagVO> getAllTags();

    void createTag(TagDTO dto);

    void updateTag(Long id, TagDTO dto);

    void deleteTag(Long id);
}
