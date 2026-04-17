import EChartsWrapper from '../EChartsWrapper'

const PALETTE = [
  ['#00d4ff', '#0051cc'], ['#a855f7', '#5b21b6'],
  ['#00ffaa', '#059669'], ['#fbbf24', '#d97706'],
  ['#f43f5e', '#be123c'], ['#06b6d4', '#0e7490'],
  ['#fb923c', '#c2410c'], ['#34d399', '#065f46'],
]

function gradient([top, bottom]) {
  return {
    type: 'linear', x: 0, y: 0, x2: 1, y2: 1,
    colorStops: [
      { offset: 0, color: top + 'cc' },
      { offset: 1, color: bottom + '88' },
    ],
  }
}

function fmt(val) {
  if (Math.abs(val) >= 1_000_000) return '$' + (val / 1_000_000).toFixed(1) + 'M'
  if (Math.abs(val) >= 1_000)     return '$' + (val / 1_000).toFixed(0) + 'K'
  return '$' + val
}

function buildTree(data, nameField, valueField, groupField) {
  if (!groupField) {
    return data.map((row, i) => ({
      name:  row[nameField] ?? `Item ${i}`,
      value: row[valueField] ?? 0,
      itemStyle: { color: gradient(PALETTE[i % PALETTE.length]) },
    }))
  }

  const groups = {}
  data.forEach(row => {
    const g = String(row[groupField] ?? 'Other')
    if (!groups[g]) groups[g] = []
    groups[g].push(row)
  })

  return Object.entries(groups).map(([g, rows], gi) => ({
    name:      g,
    itemStyle: { color: gradient(PALETTE[gi % PALETTE.length]) },
    children:  rows.map((row, ci) => ({
      name:  row[nameField] ?? `Item ${ci}`,
      value: row[valueField] ?? 0,
      itemStyle: { color: gradient(PALETTE[gi % PALETTE.length]) },
    })),
  }))
}

export default function TreemapChart({ uiSchema, data }) {
  const treeData = buildTree(data, uiSchema.nameField, uiSchema.valueField, uiSchema.groupField)

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title, left: 'center', top: 10,
      textStyle: { color: '#e0e8ff', fontSize: 15, fontWeight: 700, textShadowBlur: 8, textShadowColor: '#a855f733' },
    },
    tooltip: {
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor: '#00d4ff33', borderWidth: 1, padding: [10, 14],
      textStyle: { color: '#c0d0e8', fontSize: 12 },
      formatter: p => {
        const path = (p.treePathInfo ?? []).map(n => n.name).filter(Boolean)
        return `
          <div style="font-weight:700;color:#00d4ff;margin-bottom:6px">${p.name}</div>
          <div>Value: <b style="color:#fff">${fmt(p.value)}</b></div>
          ${path.length > 1 ? `<div style="color:#607898;font-size:11px;margin-top:4px">${path.join(' › ')}</div>` : ''}
        `
      },
    },
    series: [{
      type: 'treemap',
      data: treeData,
      top: '14%', bottom: '4%', left: '2%', right: '2%',
      roam: false, nodeClick: 'zoomToNode',
      breadcrumb: {
        show: true, bottom: 4, height: 22,
        textStyle: { color: '#607898', fontSize: 10 },
        itemStyle: { color: '#0a1528', borderColor: '#1e3055', borderWidth: 1 },
        emphasis: { itemStyle: { color: '#0d1e3a' } },
      },
      label: {
        color: '#e0e8ff', fontSize: 12, fontWeight: 600,
        formatter: p => `${p.name}\n${fmt(p.value)}`,
        lineHeight: 18,
      },
      upperLabel: {
        show: true, height: 26,
        color: '#e0e8ff', fontWeight: 700, fontSize: 12,
        borderRadius: [4, 4, 0, 0],
      },
      itemStyle: { borderWidth: 2, borderColor: '#070c18', gapWidth: 2, borderRadius: 2 },
      emphasis: {
        itemStyle: { shadowBlur: 24, shadowColor: '#00d4ff33' },
        label: { fontWeight: 700 },
      },
      levels: [
        { itemStyle: { borderWidth: 3, borderColor: '#070c18', gapWidth: 3 }, upperLabel: { show: false } },
        { itemStyle: { borderWidth: 1, borderColor: '#070c18', gapWidth: 1 } },
      ],
    }],
  }

  return <EChartsWrapper option={option} />
}
