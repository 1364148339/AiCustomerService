<script setup>
import { computed, h } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from './stores/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/tasks')) return '/tasks'
  return route.path
})

function createIcon(pathD) {
  return {
    render() {
      return h(
        'svg',
        {
          viewBox: '0 0 24 24',
          fill: 'none',
          xmlns: 'http://www.w3.org/2000/svg',
          'aria-hidden': 'true'
        },
        [
          h('path', {
            d: pathD,
            stroke: 'currentColor',
            'stroke-width': '1.8',
            'stroke-linecap': 'round',
            'stroke-linejoin': 'round'
          })
        ]
      )
    }
  }
}

const DeviceIcon = createIcon('M9 4.75h6a1.75 1.75 0 0 1 1.75 1.75v11a1.75 1.75 0 0 1-1.75 1.75H9a1.75 1.75 0 0 1-1.75-1.75v-11A1.75 1.75 0 0 1 9 4.75ZM10 17.25h4M10.75 7.75h2.5')
const ScenarioIcon = createIcon('M6.75 7.25h10.5M6.75 12h10.5M6.75 16.75h6.5M5.5 4.75h13a1.75 1.75 0 0 1 1.75 1.75v11a1.75 1.75 0 0 1-1.75 1.75h-13a1.75 1.75 0 0 1-1.75-1.75v-11A1.75 1.75 0 0 1 5.5 4.75Z')
const TaskIcon = createIcon('M8 7.25h9M8 12h9M8 16.75h9M5.25 7.25h.01M5.25 12h.01M5.25 16.75h.01')
const LogIcon = createIcon('M7.25 5.75h9.5M7.25 10.75h9.5M7.25 15.75h5.5M5.5 4.25h13a1.25 1.25 0 0 1 1.25 1.25v13A1.25 1.25 0 0 1 18.5 19.75h-13a1.25 1.25 0 0 1-1.25-1.25v-13A1.25 1.25 0 0 1 5.5 4.25Z')
const AlertIcon = createIcon('M12 8.25v4.5M12 16.5h.01M10.29 4.84 4.87 14.25a1.75 1.75 0 0 0 1.52 2.63h11.22a1.75 1.75 0 0 0 1.52-2.63l-5.42-9.41a1.75 1.75 0 0 0-3.42 0Z')
const StatusIcon = createIcon('M12 6.75v5.25l3.5 2M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z')
const SparkIcon = createIcon('M12 3.75 13.8 8.2 18.25 10 13.8 11.8 12 16.25 10.2 11.8 5.75 10 10.2 8.2 12 3.75Z')
const RefreshIcon = createIcon('M18.25 8.5V4.75H14.5M5.75 15.5v3.75H9.5M6.7 8.2A6.5 6.5 0 0 1 17.7 6M17.3 15.8A6.5 6.5 0 0 1 6.3 18')

const navItems = [
  { index: '/devices', label: '设备', icon: DeviceIcon },
  { index: '/scenarios', label: '场景', icon: ScenarioIcon },
  { index: '/tasks', label: '任务列表', icon: TaskIcon },
  { index: '/logs', label: '日志', icon: LogIcon },
  { index: '/alerts', label: '告警', icon: AlertIcon }
]

const currentPageTitle = computed(() => {
  const map = {
    '/devices': '设备总览',
    '/scenarios': '场景编排',
    '/tasks': '任务列表',
    '/logs': '运行日志',
    '/alerts': '告警中心'
  }
  return map[activeMenu.value] || '控制台'
})
</script>

<template>
  <el-container class="layout-container">
    <el-aside width="260px" class="sidebar">
      <div class="sidebar-shell">
        <div class="logo-box">
          <div class="brand-mark">AI</div>
          <div class="brand-copy">
            <h1>AiMacroDroid</h1>
            <p class="subtitle">可靠与透明看板</p>
          </div>
        </div>

        <div class="nav-caption">工作台导航</div>
        <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical"
          router
        >
          <el-menu-item v-for="item in navItems" :key="item.index" :index="item.index">
            <span class="menu-icon">
              <component :is="item.icon" />
            </span>
            <span class="menu-label">{{ item.label }}</span>
          </el-menu-item>
        </el-menu>

        <div class="sidebar-footer">
          <div class="footer-header">
            <span class="footer-icon">
              <StatusIcon />
            </span>
            <span class="footer-label">当前状态</span>
          </div>
          <div class="footer-value">{{ appStore.summaryText }}</div>
        </div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="topbar-main">
          <div class="topbar-badge">
            <SparkIcon />
            <span>控制台</span>
          </div>
          <div class="page-title">{{ currentPageTitle }}</div>
          <div class="summary">{{ appStore.summaryText }}</div>
        </div>

        <div class="topbar-side">
          <div class="status-capsule">
            <div class="status-capsule__icon">
              <RefreshIcon />
            </div>
            <div class="status-capsule__content">
              <div class="status-capsule__row">
                <span class="status-dot"></span>
                <span class="status-capsule__online">系统在线</span>
              </div>
              <div class="status-capsule__meta">最后刷新 {{ appStore.lastRefreshAt || '--' }}</div>
            </div>
          </div>
        </div>
      </el-header>

      <el-main class="content-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  padding: 14px;
  background: linear-gradient(180deg, #0f172a 0%, #111827 100%);
}

