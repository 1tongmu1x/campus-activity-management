import { createApp } from 'vue'
import './admin.css'
import './public.css'

const registration = /^\/activities\/([^/]+)\/?$/.exec(window.location.pathname)
const checkIn = /^\/check-in\/([^/]+)\/?$/.exec(window.location.pathname)

async function mountPage() {
  if (registration) {
    const { default: Activity } = await import('./PublicActivity.vue')
    createApp(Activity, { code: registration[1] }).mount('#app')
  } else if (checkIn) {
    const { default: CheckIn } = await import('./PublicCheckIn.vue')
    createApp(CheckIn, { code: checkIn[1] }).mount('#app')
  } else {
    const { default: Admin } = await import('./App.vue')
    createApp(Admin).mount('#app')
  }
}

mountPage()
