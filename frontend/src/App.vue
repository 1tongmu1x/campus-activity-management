<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ArrowLeft, ArrowRight, Calendar, Collection, Plus, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { adminApi } from './adminApi'
import { formatDate, statuses, typeLabel, types } from './activityLabels'
import { registrationUrl } from './publicOrigin'
const blankFilters = () => ({ type: '', status: '', from: '', to: '' })
const blankForm = () => ({
  title: '', activityType: '', description: '', location: '',
  startsAt: '', endsAt: '', registrationDeadline: '', capacity: 50,
})
const user = ref(null)
const checking = ref(true)
const authBusy = ref(false)
const authError = ref('')
const username = ref('')
const password = ref('')
const filters = ref(blankFilters())
const appliedFilters = ref(blankFilters())
const rows = ref([])
const page = ref(0)
const listBusy = ref(false)
const listError = ref('')
const notice = ref('')
const actionBusy = ref(false)
const actionError = ref('')
const editor = ref()
const confirmation = ref()
const shareDialog = ref()
const rosterDialog = ref()
const shareUrl = ref('')
const qrUrl = ref('')
const shareMessage = ref('')
const shareTitle = ref('')
const rosterTitle = ref('')
const rosterId = ref(null)
const roster = ref([])
const rosterPage = ref(0)
const rosterBusy = ref(false)
const rosterMessage = ref('')
const localShare = computed(() => {
  if (!shareUrl.value) return false
  return ['127.0.0.1', 'localhost', '[::1]'].includes(new URL(shareUrl.value).hostname)
})
const editing = ref(null)
const form = ref(blankForm())
const pending = ref(null)
const editorTitle = computed(() => editing.value ? '编辑活动' : '新建活动')
const toSeconds = (date) => date.length === 16 ? `${date}:00` : date

function showLogin() {
  user.value = null
  rows.value = []
  page.value = 0
  notice.value = ''
  listError.value = ''
  filters.value = blankFilters()
  appliedFilters.value = blankFilters()
}

function requireLogin(error) {
  if (error.status !== 401) return false
  editor.value?.close()
  confirmation.value?.close()
  rosterDialog.value?.close()
  showLogin()
  authError.value = '会话已结束，请重新登录。'
  return true
}

async function restoreSession() {
  checking.value = true
  authError.value = ''
  try {
    user.value = await adminApi.me()
    await loadList()
  } catch (error) {
    if (error.status !== 401) authError.value = '暂时无法连接后端，请确认后端已启动后重试。'
  } finally {
    checking.value = false
  }
}

async function login() {
  authBusy.value = true
  authError.value = ''
  try {
    await adminApi.login(username.value.trim(), password.value)
    user.value = await adminApi.me()
    await loadList()
  } catch (error) {
    authError.value = error.status === 401 ? '账号或密码不正确。'
      : '登录失败，请确认后端已启动后重试。'
  } finally {
    password.value = ''
    authBusy.value = false
  }
}

async function logout() {
  authBusy.value = true
  listError.value = ''
  try {
    await adminApi.logout()
    showLogin()
  } catch (error) {
    if (!requireLogin(error)) listError.value = '退出失败，请重试。'
  } finally {
    authBusy.value = false
  }
}

async function loadList(target = page.value, query = appliedFilters.value) {
  if (listBusy.value) return
  listBusy.value = true
  listError.value = ''
  try {
    const entries = await adminApi.list(query, target)
    if (target > 0 && entries.length === 0) {
      if (target === page.value) {
        rows.value = await adminApi.list(query, target - 1)
        page.value = target - 1
      } else {
        notice.value = '已经是最后一页。'
      }
      return
    }
    rows.value = entries
    page.value = target
    appliedFilters.value = { ...query }
  } catch (error) {
    if (!requireLogin(error)) listError.value = '活动列表加载失败，请重试。'
  } finally {
    listBusy.value = false
  }
}

