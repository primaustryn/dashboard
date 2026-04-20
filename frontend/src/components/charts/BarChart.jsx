import EChartsWrapper from '../EChartsWrapper'
import { escapeHtml as esc } from '../../utils/html'

const GRADIENTS = [
  ['#00d4ff', '#0051cc'],
  ['#a855f7', '#5b21b6'],
  ['#00ffaa', '#059669'],
  ['#fbbf24', '#d97706'],
  ['#f43f5e', '#be123c'],
  ['#06b6d4', '#0e7490'],
  ['#fb923c', '#c2410c'],
  ['#34d399', '#065f46'],
]

function barGradient([top, bottom]) {
  return {
    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [
      { offset: 0, color: top },
      { offset: 1, color: bottom },
    ],
  }
}

function fmt(val) {
  if (val == null) return ''
  if (Math.abs(val) >= 1_000_000) return '$' + (val / 1_000_000).toFixed(1) + 'M'
  if (Math.abs(val) >= 1_000)     return '$' + (val / 1_000).toFixed(1) + 'K'
  return '$' + val
}

export default function BarChart({ uiSchema, data }) {
  const xValues = data.map(row => row[uiSchema.xAxis?.field])

  const seriesItems = (uiSchema.series ?? []).map((s, si) => ({
    name:       s.name,
    type:       'bar',
    barMaxWidth: 52,
    barMinHeight: 4,
    itemStyle: {
      color:        params => barGradient(GRADIENTS[params.dataIndex % GRADIENTS.length]),
      borderRadius: [6, 6, 0, 0],
      shadowBlur:   14,
      shadowColor:  GRADIENTS[si % GRADIENTS.length][0] + '66',
    },
    emphasis: {
      itemStyle: {
        shadowBlur:  32,
        shadowColor: GRADIENTS[si % GRADIENTS.length][0] + 'cc',
      },
    },
    label: {
      show:      true,
      position:  'top',
      color:     '#a0b4cc',
      fontSize:  10,
      fontWeight: 600,
      formatter: p => fmt(p.value),
    },
    data: data.map(row => row[s.valueField]),
  }))

  const allVals  = seriesItems.flatMap(s => s.data).filter(v => v != null)
  const avgVal   = allVals.length ? allVals.reduce((a, b) => a + b, 0) / allVals.length : 0

  if (seriesItems[0]) {
    seriesItems[0].markLine = {
      silent: true,
      symbol: ['none', 'none'],
      lineStyle: { color: '#fbbf24', type: 'dashed', width: 1.5, opacity: 0.6 },
      label: {
        color:      '#fbbf24',
        fontSize:   10,
        fontWeight: 700,
        formatter:  `Avg: ${fmt(avgVal)}`,
      },
      data: [{ type: 'average' }],
    }
  }

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title,
      left: 'center',
      top: 10,
      textStyle: {
        color:      '#e0e8ff',
        fontSize:   15,
        fontWeight: 700,
        textShadowBlur:  8,
        textShadowColor: '#00d4ff44',
      },
    },
    tooltip: {
      trigger:         'axis',
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor:     '#00d4ff33',
      borderWidth:     1,
      padding:         [10, 14],
      textStyle:       { color: '#c0d0e8', fontSize: 12 },
      formatter: params => {
        const hdr  = `<div style="font-weight:700;margin-bottom:6px;color:#00d4ff;font-size:13px">${esc(params[0]?.axisValue)}</div>`
        const rows = params.map(p =>
          `<div style="margin:2px 0">${p.marker} ${esc(p.seriesName)}: <b style="color:#fff">${fmt(p.value)}</b></div>`
        ).join('')
        return hdr + rows
      },
    },
    legend: {
      bottom: 4,
      textStyle:     { color: '#607898', fontSize: 11 },
      inactiveColor: '#2a3a55',
    },
    grid: { left: '2%', right: '2%', top: '18%', bottom: '12%', containLabel: true },
    xAxis: {
      type: 'category',
      name: uiSchema.xAxis?.label,
      nameTextStyle: { color: '#607898', fontSize: 10 },
      data:          xValues,
      axisLabel:     { color: '#607898', rotate: 20, fontSize: 11 },
      axisLine:      { lineStyle: { color: '#1a2d4d' } },
      axisTick:      { show: false },
      splitLine:     { show: false },
    },
    yAxis: {
      type: 'value',
      name: uiSchema.yAxis?.label,
      nameTextStyle: { color: '#607898', fontSize: 10 },
      axisLabel:     { color: '#607898', fontSize: 11, formatter: fmt },
      axisLine:      { show: false },
      axisTick:      { show: false },
      splitLine:     { lineStyle: { color: '#111e33', type: 'dashed' } },
    },
    series: seriesItems,
  }

  return <EChartsWrapper option={option} />
}
