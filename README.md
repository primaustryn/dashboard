# Metadata-Driven Dynamic Dashboard Engine

A **zero-code-change** dashboard platform for air-gapped banking environments.  
All widget behavior — SQL query, chart type, axes, title — lives in a database table (`WIDGET_MASTER`).  
Adding a new widget requires **no Java or React code changes**: only an API call.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser (React + ECharts)                                      │
│  DashboardBoard → fetches widget list → renders each WidgetCard │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTP /api/v1/...
┌────────────────────▼────────────────────────────────────────────┐
│  Spring Boot (port 8081)                                        │
│                                                                 │
│  WidgetEngineController                                         │
│    └─ WidgetEngineService                                       │
│         ├─ reads WIDGET_MASTER from Meta DB (H2: metadb)        │
│         └─ executes query_sql against Target DB (H2: targetdb)  │
│                                                                 │
│  WidgetAdminController  — CRUD for WIDGET_MASTER rows           │
│  GenericDataController  — insert rows into any target table     │
│  TargetSchemaController — execute DDL on target DB              │
└─────────────────────────────────────────────────────────────────┘
```

### Two Databases

| Database | H2 Name | Purpose |
|----------|---------|---------|
| **Meta DB** | `metadb` | Widget definitions (`WIDGET_MASTER`) |
| **Target DB** | `targetdb` | Business data (sales, trades, risk, FX) |

---

## Quick Start

```bash
# 1. Start the backend (Spring Boot on :8081)
./run.sh

# 2. In another terminal — seed tables, data, and register all 6 widgets
./setup.sh

# 3. Start the frontend dev server (React on :5173)
cd frontend && npm install && npm run dev

# Open http://localhost:5173
```

---

## WIDGET_MASTER Schema

Every widget is a single row in this table.

| Column | Type | Description |
|--------|------|-------------|
| `widget_id` | VARCHAR(50) PK | Unique identifier, e.g. `WD_SALES_REGION` |
| `target_db` | VARCHAR(100) | DataSource key registered in `DataSourceConfig` |
| `query_sql` | CLOB | SQL executed against the target DB (named params: `:param`) |
| `dynamic_config` | CLOB | JSON — chart type, axes, title (the `uiSchema`) |
| `is_active` | BOOLEAN | `true` = visible in dashboard; `false` = hidden |

---

## How to Add a New Widget

### Step 1 — Create the table (if needed)

```bash
curl -s -X POST http://localhost:8081/api/v1/target/schema/execute \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "CREATE TABLE IF NOT EXISTS MY_TABLE (
              id        BIGINT AUTO_INCREMENT PRIMARY KEY,
              category  VARCHAR(100) NOT NULL,
              value     DECIMAL(20,2) NOT NULL,
              txn_date  DATE NOT NULL
            )"
  }'
```

### Step 2 — Seed data

Single row:
```bash
curl -s -X POST http://localhost:8081/api/v1/target/MY_TABLE/rows \
  -H "Content-Type: application/json" \
  -d '{"category": "Alpha", "value": 1500000, "txn_date": "2024-01-15"}'
```

Batch (array):
```bash
curl -s -X POST http://localhost:8081/api/v1/target/MY_TABLE/rows/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"category": "Alpha", "value": 1500000, "txn_date": "2024-01-15"},
    {"category": "Beta",  "value": 2300000, "txn_date": "2024-01-15"}
  ]'
```

### Step 3 — Register the widget

```bash
curl -s -X POST http://localhost:8081/api/v1/admin/widgets \
  -H "Content-Type: application/json" \
  -d '{
    "widgetId":    "WD_MY_WIDGET",
    "targetDb":    "targetDataSource",
    "querySql":    "SELECT category, SUM(value) AS total FROM MY_TABLE GROUP BY category",
    "dynamicConfig": "{
      \"chart_type\": \"bar\",
      \"title\":      \"My Widget\",
      \"xAxis\":      { \"field\": \"category\", \"label\": \"Category\" },
      \"yAxis\":      { \"label\": \"Total Value\" },
      \"series\":     [{ \"name\": \"Total\", \"valueField\": \"total\" }]
    }"
  }'
```

The widget appears in the dashboard **immediately** — no restart, no code change.

---

## uiSchema Reference

The `dynamic_config` JSON field drives how the frontend renders each widget.

### Bar Chart

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

The `field` / `valueField` values must match the column aliases in your `query_sql` (case-insensitive — H2 uppercase names are normalized to lowercase automatically).

### Pie Chart

```json
{
  "chart_type": "pie",
  "title": "Sales by Product",
  "nameField":  "product",
  "valueField": "total_amount"
}
```

### Line Chart

```json
{
  "chart_type": "line",
  "title": "Sales Trend",
  "xAxis": { "field": "sale_month", "label": "Month" },
  "yAxis": { "label": "Amount (USD)" },
  "series": [
    { "name": "Monthly Sales", "valueField": "total_amount" }
  ]
}
```

---

## Widget Lifecycle API

### List all widgets

```bash
# All widgets
curl http://localhost:8081/api/v1/admin/widgets

