import request from '@/utils/request'
import type { Result } from '@/types/api'

interface FileUploadVO {
  url: string
  filename: string
}

export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Result<FileUploadVO>>('/v1/admin/files/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
