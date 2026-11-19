<script setup>
import { computed, onMounted, ref } from 'vue'
import activityImage from './assets/activity.jpg'
import locationImage from './assets/location.jpg'
import timeImage from './assets/time.jpg'
import { publicApi } from './adminApi'
import { formatDate, statuses, typeLabel } from './activityLabels'

const props = defineProps({ code: { type: String, required: true } })
const activity = ref(null)
const loading = ref(true)
const loadError = ref('')
const form = ref({ name: '', phone: '', college: '' })
const busy = ref(false)
const error = ref('')
const submitted = ref(false)
const canRegister = computed(() => activity.value?.status === 'REGISTRATION_OPEN' && !activity.value.registrationFull)
const messages = {
  ACTIVITY_NOT_FOUND: '活动链接已失效。',
  ACTIVITY_FULL: '报名人数已满。',
  REGISTRATION_CLOSED: '报名时间已截止。',
  DUPLICATE_REGISTRATION: '该手机号已报名，请勿重复提交。',
  INVALID_REGISTRATION: '请检查姓名、手机号和学院。',
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    if (!/^[0-9a-f]{12}$/.test(props.code)) {
      loadError.value = '活动链接无效。'
      return
    }
    activity.value = await publicApi.get(props.code)
    document.title = `${activity.value.title} | 校园活动`
  } catch (err) {
    loadError.value = err.status === 404 ? '活动未发布或链接已失效。' : '暂时无法加载活动，请刷新重试。'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (busy.value || submitted.value || !canRegister.value) return
  error.value = ''
  busy.value = true
  try {
    await publicApi.register(props.code, form.value)
    submitted.value = true
    form.value = { name: '', phone: '', college: '' }
  } catch (err) {
    error.value = messages[err.code] || '提交失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="public-page">
    <div class="public-brand">高校学生活动</div>
    <section v-if="loading" class="public-panel" role="status">正在加载活动…</section>
    <section v-else-if="loadError" class="public-panel" role="alert">{{ loadError }}</section>
    <template v-else-if="activity">
      <header class="public-hero">
        <h1>{{ activity.title }}</h1>
        <span class="public-state" :class="{ 'public-state-open': canRegister }">
          {{ activity.status === 'REGISTRATION_OPEN' && activity.registrationFull ? '名额已满' : statuses[activity.status] }}
        </span>
      </header>

      <section class="public-panel public-details" aria-label="活动信息">
        <dl class="public-facts">
          <div class="public-fact public-fact-activity">
            <dt>
              <span class="public-fact-art" aria-hidden="true">
                <img :src="activityImage" alt="" width="58" height="58" />
                <span>活动</span>
              </span>
              活动类型
            </dt>
            <dd>{{ typeLabel(activity.activityType) }}</dd>
          </div>
          <div class="public-fact public-fact-location">
            <dt>
              <span class="public-fact-art" aria-hidden="true">
                <img :src="locationImage" alt="" width="58" height="58" />
                <span>地点</span>
              </span>
              活动地点
            </dt>
            <dd>{{ activity.location }}</dd>
          </div>
          <div class="public-fact public-fact-time">
            <dt>
              <span class="public-fact-art" aria-hidden="true">
                <img :src="timeImage" alt="" width="58" height="58" />
                <span>时间</span>
              </span>
              活动时间（北京时间）
            </dt>
            <dd>
              <span class="public-date-range">
                <time :datetime="activity.startsAt">{{ formatDate(activity.startsAt) }}</time>
                <span>至</span>
                <time :datetime="activity.endsAt">{{ formatDate(activity.endsAt) }}</time>
              </span>
              <span class="public-deadline">报名截止 <time :datetime="activity.registrationDeadline">{{ formatDate(activity.registrationDeadline) }}</time></span>
            </dd>
          </div>
        </dl>
        <h2>活动简介</h2>
        <p class="public-description">{{ activity.description }}</p>
      </section>

      <section class="public-panel" aria-labelledby="registration-label">
        <h2 id="registration-label">活动报名</h2>
        <p v-if="submitted" class="success" role="status">报名成功，欢迎参加！</p>
        <p v-else-if="!canRegister" class="public-muted">
          {{ activity.status === 'REGISTRATION_OPEN' ? '活动名额已满。' : '本活动已停止报名。' }}
        </p>
        <form v-else class="public-form" @submit.prevent="submit">
          <label>姓名<input v-model.trim="form.name" autocomplete="name" required maxlength="64" /></label>
          <label>手机号<input v-model.trim="form.phone" type="tel" autocomplete="tel-national" inputmode="numeric"
            pattern="1[3-9][0-9]{9}" maxlength="11" required /></label>
          <label>学院<input v-model.trim="form.college" required maxlength="100" /></label>
          <p class="public-muted">报名信息用于活动管理和签到，仅管理员可查看。</p>
          <p v-if="error" class="error" role="alert">{{ error }}</p>
          <button class="button primary" :disabled="busy">{{ busy ? '正在提交…' : '提交报名' }}</button>
        </form>
      </section>
    </template>
  </main>
</template>
