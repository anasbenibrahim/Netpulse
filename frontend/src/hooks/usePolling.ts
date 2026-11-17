import { useCallback, useEffect, useRef, useState } from 'react'

export const REFRESH_MS = 5000

interface PollingState<T> {
  data: T | undefined
  error: string | undefined
  loading: boolean
  refresh: () => void
}

/**
 * Calls `fetcher` now and every `intervalMs`, re-running immediately when `deps` change.
 * Keeps the last good data on screen if a refresh fails.
 */
export function usePolling<T>(fetcher: () => Promise<T>, deps: unknown[], intervalMs = REFRESH_MS): PollingState<T> {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<string>()
  const [loading, setLoading] = useState(true)
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const load = useCallback(async (isCancelled: () => boolean) => {
    try {
      const result = await fetcherRef.current()
      if (!isCancelled()) {
        setData(result)
        setError(undefined)
      }
    } catch (e) {
      if (!isCancelled()) setError(e instanceof Error ? e.message : String(e))
    } finally {
      if (!isCancelled()) setLoading(false)
    }
  }, [])

  const [tick, setTick] = useState(0)
  const refresh = useCallback(() => setTick((t) => t + 1), [])

  useEffect(() => {
    let cancelled = false
    const isCancelled = () => cancelled
    setLoading(true)
    void load(isCancelled)
    const timer = setInterval(() => void load(isCancelled), intervalMs)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick, intervalMs, load])

  return { data, error, loading, refresh }
}
