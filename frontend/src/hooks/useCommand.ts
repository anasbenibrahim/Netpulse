import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { DeviceCommand } from '../api/types'

/**
 * Starts a device command and polls it every second until the device has answered
 * (status leaves SENT) or the backend marks it TIMED_OUT.
 */
export function useCommand() {
  const [command, setCommand] = useState<DeviceCommand>()
  const [error, setError] = useState<string>()

  const start = useCallback(async (send: () => Promise<DeviceCommand>) => {
    setError(undefined)
    setCommand(undefined)
    try {
      setCommand(await send())
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  const pending = command?.status === 'SENT'

  useEffect(() => {
    if (!pending || !command) return
    let cancelled = false
    const timer = setInterval(async () => {
      try {
        const latest = await api.command(command.id)
        if (!cancelled) setCommand(latest)
      } catch {
        // keep polling; the backend may be restarting
      }
    }, 1000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [pending, command])

  const reset = useCallback(() => {
    setCommand(undefined)
    setError(undefined)
  }, [])

  return { command, error, pending, start, reset }
}
