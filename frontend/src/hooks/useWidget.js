import { useState, useEffect } from 'react'
import { fetchWidget } from '../api/widgetApi'

/**
 * Encapsulates the fetch lifecycle for a single widget so DashboardBoard
 * stays declarative — it only describes *which* widgets to show, not *how*
 * to load them.
 */
export function useWidget(widgetId, params = {}) {
  const [data, setData]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    fetchWidget(widgetId, params)
      .then(res => { if (!cancelled) setData(res) })
      .catch(err => { if (!cancelled) setError(err.message) })
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }   // cleanup on unmount / widgetId change
  }, [widgetId])                        // eslint-disable-line react-hooks/exhaustive-deps

  return { data, loading, error }
}
