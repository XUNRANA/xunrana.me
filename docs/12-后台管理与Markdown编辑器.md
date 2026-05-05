# 12 - 后台管理与 Markdown 编辑器

> 本文档讲解后台管理系统的页面设计、Element Plus 组件使用、Markdown 编辑器集成、图片上传对接，以及前后台页面的核心实现细节。

---

## 目录

1. [后台管理系统概述](#1-后台管理系统概述)
2. [后台布局设计](#2-后台布局设计)
3. [登录页面](#3-登录页面)
4. [仪表盘](#4-仪表盘)
5. [文章管理（CRUD）](#5-文章管理crud)
6. [分类与标签管理](#6-分类与标签管理)
7. [评论审核](#7-评论审核)
8. [操作日志](#8-操作日志)
9. [Markdown 编辑器集成](#9-markdown-编辑器集成)
10. [图片上传对接](#10-图片上传对接)
11. [前台博客页面](#11-前台博客页面)
12. [面试常见问题](#12-面试常见问题)

---

## 1. 后台管理系统概述

### 1.1 后台功能清单

| 页面 | 路由 | 功能 |
|------|------|------|
| 登录 | `/admin/login` | 用户名密码登录，获取 JWT |
| 仪表盘 | `/admin/dashboard` | 文章/分类/标签/评论数量统计 |
| 文章管理 | `/admin/articles` | 文章列表、新建、编辑、删除 |
| 分类管理 | `/admin/categories` | 分类 CRUD |
| 标签管理 | `/admin/tags` | 标签 CRUD |
| 评论管理 | `/admin/comments` | 评论列表、审核通过/拒绝 |
| 操作日志 | `/admin/logs` | 查看操作审计记录 |

### 1.2 Element Plus 组件使用

本项目大量使用 Element Plus 组件：

| 组件 | 用途 |
|------|------|
| `el-container` / `el-aside` / `el-main` | 页面布局 |
| `el-menu` / `el-menu-item` | 侧边栏导航菜单 |
| `el-table` / `el-table-column` | 数据表格 |
| `el-form` / `el-form-item` / `el-input` | 表单 |
| `el-dialog` | 弹窗 |
| `el-card` | 卡片容器 |
| `el-pagination` | 分页 |
| `el-tag` | 标签/状态标记 |
| `el-popconfirm` | 删除确认气泡 |
| `el-dropdown` | 用户菜单下拉 |

---

## 2. 后台布局设计

### 2.1 经典后台布局

后台采用经典的「左侧菜单 + 顶部栏 + 内容区」布局：

```
┌──────────┬───────────────────────────────┐
│          │  顶部栏（折叠按钮 + 用户信息）  │
│  侧边栏  ├───────────────────────────────┤
│  （菜单） │                               │
│          │        内容区                  │
│          │                               │
│          │                               │
└──────────┴───────────────────────────────┘
```

### 2.2 侧边栏实现

```vue
<el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'">
  <el-menu
    :default-active="route.path"
    :collapse="appStore.sidebarCollapsed"
    router
  >
    <el-menu-item index="/admin/dashboard">
      <el-icon><Monitor /></el-icon>
      <template #title>仪表盘</template>
    </el-menu-item>
    <!-- 其他菜单项 -->
  </el-menu>
</el-aside>
```

关键配置：
- `:collapse` — 控制菜单折叠状态，由 `useAppStore` 管理
- `router` — 让 `el-menu-item` 的 `index` 作为路由路径自动跳转
- `:default-active` — 高亮当前路由对应的菜单项

### 2.3 顶部栏

```vue
<el-header>
  <el-icon @click="appStore.toggleSidebar">
    <Fold v-if="!appStore.sidebarCollapsed" />
    <Expand v-else />
  </el-icon>
  <el-dropdown @command="handleCommand">
    <span>{{ userStore.userInfo?.nickname || 'Admin' }}</span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="logout">退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</el-header>
```

---

## 3. 登录页面

### 3.1 登录表单

```vue
<el-form ref="formRef" :model="form" :rules="rules">
  <el-form-item prop="username">
    <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
  </el-form-item>
  <el-form-item prop="password">
    <el-input v-model="form.password" type="password" placeholder="密码" show-password />
  </el-form-item>
  <el-button type="primary" @click="handleLogin">登录</el-button>
</el-form>
```

### 3.2 登录流程

```
用户输入用户名密码
  → 表单校验（rules）
  → 调用 userStore.login(username, password)
    → POST /v1/auth/login
    → 成功：保存 token + refreshToken 到 localStorage
  → 调用 userStore.fetchUserInfo()
    → GET /v1/auth/info
  → 跳转 /admin/dashboard
```

### 3.3 表单校验

```typescript
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
```

Element Plus 的表单校验是声明式的：在 `rules` 中定义校验规则，在 `el-form-item` 上通过 `prop` 绑定字段名，提交前调用 `formRef.validate()` 统一校验。

---

## 4. 仪表盘

### 4.1 统计卡片

```vue
<el-row :gutter="20">
  <el-col :span="6">
    <el-card>
      <template #header>文章数</template>
      <div class="stat-value">{{ stats.articles }}</div>
    </el-card>
  </el-col>
  <!-- 其他统计卡片 -->
</el-row>
```

### 4.2 数据聚合

仪表盘的数据来自多个 API 的聚合：

```typescript
const [articlesRes, categoriesRes, tagsRes] = await Promise.all([
  getArticles({ current: 1, size: 1 }),  // 只取 total
  getCategories(),
  getTags(),
])
stats.value.articles = articlesRes.data.data.total
stats.value.categories = categoriesRes.data.data.length
stats.value.tags = tagsRes.data.data.length
```

用 `Promise.all` 并行请求，比串行快。

---

## 5. 文章管理（CRUD）

### 5.1 列表展示

```vue
<el-table :data="articles" v-loading="loading" stripe>
  <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
  <el-table-column prop="categoryName" label="分类" width="120" />
  <el-table-column label="状态" width="80">
    <template #default="{ row }">
      <el-tag :type="row.status === 1 ? 'success' : 'info'">
        {{ row.status === 1 ? '已发布' : '草稿' }}
      </el-tag>
    </template>
  </el-table-column>
  <el-table-column prop="viewCount" label="浏览量" width="80" />
  <el-table-column prop="createdAt" label="创建时间" width="170" />
  <el-table-column label="操作" width="160">
    <template #default="{ row }">
      <el-button @click="openDialog(row)">编辑</el-button>
      <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
        <template #reference>
          <el-button type="danger">删除</el-button>
        </template>
      </el-popconfirm>
    </template>
  </el-table-column>
</el-table>
```

### 5.2 新增/编辑弹窗

弹窗复用同一个 `el-dialog`，通过 `editingId` 区分新增和编辑：

```typescript
function openDialog(row?: ArticleVO) {
  if (row) {
    editingId.value = row.id
    form.value = { ...row, tagIds: row.tags?.map(t => t.id) || [] }
  } else {
    editingId.value = null
    form.value = defaultForm()
  }
  dialogVisible.value = true
}
```

### 5.3 分页

```vue
<el-pagination
  v-model:current-page="query.current"
  v-model:page-size="query.size"
  :total="total"
  layout="total, prev, pager, next"
  @current-change="fetchArticles"
/>
```

`v-model:current-page` 双向绑定当前页码，翻页时自动触发 `fetchArticles` 重新请求数据。

---

## 6. 分类与标签管理

分类和标签管理的结构几乎一样：表格 + 新增/编辑弹窗。区别在于字段数量：

- **分类**：name, slug, description
- **标签**：name, slug

实现模式完全相同，只是表单字段不同。

---

## 7. 评论审核

### 7.1 审核操作

```vue
<el-table-column label="操作">
  <template #default="{ row }">
    <el-button v-if="row.status !== 1" type="success" @click="handleReview(row.id, 1)">通过</el-button>
    <el-button v-if="row.status !== 2" type="warning" @click="handleReview(row.id, 2)">拒绝</el-button>
  </template>
</el-table-column>
```

审核调用 `PUT /v1/admin/comments/{id}/status?status={status}`，status 取值：
- 0：待审核
- 1：通过
- 2：拒绝

---

## 8. 操作日志

操作日志页面是纯只读的，只展示表格和分页。数据来自后端的 `operation_log` 表，由 `OperationLogAspect` 自动记录。

---

## 9. Markdown 编辑器集成

### 9.1 md-editor-v3

本项目使用 `md-editor-v3` 作为 Markdown 编辑和预览组件。它支持：
- 实时编辑 + 预览
- 代码高亮
- 工具栏（加粗、斜体、标题、链接、图片等）
- 图片上传钩子
- 主题切换

### 9.2 编辑器配置

```vue
<template>
  <MdEditor v-model="form.content" style="height: 400px" @onUploadImg="handleUploadImg" />
</template>

<script setup>
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
</script>
```

- `v-model` 双向绑定 Markdown 源码
- `@onUploadImg` — 用户在编辑器中插入图片时触发，需要返回图片 URL 数组

### 9.3 预览组件

文章详情页使用 `MdPreview` 渲染 Markdown：

```vue
<template>
  <MdPreview :modelValue="article.content" />
</template>

<script setup>
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
</script>
```

---

## 10. 图片上传对接

### 10.1 编辑器内图片上传

当用户在 Markdown 编辑器中点击「上传图片」按钮时，`@onUploadImg` 被触发：

```typescript
async function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  const urls = await Promise.all(
    files.map(async (file) => {
      const { data } = await uploadImage(file)
      return data.data.url  // 后端返回的图片 URL
    })
  )
  callback(urls)  // 把 URL 传回编辑器，自动插入 Markdown
}
```

流程：
1. 用户选择图片文件
2. 调用 `uploadImage` API（POST `/v1/admin/files/upload/image`）
3. 后端保存文件，返回 `{ url, filename }`
4. 将 URL 传给 `callback`，编辑器自动插入 `![image](url)`

### 10.2 文件上传 API

```typescript
// api/file.ts
export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Result<FileUploadVO>>('/v1/admin/files/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
```

`FormData` 让 Axios 以 `multipart/form-data` 格式发送文件，和 HTML 表单上传效果一样。

---

## 11. 前台博客页面

### 11.1 首页布局

首页采用「左侧文章列表 + 右侧边栏」的经典博客布局：

```
┌─────────────────────────────┬──────────┐
│                             │  分类    │
│  文章卡片 1                 │  - Java  │
│  文章卡片 2                 │  - Vue   │
│  文章卡片 3                 │          │
│                             │  标签    │
│  [1] [2] [3] ... 分页      │  Spring  │
│                             │  Redis   │
└─────────────────────────────┴──────────┘
```

### 11.2 文章详情页

文章详情页包含：
1. 文章标题 + 元信息（分类、标签、浏览量、时间）
2. Markdown 渲染（`MdPreview`）
3. 评论区（评论列表 + 发表表单）

评论列表支持树形结构（父子评论），后端返回的 `CommentVO` 已经包含 `children` 数组。

### 11.3 分类/标签页

分类页和标签页的交互模式相同：
1. 展示所有分类/标签
2. 点击某个分类/标签，筛选显示对应的文章列表
3. URL 通过 query 参数同步状态（`?slug=java`）

---

## 12. 面试常见问题

### Q1: Element Plus 的表单校验是怎么工作的？

Element Plus 的表单校验基于 `async-validator` 库。在 `el-form` 上绑定 `:model` 和 `:rules`，在 `el-form-item` 上通过 `prop` 指定字段名。调用 `formRef.validate()` 会遍历所有 `el-form-item`，根据 `rules` 中定义的规则校验对应字段。校验失败会在表单项下方显示错误信息。

### Q2: 如何实现 Token 无感续期？

请求拦截器在每次请求时附带 Token。响应拦截器捕获 401 错误，用 refreshToken 请求新的 accessToken，成功后重试原请求。用 `_retry` 标记防止无限重试。这个过程对用户完全透明。

### Q3: Markdown 编辑器的图片上传流程？

编辑器触发 `@onUploadImg` 回调 → 调用后端文件上传接口 → 后端保存文件并返回 URL → 将 URL 传回编辑器的 callback → 编辑器自动插入 `![image](url)` Markdown 语法。

### Q4: 前后端分离项目的跨域问题怎么解决？

开发环境用 Vite 的 proxy 代理，把 `/api` 请求转发到后端。生产环境有两种方案：
1. Nginx 反向代理：前端和后端部署在同一个域名下，Nginx 根据路径转发
2. 后端配置 CORS：允许前端域名的跨域请求

本项目后端已经配置了 CORS（`CorsConfig.java`），但生产环境推荐用 Nginx 代理。
