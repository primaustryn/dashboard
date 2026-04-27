# Metadata-Driven Dynamic Dashboard Engine

A **zero-code-change** dashboard platform for air-gapped banking environments.  
Widget SQL, chart type, axes, and title all live in the database.  
**Adding a new widget = YAML file + API call. No Java, no React rebuild.**

---

## Table of Contents

1. [Architecture](#architecture)
2. [Quick Start](#quick-start)
3. [Database Schema](#database-schema)
4. [GitOps Widget Deployment](#gitops-widget-deployment)
5. [Registered Widgets](#registered-widgets)
6. [How to Add a Widget](#how-to-add-a-widget)
7. [How to Update a Widget](#how-to-update-a-widget)
8. [How to Delete a Widget](#how-to-delete-a-widget)
9. [Activate / Deactivate](#activate--deactivate)
10. [uiSchema Reference — All 9 Chart Types](#uischema-reference--all-9-chart-types)
11. [Admin API Reference](#admin-api-reference)
12. [Data Ingestion API](#data-ingestion-api)
13. [Data Flow](#data-flow)
14. [Security Controls](#security-controls)
15. [Project Structure](#project-structure)
16. [Adding a New Chart Type (Frontend)](#adding-a-new-chart-type-frontend)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  Browser  (React + ECharts, Vite dev server on :5173)               │
│  DashboardBoard → polls active widget list → renders WidgetCards    │
│  WidgetRenderer → semantic vocab (visualization:) + legacy fallback │
└────────────────────┬────────────────────────────────────────────────┘
                     │ HTTP  /api/v1/...
┌────────────────────▼────────────────────────────────────────────────┐
│  Spring Boot  (port 8081)                                           │
│                                                                     │
│  SecurityHeaderFilter    — security response headers                │
│  AuditInterceptor        — HTTP request audit log (AUDIT_LOG)       │
│                                                                     │
│  WidgetDeployController  — POST /api/v1/admin/widgets/deploy        │
│    └─ WidgetDeployService                                           │
│         parse YAML → validate → SQL dry-run → Base64-encode → save  │
│                                                                     │
│  WidgetEngineController  — GET /api/v1/widgets/{id}                 │
│    └─ WidgetEngineService (dual-path resolution)                    │
│         ├─ Primary:  WIDGET_PAYLOAD (GitOps, Base64-chunked)        │
│         └─ Fallback: WIDGET_QUERY + WIDGET_CONFIG (legacy EAV)      │
│              └─ DynamicWidgetDao (30 s timeout · 10 000-row cap)    │
│                                                                     │
│  WidgetAdminController   — CRUD /api/v1/admin/widgets               │
│    └─ WidgetDefinitionService → WidgetAuditDao (WIDGET_AUDIT)       │
│  GenericDataController   — insert rows into any target table        │
└─────────────────────────────────────────────────────────────────────┘
         │                              │
┌────────▼──────────────┐    ┌──────────▼──────────┐
│  Meta DB (metadb)     │    │ Target DB (targetdb) │
│  WIDGET_MASTER        │    │ SALES_SUMMARY        │
│  WIDGET_PAYLOAD  (v2) │    │ TRADE_SUMMARY        │
│  WIDGET_QUERY    (v1) │    │ RISK_SUMMARY         │
│  WIDGET_CONFIG   (v1) │    │ FX_RATE              │
│  WIDGET_AUDIT         │    │ RISK_MATRIX  …etc.   │
│  AUDIT_LOG            │    └─────────────────────┘
└───────────────────────┘
```

### Two Databases

| Database | H2 Name | Purpose |
|----------|---------|---------|
| **Meta DB** | `metadb` | Widget definitions, lifecycle audit, HTTP audit |
| **Target DB** | `targetdb` | Business data (sales, trades, risk, FX, …) |

---

## Quick Start

### Prerequisites

- Java 21+
- Node.js 18+
- Backend not already running on port 8081

### Steps

```bash
# Terminal 1 — Start the backend (all target tables created automatically at startup)
./run.sh

# Terminal 2 — Seed initial data and register the first 6 widgets
./setup.sh

# (Optional) Add 6 more chart-type widgets
./setup-new-charts.sh

# Terminal 3 — Start the frontend dev server
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173**

> **Map widget** (`WD_GLOBAL_MAP`) requires `world.json` in `frontend/public/`:
> ```bash
> curl -o frontend/public/world.json \
>   "https://echarts.apache.org/examples/data/asset/geo/world.json"
> ```

> **Credentials**: By default the server uses H2 in-memory databases with no password.  
> For production, supply credentials via environment variables (see [Security Controls](#security-controls)).

---

## Database Schema

### Meta DB — Widget Definitions & Audit

The meta DB uses **VARCHAR-only** tables (no CLOB/TEXT/BLOB).  
This design is compatible with Oracle, Tibero, and any database that restricts column types.

```
WIDGET_MASTER (one row per widget)
┌─────────────┬──────────────┬───────────────────────────────────────┐
│ widget_id   │ VARCHAR(50)  │ PK — e.g. "WD_SALES_REGION"          │
│ target_db   │ VARCHAR(100) │ DataSource registry key — "TARGET_DB" │
│ is_active   │ BOOLEAN      │ true = shown on dashboard             │
└─────────────┴──────────────┴───────────────────────────────────────┘

WIDGET_PAYLOAD (v2 — GitOps path, Base64-chunked)
┌─────────────┬──────────────┬───────────────────────────────────────────────┐
│ widget_id   │ VARCHAR(50)  │ FK → WIDGET_MASTER                            │
│ payload_type│ VARCHAR(20)  │ 'SQL' | 'UI_SCHEMA'                           │
│ chunk_order │ INT          │ 0-based; reassemble in ASC order              │
│ base64_data │ VARCHAR(4000)│ Base64-encoded fragment (ASCII-safe for CJK)  │
└─────────────┴──────────────┴───────────────────────────────────────────────┘

  Why Base64?  Chunking raw UTF-8 at a 4 000-byte boundary can split a multi-byte
  Korean/CJK codepoint mid-character.  Base64-encoding converts arbitrary bytes to
  7-bit ASCII first — every stored character is exactly one byte, making the
  VARCHAR(4000) limit safe for any source charset.

WIDGET_QUERY (v1 legacy — raw SQL chunks, FK → WIDGET_MASTER)
┌─────────────┬──────────────┬───────────────────────────────────────┐
│ widget_id   │ VARCHAR(50)  │ FK → WIDGET_MASTER                    │
│ chunk_order │ INT          │ 0-based order for reassembly           │
│ chunk_text  │ VARCHAR(4000)│ Raw SQL fragment (no encoding)        │
└─────────────┴──────────────┴───────────────────────────────────────┘

WIDGET_CONFIG (v1 legacy — EAV, each top-level JSON key as one row)
┌─────────────┬──────────────┬───────────────────────────────────────┐
│ widget_id   │ VARCHAR(50)  │ FK → WIDGET_MASTER                    │
│ config_key  │ VARCHAR(100) │ JSON key  — e.g. "chart_type"         │
│ config_val  │ VARCHAR(1000)│ JSON value — e.g. "\"bar\""           │
└─────────────┴──────────────┴───────────────────────────────────────┘

WIDGET_AUDIT (widget lifecycle events)
┌────────────┬──────────────┬────────────────────────────────────────┐
│ id         │ BIGINT       │ PK AUTO_INCREMENT                      │
│ widget_id  │ VARCHAR(50)  │ Which widget was acted on              │
│ action     │ VARCHAR(20)  │ CREATE / UPDATE / DELETE / ACTIVATE … │
│ changed_at │ TIMESTAMP    │ DEFAULT CURRENT_TIMESTAMP              │
└────────────┴──────────────┴────────────────────────────────────────┘

AUDIT_LOG (HTTP request log — one row per /api/** call)
┌─────────────┬──────────────┬───────────────────────────────────────┐
│ id          │ BIGINT       │ PK AUTO_INCREMENT                     │
│ request_ts  │ TIMESTAMP    │ DEFAULT CURRENT_TIMESTAMP             │
│ http_method │ VARCHAR(10)  │ GET / POST / PUT / PATCH / DELETE     │
│ request_uri │ VARCHAR(500) │ Request path                          │
│ client_ip   │ VARCHAR(50)  │ Resolved from X-Forwarded-For / remote│
│ status_code │ INT          │ HTTP response status                  │
└─────────────┴──────────────┴───────────────────────────────────────┘
```

### Target DB — Business Data

All 10 tables are created automatically at startup via `db/target/schema.sql`.  
**No DDL API calls are required.**

```sql
SALES_SUMMARY    (id, region, product, amount DECIMAL, sale_date DATE)
TRADE_SUMMARY    (id, symbol, desk, notional DECIMAL, trade_date DATE)
RISK_SUMMARY     (id, portfolio, var_amount DECIMAL, report_date DATE)
FX_RATE          (id, pair, rate DECIMAL, rate_date DATE)
RISK_MATRIX      (id, desk, risk_type, score DECIMAL)
PORTFOLIO_SCORES (id, portfolio, market_score, credit_score, liquidity_score, op_score, compliance_score)
VAR_UTILIZATION  (id, portfolio, utilization DECIMAL)
ASSET_PERFORMANCE(id, asset, risk_pct, return_pct, volume DECIMAL)
PORTFOLIO_AUM    (id, region, asset_class, aum DECIMAL)
GLOBAL_EXPOSURE  (id, country, exposure DECIMAL)
```

---

## GitOps Widget Deployment

The recommended path for adding or updating widgets is the **GitOps deploy endpoint**.  
A YAML file committed to your widget registry repo is POSTed to `/deploy` by CI/CD.

### YAML file format

```yaml
widgetId:  WD_SALES_REGION         # 2–50 uppercase alphanumeric/underscore
targetDb:  TARGET_DB               # must match a registered DataSource key

sql: |
  SELECT region,
         SUM(amount) AS total_amount
  FROM   SALES_SUMMARY
  GROUP  BY region
  ORDER  BY total_amount DESC

uiSchema:
  visualization: comparison        # semantic key (see vocabulary below)
  title:         Sales by Region
  priority:      medium            # critical | high | medium (default) | low
  xAxis:
    field: region
    label: Region
  yAxis:
    label: Amount (USD)
  series:
    - name:       Total Sales
      valueField: total_amount
```

### Deploy command

```bash
curl -s -X POST http://localhost:8081/api/v1/admin/widgets/deploy \
     -H "Content-Type: text/plain" \
     --data-binary @widgets/WD_SALES_REGION.yml
```

### Deployment pipeline (5 stages)

| Stage | What happens |
|-------|-------------|
| 1. Parse | SnakeYAML parses the YAML into a typed request object |
| 2. Validate | Checks required fields, `widgetId` format (`[A-Z][A-Z0-9_]{1,49}`), `targetDb` in registry, `visualization` key present |
| 3. SQL Dry-Run | Executes `SELECT * FROM ({sql}) __preflight WHERE 1=0` against target DB — catches syntax errors, missing tables, bad columns **before** any write |
| 4. Encode | SQL and uiSchema JSON are Base64-encoded then split into ≤4 000-char VARCHAR chunks |
| 5. Persist | Chunks written atomically to `WIDGET_PAYLOAD`; re-deploying the same `widgetId` is fully idempotent |

### HTTP responses

| Status | Meaning |
|--------|---------|
| `201 Created` | Widget deployed; `Location` header → `/api/v1/widgets/{widgetId}` |
| `400 Bad Request` | Malformed YAML or validation failure (missing field, bad `widgetId` format, unknown `targetDb`) |
| `422 Unprocessable Entity` | YAML was valid but SQL dry-run failed (syntax error, missing table, etc.) |

### Dual-path resolution (zero-downtime migration)

`WidgetEngineService` resolves widget metadata in this order:

1. **Primary** — `WIDGET_PAYLOAD` (GitOps, Base64-chunked). Used for any widget deployed via `/deploy`.
2. **Fallback** — `WIDGET_QUERY` + `WIDGET_CONFIG` (legacy EAV). Used for widgets registered via the old `POST /api/v1/admin/widgets` endpoint.

Existing widgets continue to work without re-registration. Migrate them at your own pace.

### Semantic visualization vocabulary

The `visualization` key in `uiSchema` maps to an ECharts component via `SEMANTIC_REGISTRY`:

| Semantic key | Chart | Analytical intent |
|-------------|-------|------------------|
| `comparison` | BarChart | How do values compare across categories? |
| `proportion` | PieChart | What share does each part hold? |
| `trend` | LineChart | How has a metric changed over time? |
| `distribution` | HeatmapChart | Where is the density/intensity concentrated? |
| `profile` | RadarChart | What is the multi-dimensional risk profile? |
| `utilization` | GaugeChart | How close is this metric to its operational limit? |
| `correlation` | ScatterChart | Is there a relationship between two continuous variables? |
| `hierarchy` | TreemapChart | How is a total broken down into nested sub-categories? |
| `geography` | GeoChart | Where in the world does this exposure/position exist? |

Legacy widgets using `chart_type: bar|pie|line|…` continue to render correctly via `LEGACY_REGISTRY`.

### Priority color palettes

The optional `priority` field in `uiSchema` controls the chart color palette:

| Value | Color | Meaning |
|-------|-------|---------|
| `critical` | Red | Immediate action required |
| `high` | Amber | Elevated concern |
| `medium` | Blue | Normal monitoring (default) |
| `low` | Gray | Informational / archival |

---

## Registered Widgets

After running both setup scripts, 12 widgets are active:

| Widget ID | Visualization | Source Table | Description |
|-----------|--------------|--------------|-------------|
| `WD_SALES_REGION` | comparison (bar) | SALES_SUMMARY | Total sales amount by region |
| `WD_SALES_PRODUCT` | proportion (pie) | SALES_SUMMARY | Revenue share by product |
| `WD_SALES_TREND` | trend (line) | SALES_SUMMARY | Monthly revenue trend |
| `WD_TRADE_DESK` | proportion (pie) | TRADE_SUMMARY | Notional by trading desk |
| `WD_RISK_VAR` | comparison (bar) | RISK_SUMMARY | Value at Risk by portfolio |
| `WD_FX_TREND` | trend (line) | FX_RATE | USD/KRW exchange rate trend |
| `WD_RISK_HEATMAP` | distribution (heatmap) | RISK_MATRIX | Desk × risk type intensity matrix |
| `WD_PORTFOLIO_RADAR` | profile (radar) | PORTFOLIO_SCORES | 5-axis risk profile per portfolio |
| `WD_VAR_GAUGE` | utilization (gauge) | VAR_UTILIZATION | VaR limit utilization % |
| `WD_ASSET_SCATTER` | correlation (scatter) | ASSET_PERFORMANCE | Risk vs return bubble chart |
| `WD_AUM_TREEMAP` | hierarchy (treemap) | PORTFOLIO_AUM | AUM by region / asset class |
| `WD_GLOBAL_MAP` | geography (map) | GLOBAL_EXPOSURE | Global credit exposure choropleth |

---

## How to Add a Widget

### Recommended — GitOps YAML deploy

```bash
# 1. Seed data into the target table
curl -s -X POST http://localhost:8081/api/v1/target/MY_TABLE/rows/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"category": "Alpha", "value": 1500000},
    {"category": "Beta",  "value": 2300000}
  ]'

# 2. Create the YAML definition
cat > widgets/WD_MY_WIDGET.yml << 'EOF'
widgetId:  WD_MY_WIDGET
targetDb:  TARGET_DB
sql: |
  SELECT category, SUM(value) AS total FROM MY_TABLE GROUP BY category
uiSchema:
  visualization: comparison
  title: My Widget
  xAxis: { field: category, label: Category }
  yAxis: { label: Total Value }
  series:
    - name: Total
      valueField: total
EOF

# 3. Deploy
curl -s -X POST http://localhost:8081/api/v1/admin/widgets/deploy \
     -H "Content-Type: text/plain" \
     --data-binary @widgets/WD_MY_WIDGET.yml
```

The widget appears on the dashboard immediately — no server restart, no code change.

### Legacy — JSON API (still supported)

```bash
curl -s -X POST http://localhost:8081/api/v1/admin/widgets \
  -H "Content-Type: application/json" \
  -d '{
    "widgetId":      "WD_MY_WIDGET",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT category, SUM(value) AS total FROM MY_TABLE GROUP BY category",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"My Widget\",\"xAxis\":{\"field\":\"category\",\"label\":\"Category\"},\"yAxis\":{\"label\":\"Total Value\"},\"series\":[{\"name\":\"Total\",\"valueField\":\"total\"}]}"
  }'
```

> **Field name casing**: H2 returns column names in UPPERCASE. The frontend normalizes them to lowercase automatically, so `querySql` aliases should use lowercase (`total`, not `TOTAL`).

---

## How to Update a Widget

### GitOps path — edit the YAML and re-deploy

```bash
# Edit widgets/WD_MY_WIDGET.yml, then:
curl -s -X POST http://localhost:8081/api/v1/admin/widgets/deploy \
     -H "Content-Type: text/plain" \
     --data-binary @widgets/WD_MY_WIDGET.yml
```

Re-deploying the same `widgetId` is fully idempotent — existing chunks are replaced atomically.

### Legacy path — PUT

`PUT` replaces `querySql`, `dynamicConfig`, and `targetDb` atomically.

```bash
curl -s -X PUT http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET \
  -H "Content-Type: application/json" \
  -d '{
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT category, SUM(value) AS total FROM MY_TABLE WHERE value > 1000000 GROUP BY category",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"My Widget (filtered)\",...}"
  }'
```

An `UPDATE` row is written to `WIDGET_AUDIT`.

---

## How to Delete a Widget

Permanently removes the widget definition. Table data is **not** affected.

```bash
curl -s -X DELETE http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET
```

A `DELETE` row is written to `WIDGET_AUDIT`.

---

## Activate / Deactivate

Deactivating hides the widget from the dashboard without deleting data or definition.

```bash
# Hide from dashboard
curl -s -X PATCH http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET/deactivate

# Show again
curl -s -X PATCH http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET/activate

# List only active widgets
curl -s "http://localhost:8081/api/v1/admin/widgets?activeOnly=true"

# List all widgets (active + inactive)
curl -s "http://localhost:8081/api/v1/admin/widgets"
```

`ACTIVATE` and `DEACTIVATE` events are written to `WIDGET_AUDIT`.

---

## uiSchema Reference — All 9 Chart Types

The `uiSchema` block in the GitOps YAML (or `dynamicConfig` JSON in the legacy API) drives frontend rendering.  
Field names in `xAxis.field` / `valueField` / `nameField` etc. must match the lowercase column aliases in the SQL.

> **Semantic keys are recommended** (`visualization: comparison`). Legacy `chart_type: bar` keys continue to work via `LEGACY_REGISTRY`.

### comparison / bar

```yaml
uiSchema:
  visualization: comparison
  title: Sales by Region
  xAxis: { field: region, label: Region }
  yAxis: { label: Amount (USD) }
  series:
    - name: Total Sales
      valueField: total_amount
```

*Example query*: `SELECT region, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY region`

---

### proportion / pie

```yaml
uiSchema:
  visualization: proportion
  title: Revenue by Product
  nameField:  product
  valueField: total_amount
```

*Example query*: `SELECT product, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY product`

---

### trend / line

```yaml
uiSchema:
  visualization: trend
  title: Monthly Revenue Trend
  xAxis: { field: sale_month, label: Month }
  yAxis: { label: Amount (USD) }
  series:
    - name: Monthly Sales
      valueField: total_amount
```

*Example query*: `SELECT FORMATDATETIME(sale_date,'yyyy-MM') AS sale_month, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY sale_month ORDER BY sale_month`

---

### distribution / heatmap

```yaml
uiSchema:
  visualization: distribution
  title:      Risk Score Matrix by Desk
  xField:     desk
  yField:     risk_type
  valueField: score
  xLabel:     Trading Desk
  yLabel:     Risk Category
```

*Example query*: `SELECT desk, risk_type, score FROM RISK_MATRIX ORDER BY desk, risk_type`

---

### profile / radar

```yaml
uiSchema:
  visualization: profile
  title:       Portfolio Risk Profile
  nameField:   portfolio
  indicators:
    - { name: Market,      max: 100 }
    - { name: Credit,      max: 100 }
    - { name: Liquidity,   max: 100 }
    - { name: Operational, max: 100 }
    - { name: Compliance,  max: 100 }
  valueFields: [market_score, credit_score, liquidity_score, op_score, compliance_score]
```

`indicators[i].name` labels each axis. `valueFields[i]` is the corresponding column alias (order must match).

*Example query*: `SELECT portfolio, market_score, credit_score, liquidity_score, op_score, compliance_score FROM PORTFOLIO_SCORES ORDER BY portfolio`

---

### utilization / gauge

```yaml
uiSchema:
  visualization: utilization
  title:      VaR Limit Utilization
  nameField:  portfolio
  valueField: utilization
  max:        100
  unit:       "%"
```

Multiple rows → multiple gauges rendered side-by-side in a grid.

*Example query*: `SELECT portfolio, utilization FROM VAR_UTILIZATION ORDER BY portfolio`

---

### correlation / scatter

```yaml
uiSchema:
  visualization: correlation
  title:     Risk vs Return (bubble = AUM)
  xField:    risk_pct
  yField:    return_pct
  sizeField: volume
  nameField: asset
  xLabel:    Risk (%)
  yLabel:    Return (%)
```

`sizeField` drives the bubble radius. `nameField` appears in the tooltip.

*Example query*: `SELECT asset, risk_pct, return_pct, volume FROM ASSET_PERFORMANCE ORDER BY volume DESC`

---

### hierarchy / treemap

```yaml
uiSchema:
  visualization: hierarchy
  title:      AUM by Region & Asset Class
  nameField:  asset_class
  valueField: aum
  groupField: region
```

`groupField` creates the parent nodes. `nameField` is the leaf label.

*Example query*: `SELECT region, asset_class, aum FROM PORTFOLIO_AUM ORDER BY region, aum DESC`

---

### geography / map

```yaml
uiSchema:
  visualization: geography
  title:      Global Credit Exposure
  nameField:  country
  valueField: exposure
```

`nameField` values must match country names in the ECharts world GeoJSON exactly  
(e.g. `"United States"`, `"South Korea"`, `"United Kingdom"`).

Requires `frontend/public/world.json`:
```bash
curl -o frontend/public/world.json \
  "https://echarts.apache.org/examples/data/asset/geo/world.json"
```

*Example query*: `SELECT country, exposure FROM GLOBAL_EXPOSURE ORDER BY exposure DESC`

---

## Admin API Reference

### GitOps deploy endpoint (recommended)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/admin/widgets/deploy` | Deploy a widget from a YAML definition (parse → validate → SQL dry-run → Base64-encode → persist) |

Accepts `Content-Type: text/plain`, `application/yaml`, or `application/x-yaml`.

**Response body (201):**
```json
{
  "widgetId":       "WD_SALES_REGION",
  "status":         "DEPLOYED",
  "sqlChunks":      1,
  "uiSchemaChunks": 1
}
```

### Legacy CRUD endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/admin/widgets` | Register a new widget (EAV storage) |
| `GET` | `/api/v1/admin/widgets` | List all widgets (`?activeOnly=true` to filter) |
| `PUT` | `/api/v1/admin/widgets/{widgetId}` | Replace querySql / dynamicConfig / targetDb |
| `PATCH` | `/api/v1/admin/widgets/{widgetId}/activate` | Mark widget as active |
| `PATCH` | `/api/v1/admin/widgets/{widgetId}/deactivate` | Mark widget as inactive (hidden) |
| `DELETE` | `/api/v1/admin/widgets/{widgetId}` | Permanently remove widget definition |

### Widget data endpoint

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/widgets/{widgetId}` | Fetch widget data + uiSchema (used by frontend) |

**Response:**
```json
{
  "widgetId": "WD_SALES_REGION",
  "uiSchema": {
    "visualization": "comparison",
    "title": "Sales by Region",
    "xAxis": { "field": "region", "label": "Region" },
    "yAxis": { "label": "Amount (USD)" },
    "series": [{ "name": "Total Sales", "valueField": "total_amount" }]
  },
  "data": [
    { "region": "North", "total_amount": 26450000 },
    { "region": "South", "total_amount": 25100000 }
  ],
  "truncated": false
}
```

`truncated: true` means the result set was capped at 10,000 rows server-side. Refine the widget SQL with a `WHERE` clause or aggregation to avoid this.

---

## Data Ingestion API

### Insert rows

```
POST /api/v1/target/{tableName}/rows
Body: { "col1": "val1", "col2": 123 }

POST /api/v1/target/{tableName}/rows/batch
Body: [ { "col1": "val1" }, { "col1": "val2" } ]
```

### Read rows

```
GET /api/v1/target/{tableName}/rows
Response: [ { "id": 1, "col1": "val1" }, ... ]
```

**Identifier safety**: Table and column names are validated against `[A-Za-z][A-Za-z0-9_]{0,127}` and then verified to exist in `INFORMATION_SCHEMA.TABLES` / `INFORMATION_SCHEMA.COLUMNS` before any SQL is executed. Unknown names are rejected with `400 Bad Request`.

> **No DDL API**: Schema changes must be made by editing `src/main/resources/db/target/schema.sql` and restarting the application. Runtime DDL execution was removed to eliminate the attack surface.

---

## Data Flow

```
POST /api/v1/admin/widgets/deploy (rawYaml)
          │
          ├─ 1. Parse YAML (SnakeYAML)
          ├─ 2. Validate (widgetId format, targetDb exists, visualization key)
          ├─ 3. SQL Dry-Run → SELECT * FROM ({sql}) __preflight WHERE 1=0
          │      └─ 422 if DB rejects; DML in subquery is always a syntax error
          ├─ 4. Base64-encode SQL + uiSchema JSON (charset-safe for Korean/CJK)
          └─ 5. UPSERT WIDGET_MASTER + replace WIDGET_PAYLOAD chunks (idempotent)

GET /api/v1/widgets/{widgetId}?param=value
          │
          ▼
  SecurityHeaderFilter  (adds X-Content-Type-Options, X-Frame-Options, CSP, …)
          │
          ▼
  AuditInterceptor      (writes to AUDIT_LOG after response)
          │
          ▼
  WidgetEngineController
          │
          ▼
  WidgetEngineService
    1. WIDGET_MASTER     → verify widget exists + is_active, get targetDb
    2a. WIDGET_PAYLOAD   → reassemble Base64 chunks → decode → querySql + uiSchema
        (primary path — GitOps widgets)
    2b. WIDGET_QUERY + WIDGET_CONFIG  → fallback for pre-GitOps widgets
    3.  Resolve DataSource from registry using targetDb key
    4.  Execute querySql against target DataSource (named params :param substituted)
        └─ DynamicWidgetDao enforces 30 s query timeout + 10 000-row cap
    5.  Return WidgetResponse { widgetId, uiSchema, data[], truncated }
          │
          ▼
  Frontend: WidgetRenderer
    - Reads uiSchema.visualization → SEMANTIC_REGISTRY (new GitOps widgets)
    - Falls back to uiSchema.chart_type → LEGACY_REGISTRY (pre-GitOps widgets)
    - Applies priority color palette (critical/high/medium/low)
    - Passes { uiSchema, data, colors } to matching chart component
```

---

## Security Controls

| Control | Implementation | Purpose |
|---------|---------------|---------|
| No DDL API | `TargetSchemaController` removed; schema managed via `schema.sql` | Eliminates runtime schema tampering |
| SQL dry-run gate | `WidgetDeployService.dryRunSql()`: `SELECT * FROM ({sql}) WHERE 1=0` | Catches bad SQL at deploy time; rejects DML implicitly |
| Query timeout | `DynamicWidgetDao`: 30 s via `setQueryTimeout()` | Prevents runaway queries (DoS) |
| Result row cap | `DynamicWidgetDao`: 10 000 rows via `setMaxRows()` | Prevents memory exhaustion |
| Identifier validation | `GenericRowDao`: regex + INFORMATION_SCHEMA lookup | Prevents SQL injection on table/column names |
| Credential externalization | `application.yml`: `${ENV_VAR:default}` pattern | Keeps secrets out of source control |
| HTTP audit log | `AuditInterceptor` → `AUDIT_LOG` table | Compliance audit trail for all API calls |
| Widget lifecycle audit | `WidgetAuditDao` → `WIDGET_AUDIT` table | Immutable record of widget CRUD events |
| Security response headers | `SecurityHeaderFilter` | Mitigates XSS, clickjacking, MIME sniffing |
| DB error masking | `GlobalExceptionHandler` (`DataAccessException`) | Prevents SQL/schema details leaking to HTTP response |

### Environment variables for production credentials

| Variable | Default (dev) | Description |
|----------|--------------|-------------|
| `META_DB_URL` | `jdbc:h2:mem:metadb;…` | JDBC URL for meta database |
| `META_DB_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `META_DB_USER` | `sa` | Meta DB username |
| `META_DB_PASSWORD` | _(empty)_ | Meta DB password |
| `TARGET_DB_URL` | `jdbc:h2:mem:targetdb;…` | JDBC URL for target database |
| `TARGET_DB_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `TARGET_DB_USER` | `sa` | Target DB username |
| `TARGET_DB_PASSWORD` | _(empty)_ | Target DB password |

### Security response headers

| Header | Value | Purpose |
|--------|-------|---------|
| `X-Content-Type-Options` | `nosniff` | Prevents MIME-type sniffing |
| `X-Frame-Options` | `DENY` | Blocks iframe embedding (clickjacking) |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'unsafe-inline'; …` | Restricts resource loading |
| `Referrer-Policy` | `no-referrer` | Suppresses Referer on cross-origin requests |
| `Cache-Control` (API paths) | `no-store, no-cache, must-revalidate` | Prevents caching of financial data |

---

## Project Structure

```
dashboard/
├── run.sh                            # Start Spring Boot (ensures Java 21)
├── setup.sh                          # Seed data → register 6 widgets
│                                     #   ./setup.sh scenarios  — lifecycle demo
├── setup-new-charts.sh               # Register 6 more chart-type widgets
│                                     #   ./setup-new-charts.sh reset     — re-seed
│                                     #   ./setup-new-charts.sh teardown  — remove widgets
│
├── src/main/java/com/shb/dashboard/
│   ├── audit/
│   │   └── AuditInterceptor.java         # Writes every /api/** request to AUDIT_LOG
│   ├── config/
│   │   ├── DataSourceConfig.java         # Dual H2 datasource + dataSourceRegistry map
│   │   ├── SecurityHeaderFilter.java     # OncePerRequestFilter — 5 security headers
│   │   └── WebConfig.java                # CORS + AuditInterceptor registration
│   ├── controller/
│   │   ├── WidgetEngineController.java   # GET /api/v1/widgets/{id}
│   │   ├── WidgetDeployController.java   # POST /api/v1/admin/widgets/deploy (GitOps)
│   │   ├── WidgetAdminController.java    # CRUD /api/v1/admin/widgets (legacy)
│   │   └── GenericDataController.java   # /api/v1/target/{table}/rows
│   ├── service/
│   │   ├── WidgetEngineService.java      # Dual-path orchestrator (WIDGET_PAYLOAD → EAV fallback)
│   │   ├── WidgetDeployService.java      # 5-stage GitOps pipeline (parse→validate→dry-run→encode→persist)
│   │   ├── WidgetDefinitionService.java  # Legacy widget CRUD + WidgetAuditDao calls
│   │   └── GenericDataService.java       # Dynamic row insertion
│   ├── dao/
│   │   ├── WidgetPayloadDao.java         # Base64-chunked WIDGET_PAYLOAD CRUD
│   │   ├── WidgetDefinitionDao.java      # Legacy EAV read/write (MASTER+QUERY+CONFIG)
│   │   ├── DynamicWidgetDao.java         # Executes querySql (timeout + row cap)
│   │   ├── GenericRowDao.java            # Dynamic INSERT (regex + INFORMATION_SCHEMA guard)
│   │   └── WidgetAuditDao.java           # Writes widget lifecycle events to WIDGET_AUDIT
│   ├── model/
│   │   ├── WidgetDeployRequest.java      # Typed GitOps YAML payload (widgetId, targetDb, sql, uiSchema)
│   │   ├── DeployResult.java             # Deploy response (widgetId, status, sqlChunks, uiSchemaChunks)
│   │   ├── WidgetDefinition.java         # Legacy API contract (request/response body)
│   │   ├── WidgetMeta.java               # Internal record (targetDb, querySql, config)
│   │   └── WidgetResponse.java           # API response (widgetId, uiSchema, data, truncated)
│   └── exception/
│       ├── SqlDryRunException.java       # Carries widgetId + dbError → 422 response
│       ├── WidgetNotFoundException.java
│       └── GlobalExceptionHandler.java   # 422 dry-run · 404 not found · 400 bad request · 500 masked DB error
│
├── src/main/resources/
│   ├── db/meta/schema.sql            # 6 tables: WIDGET_MASTER · WIDGET_PAYLOAD (v2) ·
│   │                                 #           WIDGET_QUERY · WIDGET_CONFIG (v1 legacy) ·
│   │                                 #           WIDGET_AUDIT · AUDIT_LOG
│   ├── db/target/schema.sql          # All 10 business tables (auto-created at startup)
│   └── application.yml               # Ports, dual-datasource URLs (env-var overridable), H2 console
│
└── frontend/src/
    ├── api/widgetApi.js              # Axios client (fetches widget list + data; lowercases keys)
    ├── hooks/
    │   ├── useWidgetList.js          # GET /admin/widgets?activeOnly=true
    │   └── useWidget.js              # GET /widgets/{id}
    ├── pages/
    │   └── DashboardBoard.jsx        # Responsive grid; renders WidgetCard per active widget
    └── components/
        ├── WidgetRenderer.jsx        # SEMANTIC_REGISTRY (visualization:) + LEGACY_REGISTRY (chart_type:)
        │                             # + PRIORITY_PALETTES (critical/high/medium/low)
        ├── EChartsWrapper.jsx        # Shared ECharts instance wrapper
        └── charts/
            ├── BarChart.jsx          # comparison / bar
            ├── PieChart.jsx          # proportion / pie
            ├── LineChart.jsx         # trend / line
            ├── HeatmapChart.jsx      # distribution / heatmap
            ├── RadarChart.jsx        # profile / radar
            ├── GaugeChart.jsx        # utilization / gauge
            ├── ScatterChart.jsx      # correlation / scatter
            ├── TreemapChart.jsx      # hierarchy / treemap
            └── GeoChart.jsx          # geography / map  (needs frontend/public/world.json)
```

---

## Adding a New Chart Type (Frontend)

**1. Create `frontend/src/components/charts/MyChart.jsx`:**

```jsx
import EChartsWrapper from '../EChartsWrapper'

export default function MyChart({ uiSchema, data, colors }) {
  const option = {
    color: colors,  // priority palette forwarded by WidgetRenderer
    // Build ECharts option using uiSchema fields and data rows
  }
  return <EChartsWrapper option={option} />
}
```

**2. Register in `frontend/src/components/WidgetRenderer.jsx`:**

```js
import MyChart from './charts/MyChart'

// Add a semantic key to SEMANTIC_REGISTRY (preferred):
const SEMANTIC_REGISTRY = {
  // ...existing keys...
  momentum: MyChart,   // new semantic key
}

// Or add a legacy key to LEGACY_REGISTRY if backward compatibility is needed:
const LEGACY_REGISTRY = {
  // ...existing keys...
  mychart: MyChart,
}
```

Any widget deployed with `visualization: momentum` (or legacy `chart_type: mychart`) now renders correctly.  
No backend change. No restart.

---

## Registered DataSource Keys

The `targetDb` field in widget definitions must match a key in `DataSourceConfig#dataSourceRegistry`.

| Key | Points to |
|-----|-----------|
| `TARGET_DB` | `jdbc:h2:mem:targetdb` — the business data DB |

To add a new target database (e.g. Oracle), add one entry to `DataSourceConfig#dataSourceRegistry`.  
That is the **only** Java change ever needed to support a new data source.

---

## Notes for Air-Gapped / Production Deployment

- Replace H2 with Oracle or Tibero: update the environment variables listed in [Security Controls](#security-controls), add the JDBC driver JAR to the classpath.
- All meta tables use only `VARCHAR(N)`, `INT`, `BIGINT`, `BOOLEAN`, and `TIMESTAMP` — compatible with any SQL database without type adjustments.
- Named parameters (`:param`) in widget SQL are bound by `NamedParameterJdbcTemplate` — safe from SQL injection on query values.
- Table and column identifiers in `GenericRowDao` are validated with regex **and** confirmed against `INFORMATION_SCHEMA` — safe from SQL injection on identifiers. Adapt the `INFORMATION_SCHEMA` queries to `USER_TABLES` / `USER_TAB_COLUMNS` when targeting Oracle.
- Schema changes to the target DB must go through `db/target/schema.sql` and a controlled deployment — there is no runtime DDL endpoint.
- The SQL dry-run gate at deploy time (`SELECT * FROM ({sql}) WHERE 1=0`) catches malformed or unauthorized SQL before it is stored, providing an additional change-control checkpoint.
