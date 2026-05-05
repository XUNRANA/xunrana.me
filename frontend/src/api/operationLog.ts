import request from '@/utils/request'
import type { Result, PageResult, PageQuery } from '@/types/api'
import type { OperationLogVO } from '@/types/operationLog'

export function getOperationLogs(params: PageQuery) {
  return request.get<Result<PageResult<OperationLogVO>>>('/v1/admin/logs', { params })
}
