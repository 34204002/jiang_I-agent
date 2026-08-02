import {api} from './api'
import {showToast} from './toast'
import type {Reminder} from '../types'

const POLL_INTERVAL = 30_000

let timer: number | null = null
// 近期已弹过通知的提醒 id，避免同一提醒重复弹
const toasted = new Set<number>()

/** 启动提醒轮询：登录后调用。每 30s 检查一次到期提醒，弹提示并 ack。 */
export function startReminderPolling(): void {
  if (timer != null) return
  checkReminders()
  timer = window.setInterval(checkReminders, POLL_INTERVAL)
}

/** 停止提醒轮询（登出等场景） */
export function stopReminderPolling(): void {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

async function checkReminders(): Promise<void> {
  const json = await api.get<Reminder[]>('/api/reminders?pending=true')
  if (json.code !== 200 || !Array.isArray(json.data)) return
  const now = Date.now()
  for (const r of json.data) {
    if (r.fired !== 0 || toasted.has(r.id)) continue
    const dueTime = new Date(r.remindAt).getTime()
    if (!Number.isNaN(dueTime) && dueTime <= now) {
      toasted.add(r.id)
      showToast(`⏰ 提醒: ${r.message}`, 'ok')
      // 确认已送达（幂等），避免下次轮询重复弹
      api.post(`/api/reminders/${r.id}/ack`).catch(() => {})
    }
  }
}
