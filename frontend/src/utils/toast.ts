const TOAST_DURATION = 2000
const TOAST_FADE = 300

export function showToast(msg: string, type: 'info' | 'ok' | 'error' = 'info'): void {
    const t = document.createElement('div')
    t.className = 'toast ' + type
    t.textContent = msg
    const wrap = document.getElementById('toastWrap') || document.body
    wrap.appendChild(t)
    setTimeout(() => {
        t.style.opacity = '0';
        setTimeout(() => t.remove(), TOAST_FADE)
    }, TOAST_DURATION)
}

