const auth = '/api/admin/auth'
const activities = '/api/admin/activities'

export class ApiError extends Error {
  constructor(status, code) {
    super(`请求失败 (${status})`)
    this.status = status
    this.code = code
  }
}

async function request(path, init) {
  const response = await fetch(path, init)
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new ApiError(response.status, error?.code)
  }
  if (response.status !== 204 && response.status !== 201) return response.json()
  if (response.status === 201 && response.headers.get('content-type')?.includes('application/json')) {
    return response.json()
  }
}

async function write(path, method, body, csrfPath = `${auth}/csrf`) {
  const { headerName, token } = await request(csrfPath)
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
  registrations: (id, page) => request(`${activities}/${id}/registrations?page=${page}&size=20`),
  create: (activity) => write(activities, 'POST', activity),
  update: (id, version, activity) =>
    write(`${activities}/${id}?version=${version}`, 'PUT', activity),
  publish: (id) => write(`${activities}/${id}/publish`, 'POST'),
  remove: (id, version) => write(`${activities}/${id}?version=${version}`, 'DELETE'),
}

export const publicApi = {
  get: (code) => request(`/api/public/activities/${code}`),
  register: (code, participant) =>
    write(`/api/public/activities/${code}/registrations`, 'POST', participant, '/api/public/csrf'),
}
