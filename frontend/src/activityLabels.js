export const types = { LECTURE: '讲座', PRESENTATION: '宣讲会', CAREER_GUIDANCE: '就业指导', OTHER: '其他' }
export const statuses = {
  DRAFT: '未发布', REGISTRATION_OPEN: '报名中', REGISTRATION_CLOSED: '已截止',
  FINISHED: '已结束', CANCELLED: '已取消',
}
export const formatDate = (date) => date ? date.slice(0, 16).replace('T', ' ') : '—'
export const typeLabel = (type) => types[type] || type
