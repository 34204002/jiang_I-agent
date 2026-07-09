export function showToast(msg: string, type: 'info' | 'ok' | 'error' = 'info'): void {
    const t = document.createElement('div')
    t.className = 'toast ' + type
    t.textContent = msg
    const wrap = document.getElementById('toastWrap') || document.body
    wrap.appendChild(t)
    setTimeout(() => {
        t.style.opacity = '0';
        setTimeout(() => t.remove(), 300)
    }, 2000)
}

// Make available globally (used by old common.js callers)
;(window as unknown as Record<string, unknown>).showToast = showToast
