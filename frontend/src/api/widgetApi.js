import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
})

/**
 * Fetches the unified widget payload (uiSchema + data) from the backend engine.
 *
 * @param {string} widgetId - matches WIDGET_MASTER.widget_id
 * @param {object} params   - optional filter key/value pairs (bound to SQL named params)
 * @returns {Promise<{ widgetId: string, uiSchema: object, data: object[] }>}
 */
export async function fetchWidgetList(params = {}) {
  const { data } = await client.get('/admin/widgets', { params })
  return data
}

export async function fetchWidget(widgetId, params = {}) {
  const { data } = await client.get(`/widgets/${widgetId}`, { params })
  // H2 (and many JDBC drivers) return column names in uppercase.
  // Normalise to lowercase so uiSchema field names match regardless of DB vendor.
  data.data = data.data.map(row =>
    Object.fromEntries(Object.entries(row).map(([k, v]) => [k.toLowerCase(), v]))
  )
  return data
}