.sidebar-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.96) 0%, rgba(17, 24, 39, 0.92) 100%);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.logo-box {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.18) 0%, rgba(37, 99, 235, 0.08) 100%);
  border: 1px solid rgba(96, 165, 250, 0.18);
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #eff6ff;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.28);
}

.brand-copy h1 {
  margin: 0;
  font-size: 20px;
  color: #f8fafc;
  letter-spacing: 0.02em;
}

.subtitle {
  margin: 6px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.nav-caption {
  margin: 22px 10px 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: #64748b;
}

.el-menu-vertical {
  flex: 1;
  border-right: none;
  background: transparent;
}

.el-menu-vertical :deep(.el-menu) {
  background: transparent;
}

.el-menu-vertical :deep(.el-menu-item) {
  position: relative;
  height: 52px;
  margin-bottom: 8px;
  padding-left: 14px;
  border-radius: 14px;
  color: #cbd5e1;
  background: transparent;
  transition: transform 0.18s ease, background-color 0.18s ease, box-shadow 0.2s ease, color 0.18s ease;
}

.el-menu-vertical :deep(.el-menu-item::before) {
  content: '';
  position: absolute;
  left: 8px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: transparent;
  transition: background-color 0.18s ease, opacity 0.18s ease;
  opacity: 0;
}

.el-menu-vertical :deep(.el-menu-item:hover) {
  transform: translateX(3px);
  color: #f8fafc;
  background: linear-gradient(135deg, rgba(51, 65, 85, 0.78) 0%, rgba(30, 41, 59, 0.72) 100%);
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.08);
}

.el-menu-vertical :deep(.el-menu-item:hover::before) {
  opacity: 1;
  background: rgba(96, 165, 250, 0.55);
}

.el-menu-vertical :deep(.el-menu-item.is-active) {
  transform: translateX(6px);
  color: #eff6ff;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.98) 0%, rgba(37, 99, 235, 0.94) 100%);
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.3);
}

.el-menu-vertical :deep(.el-menu-item.is-active::before) {
  opacity: 1;
  background: rgba(255, 255, 255, 0.88);
}

.menu-icon {
  width: 28px;
  height: 28px;
  margin-right: 12px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: currentColor;
  background: rgba(148, 163, 184, 0.14);
  transition: transform 0.18s ease, background-color 0.18s ease, box-shadow 0.2s ease;
}

.menu-icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.el-menu-vertical :deep(.el-menu-item:hover) .menu-icon {
  transform: scale(1.04);
  background: rgba(148, 163, 184, 0.2);
}

.el-menu-vertical :deep(.el-menu-item.is-active) .menu-icon {
  background: rgba(255, 255, 255, 0.18);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
}

.menu-label {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.sidebar-footer {
  margin-top: 12px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.58);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.footer-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #93c5fd;
}

.footer-icon :deep(svg) {
  width: 15px;
  height: 15px;
}

.footer-label {
  font-size: 12px;
  color: #64748b;
}

.footer-value {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: #e2e8f0;
}

.topbar {
  height: auto;
  min-height: 88px;
  overflow: visible;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(248, 250, 252, 0.96) 100%);
  border-bottom: 1px solid #e5e7eb;
  backdrop-filter: blur(10px);
  box-sizing: border-box;
}

.topbar-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.topbar-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #1d4ed8;
  background: #dbeafe;
}

.topbar-badge :deep(svg) {
  width: 14px;
  height: 14px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.15;
  color: #0f172a;
}

.summary {
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
}

.topbar-side {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.status-capsule {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  border: 1px solid #dbe7f3;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.status-capsule__icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
  background: #eff6ff;
  flex-shrink: 0;
}

.status-capsule__icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.status-capsule__content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.status-capsule__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.12);
}

.status-capsule__online {
  font-size: 12px;
  font-weight: 600;
  color: #166534;
}

.status-capsule__meta {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.content-main {
  background-color: var(--el-bg-color-page);
  padding: 16px 20px 20px;
}

@media (max-width: 1100px) {
  .topbar {
    align-items: flex-start;
    padding: 12px 16px;
  }

  .topbar-side {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 900px) {
  .sidebar {
    width: 220px !important;
    padding: 10px;
  }

  .sidebar-shell {
    border-radius: 20px;
    padding: 10px;
  }

  .logo-box {
    padding: 12px;
  }

  .brand-mark {
    width: 42px;
    height: 42px;
    border-radius: 12px;
  }

  .page-title {
    font-size: 19px;
  }

  .el-menu-vertical :deep(.el-menu-item:hover),
  .el-menu-vertical :deep(.el-menu-item.is-active) {
    transform: translateX(0);
  }
}

@media (max-width: 720px) {
  .topbar {
    gap: 10px;
  }

  .topbar-main {
    gap: 4px;
  }

  .summary {
    display: none;
  }

  .status-capsule {
    width: 100%;
  }

  .status-capsule__meta {
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
