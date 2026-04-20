import EChartsWrapper from '../EChartsWrapper'
import { escapeHtml as esc } from '../../utils/html'

export default function HeatmapChart({ uiSchema, data }) {
  const xValues = [...new Set(data.map(row => String(row[uiSchema.xField] ?? '')))]
  const yValues = [...new Set(data.map(row => String(row[uiSchema.yField] ?? '')))]

  const gridData = data.map(row => [
    xValues.indexOf(String(row[uiSchema.xField])),
    yValues.indexOf(String(row[uiSchema.yField])),
    row[uiSchema.valueField] ?? 0,
  ])

  const allVals = gridData.map(d => d[2])
  const minVal  = Math.min(...allVals)
  const maxVal  = Math.max(...allVals)

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title,
      left: 'center', top: 10,
      textStyle: { color: '#e0e8ff', fontSize: 15, fontWeight: 700, textShadowBlur: 8, textShadowColor: '#00d4ff33' },
    },
    tooltip: {
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor: '#00d4ff33', borderWidth: 1, padding: [10, 14],
      textStyle: { color: '#c0d0e8', fontSize: 12 },
      formatter: p => `
        <div style="margin-bottom:6px">
          <b style="color:#00d4ff">${esc(xValues[p.data[0]])}</b>
          <span style="color:#607898"> × </span>
          <b style="color:#a855f7">${esc(yValues[p.data[1]])}</b>
        </div>
        <div>Value: <b style="color:#fff">${Number(p.data[2]).toLocaleString()}</b></div>
      `,
    },
    grid: { left: '14%', right: '10%', top: '18%', bottom: '20%' },
    xAxis: {
      type: 'category', data: xValues,
      name: uiSchema.xLabel, nameTextStyle: { color: '#607898', fontSize: 10 },
      axisLabel: { color: '#607898', rotate: 30, fontSize: 10 },
      axisLine: { lineStyle: { color: '#1a2d4d' } }, axisTick: { show: false },
      splitArea: { show: true, areaStyle: { color: ['rgba(0,0,0,0)', 'rgba(0,212,255,0.025)'] } },
    },
    yAxis: {
      type: 'category', data: yValues,
      name: uiSchema.yLabel, nameTextStyle: { color: '#607898', fontSize: 10 },
      axisLabel: { color: '#607898', fontSize: 10 },
      axisLine: { lineStyle: { color: '#1a2d4d' } }, axisTick: { show: false },
      splitArea: { show: true, areaStyle: { color: ['rgba(0,0,0,0)', 'rgba(168,85,247,0.025)'] } },
    },
    visualMap: {
      min: minVal, max: maxVal,
      calculable: true, orient: 'horizontal', left: 'center', bottom: 4,
      textStyle: { color: '#607898', fontSize: 10 },
      inRange: { color: ['#071428', '#0c2048', '#0051cc', '#0099ff', '#00d4ff', '#00ffcc', '#b8ff6e'] },
      outOfRange: { color: '#0a1528' },
    },
    series: [{
      type: 'heatmap',
      data: gridData,
      label: {
        show: gridData.length <= 80,
        color: '#e0e8ff', fontSize: 10, fontWeight: 600,
        formatter: p => Number(p.data[2]).toLocaleString(),
      },
      itemStyle: { borderWidth: 2, borderColor: '#070c18', borderRadius: 3 },
      emphasis: {
        itemStyle: { shadowBlur: 24, shadowColor: '#00d4ffaa', borderColor: '#00d4ff', borderWidth: 1 },
      },
    }],
  }

  return <EChartsWrapper option={option} />
}
