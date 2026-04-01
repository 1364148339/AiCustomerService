import { createRouter, createWebHistory } from 'vue-router'
import DevicesView from '../views/DevicesView.vue'
import ScenariosView from '../views/ScenariosView.vue'
import TaskListView from '../views/TaskListView.vue'
import TasksNewView from '../views/TasksNewView.vue'
import TaskDetailView from '../views/TaskDetailView.vue'
import LogsView from '../views/LogsView.vue'
import AlertsView from '../views/AlertsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/devices' },
    { path: '/devices', name: 'devices', component: DevicesView },
    { path: '/scenarios', name: 'scenarios', component: ScenariosView },
    { path: '/tasks', name: 'tasks', component: TaskListView },
    { path: '/tasks/new', name: 'task-new', component: TasksNewView },
    { path: '/tasks/:id', name: 'task-detail', component: TaskDetailView, props: true },
    { path: '/logs', name: 'logs', component: LogsView },
    { path: '/alerts', name: 'alerts', component: AlertsView }
  ]
})

export default router
