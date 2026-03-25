package me.xunrana.blog.service;

import me.xunrana.blog.model.dto.CategoryDTO;
import me.xunrana.blog.model.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    List<CategoryVO> getAllCategories();

    void createCategory(CategoryDTO dto);

    void updateCategory(Long id, CategoryDTO dto);

    void deleteCategory(Long id);
}
