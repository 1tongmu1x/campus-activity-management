const auth = '/api/admin/auth'
const activities = '/api/admin/activities'

export class ApiError extends Error {
  constructor(status) {
    super(`请求失败 (${status})`)
    this.status = status
  }
}

async function request(path, init) {
  const response = await fetch(path, init)
  if (!response.ok) throw new ApiError(response.status)
  if (response.status !== 204) return response.json()
}

async function write(path, method, body) {
  const { headerName, token } = await request(`${auth}/csrf`)
  const headers = { [headerName]: token }
  if (body != null && !(body instanceof URLSearchParams)) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }
  return request(path, { method, headers, body })
}

export const adminApi = {
  me: () => request(`${auth}/me`),
  login: (username, password) =>
    write(`${auth}/login`, 'POST', new URLSearchParams({ username, password })),
  logout: () => write(`${auth}/logout`, 'POST'),
  list: (filters, page) => {
    const params = new URLSearchParams({ page, size: 20 })
    for (const [key, value] of Object.entries(filters)) {
      if (value) params.set(key, value)
    }
    return request(`${activities}?${params}`)
  },
  get: (id) => request(`${activities}/${id}`),
  create: (activity) => write(activities, 'POST', activity),
  update: (id, version, activity) =>
    write(`${activities}/${id}?version=${version}`, 'PUT', activity),
  publish: (id) => write(`${activities}/${id}/publish`, 'POST'),
  remove: (id, version) => write(`${activities}/${id}?version=${version}`, 'DELETE'),
}
