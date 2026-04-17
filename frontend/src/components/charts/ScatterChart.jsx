import EChartsWrapper from '../EChartsWrapper'

const COLORS = ['#00d4ff', '#a855f7', '#00ffaa', '#fbbf24', '#f43f5e', '#06b6d4', '#fb923c', '#34d399']

function radial(color) {
  return {
    type: 'radial', x: 0.4, y: 0.3, r: 0.7,
    colorStops: [
      { offset: 0, color: color + 'ff' },
      { offset: 1, color: color + '44' },
    ],
  }
}

export default function ScatterChart({ uiSchema, data }) {
  const sizes  = data.map(r => Number(r[uiSchema.sizeField]) || 1)
  const maxSz  = Math.max(...sizes, 1)

  const scatterData = data.map((row, i) => {
    const color = COLORS[i % COLORS.length]
    const sz    = 14 + (sizes[i] / maxSz) * 44
    return {
      name:       String(row[uiSchema.nameField] ?? i),
      value:      [row[uiSchema.xField], row[uiSchema.yField]],
      rawSize:    sizes[i],
      symbolSize: sz,
      itemStyle: {
        color:       radial(color),
        shadowBlur:  16,
        shadowColor: color + '66',
        borderColor: '#070c18',
        borderWidth: 1.5,
      },
    }
  })

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
      formatter: p => `
        <div style="font-weight:700;color:#00d4ff;font-size:13px;margin-bottom:6px">${p.data.name}</div>
        <div style="margin:2px 0">${uiSchema.xLabel ?? 'X'}: <b style="color:#fff">${p.value[0]}</b></div>
        <div style="margin:2px 0">${uiSchema.yLabel ?? 'Y'}: <b style="color:#fff">${p.value[1]}</b></div>
        ${uiSchema.sizeField ? `<div style="margin:2px 0;color:#8099bb">Size: <b style="color:#fff">${p.data.rawSize}</b></div>` : ''}
      `,
    },
    grid: { left: '6%', right: '4%', top: '18%', bottom: '12%', containLabel: true },
    xAxis: {
      type: 'value',
      name: uiSchema.xLabel, nameTextStyle: { color: '#607898', fontSize: 10 },
      axisLabel: { color: '#607898', fontSize: 11 },
      axisLine: { lineStyle: { color: '#1a2d4d' } }, axisTick: { show: false },
      splitLine: { lineStyle: { color: '#111e33', type: 'dashed' } },
    },
    yAxis: {
      type: 'value',
      name: uiSchema.yLabel, nameTextStyle: { color: '#607898', fontSize: 10 },
      axisLabel: { color: '#607898', fontSize: 11 },
      axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: '#111e33', type: 'dashed' } },
    },
    series: [{
      type: 'scatter',
      data: scatterData,
      label: {
        show: true, position: 'top',
        formatter: p => p.data.name,
        color: '#8099bb', fontSize: 10,
      },
      emphasis: {
        scale: true,
        label: { color: '#e0e8ff', fontWeight: 700 },
        itemStyle: { shadowBlur: 32 },
      },
    }],
  }

  return <EChartsWrapper option={option} />
}
