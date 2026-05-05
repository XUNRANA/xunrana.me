import request from '@/utils/request'
import type { Result } from '@/types/api'
import type { LoginDTO, LoginVO, UserVO } from '@/types/auth'

export function login(data: LoginDTO) {
  return request.post<Result<LoginVO>>('/v1/auth/login', data)
}

export function refreshToken(refreshToken: string) {
  return request.post<Result<LoginVO>>('/v1/auth/refresh', null, {
    params: { refreshToken },
  })
}

export function logout() {
  return request.post<Result<void>>('/v1/auth/logout')
}

export function getUserInfo() {
  return request.get<Result<UserVO>>('/v1/auth/info')
}
