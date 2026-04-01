<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useScenariosStore } from '../stores/scenarios'

const router = useRouter()
const scenariosStore = useScenariosStore()
const rows = computed(() => scenariosStore.list)
const loading = computed(() => scenariosStore.loading)
const stepsDraft = computed(() => scenariosStore.stepsDraft)
const detail = computed(() => scenariosStore.detail)
const detailLoading = computed(() => scenariosStore.detailLoading)
const detailVisible = ref(false)
const createVisible = ref(false)
const saving = ref(false)
const createForm = ref({
  scenarioName: '',
  scenarioKey: '',
  description: '',
  taskType: 'CHECKIN'
})

function statusTagType(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DEPRECATED') return 'warning'
  return 'info'
}

async function openDetail(row) {
  detailVisible.value = true
  await scenariosStore.refreshScenarioDetail(row.scenarioKey)
}

async function saveSteps() {
  saving.value = true
  try {
    await scenariosStore.persistSteps()
    ElMessage.success('步骤已保存')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function createScenarioAction() {
  if (!createForm.value.scenarioName || !createForm.value.scenarioKey) {
    ElMessage.warning('请填写场景名称和场景标识')
    return
  }
  try {
    const created = await scenariosStore.addScenario(createForm.value)
    createVisible.value = false
    createForm.value = { scenarioName: '', scenarioKey: '', description: '', taskType: 'CHECKIN' }
    ElMessage.success(`已创建场景 ${created.scenarioKey}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '创建场景失败')
  }
}

onMounted(async () => {
  await scenariosStore.refreshScenarios()
  scenariosStore.startPolling()
})

onBeforeUnmount(() => {
  scenariosStore.stopPolling()
})
</script>

<template>
  <div class="scenarios-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>场景管理与步骤编排</span>
          <div class="header-actions">
            <el-tag type="success">ACTIVE {{ scenariosStore.activeCount }}</el-tag>
            <el-button @click="scenariosStore.refreshScenarios" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="createVisible = true">新建场景</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="scenarioKey" label="场景标识" min-width="160" />
        <el-table-column prop="scenarioName" label="场景名称" min-width="180" />
        <el-table-column prop="versionNo" label="版本" width="90" align="center" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">编排步骤</el-button>
            <el-button link @click="router.push(`/tasks/new?scenarioKey=${row.scenarioKey}`)">创建任务</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建场景" width="520px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="场景名称">
          <el-input v-model="createForm.scenarioName" />
        </el-form-item>
        <el-form-item label="场景标识">
          <el-input v-model="createForm.scenarioKey" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="createForm.taskType" style="width: 100%">
            <el-option label="签到任务" value="CHECKIN" />
            <el-option label="视频领奖任务" value="VIDEO_REWARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createScenarioAction">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detail ? `${detail.scenarioName} (${detail.scenarioKey})` : '场景详情'" size="70%">
      <div v-loading="detailLoading">
        <div class="detail-actions">
          <el-button @click="scenariosStore.appendStep">新增步骤</el-button>
          <el-button type="primary" :loading="saving" @click="saveSteps">保存步骤</el-button>
        </div>
        <el-table :data="stepsDraft" border>
          <el-table-column prop="orderNo" label="序号" width="72" align="center" />
          <el-table-column label="步骤名" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.stepName" />
            </template>
          </el-table-column>
          <el-table-column label="动作" width="160">
            <template #default="{ row }">
              <el-input v-model="row.action" />
            </template>
          </el-table-column>
          <el-table-column label="参数(JSON)" min-width="240">
            <template #default="{ row }">
              <el-input v-model="row.paramsText" type="textarea" :rows="3" />
            </template>
          </el-table-column>
          <el-table-column label="超时(ms)" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.timeoutMs" :min="0" :step="1000" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="重试" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.retryPolicy.maxRetries" :min="0" :max="10" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="启用" width="90" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ $index }">
              <el-button link @click="scenariosStore.moveStep($index, 'up')">上移</el-button>
              <el-button link @click="scenariosStore.moveStep($index, 'down')">下移</el-button>
              <el-button link type="danger" @click="scenariosStore.removeStep($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.detail-actions {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
</style>
