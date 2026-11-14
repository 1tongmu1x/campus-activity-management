import { createApp } from 'vue'
import './admin.css'

const publicPath = /^\/activities\/([^/]+)\/?$/.exec(window.location.pathname)
const page = publicPath ? import('./PublicActivity.vue') : import('./App.vue')
page.then(({ default: App }) => createApp(App, publicPath ? { code: publicPath[1] } : {}).mount('#app'))
