import ReactECharts from 'echarts-for-react'

export default function EChartsWrapper({ option, style }) {
  const mergedStyle = { height: '420px', width: '100%', ...style }

  return (
    <ReactECharts
      option={option}
      style={mergedStyle}
      notMerge={true}
      lazyUpdate={false}
    />
  )
}
