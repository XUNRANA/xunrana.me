import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserVO } from '@/types/auth'
import * as authApi from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const refreshTokenValue = ref(localStorage.getItem('refreshToken') || '')
  const userInfo = ref<UserVO | null>(null)

  async function login(username: string, password: string) {
    const { data } = await authApi.login({ username, password })
    token.value = data.data.accessToken
    refreshTokenValue.value = data.data.refreshToken
    localStorage.setItem('token', data.data.accessToken)
    localStorage.setItem('refreshToken', data.data.refreshToken)
  }

  async function refresh() {
    const { data } = await authApi.refreshToken(refreshTokenValue.value)
    token.value = data.data.accessToken
    refreshTokenValue.value = data.data.refreshToken
    localStorage.setItem('token', data.data.accessToken)
    localStorage.setItem('refreshToken', data.data.refreshToken)
  }

  async function fetchUserInfo() {
    const { data } = await authApi.getUserInfo()
    userInfo.value = data.data
  }

  function logout() {
    token.value = ''
    refreshTokenValue.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }

  return { token, refreshToken: refreshTokenValue, userInfo, login, refresh, fetchUserInfo, logout }
})
