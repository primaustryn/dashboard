import EChartsWrapper from '../EChartsWrapper'

const COLORS = ['#00d4ff', '#a855f7', '#00ffaa', '#fbbf24', '#f43f5e', '#06b6d4']

export default function RadarChart({ uiSchema, data }) {
  const indicators  = uiSchema.indicators  ?? []
  const valueFields = uiSchema.valueFields ?? []

  const seriesData = data.map((row, i) => {
    const color = COLORS[i % COLORS.length]
    return {
      name:      row[uiSchema.nameField] ?? `Series ${i + 1}`,
      value:     valueFields.map(f => Number(row[f]) || 0),
      lineStyle: { color, width: 2, shadowBlur: 10, shadowColor: color + '88' },
      areaStyle: { color: color + '28' },
      itemStyle: { color, borderColor: '#070c18', borderWidth: 2, shadowBlur: 10, shadowColor: color + 'aa' },
    }
  })

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title, left: 'center', top: 10,
      textStyle: { color: '#e0e8ff', fontSize: 15, fontWeight: 700, textShadowBlur: 8, textShadowColor: '#a855f733' },
    },
    tooltip: {
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor: '#a855f733', borderWidth: 1, padding: [10, 14],
      textStyle: { color: '#c0d0e8', fontSize: 12 },
      formatter: params => {
        const p = params[0] ?? params
        const series = Array.isArray(params) ? params : [params]
        return series.map(s =>
          `<div style="margin-bottom:4px"><b style="color:${COLORS[seriesData.findIndex(d => d.name === s.name) % COLORS.length]}">${s.name}</b></div>` +
          indicators.map((ind, j) =>
            `<div style="margin:2px 0;color:#8099bb">${ind.name}: <b style="color:#fff">${s.value[j]}</b></div>`
          ).join('')
        ).join('<hr style="border-color:#1e3055;margin:6px 0"/>')
      },
    },
    legend: {
      bottom: 4, icon: 'circle',
      textStyle: { color: '#607898', fontSize: 11 }, inactiveColor: '#2a3a55',
    },
    radar: {
      indicator: indicators.map(ind => ({ ...ind, axisLabel: { show: false } })),
      center: ['50%', '53%'], radius: '60%', splitNumber: 4,
      axisName: { color: '#8099bb', fontSize: 11, fontWeight: 600 },
      axisLine:  { lineStyle: { color: '#1a2d4d' } },
      splitLine: { lineStyle: { color: '#141e30', width: 1 } },
      splitArea: {
        areaStyle: {
          color: ['rgba(0,212,255,0.02)', 'rgba(168,85,247,0.02)', 'rgba(0,255,170,0.02)', 'rgba(0,0,0,0)'],
        },
      },
    },
    series: [{
      type: 'radar', data: seriesData,
      symbol: 'circle', symbolSize: 6,
      emphasis: {
        lineStyle: { width: 3 },
        itemStyle: { shadowBlur: 20 },
        areaStyle: { opacity: 0.5 },
      },
    }],
  }

  return <EChartsWrapper option={option} />
}
