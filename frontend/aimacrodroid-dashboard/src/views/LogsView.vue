<script setup>
import { ref } from 'vue'
import { getLogs, getTasks } from '../mock/api'

const filters = ref({ taskId: '', deviceId: '' })
const rows = ref([])
const tasks = ref([])
const loading = ref(false)

async function init() {
  loading.value = true
  try {
    tasks.value = await getTasks()
    await query()
  } finally {
    loading.value = false
  }
}

async function query() {
  loading.value = true
  try {
    rows.value = await getLogs(filters.value)
  } finally {
    loading.value = false
  }
}

init()
</script>

<template>
  <div class="logs-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>日志检索</span>
        </div>
      </template>

      <el-form :inline="true" :model="filters" class="demo-form-inline">
        <el-form-item label="任务">
          <el-select v-model="filters.taskId" placeholder="全部任务" clearable style="width: 200px">
            <el-option
              v-for="item in tasks"
              :key="item.taskId"
              :label="item.taskId"
              :value="item.taskId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-input v-model="filters.deviceId" placeholder="deviceId，如 dev-01" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query" :loading="loading">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border style="width: 100%">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ new Date(row.timestamp).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务" width="150" />
        <el-table-column prop="deviceId" label="设备" width="150" />
        <el-table-column prop="level" label="级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.level === 'ERROR' ? 'danger' : row.level === 'WARN' ? 'warning' : 'info'"
              effect="plain"
            >
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="内容" min-width="300" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  font-weight: bold;
}
</style>
