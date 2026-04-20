import EChartsWrapper from '../EChartsWrapper'
import { escapeHtml as esc } from '../../utils/html'

const PALETTE = [
  '#00d4ff', '#a855f7', '#00ffaa', '#fbbf24',
  '#f43f5e', '#06b6d4', '#fb923c', '#34d399',
]

function radialGradient(color) {
  return {
    type: 'radial', x: 0.5, y: 0.4, r: 0.7,
    colorStops: [
      { offset: 0, color: color },
      { offset: 1, color: color + '66' },
    ],
  }
}

function fmt(val) {
  if (val == null) return ''
  if (Math.abs(val) >= 1_000_000) return '$' + (val / 1_000_000).toFixed(2) + 'M'
  if (Math.abs(val) >= 1_000)     return '$' + (val / 1_000).toFixed(1) + 'K'
  return '$' + val
}

export default function PieChart({ uiSchema, data }) {
  const total = data.reduce((sum, row) => sum + (Number(row[uiSchema.valueField]) || 0), 0)

  const pieData = data.map((row, i) => ({
    name:  row[uiSchema.nameField],
    value: row[uiSchema.valueField],
    itemStyle: {
      color:       radialGradient(PALETTE[i % PALETTE.length]),
      shadowBlur:  24,
      shadowColor: PALETTE[i % PALETTE.length] + '55',
      borderWidth: 2,
      borderColor: '#070c18',
    },
  }))

  const option = {
    backgroundColor: 'transparent',
    color: PALETTE,
    title: [
      {
        text: uiSchema.title,
        left: 'center',
        top: 10,
        textStyle: {
          color:      '#e0e8ff',
          fontSize:   15,
          fontWeight: 700,
          textShadowBlur:  8,
          textShadowColor: '#a855f744',
        },
      },
      {
        text:     fmt(total),
        subtext:  'Total',
        left:     '49.5%',
        top:      '44%',
        textAlign: 'center',
        textStyle: {
          color:      '#00d4ff',
          fontSize:   17,
          fontWeight: 800,
          textShadowBlur:  12,
          textShadowColor: '#00d4ff66',
        },
        subtextStyle: { color: '#607898', fontSize: 11 },
      },
    ],
    tooltip: {
      trigger:         'item',
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor:     '#a855f733',
      borderWidth:     1,
      padding:         [10, 14],
      textStyle:       { color: '#c0d0e8', fontSize: 12 },
      formatter: p => `
        <div style="font-weight:700;color:${PALETTE[p.dataIndex % PALETTE.length]};font-size:13px;margin-bottom:6px">${esc(p.name)}</div>
        <div style="margin:2px 0">${p.marker} Value: <b style="color:#fff">${fmt(p.value)}</b></div>
        <div style="color:#607898;margin-top:4px;font-size:11px">${p.percent.toFixed(1)}% of total</div>
      `,
    },
    legend: {
      bottom: 6,
      icon:      'circle',
      itemGap:   14,
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#607898', fontSize: 11 },
    },
    series: [{
      type:   'pie',
      radius: ['42%', '68%'],
      center: ['50%', '52%'],
      data:   pieData,
      emphasis: {
        scale:     true,
        scaleSize: 10,
        itemStyle: {
          shadowBlur: 48,
        },
        label: { fontSize: 13, fontWeight: 700 },
      },
      label: {
        color:      '#8099bb',
        fontSize:   11,
        formatter:  '{b}\n{d}%',
        lineHeight: 18,
      },
      labelLine: {
        length:      12,
        length2:     8,
        smooth:      true,
        lineStyle:   { color: '#1e3055', width: 1.5 },
      },
      animationType:   'expansion',
      animationEasing: 'cubicOut',
      animationDuration: 900,
    }],
  }

  return <EChartsWrapper option={option} />
}
