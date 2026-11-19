<script setup>
import { computed, ref } from 'vue'
import { adminApi } from './adminApi'
import { formatDate } from './activityLabels'
import { checkInUrl } from './publicOrigin'

const emit = defineEmits(['session-expired'])
const dialog = ref(null)
const activity = ref(null)
const status = ref(null)
const busy = ref(false)
const error = ref('')
const message = ref('')
const link = ref('')
const qr = ref('')
const localLink = computed(() => link.value && ['localhost', '127.0.0.1', '[::1]'].includes(new URL(link.value).hostname))

function show(row) {
  activity.value = row
  status.value = null
  link.value = ''
  qr.value = ''
  dialog.value.showModal()
  update(adminApi.checkInStatus)
}

function close() {
  dialog.value?.close()
}

defineExpose({ show, close })

async function update(action) {
  if (busy.value) return
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    status.value = await action(activity.value.id)
    const oldLink = link.value
    link.value = ''
    if (!status.value.code) {
      qr.value = ''
      return
    }
    try {
      link.value = checkInUrl(status.value.code, window.location.origin).href
      if (link.value !== oldLink || !qr.value) {
        qr.value = ''
        const { default: generator } = await import('qrcode')
        qr.value = await generator.toDataURL(link.value, { width: 360, margin: 4 })
      }
    } catch {
      qr.value = ''
      error.value = link.value ? '二维码生成失败，可以使用下方链接。' : '无法生成签到地址，请检查公网地址配置。'
    }
  } catch (cause) {
    if (cause.status === 401) {
      emit('session-expired', cause)
    } else if (cause.code === 'CHECK_IN_OUTSIDE_WINDOW') {
      error.value = '当前时间无法开启签到，请刷新状态。'
    } else {
      error.value = '操作失败，请刷新签到状态后重试。'
    }
  } finally {
    busy.value = false
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(link.value)
    message.value = '签到链接已复制。'
  } catch {
    message.value = '请选中链接并复制。'
  }
}
</script>

<template>
  <dialog ref="dialog" class="confirmation" aria-labelledby="admin-check-in-title" @cancel.prevent="!busy && close()">
    <div class="dialog-content">
      <h2 id="admin-check-in-title">现场签到</h2>
      <p class="confirmation-text">{{ activity?.title }}</p>
      <p v-if="busy && !status" role="status">正在加载签到状态…</p>
      <template v-if="status">
        <p><strong>已签到 {{ status.checkedInCount }} 人</strong></p>
        <p v-if="status.state === 'TOO_EARLY'" class="share-note">
          {{ formatDate(status.opensAt) }} 后可以开启签到（北京时间）。
        </p>
        <p v-else-if="status.state === 'ENDED'" class="share-note">活动已结束，签到记录可以在报名名单里查看。</p>
        <p v-else-if="status.state === 'READY'" class="share-note">开启后，将签到二维码展示给到场同学。</p>
        <template v-else-if="status.state === 'OPEN'">
          <p class="share-note">签到已开启，最晚至 {{ formatDate(status.endsAt) }}（北京时间）。</p>
          <img v-if="qr" :src="qr" class="share-qr" width="240" height="240" alt="现场签到二维码" />
          <p v-if="localLink" class="share-note">当前地址仅在本机可用。手机签到时，请使用公网演示或局域网地址。</p>
          <label v-if="link">签到链接<input :value="link" readonly @focus="$event.target.select()" /></label>
        </template>
      </template>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="message" role="status">{{ message }}</p>
      <div class="dialog-actions share-actions">
        <button class="button quiet" :disabled="busy" @click="close">关闭窗口</button>
        <button class="button quiet" :disabled="busy" @click="update(adminApi.checkInStatus)">刷新状态</button>
        <button v-if="status?.state === 'READY'" class="button primary" :disabled="busy" @click="update(adminApi.openCheckIn)">开启签到</button>
        <button v-if="link" class="button secondary" :disabled="busy" @click="copyLink">复制签到链接</button>
        <button v-if="status?.state === 'OPEN'" class="button danger" :disabled="busy" @click="update(adminApi.closeCheckIn)">结束签到</button>
      </div>
    </div>
  </dialog>
</template>
