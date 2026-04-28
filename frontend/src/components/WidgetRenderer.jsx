import BarChart          from './charts/BarChart'
import PieChart          from './charts/PieChart'
import LineChart         from './charts/LineChart'
import HeatmapChart      from './charts/HeatmapChart'
import RadarChart        from './charts/RadarChart'
import GaugeChart        from './charts/GaugeChart'
import ScatterChart      from './charts/ScatterChart'
import TreemapChart      from './charts/TreemapChart'
import GeoChart          from './charts/GeoChart'
import CandlestickChart  from './charts/CandlestickChart'

// ─────────────────────────────────────────────────────────────────────────────
// Semantic Visualization Registry
//
// Keys are human-intent terms from the GitOps YAML uiSchema (e.g.
// `visualization: comparison`).  Values are the ECharts component that best
// renders that intent.
//
// Why semantic keys instead of library-specific ones (e.g. chart_type: bar)?
//   - A financial analyst writing a widget YAML thinks in terms of analytical
//     intent ("I want to compare these values"), not library internals.
//   - Decoupling intent from implementation means we can swap the charting
//     library (ECharts → D3, Recharts, etc.) without touching a single YAML
//     file or database row.
//   - 100+ widgets across teams benefit from a controlled vocabulary that
//     makes dashboard governance and auditing tractable.
// ─────────────────────────────────────────────────────────────────────────────
const SEMANTIC_REGISTRY = {
  // How do values compare across categories?  → vertical bar chart
  comparison:   BarChart,

  // What share does each part hold?  → pie / donut chart
  proportion:   PieChart,

  // How has a metric changed over time?  → line chart
  trend:        LineChart,

  // Where is the density / intensity concentrated?  → heatmap matrix
  distribution: HeatmapChart,

  // What is the multi-dimensional risk profile?  → radar / spider chart
  profile:      RadarChart,

  // How close is this metric to its operational limit?  → gauge chart
  utilization:  GaugeChart,

  // Is there a relationship between two continuous variables?  → scatter / bubble
  correlation:  ScatterChart,

  // How is a total broken down into nested sub-categories?  → treemap
  hierarchy:    TreemapChart,

  // Where in the world does this exposure / position exist?  → choropleth map
  geography:    GeoChart,

  // How did a price / rate move over a period?  → candlestick (OHLC) chart
  ohlc:         CandlestickChart,
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy Registry  (backward compatibility)
//
// Widgets registered via the old POST /api/v1/admin/widgets API still carry
// `chart_type: bar|pie|...` in their uiSchema.  This registry resolves those
// until all widgets are migrated to the GitOps deploy path.
// Remove this block once migration is complete.
// ─────────────────────────────────────────────────────────────────────────────
const LEGACY_REGISTRY = {
  bar:     BarChart,
  pie:     PieChart,
  line:    LineChart,
  heatmap: HeatmapChart,
  radar:   RadarChart,
  gauge:   GaugeChart,
  scatter:      ScatterChart,
  treemap:      TreemapChart,
  map:          GeoChart,
  candlestick:  CandlestickChart,
}

// ─────────────────────────────────────────────────────────────────────────────
// Priority → ECharts Color Palette
//
// Financial dashboards use color to communicate operational urgency.
// The resolved palette is forwarded to chart components as a rendering hint;
// each chart component may apply it or ignore it depending on chart type.
//
// `priority` is an optional semantic field in the uiSchema.  Widgets without
// a priority key default to 'medium' (blue — standard monitoring).
// ─────────────────────────────────────────────────────────────────────────────
const PRIORITY_PALETTES = {
  critical: ['#ef4444', '#f87171', '#fca5a5', '#fee2e2'], // Red   — immediate action required
  high:     ['#f97316', '#fb923c', '#fdba74', '#ffedd5'], // Amber — elevated concern
  medium:   ['#3b82f6', '#60a5fa', '#93c5fd', '#dbeafe'], // Blue  — normal monitoring (default)
  low:      ['#6b7280', '#9ca3af', '#d1d5db', '#f3f4f6'], // Gray  — informational / archival
}
const DEFAULT_PRIORITY = 'medium'

// ─────────────────────────────────────────────────────────────────────────────
// WidgetRenderer
//
// Resolution order:
//   1. uiSchema.visualization  (semantic — new GitOps widgets)
//   2. uiSchema.chart_type     (legacy   — old admin API widgets)
//   3. Error state             (unknown type shown inline)
// ─────────────────────────────────────────────────────────────────────────────
export default function WidgetRenderer({ uiSchema, data }) {
  if (!uiSchema || !data) return null

  const ChartComponent =
    SEMANTIC_REGISTRY[uiSchema.visualization] ??
    LEGACY_REGISTRY[uiSchema.chart_type]

  if (!ChartComponent) {
    return <UnknownVisualization uiSchema={uiSchema} />
  }

  const colors = PRIORITY_PALETTES[uiSchema.priority] ?? PRIORITY_PALETTES[DEFAULT_PRIORITY]

  // `colors` is a rendering hint — chart components that support priority-based
  // theming accept it; components that don't simply ignore the extra prop.
  return <ChartComponent uiSchema={uiSchema} data={data} colors={colors} />
}

// ─────────────────────────────────────────────────────────────────────────────
// Error state — shown inline so other widgets on the dashboard still render
// ─────────────────────────────────────────────────────────────────────────────
function UnknownVisualization({ uiSchema }) {
  const received = uiSchema.visualization ?? uiSchema.chart_type ?? '(none)'

  return (
    <div style={{
      padding: '16px 20px',
      border: '1px solid #374151',
      borderRadius: 6,
      background: '#111827',
      fontFamily: 'monospace',
      fontSize: 12,
      color: '#fbbf24',
    }}>
      <div style={{ marginBottom: 8 }}>
        <strong>Unknown visualization type:</strong>{' '}
        <code style={{ background: '#1f2937', padding: '2px 6px', borderRadius: 3 }}>
          {received}
        </code>
      </div>

      <div style={{ color: '#9ca3af', lineHeight: 1.6 }}>
        <div>
          <strong style={{ color: '#6b7280' }}>Semantic (recommended):</strong>{' '}
          {Object.keys(SEMANTIC_REGISTRY).join(' · ')}
        </div>
        <div>
          <strong style={{ color: '#6b7280' }}>Legacy:</strong>{' '}
          {Object.keys(LEGACY_REGISTRY).join(' · ')}
        </div>
      </div>
    </div>
  )
}