function applyFilters() {
  notice.value = ''
  if (filters.value.from && filters.value.to && filters.value.from >= filters.value.to) {
    listError.value = '筛选结束时间必须晚于起始时间。'
    return
  }
  loadList(0, filters.value)
}

function resetFilters() {
  filters.value = blankFilters()
  applyFilters()
}

async function openEditor(row) {
  actionError.value = ''
  notice.value = ''
  editing.value = null
  form.value = blankForm()
  if (row) {
    actionBusy.value = true
    listError.value = ''
    try {
      editing.value = await adminApi.get(row.id)
      for (const key of Object.keys(form.value)) form.value[key] = editing.value[key] ?? ''
    } catch (error) {
      if (!requireLogin(error)) listError.value = '无法读取活动详情，请刷新列表后重试。'
      return
    } finally {
      actionBusy.value = false
    }
  }
  editor.value.showModal()
}

async function save() {
  actionError.value = ''
  const input = { ...form.value }
  if (['title', 'description', 'location'].some(key => !input[key].trim())) {
    actionError.value = '请填写活动名称、简介和地点。'
    return
  }
  for (const key of ['startsAt', 'endsAt', 'registrationDeadline']) input[key] = toSeconds(input[key])
  if (input.endsAt <= input.startsAt || input.registrationDeadline > input.startsAt) {
    actionError.value = '结束时间须晚于开始时间，报名截止时间不得晚于开始时间。'
    return
  }
  if (editing.value && input.capacity < editing.value.registeredCount) {
    actionError.value = '人数上限不得低于已报名人数。'
    return
  }
  actionBusy.value = true
  try {
    if (editing.value) {
      await adminApi.update(editing.value.id, editing.value.version, input)
      notice.value = '活动信息已保存。'
      await loadList()
    } else {
      await adminApi.create(input)
      notice.value = '活动草稿已创建。'
      filters.value = blankFilters()
      await loadList(0, filters.value)
    }
    editor.value?.close()
  } catch (error) {
    if (!requireLogin(error)) {
      actionError.value = error.status === 409 ? '活动数据已变化，请关闭窗口并重新打开编辑。'
        : '保存失败，请检查表单后重试。'
    }
  } finally {
    actionBusy.value = false
  }
}

async function askAction(row, kind) {
  notice.value = ''
  actionError.value = ''
  pending.value = { row, kind }
  await nextTick()
  confirmation.value.showModal()
}

async function confirmAction() {
  const { row, kind } = pending.value
  actionBusy.value = true
  actionError.value = ''
  try {
    if (kind === 'publish') await adminApi.publish(row.id)
    else await adminApi.remove(row.id, row.version)
    confirmation.value?.close()
    notice.value = kind === 'publish' ? '活动已发布。' : '活动已删除。'
    await loadList()
  } catch (error) {
    if (!requireLogin(error)) {
      actionError.value = error.status === 409
        ? (kind === 'publish' ? '发布失败，请检查活动是否已截止，或刷新列表查看最新状态。'
          : '活动已被修改，请关闭窗口并刷新列表。')
        : '操作失败，请刷新列表后重试。'
    }
  } finally {
    actionBusy.value = false
  }
}

async function openShare(row) {
  try {
    shareUrl.value = registrationUrl(row.publicCode, window.location.origin).href
  } catch {
    listError.value = '公网报名地址配置不正确，请检查部署设置。'
    return
  }
  listError.value = ''
  shareTitle.value = row.title
  const url = shareUrl.value
  qrUrl.value = ''
  shareMessage.value = ''
  shareDialog.value.showModal()
  try {
    const { default: qr } = await import('qrcode')
    const image = await qr.toDataURL(url, { width: 320, margin: 4 })
    if (shareUrl.value === url) qrUrl.value = image
  } catch {
    if (shareUrl.value === url) shareMessage.value = '二维码生成失败，可以分享下面的链接。'
  }
}

