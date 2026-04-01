<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAlerts } from '../mock/api'

const router = useRouter()
const taskId = ref('')
const rows = ref([])
const loading = ref(false)

async function query() {
  loading.value = true
  try {
    rows.value = await getAlerts({ taskId: taskId.value })
  } finally {
    loading.value = false
  }
}

query()
</script>

<template>
  <div class="alerts-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>告警列表</span>
        </div>
      </template>

      <el-form :inline="true" class="demo-form-inline">
        <el-form-item label="任务">
          <el-input v-model="taskId" placeholder="按 taskId 过滤" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query" :loading="loading">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border style="width: 100%">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务" width="150" />
        <el-table-column prop="deviceId" label="设备" width="150" />
        <el-table-column prop="type" label="类型" width="150" />
        <el-table-column prop="code" label="错误码" min-width="150" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'" effect="plain">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/tasks/${row.taskId}`)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  font-weight: bold;
}
</style>
