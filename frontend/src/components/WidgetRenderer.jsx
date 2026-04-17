import BarChart      from './charts/BarChart'
import PieChart      from './charts/PieChart'
import LineChart     from './charts/LineChart'
import HeatmapChart  from './charts/HeatmapChart'
import RadarChart    from './charts/RadarChart'
import GaugeChart    from './charts/GaugeChart'
import ScatterChart  from './charts/ScatterChart'
import TreemapChart  from './charts/TreemapChart'
import GeoChart      from './charts/GeoChart'

const CHART_REGISTRY = {
  bar:      BarChart,
  pie:      PieChart,
  line:     LineChart,
  heatmap:  HeatmapChart,
  radar:    RadarChart,
  gauge:    GaugeChart,
  scatter:  ScatterChart,
  treemap:  TreemapChart,
  map:      GeoChart,
}

export default function WidgetRenderer({ uiSchema, data }) {
  if (!uiSchema || !data) return null

  const ChartComponent = CHART_REGISTRY[uiSchema.chart_type]

  if (!ChartComponent) {
    return (
      <div style={{ padding: 20, color: '#fbbf24', fontFamily: 'monospace', fontSize: 12 }}>
        Unknown chart_type: <b>{uiSchema.chart_type}</b><br />
        <span style={{ color: '#607898' }}>
          Supported: {Object.keys(CHART_REGISTRY).join(', ')}
        </span>
      </div>
    )
  }

  return <ChartComponent uiSchema={uiSchema} data={data} />
}
