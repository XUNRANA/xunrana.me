export interface OperationLogVO {
  id: number
  module: string
  operation: string
  method: string
  params: string
  ip: string
  userId: number
  username: string
  createdAt: string
}
