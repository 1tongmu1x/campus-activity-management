<script setup>
import { onMounted, ref } from 'vue'
import { formatDate } from './activityLabels'
import { checkInApi } from './adminApi'

const props = defineProps({ code: { type: String, required: true } })
const activity = ref(null)
const loading = ref(true)
const busy = ref(false)
const phone = ref('')
const unavailable = ref('')
const error = ref('')
const confirmation = ref('')
const closed = '签到已关闭或链接已失效，请向现场工作人员获取二维码。'

onMounted(async () => {
  try {
    if (!/^[0-9a-f]{32}$/.test(props.code)) {
      unavailable.value = closed
      return
    }
    activity.value = await checkInApi.get(props.code)
    document.title = `${activity.value.title} | 现场签到`
  } catch (cause) {
    unavailable.value = [404, 409].includes(cause.status) ? closed : '暂时无法加载签到，请刷新重试。'
  } finally {
    loading.value = false
  }
})

async function submit() {
  if (busy.value || confirmation.value || unavailable.value) return
  busy.value = true
  error.value = ''
  try {
    await checkInApi.submit(props.code, phone.value)
    confirmation.value = '签到成功，欢迎参加活动！'
    phone.value = ''
  } catch (cause) {
    switch (cause.code) {
      case 'CHECK_IN_UNAVAILABLE':
      case 'CHECK_IN_OUTSIDE_WINDOW':
        unavailable.value = closed
        break
      case 'ALREADY_CHECKED_IN':
        confirmation.value = '这个手机号已经签到。'
        phone.value = ''
        break
      case 'REGISTRATION_NOT_FOUND':
        error.value = '没有找到该手机号的报名记录，请核对后再试。'
        break
      case 'INVALID_CHECK_IN_PHONE':
        error.value = '请输入正确的 11 位报名手机号。'
        break
      default:
        error.value = '签到提交失败，请稍后重试。'
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="public-page">
    <div class="public-brand">高校学生活动</div>
    <header class="public-hero">
      <h1>{{ activity?.title || '活动签到' }}</h1>
      <span v-if="activity" class="public-state">现场签到</span>
    </header>
    <section class="public-panel" aria-labelledby="check-in-title">
      <h2 id="check-in-title">活动签到</h2>
      <p v-if="loading" role="status">正在加载签到…</p>
      <p v-else-if="unavailable" class="error" role="alert">{{ unavailable }}</p>
      <p v-else-if="confirmation" class="success" role="status">{{ confirmation }}</p>
      <form v-else class="public-form" @submit.prevent="submit">
        <p class="public-muted">{{ activity.location }} · {{ formatDate(activity.endsAt) }} 前签到（北京时间）</p>
        <label>报名手机号<input v-model.trim="phone" type="tel" autocomplete="tel-national"
          inputmode="numeric" pattern="1[3-9][0-9]{9}" maxlength="11" required /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="button primary" :disabled="busy">{{ busy ? '正在签到…' : '确认签到' }}</button>
      </form>
    </section>
  </main>
</template>
