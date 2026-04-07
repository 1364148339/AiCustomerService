<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useScenariosStore } from '../stores/scenarios'

const scenariosStore = useScenariosStore()
const rows = computed(() => scenariosStore.list)
const loading = computed(() => scenariosStore.loading)
const stepsDraft = computed(() => scenariosStore.stepsDraft)
const detail = computed(() => scenariosStore.detail)
const detailLoading = computed(() => scenariosStore.detailLoading)
const detailVisible = ref(false)
const createVisible = ref(false)
const saving = ref(false)
const publishingKey = ref('')
const createForm = ref({
  scenarioName: '',
  scenarioKey: '',
  description: ''
})

const metrics = computed(() => ({
  total: rows.value.length,
  active: scenariosStore.activeCount,
  editing: detailVisible.value ? 1 : 0,
  draft: rows.value.filter((item) => item.status !== 'ACTIVE').length
}))

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
    createForm.value = { scenarioName: '', scenarioKey: '', description: '' }
    ElMessage.success(`已创建场景 ${created.scenarioKey}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '创建场景失败')
  }
}

async function publishScenarioAction(row) {
  publishingKey.value = row.scenarioKey
  try {
    await scenariosStore.publishScenarioByKey(row.scenarioKey)
    ElMessage.success(`场景已发布：${row.scenarioKey}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '发布场景失败')
  } finally {
    publishingKey.value = ''
  }
}

onMounted(async () => {
  await scenariosStore.refreshScenarios()
})
</script>

<template>
  <div class="page-shell scenarios-view">
    <div class="metric-grid">
      <el-card shadow="hover" class="metric-card metric-card--blue">
        <div class="metric-card__label">场景总数</div>
        <div class="metric-card__value">{{ metrics.total }}</div>
        <div class="metric-card__sub">包含已发布与草稿场景</div>
      </el-card>
      <el-card shadow="hover" class="metric-card metric-card--green">
        <div class="metric-card__label">已发布</div>
        <div class="metric-card__value">{{ metrics.active }}</div>
        <div class="metric-card__sub">可直接用于任务编排</div>
      </el-card>
      <el-card shadow="hover" class="metric-card metric-card--purple">
        <div class="metric-card__label">编辑中</div>
        <div class="metric-card__value">{{ metrics.editing }}</div>
        <div class="metric-card__sub">当前抽屉中正在编辑</div>
      </el-card>
      <el-card shadow="hover" class="metric-card metric-card--orange">
        <div class="metric-card__label">待发布</div>
        <div class="metric-card__value">{{ metrics.draft }}</div>
        <div class="metric-card__sub">尚未激活的场景版本</div>
      </el-card>
    </div>

    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="page-header">
          <div class="page-header__main">
            <div class="page-header__title">场景管理与步骤编排</div>
            <div class="page-header__desc">统一查看场景状态、版本信息，并在抽屉中直接编排执行步骤。</div>
          </div>
          <div class="page-header__actions">
            <el-tag type="success" effect="plain" round>ACTIVE {{ scenariosStore.activeCount }}</el-tag>
            <el-button @click="scenariosStore.refreshScenarios" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="createVisible = true">新建场景</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" border class="data-table">
        <el-table-column prop="scenarioKey" label="场景标识" min-width="160" />
        <el-table-column prop="scenarioName" label="场景名称" min-width="180" />
        <el-table-column prop="versionNo" label="版本" width="90" align="center" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light" round>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">编排步骤</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              link
              type="success"
              :loading="publishingKey === row.scenarioKey"
              @click="publishScenarioAction(row)"
            >
              发布
            </el-button>
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
        <div class="drawer-toolbar">
          <el-button @click="scenariosStore.appendStep">新增步骤</el-button>
          <el-button type="primary" :loading="saving" @click="saveSteps">保存步骤</el-button>
        </div>
        <el-table :data="stepsDraft" border class="data-table">
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
.drawer-toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
</style>
