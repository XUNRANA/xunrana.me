import axios from 'axios'
import type { Result } from '@/types/api'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as Result<unknown>
    if (result.code !== 200) {
      return Promise.reject(new Error(result.message))
    }
    return response
  },
  async (error) => {
    const originalRequest = error.config
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      const userStore = useUserStore()
      try {
        await userStore.refresh()
        originalRequest.headers.Authorization = `Bearer ${userStore.token}`
        return request(originalRequest)
      } catch {
        userStore.logout()
        router.push('/admin/login')
        return Promise.reject(error)
      }
    }
    return Promise.reject(error)
  }
)

export default request
