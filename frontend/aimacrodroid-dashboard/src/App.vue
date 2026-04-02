<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from './stores/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/tasks')) return '/tasks'
  return route.path
})
</script>

<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="sidebar">
      <div class="logo-box">
        <h1>AiMacroDroid</h1>
        <p class="subtitle">Phase1 可靠与透明看板</p>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical"
        router
      >
        <el-menu-item index="/devices">
          <span>设备</span>
        </el-menu-item>
        <el-menu-item index="/scenarios">
          <span>场景</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <span>任务列表</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <span>日志</span>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <span>告警</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="topbar">
        <div class="summary">{{ appStore.summaryText }}</div>
        <div class="refresh-time">最后刷新：{{ appStore.lastRefreshAt || '--' }}</div>
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
  border-right: solid 1px var(--el-menu-border-color);
  background-color: #fff;
}
.logo-box {
  padding: 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.logo-box h1 {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}
.subtitle {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.el-menu-vertical {
  border-right: none;
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.content-main {
  background-color: var(--el-bg-color-page);
  padding: 20px;
}
</style>
