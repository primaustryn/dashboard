import { useWidget } from '../hooks/useWidget'
import { useWidgetList } from '../hooks/useWidgetList'
import WidgetRenderer from '../components/WidgetRenderer'

export default function DashboardBoard() {
  const { widgets, loading: listLoading, error: listError } = useWidgetList()

  if (listLoading) return <div style={styles.center}>Loading dashboard...</div>
  if (listError)   return <div style={{ ...styles.center, color: '#f43f5e' }}>Failed to load widget list: {listError}</div>
  if (widgets.length === 0) return (
    <div style={styles.center}>
      No widgets registered.<br />
      <span style={{ color: '#607898', fontSize: '13px' }}>POST to /api/v1/admin/widgets to add one.</span>
    </div>
  )

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <div style={styles.logoRow}>
          <div style={styles.logoDot} />
          <div style={styles.logoDot2} />
        </div>
        <h1 style={styles.title}>Dynamic Dashboard Engine</h1>
        <p style={styles.subtitle}>Metadata-driven &nbsp;·&nbsp; Air-gapped &nbsp;·&nbsp; Zero code change per widget</p>
      </header>

      <main style={styles.grid}>
        {widgets.map(({ widgetId }) => (
          <WidgetCard key={widgetId} widgetId={widgetId} />
        ))}
      </main>

      <footer style={styles.footer}>
        {widgets.length} widget{widgets.length !== 1 ? 's' : ''} active
      </footer>
    </div>
  )
}

function WidgetCard({ widgetId, params }) {
  const { data, loading, error } = useWidget(widgetId, params)

  return (
    <div className="widget-card" style={styles.card}>
      <div style={styles.cardHeader}>
        <span style={styles.dot} />
        <span style={styles.dot2} />
        <span style={styles.dot3} />
        <span style={styles.widgetId}>{widgetId}</span>
      </div>

      {loading && <div style={styles.state}><span style={styles.spinner}>◌</span> Loading...</div>}

      {error && (
        <div style={{ ...styles.state, color: '#f43f5e' }}>
          {error}
        </div>
      )}

      {data && (
        <WidgetRenderer uiSchema={data.uiSchema} data={data.data} />
      )}
    </div>
  )
}

const styles = {
  page: {
    padding: '28px 32px 48px',
    minHeight: '100vh',
    background: 'linear-gradient(160deg, #070c18 0%, #0a1020 55%, #07101e 100%)',
  },
  center: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    color: '#607898',
    fontSize: '15px',
    gap: '8px',
    background: '#070c18',
  },
  header: {
    marginBottom: '36px',
    paddingBottom: '24px',
    borderBottom: '1px solid #0f1e35',
  },
  logoRow: {
    display: 'flex',
    gap: '6px',
    marginBottom: '16px',
    alignItems: 'center',
  },
  logoDot: {
    width: '10px',
    height: '10px',
    borderRadius: '50%',
    background: '#00d4ff',
    boxShadow: '0 0 10px #00d4ff, 0 0 20px #00d4ff66',
  },
  logoDot2: {
    width: '10px',
    height: '10px',
    borderRadius: '50%',
    background: '#a855f7',
    boxShadow: '0 0 10px #a855f7, 0 0 20px #a855f766',
  },
  title: {
    fontSize: '28px',
    fontWeight: 800,
    letterSpacing: '-0.5px',
    margin: 0,
    background: 'linear-gradient(90deg, #00d4ff 0%, #a855f7 60%, #00ffaa 100%)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    backgroundClip: 'text',
  },
  subtitle: {
    marginTop: '8px',
    fontSize: '12px',
    color: '#2d4a6a',
    letterSpacing: '0.12em',
    textTransform: 'uppercase',
    fontWeight: 500,
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(580px, 1fr))',
    gap: '24px',
  },
  card: {
    background: 'linear-gradient(145deg, #0b1526 0%, #08101e 100%)',
    borderRadius: '14px',
    border: '1px solid #0f1e35',
    boxShadow: '0 4px 28px rgba(0,0,0,0.5), 0 0 0 1px rgba(0,212,255,0.03)',
    overflow: 'hidden',
    transition: 'border-color 0.25s ease, box-shadow 0.25s ease',
  },
  cardHeader: {
    padding: '10px 16px',
    background: 'linear-gradient(90deg, #0a1828 0%, #08111f 100%)',
    borderBottom: '1px solid #0f1e35',
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  dot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#00d4ff',
    boxShadow: '0 0 6px #00d4ff88',
    flexShrink: 0,
  },
  dot2: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#a855f7',
    boxShadow: '0 0 6px #a855f788',
    flexShrink: 0,
  },
  dot3: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#00ffaa',
    boxShadow: '0 0 6px #00ffaa88',
    flexShrink: 0,
  },
  widgetId: {
    marginLeft: '6px',
    color: '#3a6080',
    fontSize: '10px',
    fontFamily: 'monospace',
    letterSpacing: '0.14em',
    textTransform: 'uppercase',
    fontWeight: 600,
  },
  state: {
    padding: '70px',
    textAlign: 'center',
    color: '#2d4a6a',
    fontSize: '14px',
  },
  spinner: {
    display: 'inline-block',
    marginRight: '6px',
    animation: 'spin 1s linear infinite',
  },
  footer: {
    marginTop: '40px',
    textAlign: 'center',
    color: '#1a2d45',
    fontSize: '11px',
    letterSpacing: '0.1em',
    textTransform: 'uppercase',
  },
}
