export interface CommentVO {
  id: number
  articleId: number
  articleTitle: string
  parentId: number | null
  nickname: string
  content: string
  status: number
  createdAt: string
  children: CommentVO[]
}

export interface CommentDTO {
  nickname: string
  email: string
  content: string
  parentId: number | null
}
