import request from '@/utils/request'
import type { Result } from '@/types/api'
import type { CategoryVO, CategoryDTO } from '@/types/category'

export function getCategories() {
  return request.get<Result<CategoryVO[]>>('/v1/categories')
}

export function createCategory(data: CategoryDTO) {
  return request.post<Result<void>>('/v1/admin/categories', data)
}

export function updateCategory(id: number, data: CategoryDTO) {
  return request.put<Result<void>>(`/v1/admin/categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return request.delete<Result<void>>(`/v1/admin/categories/${id}`)
}
