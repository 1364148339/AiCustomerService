import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import DevicesView from '../views/DevicesView.vue'
import TasksNewView from '../views/TasksNewView.vue'
import TaskDetailView from '../views/TaskDetailView.vue'
import LogsView from '../views/LogsView.vue'
import AlertsView from '../views/AlertsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/devices', name: 'devices', component: DevicesView },
    { path: '/tasks/new', name: 'task-new', component: TasksNewView },
    { path: '/tasks/:id', name: 'task-detail', component: TaskDetailView, props: true },
    { path: '/logs', name: 'logs', component: LogsView },
    { path: '/alerts', name: 'alerts', component: AlertsView }
  ]
})

export default router
