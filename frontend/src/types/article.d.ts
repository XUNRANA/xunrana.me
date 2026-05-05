import type { PageQuery } from './api'

export interface ArticleVO {
  id: number
  title: string
  slug: string
  summary: string
  coverImage: string
  categoryId: number
  categoryName: string
  viewCount: number
  status: number
  createdAt: string
  updatedAt: string
  tags: TagVO[]
}

export interface ArticleDetailVO {
  id: number
  title: string
  slug: string
  summary: string
  content: string
  coverImage: string
  categoryId: number
  categoryName: string
  viewCount: number
  status: number
  createdAt: string
  updatedAt: string
  tags: TagVO[]
}

export interface ArticleDTO {
  title: string
  slug: string
  summary: string
  content: string
  coverImage: string
  categoryId: number
  tagIds: number[]
  status: number
}

export interface ArticleQueryDTO extends PageQuery {
  keyword?: string
  categoryId?: number
  tagId?: number
  status?: number
}
