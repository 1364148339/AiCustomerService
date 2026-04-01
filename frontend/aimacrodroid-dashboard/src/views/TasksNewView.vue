<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTask, getDevices } from '../mock/api'

const router = useRouter()
const devices = ref([])
const submitting = ref(false)
const result = ref(null)
const form = ref({
  type: 'CHECKIN',
  track: 'ATOMIC',
  devices: ['dev-01'],
  priority: 5,
  intent: 'daily_checkin'
})

async function init() {
  devices.value = await getDevices()
}

function toggleDevice(deviceId) {
  if (form.value.devices.includes(deviceId)) {
    form.value.devices = form.value.devices.filter((id) => id !== deviceId)
    return
  }
  form.value.devices.push(deviceId)
}

async function submit() {
  if (!form.value.devices.length) return
  submitting.value = true
  const payload =
    form.value.track === 'ATOMIC'
      ? {
          type: form.value.type,
          track: 'ATOMIC',
          devices: form.value.devices,
          commands: [{ commandId: 'c1', action: 'find_and_tap', params: { target: 'text:签到' } }],
          priority: form.value.priority
        }
      : {
          type: form.value.type,
          track: 'INTENT',
          devices: form.value.devices,
          intent: form.value.intent,
          constraints: { deadlineMs: 600000, maxRetries: 2 },
          successCriteria: { uiTextContains: ['签到成功', '已领取'] },
          priority: form.value.priority
        }
  result.value = await createTask(payload)
  submitting.value = false
}

init()
</script>

<template>
  <div class="tasks-new-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>创建任务</span>
        </div>
      </template>

      <el-form :model="form" label-width="120px" style="max-width: 600px">
        <el-form-item label="任务类型">
          <el-select v-model="form.type" placeholder="请选择任务类型">
            <el-option label="CHECKIN" value="CHECKIN" />
            <el-option label="VIDEO_REWARD" value="VIDEO_REWARD" />
          </el-select>
        </el-form-item>

        <el-form-item label="轨道">
          <el-radio-group v-model="form.track">
            <el-radio-button value="ATOMIC">ATOMIC</el-radio-button>
            <el-radio-button value="INTENT">INTENT</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="9" />
        </el-form-item>

        <el-form-item label="Intent" v-if="form.track === 'INTENT'">
          <el-input v-model="form.intent" placeholder="如: daily_checkin" />
        </el-form-item>

        <el-form-item label="选择设备">
          <div class="device-list">
            <el-checkbox-group v-model="form.devices">
              <el-checkbox-button
                v-for="item in devices"
                :key="item.id"
                :value="item.id"
                :disabled="!item.online"
              >
                {{ item.id }} ({{ item.online ? '在线' : '离线' }})
              </el-checkbox-button>
            </el-checkbox-group>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submit" :loading="submitting" :disabled="form.devices.length === 0">
            提交任务
          </el-button>
          <el-button v-if="result" @click="router.push(`/tasks/${result.taskId}`)">
            查看任务详情
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="result"
        :title="`已创建任务：${result.taskId}，状态：${result.status}`"
        type="success"
        show-icon
        :closable="false"
        style="margin-top: 20px; max-width: 600px;"
      />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  font-weight: bold;
}
.device-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
