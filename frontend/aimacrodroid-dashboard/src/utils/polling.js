export function createPagePolling(refresh, interval = 3000) {
  let timer = null
  let running = false
  let bound = false

  async function trigger() {
    await Promise.resolve(refresh())
  }

  function clearTimer() {
    if (!timer) return
    clearInterval(timer)
    timer = null
  }

  async function onVisibleRefresh() {
    if (!running || typeof document === 'undefined') return
    if (document.hidden) {
      clearTimer()
      return
    }
    await trigger()
    clearTimer()
    timer = setInterval(trigger, interval)
  }

  function bindVisibility() {
    if (bound || typeof document === 'undefined') return
    document.addEventListener('visibilitychange', onVisibleRefresh)
    bound = true
  }

  function unbindVisibility() {
    if (!bound || typeof document === 'undefined') return
    document.removeEventListener('visibilitychange', onVisibleRefresh)
    bound = false
  }

  async function start() {
    if (running) return
    running = true
    bindVisibility()
    await onVisibleRefresh()
  }

  function stop() {
    running = false
    clearTimer()
    unbindVisibility()
  }

  return { start, stop }
}
