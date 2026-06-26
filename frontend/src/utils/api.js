import { TOKEN } from '../stores/state'

function authHeaders(overrides) {
  const h = { headers: { 'Authorization': 'Bearer ' + TOKEN }, ...overrides }
  if (overrides && overrides.headers)
    h.headers = { ...h.headers, ...overrides.headers }
  return h
}

function handle(r) { return r.json().then(json => { if (!r.ok) throw new Error(json.message||'请求失败'); return json }) }

export const api = {
  get: (url) => fetch(url, authHeaders()).then(handle).catch(e => ({ code:-1, message:e.message })),
  post: (url, body) => fetch(url, authHeaders({
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  })).then(handle).catch(e => ({ code:-1, message:e.message })),
  put: (url, body) => fetch(url, authHeaders({
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  })).then(handle).catch(e => ({ code:-1, message:e.message })),
  del: (url, body) => {
    const opts = authHeaders({ method: 'DELETE' })
    if (body) { opts.headers = { ...opts.headers, 'Content-Type': 'application/json' }; opts.body = JSON.stringify(body) }
    return fetch(url, opts).then(handle).catch(e => ({ code:-1, message:e.message }))
  },
}
