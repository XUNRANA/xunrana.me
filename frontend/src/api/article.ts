import request from '@/utils/request'
import type { Result, PageResult } from '@/types/api'
import type { ArticleVO, ArticleDetailVO, ArticleDTO, ArticleQueryDTO } from '@/types/article'

export function getArticles(params: ArticleQueryDTO) {
  return request.get<Result<PageResult<ArticleVO>>>('/v1/articles', { params })
}

export function getArticleBySlug(slug: string) {
  return request.get<Result<ArticleDetailVO>>(`/v1/articles/${slug}`)
}

export function searchArticles(params: ArticleQueryDTO) {
  return request.get<Result<PageResult<ArticleVO>>>('/v1/articles/search', { params })
}

export function getArchives() {
  return request.get<Result<ArticleVO[]>>('/v1/articles/archives')
}

export function createArticle(data: ArticleDTO) {
  return request.post<Result<void>>('/v1/admin/articles', data)
}

export function updateArticle(id: number, data: ArticleDTO) {
  return request.put<Result<void>>(`/v1/admin/articles/${id}`, data)
}

export function deleteArticle(id: number) {
  return request.delete<Result<void>>(`/v1/admin/articles/${id}`)
}
