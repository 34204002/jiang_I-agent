import { TOKEN } from '../stores/state'

function authHeaders(overrides) {
  const h = { headers: { 'X-Auth-Token': TOKEN }, ...overrides }
  if (overrides && overrides.headers)
    h.headers = { ...h.headers, ...overrides.headers }
  return h
}

export const api = {
  get: (url) => fetch(url, authHeaders()).then(r => r.json()),
  post: (url, body) => fetch(url, authHeaders({
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })).then(r => r.json()),
  del: (url, body) => {
    const opts = authHeaders({ method: 'DELETE' })
    if (body) {
      opts.headers = { ...opts.headers, 'Content-Type': 'application/json' }
      opts.body = JSON.stringify(body)
    }
    return fetch(url, opts).then(r => r.json())
  },
}
