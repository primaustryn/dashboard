# Metadata-Driven Dynamic Dashboard Engine

A **zero-code-change** dashboard platform for air-gapped banking environments.  
Widget SQL, chart type, axes, and title all live in the database.  
**Adding a new widget = API call only. No Java, no React rebuild.**

---

## Table of Contents

1. [Architecture](#architecture)
2. [Quick Start](#quick-start)
3. [Database Schema](#database-schema)
4. [Registered Widgets](#registered-widgets)
5. [How to Add a Widget](#how-to-add-a-widget)
6. [How to Update a Widget](#how-to-update-a-widget)
7. [How to Delete a Widget](#how-to-delete-a-widget)
8. [Activate / Deactivate](#activate--deactivate)
9. [uiSchema Reference — All 9 Chart Types](#uischema-reference--all-9-chart-types)
10. [Admin API Reference](#admin-api-reference)
11. [Data Ingestion API](#data-ingestion-api)
12. [Data Flow](#data-flow)
13. [Security Controls](#security-controls)
14. [Project Structure](#project-structure)
15. [Adding a New Chart Type (Frontend)](#adding-a-new-chart-type-frontend)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser  (React + ECharts, Vite dev server on :5173)           │
│  DashboardBoard → polls active widget list → renders WidgetCards│
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTP  /api/v1/...
┌────────────────────▼────────────────────────────────────────────┐
│  Spring Boot  (port 8081)                                       │
│                                                                 │
│  SecurityHeaderFilter    — security response headers            │
│  AuditInterceptor        — HTTP request audit log (AUDIT_LOG)   │
│                                                                 │
│  WidgetEngineController                                         │
│    └─ WidgetEngineService                                       │
│         ├─ reads widget metadata from Meta DB (H2: metadb)      │
│         └─ executes querySql against Target DB (H2: targetdb)   │
│              └─ DynamicWidgetDao (30 s timeout · 10 000-row cap)│
│                                                                 │
│  WidgetAdminController  — CRUD for widget definitions           │
│    └─ WidgetDefinitionService → WidgetAuditDao (WIDGET_AUDIT)   │
│  GenericDataController  — insert rows into any target table     │
└─────────────────────────────────────────────────────────────────┘
         │                          │
┌────────▼──────────┐    ┌──────────▼──────────┐
│  Meta DB (metadb) │    │ Target DB (targetdb) │
│  WIDGET_MASTER    │    │ SALES_SUMMARY        │
│  WIDGET_QUERY     │    │ TRADE_SUMMARY        │
│  WIDGET_CONFIG    │    │ RISK_SUMMARY         │
│  WIDGET_AUDIT     │    │ FX_RATE              │
│  AUDIT_LOG        │    │ RISK_MATRIX  …etc.   │
└───────────────────┘    └─────────────────────┘
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

The meta DB uses five **VARCHAR-only** tables (no CLOB/TEXT).  
This design is compatible with Oracle, Tibero, and any database that restricts column types.

```
WIDGET_MASTER (one row per widget)
┌─────────────┬──────────────┬───────────────────────────────────────┐
│ widget_id   │ VARCHAR(50)  │ PK — e.g. "WD_SALES_REGION"          │
│ target_db   │ VARCHAR(100) │ DataSource registry key — "TARGET_DB" │
│ is_active   │ BOOLEAN      │ true = shown on dashboard             │
└─────────────┴──────────────┴───────────────────────────────────────┘

WIDGET_QUERY (query SQL split into ≤4 KB chunks, FK → WIDGET_MASTER)
┌─────────────┬──────────────┬───────────────────────────────────────┐
│ widget_id   │ VARCHAR(50)  │ FK → WIDGET_MASTER                    │
│ chunk_order │ INT          │ 0-based order for reassembly           │
│ chunk_text  │ VARCHAR(4000)│ SQL fragment                          │
└─────────────┴──────────────┴───────────────────────────────────────┘

WIDGET_CONFIG (EAV — each top-level JSON key in uiSchema as one row)
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

## Registered Widgets

After running both setup scripts, 12 widgets are active:

| Widget ID | Chart Type | Source Table | Description |
|-----------|------------|--------------|-------------|
| `WD_SALES_REGION` | bar | SALES_SUMMARY | Total sales amount by region |
| `WD_SALES_PRODUCT` | pie | SALES_SUMMARY | Revenue share by product |
| `WD_SALES_TREND` | line | SALES_SUMMARY | Monthly revenue trend |
| `WD_TRADE_DESK` | pie | TRADE_SUMMARY | Notional by trading desk |
| `WD_RISK_VAR` | bar | RISK_SUMMARY | Value at Risk by portfolio |
| `WD_FX_TREND` | line | FX_RATE | USD/KRW exchange rate trend |
| `WD_RISK_HEATMAP` | heatmap | RISK_MATRIX | Desk × risk type intensity matrix |
| `WD_PORTFOLIO_RADAR` | radar | PORTFOLIO_SCORES | 5-axis risk profile per portfolio |
| `WD_VAR_GAUGE` | gauge | VAR_UTILIZATION | VaR limit utilization % |
| `WD_ASSET_SCATTER` | scatter | ASSET_PERFORMANCE | Risk vs return bubble chart |
| `WD_AUM_TREEMAP` | treemap | PORTFOLIO_AUM | AUM by region / asset class |
| `WD_GLOBAL_MAP` | map | GLOBAL_EXPOSURE | Global credit exposure choropleth |

---

## How to Add a Widget

Tables are pre-created at startup. Adding a widget only requires seeding data and registering the widget.

### Step 1 — Seed data

Single row:
```bash
curl -s -X POST http://localhost:8081/api/v1/target/MY_TABLE/rows \
  -H "Content-Type: application/json" \
  -d '{"category": "Alpha", "value": 1500000, "txn_date": "2024-01-15"}'
```

Batch:
```bash
curl -s -X POST http://localhost:8081/api/v1/target/MY_TABLE/rows/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"category": "Alpha", "value": 1500000, "txn_date": "2024-01-15"},
    {"category": "Beta",  "value": 2300000, "txn_date": "2024-01-15"},
    {"category": "Gamma", "value": 1100000, "txn_date": "2024-01-15"}
  ]'
```

> Table and column names are validated against `[A-Za-z][A-Za-z0-9_]{0,127}` **and** verified to exist in `INFORMATION_SCHEMA` before any INSERT is issued.

### Step 2 — Register the widget

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

The widget appears on the dashboard **immediately** — no server restart, no code change.  
A `CREATE` row is written to `WIDGET_AUDIT` automatically.

> **Field name casing**: H2 returns column names in UPPERCASE. The frontend normalizes them to lowercase automatically, so `querySql` aliases should use lowercase (`total`, not `TOTAL`).

---

## How to Update a Widget

`PUT` replaces `querySql`, `dynamicConfig`, and `targetDb` atomically.

```bash
curl -s -X PUT http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET \
  -H "Content-Type: application/json" \
  -d '{
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT category, SUM(value) AS total FROM MY_TABLE WHERE txn_date >= '\''2024-02-01'\'' GROUP BY category",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"My Widget (Feb+)\",\"xAxis\":{\"field\":\"category\",\"label\":\"Category\"},\"yAxis\":{\"label\":\"Value\"},\"series\":[{\"name\":\"Total\",\"valueField\":\"total\"}]}"
  }'
```

The updated widget renders on next browser refresh. An `UPDATE` row is written to `WIDGET_AUDIT`.

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

The `dynamicConfig` JSON (stored in `WIDGET_CONFIG`) drives frontend rendering.  
Field names in `xAxis.field` / `valueField` / `nameField` etc. must match the lowercase column aliases in `querySql`.

### bar

```json
{
  "chart_type": "bar",
  "title": "Sales by Region",
  "xAxis": { "field": "region", "label": "Region" },
  "yAxis": { "label": "Amount (USD)" },
  "series": [
    { "name": "Total Sales", "valueField": "total_amount" }
  ]
}
```

*Example query*: `SELECT region, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY region`

---

### pie

```json
{
  "chart_type": "pie",
  "title": "Revenue by Product",
  "nameField":  "product",
  "valueField": "total_amount"
}
```

*Example query*: `SELECT product, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY product`

---

### line

```json
{
  "chart_type": "line",
  "title": "Monthly Revenue Trend",
  "xAxis": { "field": "sale_month", "label": "Month" },
  "yAxis": { "label": "Amount (USD)" },
  "series": [
    { "name": "Monthly Sales", "valueField": "total_amount" }
  ]
}
```

*Example query*: `SELECT FORMATDATETIME(sale_date,'yyyy-MM') AS sale_month, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY sale_month ORDER BY sale_month`

---

### heatmap

```json
{
  "chart_type": "heatmap",
  "title":      "Risk Score Matrix by Desk",
  "xField":     "desk",
  "yField":     "risk_type",
  "valueField": "score",
  "xLabel":     "Trading Desk",
  "yLabel":     "Risk Category"
}
```

*Example query*: `SELECT desk, risk_type, score FROM RISK_MATRIX ORDER BY desk, risk_type`

---

### radar

```json
{
  "chart_type":  "radar",
  "title":       "Portfolio Risk Profile",
  "nameField":   "portfolio",
  "indicators": [
    { "name": "Market",      "max": 100 },
    { "name": "Credit",      "max": 100 },
    { "name": "Liquidity",   "max": 100 },
    { "name": "Operational", "max": 100 },
    { "name": "Compliance",  "max": 100 }
  ],
  "valueFields": ["market_score", "credit_score", "liquidity_score", "op_score", "compliance_score"]
}
```

`indicators[i].name` labels each axis. `valueFields[i]` is the corresponding column alias from `querySql` (order must match).

*Example query*: `SELECT portfolio, market_score, credit_score, liquidity_score, op_score, compliance_score FROM PORTFOLIO_SCORES ORDER BY portfolio`

---

### gauge

```json
{
  "chart_type":  "gauge",
  "title":       "VaR Limit Utilization",
  "nameField":   "portfolio",
  "valueField":  "utilization",
  "max":         100,
  "unit":        "%"
}
```

Multiple rows → multiple gauges rendered side-by-side in a grid.

*Example query*: `SELECT portfolio, utilization FROM VAR_UTILIZATION ORDER BY portfolio`

---

### scatter

```json
{
  "chart_type":  "scatter",
  "title":       "Risk vs Return (bubble = AUM)",
  "xField":      "risk_pct",
  "yField":      "return_pct",
  "sizeField":   "volume",
  "nameField":   "asset",
  "xLabel":      "Risk (%)",
  "yLabel":      "Return (%)"
}
```

`sizeField` drives the bubble radius. `nameField` appears in the tooltip.

*Example query*: `SELECT asset, risk_pct, return_pct, volume FROM ASSET_PERFORMANCE ORDER BY volume DESC`

---

### treemap

```json
{
  "chart_type":  "treemap",
  "title":       "AUM by Region & Asset Class",
  "nameField":   "asset_class",
  "valueField":  "aum",
  "groupField":  "region"
}
```

`groupField` creates the parent nodes. `nameField` is the leaf label.

*Example query*: `SELECT region, asset_class, aum FROM PORTFOLIO_AUM ORDER BY region, aum DESC`

---

### map

```json
{
  "chart_type":  "map",
  "title":       "Global Credit Exposure",
  "nameField":   "country",
  "valueField":  "exposure"
}
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

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/admin/widgets` | Register a new widget |
| `GET` | `/api/v1/admin/widgets` | List all widgets (`?activeOnly=true` to filter) |
| `PUT` | `/api/v1/admin/widgets/{widgetId}` | Replace querySql / dynamicConfig / targetDb |
| `PATCH` | `/api/v1/admin/widgets/{widgetId}/activate` | Mark widget as active |
| `PATCH` | `/api/v1/admin/widgets/{widgetId}/deactivate` | Mark widget as inactive (hidden) |
| `DELETE` | `/api/v1/admin/widgets/{widgetId}` | Permanently remove widget definition |
| `GET` | `/api/v1/widgets/{widgetId}` | Fetch widget data + uiSchema (used by frontend) |

### Request body for POST and PUT

```json
{
  "widgetId":      "WD_EXAMPLE",
  "targetDb":      "TARGET_DB",
  "querySql":      "SELECT col_a, col_b FROM MY_TABLE",
  "dynamicConfig": "{\"chart_type\":\"bar\", ...}"
}
```

> `widgetId` is required in POST. In PUT, only `targetDb` / `querySql` / `dynamicConfig` are updated.

### Response from GET `/api/v1/widgets/{widgetId}`

```json
{
  "widgetId": "WD_SALES_REGION",
  "uiSchema": {
    "chart_type": "bar",
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
    1. WIDGET_MASTER  → verify widget exists + is_active, get targetDb
    2. WIDGET_QUERY   → reassemble querySql from chunks (ORDER BY chunk_order)
    3. WIDGET_CONFIG  → rebuild dynamicConfig JSON from EAV rows
    4. Resolve DataSource from registry using targetDb key
    5. Execute querySql against target DataSource (named params :param substituted)
       └─ DynamicWidgetDao enforces 30 s query timeout + 10 000-row cap
    6. Return WidgetResponse { widgetId, uiSchema, data[], truncated }
          │
          ▼
  Frontend: WidgetRenderer
    - Reads uiSchema.chart_type → looks up CHART_REGISTRY
    - Passes { uiSchema, data } to matching chart component
    - Column names normalized to lowercase before chart rendering
```

---

## Security Controls

| Control | Implementation | Purpose |
|---------|---------------|---------|
| No DDL API | `TargetSchemaController` removed; schema managed via `schema.sql` | Eliminates runtime schema tampering |
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
│   │   └── AuditInterceptor.java     # Writes every /api/** request to AUDIT_LOG
│   ├── config/
│   │   ├── DataSourceConfig.java     # Dual H2 datasource + dataSourceRegistry map
│   │   ├── SecurityHeaderFilter.java # OncePerRequestFilter — 5 security headers
│   │   └── WebConfig.java            # CORS + AuditInterceptor registration
│   ├── controller/
│   │   ├── WidgetEngineController.java   # GET /api/v1/widgets/{id}
│   │   ├── WidgetAdminController.java    # CRUD /api/v1/admin/widgets
│   │   └── GenericDataController.java    # /api/v1/target/{table}/rows
│   ├── service/
│   │   ├── WidgetEngineService.java      # Core orchestrator (reads 3 meta tables)
│   │   ├── WidgetDefinitionService.java  # Widget CRUD + WidgetAuditDao calls
│   │   └── GenericDataService.java       # Dynamic row insertion
│   ├── dao/
│   │   ├── WidgetDefinitionDao.java      # 3-table EAV read/write (MASTER+QUERY+CONFIG)
│   │   ├── DynamicWidgetDao.java         # Executes querySql (timeout + row cap)
│   │   ├── GenericRowDao.java            # Dynamic INSERT (regex + INFORMATION_SCHEMA guard)
│   │   └── WidgetAuditDao.java           # Writes widget lifecycle events to WIDGET_AUDIT
│   ├── model/
│   │   ├── WidgetDefinition.java         # API contract (request/response body)
│   │   ├── WidgetMeta.java               # Internal record (targetDb, querySql, config)
│   │   └── WidgetResponse.java           # API response (widgetId, uiSchema, data, truncated)
│   └── exception/
│       ├── WidgetNotFoundException.java
│       └── GlobalExceptionHandler.java   # Handles DataAccessException (masks DB details)
│
├── src/main/resources/
│   ├── db/meta/schema.sql            # 5 tables: WIDGET_MASTER/QUERY/CONFIG + WIDGET_AUDIT + AUDIT_LOG
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
        ├── WidgetRenderer.jsx        # CHART_REGISTRY dispatch on uiSchema.chart_type
        ├── EChartsWrapper.jsx        # Shared ECharts instance wrapper
        └── charts/
            ├── BarChart.jsx          # bar
            ├── PieChart.jsx          # pie
            ├── LineChart.jsx         # line
            ├── HeatmapChart.jsx      # heatmap
            ├── RadarChart.jsx        # radar
            ├── GaugeChart.jsx        # gauge
            ├── ScatterChart.jsx      # scatter
            ├── TreemapChart.jsx      # treemap
            └── GeoChart.jsx          # map  (needs frontend/public/world.json)
```

---

## Adding a New Chart Type (Frontend)

The only frontend change required when introducing a new chart type:

**1. Create `frontend/src/components/charts/MyChart.jsx`:**

```jsx
import EChartsWrapper from '../EChartsWrapper'

export default function MyChart({ uiSchema, data }) {
  const option = {
    // Build ECharts option using uiSchema fields and data rows
  }
  return <EChartsWrapper option={option} />
}
```

**2. Register in `frontend/src/components/WidgetRenderer.jsx`:**

```js
import MyChart from './charts/MyChart'

const CHART_REGISTRY = {
  bar:     BarChart,
  pie:     PieChart,
  line:    LineChart,
  // ...
  mychart: MyChart,   // add this line
}
```

Any widget with `"chart_type": "mychart"` in its `dynamicConfig` now renders correctly.  
No backend change. No restart.

---

## Registered DataSource Keys

The `targetDb` field in the widget registration body must match a key in `DataSourceConfig#dataSourceRegistry`.

| Key | Points to |
|-----|-----------|
| `TARGET_DB` | `jdbc:h2:mem:targetdb` — the business data DB |

To add a new target database (e.g. Oracle), add one entry to `DataSourceConfig#dataSourceRegistry`.  
That is the **only** Java change ever needed to support a new data source.

---

## Notes for Air-Gapped / Production Deployment

- Replace H2 with Oracle or Tibero: update the environment variables listed in [Security Controls](#security-controls), add the JDBC driver JAR to the classpath.
- All five meta tables use only `VARCHAR(N)`, `INT`, `BIGINT`, `BOOLEAN`, and `TIMESTAMP` — compatible with any SQL database without type adjustments.
- `query_sql` named parameters (`:param`) are bound by `NamedParameterJdbcTemplate` — safe from SQL injection on query values.
- Table and column identifiers in `GenericRowDao` are validated with regex **and** confirmed against `INFORMATION_SCHEMA` — safe from SQL injection on identifiers. Adapt the `INFORMATION_SCHEMA` queries to `USER_TABLES` / `USER_TAB_COLUMNS` when targeting Oracle.
- Schema changes to the target DB must go through `db/target/schema.sql` and a controlled deployment — there is no runtime DDL endpoint.