async function copyShare() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    shareMessage.value = '链接已复制。'
  } catch {
    shareMessage.value = '请选中链接并复制。'
  }
}

async function loadRoster(target) {
  if (rosterBusy.value) return
  rosterMessage.value = ''
  rosterBusy.value = true
  try {
    const entries = await adminApi.registrations(rosterId.value, target)
    if (target > rosterPage.value && entries.length === 0) {
      rosterMessage.value = '已经是最后一页。'
      return
    }
    roster.value = entries
    rosterPage.value = target
  } catch (error) {
    if (!requireLogin(error)) rosterMessage.value = '名单加载失败，请重试。'
  } finally {
    rosterBusy.value = false
  }
}

function openRoster(row) {
  rosterTitle.value = row.title
  rosterId.value = row.id
  roster.value = []
  rosterPage.value = 0
  rosterDialog.value.showModal()
  loadRoster(0)
}

onMounted(restoreSession)
</script>

<template>
  <div v-if="checking" class="checking" role="status">正在连接活动管理后台…</div>
  <main v-else-if="!user" class="login-layout">
    <div class="login-brand">
      <div class="brand-icon"><Calendar aria-hidden="true" /></div>
      <div>高校学生活动管理</div>
    </div>
    <section class="login-card" aria-labelledby="login-heading">
      <h1 id="login-heading">管理员登录</h1>
      <form class="login-form" @submit.prevent="login">
        <label>账号<input v-model="username" required autocomplete="username" maxlength="64" /></label>
        <label>密码<input v-model="password" type="password" required autocomplete="current-password" /></label>
        <p v-if="authError" class="error" role="alert">{{ authError }}</p>
        <button class="button primary" :disabled="authBusy">{{ authBusy ? '正在登录…' : '登录' }}</button>
      </form>
    </section>
  </main>
  <div v-else class="app-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <div class="brand">
          <div class="brand-icon"><Calendar aria-hidden="true" /></div>
          <span>高校学生活动管理</span>
        </div>
        <div class="user-actions">
          <span>{{ user.username }}</span>
          <button class="button quiet" :disabled="authBusy || listBusy || actionBusy" @click="logout">
            <SwitchButton aria-hidden="true" /> 退出
          </button>
        </div>
      </div>
    </header>

    <main class="workspace">
      <div class="page-header">
        <div>
          <h1>活动管理</h1>
        </div>
        <button class="button primary" :disabled="listBusy || actionBusy" @click="openEditor()">
          <Plus aria-hidden="true" /> 新建活动
        </button>
      </div>

      <section class="list-panel" aria-label="活动列表">
        <form class="filters" @submit.prevent="applyFilters">
          <label>类型<select v-model="filters.type">
            <option value="">全部类型</option>
            <option v-for="(label, type) in types" :key="type" :value="type">{{ label }}</option>
          </select></label>
          <label>状态<select v-model="filters.status">
            <option value="">全部状态</option>
            <option v-for="(label, status) in statuses" :key="status" :value="status">{{ label }}</option>
          </select></label>
          <label>开始时间从<input v-model="filters.from" type="datetime-local" /></label>
          <label>开始时间到<input v-model="filters.to" type="datetime-local" /></label>
          <div class="filter-actions">
            <button class="button secondary" :disabled="listBusy || actionBusy">筛选</button>
            <button class="button quiet" type="button" :disabled="listBusy || actionBusy" @click="resetFilters">重置</button>
          </div>
        </form>
        <div class="list-heading">
          <h2>活动列表</h2>
          <button class="button quiet" type="button" :disabled="listBusy || actionBusy" @click="loadList()">
            <Refresh aria-hidden="true" /> 刷新
          </button>
        </div>
        <p v-if="listError" class="list-feedback error" role="alert">{{ listError }}</p>
        <p v-if="notice" class="list-feedback success" role="status">{{ notice }}</p>
        <div v-if="listBusy" class="empty" role="status">正在加载活动…</div>
        <div v-else-if="!rows.length && !listError" class="empty">
          <Collection aria-hidden="true" />
          <strong>暂无活动</strong>
          <span>{{ Object.values(appliedFilters).some(Boolean) ? '可以调整筛选条件后再试。' : '点击「新建活动」创建第一条活动。' }}</span>
        </div>
        <div v-else-if="rows.length" class="table-scroll" tabindex="0" aria-label="活动数据表">
          <table>
            <thead><tr><th>活动</th><th>开始 / 报名截止</th><th>状态</th><th>报名人数</th><th>发布时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td class="activity-name"><strong>{{ row.title }}</strong><small>{{ typeLabel(row.activityType) }} · {{ row.location }}</small></td>
                <td class="date-cell">{{ formatDate(row.startsAt) }}<small>{{ formatDate(row.registrationDeadline) }}</small></td>
                <td><span class="status-pill" :class="row.status.toLowerCase()">{{ statuses[row.status] ?? row.status }}</span></td>
                <td class="date-cell">{{ row.registeredCount }} / {{ row.capacity }}</td>
                <td class="date-cell">{{ formatDate(row.publishedAt) }}</td>
                <td><div class="row-actions">
                  <button class="text-button" :disabled="actionBusy" @click="openEditor(row)">编辑</button>
                  <button v-if="row.status === 'DRAFT'" class="text-button" :disabled="actionBusy" @click="askAction(row, 'publish')">发布</button>
                  <button v-if="row.publishedAt && row.status !== 'CANCELLED'" class="text-button" :disabled="actionBusy" @click="openShare(row)">分享报名</button>
                  <button v-if="row.publishedAt" class="text-button" :disabled="actionBusy || rosterBusy" @click="openRoster(row)">报名名单</button>
                  <button class="text-button danger-text" :disabled="actionBusy" @click="askAction(row, 'delete')">删除</button>
                </div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <button class="button quiet" :disabled="page === 0 || listBusy || actionBusy" @click="loadList(page - 1)">
            <ArrowLeft aria-hidden="true" /> 上一页
          </button>
          <span>第 {{ page + 1 }} 页</span>
          <button class="button quiet" :disabled="rows.length < 20 || listBusy || actionBusy" @click="loadList(page + 1)">
            下一页 <ArrowRight aria-hidden="true" />
          </button>
        </div>
      </section>
    </main>

    <dialog ref="shareDialog" class="confirmation" aria-labelledby="share-title">
      <div class="dialog-content">
        <h2 id="share-title">分享报名</h2>
        <p class="confirmation-text">{{ shareTitle }}</p>
        <img v-if="qrUrl" :src="qrUrl" class="share-qr" alt="活动报名二维码" width="220" height="220" />
        <p v-if="localShare" class="share-note">当前链接仅本机可用。手机扫码时，请先用手机可访问的地址打开后台，再生成二维码。</p>
        <label>报名链接<input :value="shareUrl" readonly @focus="$event.target.select()" /></label>
        <p v-if="shareMessage" role="status">{{ shareMessage }}</p>
        <div class="dialog-actions share-actions">
          <button class="button quiet" @click="shareDialog.close()">关闭</button>
          <button class="button secondary" @click="copyShare">复制链接</button>
          <a class="button primary" :href="shareUrl" target="_blank" rel="noopener">打开报名页</a>
        </div>
      </div>
    </dialog>

    <dialog ref="rosterDialog" class="roster-dialog" aria-labelledby="roster-title">
      <div class="dialog-content">
        <h2 id="roster-title">报名名单</h2>
        <p class="confirmation-text">{{ rosterTitle }}</p>
        <p v-if="rosterMessage" role="status">{{ rosterMessage }}</p>
        <p v-if="rosterBusy" role="status">正在加载名单…</p>
        <div v-else-if="roster.length" class="table-scroll" tabindex="0" aria-label="报名记录">
          <table class="roster-table">
            <thead><tr><th>姓名</th><th>手机号</th><th>学院</th><th>报名时间</th></tr></thead>
            <tbody>
              <tr v-for="entry in roster" :key="entry.phone">
                <td>{{ entry.name }}</td><td class="date-cell">{{ entry.phone }}</td>
                <td>{{ entry.college }}</td><td class="date-cell">{{ formatDate(entry.registeredAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else-if="!rosterMessage">暂无报名。</p>
        <div class="dialog-actions share-actions">
          <button class="button quiet" :disabled="rosterBusy || rosterPage === 0" @click="loadRoster(rosterPage - 1)">上一页</button>
          <span>第 {{ rosterPage + 1 }} 页</span>
          <button class="button quiet" :disabled="rosterBusy || roster.length < 20" @click="loadRoster(rosterPage + 1)">下一页</button>
          <button class="button secondary" :disabled="rosterBusy" @click="loadRoster(rosterPage)">刷新</button>
          <button class="button secondary" @click="rosterDialog.close()">关闭</button>
        </div>
      </div>
    </dialog>

    <dialog ref="editor" aria-labelledby="editor-title" @cancel.prevent="!actionBusy && editor.close()">
      <form class="dialog-content" @submit.prevent="save">
        <h2 id="editor-title">{{ editorTitle }}</h2>
        <div class="form-grid">
          <label class="span-two">活动名称<input v-model="form.title" required maxlength="120" autofocus /></label>
          <label>类型<select v-model="form.activityType" required>
            <option value="" disabled>选择活动类型</option>
            <option v-if="form.activityType && !types[form.activityType]" :value="form.activityType">{{ form.activityType }}</option>
            <option v-for="(label, type) in types" :key="type" :value="type">{{ label }}</option>
          </select></label>
          <label>人数上限<input v-model.number="form.capacity" type="number" min="1" max="2147483647" required /></label>
          <label class="span-two">地点<input v-model="form.location" required maxlength="255" /></label>
          <label>开始时间（北京时间）<input v-model="form.startsAt" type="datetime-local" step="1" required /></label>
          <label>结束时间（北京时间）<input v-model="form.endsAt" type="datetime-local" step="1" required /></label>
          <label class="span-two">报名截止时间<input v-model="form.registrationDeadline" type="datetime-local" step="1" required /></label>
          <label class="span-two">活动简介<textarea v-model="form.description" required maxlength="10000" rows="3"></textarea></label>
        </div>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
        <div class="dialog-actions">
          <button class="button secondary" type="button" :disabled="actionBusy" @click="editor.close()">取消</button>
          <button class="button primary" :disabled="actionBusy">{{ actionBusy ? '正在保存…' : '保存活动' }}</button>
        </div>
      </form>
    </dialog>

    <dialog ref="confirmation" class="confirmation" aria-labelledby="confirm-title"
      @cancel.prevent="!actionBusy && confirmation.close()">
      <form v-if="pending" class="dialog-content" @submit.prevent="confirmAction">
        <h2 id="confirm-title">{{ pending.kind === 'publish' ? '发布活动' : '删除活动' }}</h2>
        <p class="confirmation-text">
          {{ pending.kind === 'publish' ? '确定发布' : '确定删除' }}「{{ pending.row.title }}」？
          {{ pending.kind === 'delete' ? '已有报名记录会保留。' : '发布后，活动状态将变为报名中。' }}
        </p>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
        <div class="dialog-actions">
          <button class="button secondary" type="button" autofocus :disabled="actionBusy" @click="confirmation.close()">取消</button>
          <button class="button" :class="pending.kind === 'delete' ? 'danger' : 'primary'" :disabled="actionBusy">
            {{ pending.kind === 'publish' ? '确认发布' : '确认删除' }}
          </button>
        </div>
      </form>
    </dialog>
  </div>
</template>
