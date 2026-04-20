import { useEffect, useState } from 'react'
import * as echarts from 'echarts'
import EChartsWrapper from '../EChartsWrapper'
import { escapeHtml as esc } from '../../utils/html'

const MAP_NAME = 'world'

export default function GeoChart({ uiSchema, data }) {
  const [ready, setReady] = useState(() => !!echarts.getMap(MAP_NAME))
  const [err,   setErr]   = useState(null)

  useEffect(() => {
    if (echarts.getMap(MAP_NAME)) return
    fetch('/world.json')
      .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json() })
      .then(geo => { echarts.registerMap(MAP_NAME, geo); setReady(true) })
      .catch(() => setErr(true))
  }, [])

  if (err) return (
    <div style={MSG}>
      <div style={{ color: '#fbbf24', fontWeight: 700, marginBottom: 8 }}>Map data not found</div>
      <div style={{ color: '#607898', fontSize: 12, marginBottom: 14 }}>
        Download <code style={CODE}>world.json</code> into the <code style={CODE}>frontend/public/</code> folder:
      </div>
      <pre style={PRE}>{`curl -o frontend/public/world.json \\\n  "https://echarts.apache.org/examples/data/asset/geo/world.json"`}</pre>
    </div>
  )

  if (!ready) return <div style={MSG}><span style={{ color: '#607898' }}>Loading map…</span></div>

  const values  = data.map(row => ({ name: row[uiSchema.nameField], value: row[uiSchema.valueField] }))
  const allVals = values.map(v => v.value).filter(v => v != null)
  const maxVal  = Math.max(...allVals, 1)

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title, left: 'center', top: 10,
      textStyle: { color: '#e0e8ff', fontSize: 15, fontWeight: 700, textShadowBlur: 8, textShadowColor: '#00d4ff33' },
    },
    tooltip: {
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor: '#00d4ff33', borderWidth: 1, padding: [10, 14],
      textStyle: { color: '#c0d0e8', fontSize: 12 },
      formatter: p => {
        const name = esc(p.name)
        return p.value != null
          ? `<div style="font-weight:700;color:#00d4ff;margin-bottom:6px">${name}</div><div>Value: <b style="color:#fff">${Number(p.value).toLocaleString()}</b></div>`
          : `<div style="color:#607898">${name}: no data</div>`
      },
    },
    visualMap: {
      min: 0, max: maxVal,
      orient: 'vertical', left: 10, bottom: '14%',
      textStyle: { color: '#607898', fontSize: 10 },
      calculable: true,
      inRange:    { color: ['#071428', '#0c2048', '#0051cc', '#0099ff', '#00d4ff'] },
      outOfRange: { color: '#0a1528' },
    },
    series: [{
      type: 'map', map: MAP_NAME,
      roam: true, scaleLimit: { min: 0.8, max: 8 },
      data: values,
      itemStyle: {
        areaColor: '#0a1528',
        borderColor: '#1a2d4d', borderWidth: 0.5,
      },
      emphasis: {
        label: { show: true, color: '#e0e8ff', fontSize: 11, fontWeight: 600 },
        itemStyle: {
          areaColor: '#00d4ff33',
          shadowBlur: 20, shadowColor: '#00d4ff88',
          borderColor: '#00d4ff', borderWidth: 1,
        },
      },
      select: { itemStyle: { areaColor: '#00d4ff22' } },
      label: { show: false },
    }],
  }

  return <EChartsWrapper option={option} style={{ height: '440px' }} />
}

const MSG  = { height: '420px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', padding: 32, color: '#e0e8ff' }
const CODE = { background: '#0a1528', border: '1px solid #1a2d4d', borderRadius: 3, padding: '1px 5px', fontFamily: 'monospace', fontSize: 11, color: '#00d4ff' }
const PRE  = { background: '#0a1528', border: '1px solid #1a2d4d', borderRadius: 6, padding: '12px 16px', fontFamily: 'monospace', fontSize: 11, color: '#8099bb', textAlign: 'left', lineHeight: 1.7 }
