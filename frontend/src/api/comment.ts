import request from '@/utils/request'
import type { Result } from '@/types/api'
import type { CommentVO, CommentDTO } from '@/types/comment'

export function getComments(articleId: number) {
  return request.get<Result<CommentVO[]>>(`/v1/articles/${articleId}/comments`)
}

export function createComment(articleId: number, data: CommentDTO) {
  return request.post<Result<void>>(`/v1/articles/${articleId}/comments`, data)
}

export function updateCommentStatus(id: number, status: number) {
  return request.put<Result<void>>(`/v1/admin/comments/${id}/status`, null, {
    params: { status },
  })
}
