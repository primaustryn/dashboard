import EChartsWrapper from '../EChartsWrapper'

function arcColor(ratio) {
  if (ratio < 0.6)  return '#00d4ff'
  if (ratio < 0.85) return '#fbbf24'
  return '#f43f5e'
}

function arcGlow(ratio) {
  if (ratio < 0.6)  return '#00d4ff88'
  if (ratio < 0.85) return '#fbbf2488'
  return '#f43f5e88'
}

function gaugePositions(count) {
  if (count === 1) return [{ center: ['50%', '58%'], radius: '72%', detailOffset: ['0%', '22%'], titleOffset: ['0%', '42%'] }]
  if (count === 2) return [
    { center: ['27%', '58%'], radius: '44%', detailOffset: ['0%', '26%'], titleOffset: ['0%', '48%'] },
    { center: ['73%', '58%'], radius: '44%', detailOffset: ['0%', '26%'], titleOffset: ['0%', '48%'] },
  ]
  if (count === 3) return [
    { center: ['20%', '58%'], radius: '34%', detailOffset: ['0%', '28%'], titleOffset: ['0%', '50%'] },
    { center: ['50%', '58%'], radius: '34%', detailOffset: ['0%', '28%'], titleOffset: ['0%', '50%'] },
    { center: ['80%', '58%'], radius: '34%', detailOffset: ['0%', '28%'], titleOffset: ['0%', '50%'] },
  ]
  return [
    { center: ['26%', '38%'], radius: '28%', detailOffset: ['0%', '26%'], titleOffset: ['0%', '48%'] },
    { center: ['74%', '38%'], radius: '28%', detailOffset: ['0%', '26%'], titleOffset: ['0%', '48%'] },
    { center: ['26%', '78%'], radius: '28%', detailOffset: ['0%', '26%'], titleOffset: ['0%', '48%'] },
    { center: ['74%', '78%'], radius: '28%', detailOffset: ['0%', '26%'], titleOffset: ['0%', '48%'] },
  ]
}

export default function GaugeChart({ uiSchema, data }) {
  const max    = uiSchema.max  ?? 100
  const unit   = uiSchema.unit ?? ''
  const rows   = data.slice(0, 4)
  const pos    = gaugePositions(rows.length)
  const single = rows.length === 1

  const series = rows.map((row, i) => {
    const value = Number(row[uiSchema.valueField]) || 0
    const name  = row[uiSchema.nameField] ?? ''
    const ratio = value / max
    const color = arcColor(ratio)
    const glow  = arcGlow(ratio)
    const p     = pos[i]

    return {
      type: 'gauge',
      center: p.center,
      radius: p.radius,
      startAngle: 215, endAngle: -35,
      min: 0, max,
      progress: {
        show: true,
        width: single ? 16 : 12,
        roundCap: true,
        itemStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0,   color: '#00d4ff' },
              { offset: 0.7, color: color },
              { offset: 1,   color: color },
            ],
          },
          shadowBlur: 18, shadowColor: glow,
        },
      },
      axisLine: {
        roundCap: true,
        lineStyle: { width: single ? 16 : 12, color: [[1, '#0d1e38']] },
      },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      pointer:   { show: false },
      anchor:    { show: false },
      detail: {
        valueAnimation: true,
        fontSize: single ? 30 : 18,
        fontWeight: 800,
        color: '#e0e8ff',
        formatter: v => `{val|${v}${unit}}`,
        rich: {
          val: { fontSize: single ? 30 : 18, fontWeight: 800, color: '#e0e8ff' },
        },
        offsetCenter: p.detailOffset,
      },
      title: {
        fontSize: single ? 12 : 10,
        fontWeight: 600,
        color: '#607898',
        offsetCenter: p.titleOffset,
      },
      data: [{ value, name }],
    }
  })

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title, left: 'center', top: 10,
      textStyle: { color: '#e0e8ff', fontSize: 15, fontWeight: 700, textShadowBlur: 8, textShadowColor: '#00d4ff33' },
    },
    series,
  }

  return <EChartsWrapper option={option} />
}
