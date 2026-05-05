<template>
  <div class="blog-layout">
    <!-- Geometric accent line at very top -->
    <div class="top-accent"></div>

    <header class="blog-header">
      <div class="header-inner">
        <router-link to="/" class="logo-link">
          <span class="logo-text">XUNRANA</span>
          <span class="logo-dot">.</span>
        </router-link>
        <nav class="nav">
          <router-link to="/" class="nav-link" exact-active-class="active">
            <span class="nav-index">01</span>首页
          </router-link>
          <router-link to="/category" class="nav-link" active-class="active">
            <span class="nav-index">02</span>分类
          </router-link>
          <router-link to="/tag" class="nav-link" active-class="active">
            <span class="nav-index">03</span>标签
          </router-link>
          <router-link to="/archives" class="nav-link" active-class="active">
            <span class="nav-index">04</span>归档
          </router-link>
          <span class="nav-divider"></span>
          <router-link to="/admin/login" class="nav-link nav-admin">
            <span class="nav-index">//</span>管理
          </router-link>
        </nav>
      </div>
    </header>

    <main class="blog-main page-enter">
      <router-view />
    </main>

    <footer class="blog-footer">
      <div class="footer-inner">
        <div class="footer-left">
          <span class="footer-logo">XUNRANA</span>
          <span class="footer-sep">&mdash;</span>
          <span class="footer-tagline">A blog about code, systems, and craft.</span>
        </div>
        <div class="footer-right">
          <span class="mono-label">&copy; {{ new Date().getFullYear() }}</span>
        </div>
      </div>
      <!-- Bottom geometric bar -->
      <div class="footer-bar"></div>
    </footer>
  </div>
</template>

<style scoped>
.blog-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* Thin terracotta accent at the very top */
.top-accent {
  height: 3px;
  background: var(--accent);
}

/* ---- Header ---- */
.blog-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(12px);
}

.header-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 var(--space-xl);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo-link {
  text-decoration: none;
  display: flex;
  align-items: baseline;
}

.logo-text {
  font-family: var(--font-mono);
  font-size: 1.1rem;
  font-weight: 500;
  letter-spacing: 0.15em;
  color: var(--text-primary);
}

.logo-dot {
  font-family: var(--font-display);
  font-size: 2rem;
  color: var(--accent);
  line-height: 0;
  margin-left: -2px;
  margin-bottom: -4px;
}

/* ---- Navigation ---- */
.nav {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.nav-link {
  font-family: var(--font-body);
  font-size: 0.85rem;
  font-weight: 400;
  color: var(--text-secondary);
  text-decoration: none;
  display: flex;
  align-items: baseline;
  gap: 6px;
  transition: color 0.2s ease;
  position: relative;
}

.nav-link:hover {
  color: var(--text-primary);
}

.nav-link.active {
  color: var(--text-primary);
}

.nav-link.active::after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent);
}

.nav-index {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.05em;
  color: var(--text-tertiary);
  transition: color 0.2s ease;
}

.nav-link:hover .nav-index,
.nav-link.active .nav-index {
  color: var(--accent);
}

.nav-divider {
  width: 1px;
  height: 16px;
  background: var(--border);
}

.nav-admin {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  letter-spacing: 0.05em;
}

/* ---- Main ---- */
.blog-main {
  flex: 1;
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--space-2xl) var(--space-xl);
  width: 100%;
}

/* ---- Footer ---- */
.blog-footer {
  border-top: 1px solid var(--border);
  margin-top: auto;
}

.footer-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--space-xl);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.footer-logo {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 500;
  letter-spacing: 0.1em;
  color: var(--text-primary);
}

.footer-sep {
  color: var(--text-tertiary);
}

.footer-tagline {
  font-size: 0.8rem;
  color: var(--text-tertiary);
  font-style: italic;
  font-family: var(--font-display);
}

.footer-bar {
  height: 3px;
  background: linear-gradient(90deg, var(--accent) 0%, var(--accent) 33%, transparent 33%);
}

/* ---- Responsive ---- */
@media (max-width: 768px) {
  .header-inner {
    padding: 0 var(--space-md);
  }

  .nav {
    gap: var(--space-md);
  }

  .nav-index {
    display: none;
  }

  .nav-divider {
    display: none;
  }

  .blog-main {
    padding: var(--space-xl) var(--space-md);
  }

  .footer-inner {
    flex-direction: column;
    gap: var(--space-md);
    text-align: center;
  }
}
</style>
