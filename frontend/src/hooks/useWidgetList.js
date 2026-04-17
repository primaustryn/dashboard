import { useState, useEffect } from 'react'
import { fetchWidgetList } from '../api/widgetApi'

export function useWidgetList() {
  const [widgets, setWidgets]  = useState([])
  const [loading, setLoading]  = useState(true)
  const [error, setError]      = useState(null)

  useEffect(() => {
    fetchWidgetList({ activeOnly: true })
      .then(setWidgets)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  return { widgets, loading, error }
}
