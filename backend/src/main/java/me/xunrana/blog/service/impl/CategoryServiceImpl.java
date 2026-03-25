package me.xunrana.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.xunrana.blog.common.ErrorCode;
import me.xunrana.blog.exception.BusinessException;
import me.xunrana.blog.mapper.ArticleMapper;
import me.xunrana.blog.mapper.CategoryMapper;
import me.xunrana.blog.model.dto.CategoryDTO;
import me.xunrana.blog.model.entity.Article;
import me.xunrana.blog.model.entity.Category;
import me.xunrana.blog.model.vo.CategoryVO;
import me.xunrana.blog.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<CategoryVO> getAllCategories() {
        return categoryMapper.selectCategoryWithCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCategory(CategoryDTO dto) {
        // Check if name already exists
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getName, dto.getName()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称已存在");
        }

        Category category = new Category();
        BeanUtil.copyProperties(dto, category);

        // Generate slug from name if not provided
        if (StrUtil.isBlank(category.getSlug())) {
            category.setSlug(dto.getName().trim().toLowerCase().replace(" ", "-"));
        }

        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        category.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(category);
        log.info("Category created: id={}, name={}", category.getId(), category.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, CategoryDTO dto) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        // Check if new name conflicts with another category
        if (StrUtil.isNotBlank(dto.getName()) && !dto.getName().equals(existing.getName())) {
            Long count = categoryMapper.selectCount(
                    new LambdaQueryWrapper<Category>().eq(Category::getName, dto.getName()));
            if (count > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称已存在");
            }
        }

        BeanUtil.copyProperties(dto, existing, "id", "createdAt");
        categoryMapper.updateById(existing);
        log.info("Category updated: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        // Check if any articles reference this category
        Long articleCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, id));
        if (articleCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该分类下有文章，无法删除");
        }

        categoryMapper.deleteById(id);
        log.info("Category deleted: id={}", id);
    }
}
