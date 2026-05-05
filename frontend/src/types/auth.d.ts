export interface LoginDTO {
  username: string
  password: string
}

export interface LoginVO {
  accessToken: string
  refreshToken: string
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  avatar: string
  role: number
}