# Active only
curl "http://localhost:8081/api/v1/admin/widgets?activeOnly=true"
```

### Deactivate (hide from dashboard, data preserved)

```bash
curl -X PATCH http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET/deactivate
```

### Activate (re-show)

```bash
curl -X PATCH http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET/activate
```

### Update SQL or config

```bash
curl -X PUT http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET \
  -H "Content-Type: application/json" \
  -d '{
    "widgetId":      "WD_MY_WIDGET",
    "targetDb":      "targetDataSource",
    "querySql":      "SELECT category, SUM(value) AS total FROM MY_TABLE WHERE txn_date >= '\''2024-02-01'\'' GROUP BY category",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"My Widget (Feb+)\",\"xAxis\":{\"field\":\"category\",\"label\":\"Category\"},\"yAxis\":{\"label\":\"Value\"},\"series\":[{\"name\":\"Total\",\"valueField\":\"total\"}]}"
  }'
```

### Delete permanently

```bash
curl -X DELETE http://localhost:8081/api/v1/admin/widgets/WD_MY_WIDGET
```

---

## Adding a New Chart Type (Frontend Engineers)

The only frontend code needed when a new chart type is introduced:

**1. Create the chart component** in `frontend/src/components/charts/MyChart.jsx`:

```jsx
import EChartsWrapper from '../EChartsWrapper'

export default function MyChart({ uiSchema, data }) {
  const option = {
    // build ECharts option from uiSchema + data
  }
  return <EChartsWrapper option={option} />
}
```

**2. Register it** in `frontend/src/components/WidgetRenderer.jsx`:

```js
import MyChart from './charts/MyChart'

const CHART_REGISTRY = {
  bar:    BarChart,
  pie:    PieChart,
  line:   LineChart,
  mychart: MyChart,   // <-- add this line
}
```

**That is the only frontend change.** Any widget using `"chart_type": "mychart"` in its `dynamic_config` will now render correctly.

---

## Data Flow (Request Lifecycle)

```
GET /api/v1/widgets/{widgetId}?param=value
          │
          ▼
  WidgetEngineController
          │
          ▼
  WidgetEngineService
    1. SELECT * FROM WIDGET_MASTER WHERE widget_id = ? AND is_active = TRUE
       → WidgetMeta { targetDb, querySql, dynamicConfig }
    2. Resolve DataSource from registry using targetDb key
    3. Execute querySql against target DataSource (named params substituted)
    4. Return WidgetResponse { widgetId, uiSchema, data[] }
          │
          ▼
  Frontend: WidgetRenderer
    - Reads uiSchema.chart_type → looks up CHART_REGISTRY
    - Passes { uiSchema, data } to the matching chart component
```

---

## Project Structure

```
dashboard/
├── setup.sh                              # Full setup + lifecycle demo script
├── run.sh                                # Start Spring Boot
│
├── src/main/java/com/shb/dashboard/
│   ├── config/
│   │   └── DataSourceConfig.java         # Dual H2 datasource wiring
│   ├── controller/
│   │   ├── WidgetEngineController.java   # GET /api/v1/widgets/{id}
│   │   ├── WidgetAdminController.java    # CRUD /api/v1/admin/widgets
│   │   ├── GenericDataController.java    # /api/v1/target/{table}/rows
│   │   └── TargetSchemaController.java   # POST /api/v1/target/schema/execute
│   ├── service/
│   │   ├── WidgetEngineService.java      # Core orchestrator
│   │   ├── WidgetDefinitionService.java  # Widget lifecycle logic
│   │   └── GenericDataService.java       # Generic row insertion
│   └── dao/
│       ├── DynamicWidgetDao.java         # Executes query_sql on target DB
│       ├── WidgetDefinitionDao.java      # WIDGET_MASTER CRUD
│       └── GenericRowDao.java            # Dynamic INSERT builder
│
├── src/main/resources/
│   ├── db/meta/schema.sql                # WIDGET_MASTER DDL
│   └── application.yml                   # Datasource + port config
│
└── frontend/src/
    ├── pages/DashboardBoard.jsx          # Fetches widget list, renders cards
    ├── components/
    │   ├── WidgetRenderer.jsx            # CHART_REGISTRY dispatch
    │   └── charts/
    │       ├── BarChart.jsx
    │       ├── PieChart.jsx
    │       └── LineChart.jsx
    ├── hooks/
    │   ├── useWidgetList.js              # GET /admin/widgets?activeOnly=true
    │   └── useWidget.js                  # GET /widgets/{id}
    └── api/widgetApi.js                  # Axios client + lowercase normalization
```

---

## Registered DataSource Keys

The `targetDb` field in WIDGET_MASTER must match a key in `DataSourceConfig#dataSourceRegistry`.

| Key | Points to |
|-----|-----------|
| `targetDataSource` | `jdbc:h2:mem:targetdb` (the business data DB) |

To add a new target database (e.g. Oracle production), add an entry to `DataSourceConfig#dataSourceRegistry` — this is the **only** backend code change ever needed.

---

## Notes for Air-Gapped / Production Deployment

- Replace H2 in-memory DBs with Oracle or Tibero: update `application.yml` datasource URLs and add the JDBC driver JAR to the classpath.
- `WIDGET_MASTER` DDL uses standard SQL (`VARCHAR`, `CLOB`, `BOOLEAN`) — compatible with Oracle (`CLOB`, `NUMBER(1,0)`) with minor type adjustments.
- The `GenericRowDao` identifier validator (`[A-Za-z][A-Za-z0-9_]{0,127}`) prevents SQL injection on table and column names passed via API.
- All `query_sql` values use named parameters (`:param`) bound by `NamedParameterJdbcTemplate` — safe from injection in query values.
