import request from '@/utils/request'
import type { Result } from '@/types/api'
import type { TagVO, TagDTO } from '@/types/tag'

export function getTags() {
  return request.get<Result<TagVO[]>>('/v1/tags')
}

export function createTag(data: TagDTO) {
  return request.post<Result<void>>('/v1/admin/tags', data)
}

export function updateTag(id: number, data: TagDTO) {
  return request.put<Result<void>>(`/v1/admin/tags/${id}`, data)
}

export function deleteTag(id: number) {
  return request.delete<Result<void>>(`/v1/admin/tags/${id}`)
}
