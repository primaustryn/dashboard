#!/usr/bin/env bash
# =============================================================================
# Dashboard Engine — GitOps Widget Setup
#
# Writes YAML widget definitions to widgets/ and deploys them via the GitOps
# endpoint (POST /api/v1/admin/widgets/deploy).
#
# The YAML files in widgets/ are version-controlled artifacts — committing them
# to your widget registry repo is the intended GitOps flow.  Re-running this
# script is fully idempotent; existing widgets are replaced atomically.
#
# Usage:
#   ./setup-gitops.sh                    seed data + write YAMLs + deploy all 12
#   ./setup-gitops.sh deploy             deploy from existing widgets/ files
#   ./setup-gitops.sh deploy-one <id>    redeploy a single widget by ID
#   ./setup-gitops.sh teardown           delete all 12 managed widgets
#   ./setup-gitops.sh reset              teardown + full re-setup
# =============================================================================
set -euo pipefail

BASE="http://localhost:8081"
H_JSON="Content-Type: application/json"
WIDGETS_DIR="widgets"

ALL_WIDGETS=(
  WD_SALES_REGION
  WD_SALES_PRODUCT
  WD_SALES_TREND
  WD_TRADE_DESK
  WD_RISK_VAR
  WD_FX_TREND
  WD_RISK_HEATMAP
  WD_PORTFOLIO_RADAR
  WD_VAR_GAUGE
  WD_ASSET_SCATTER
  WD_AUM_TREEMAP
  WD_GLOBAL_MAP
  WD_FX_CANDLE
)

# ─── Output helpers ───────────────────────────────────────────────────────────
ok()   { echo "  [OK]  $*"; }
fail() { echo "  [!!]  $*" >&2; }
info() { echo "  [--]  $*"; }
step() { echo; echo "==> $*"; }

# ─── Backend health check ─────────────────────────────────────────────────────
check_backend() {
  if ! curl -sf "$BASE/api/v1/admin/widgets" > /dev/null 2>&1; then
    echo
    echo "  ERROR: Backend not reachable at $BASE"
    echo "         Start it first:  ./run.sh"
    echo
    exit 1
  fi
}

# ─── Deploy a single YAML file ───────────────────────────────────────────────
# Parses the HTTP status code and prints a human-readable result.
# 201 = deployed, 400 = validation failure, 422 = SQL dry-run failure.
deploy_widget() {
  local file="$1"
  local widget_id
  widget_id=$(basename "$file" .yml)

  local http_code
  http_code=$(curl -s \
    -o /tmp/_deploy_body.json \
    -w "%{http_code}" \
    -X POST "$BASE/api/v1/admin/widgets/deploy" \
    -H "Content-Type: text/plain" \
    --data-binary "@$file")

  local detail
  detail=$(python3 -c \
    "import json; d=json.load(open('/tmp/_deploy_body.json')); print(d.get('detail','?'))" \
    2>/dev/null \
    || grep -o '"detail":"[^"]*"' /tmp/_deploy_body.json 2>/dev/null | cut -d'"' -f4 \
    || cat /tmp/_deploy_body.json 2>/dev/null \
    || echo "(no body)")

  case "$http_code" in
    201)
      local chunks
      chunks=$(grep -o '"sqlChunks":[0-9]*' /tmp/_deploy_body.json 2>/dev/null | cut -d: -f2 || echo "?")
      ok "$widget_id  →  deployed (${chunks} SQL chunk(s))"
      ;;
    400) fail "$widget_id  →  400 Bad Request — $detail" ;;
    422) fail "$widget_id  →  422 SQL dry-run failed — $detail" ;;
    *)   fail "$widget_id  →  HTTP $http_code — $detail" ;;
  esac
}

# ─── Deploy all YAML files in widgets/ ───────────────────────────────────────
deploy_all() {
  step "Deploying YAML definitions from $WIDGETS_DIR/"
  local deployed=0 failed=0
  for yml in "$WIDGETS_DIR"/WD_*.yml; do
    if [[ ! -f "$yml" ]]; then
      fail "No YAML files found in $WIDGETS_DIR/. Run without arguments first."
      exit 1
    fi
    if deploy_widget "$yml"; then
      (( deployed++ )) || true
    else
      (( failed++ )) || true
    fi
  done
  echo
  echo "  Deployed: $deployed   Failed: $failed"
}

# ─── Teardown: delete all managed widgets ────────────────────────────────────
teardown() {
  step "Deleting all managed widgets"
  for id in "${ALL_WIDGETS[@]}"; do
    if curl -sf -X DELETE "$BASE/api/v1/admin/widgets/$id" > /dev/null 2>&1; then
      ok "Deleted $id"
    else
      info "$id not found (skipped)"
    fi
  done
}

# =============================================================================
# YAML DEFINITIONS
# Each file is written to widgets/ and can be committed to version control.
# =============================================================================
write_yamls() {
  mkdir -p "$WIDGETS_DIR"
  step "Writing YAML definitions → $WIDGETS_DIR/"

  # ── WD_SALES_REGION — comparison (bar) ──────────────────────────────────
  cat > "$WIDGETS_DIR/WD_SALES_REGION.yml" << 'YAML'
widgetId: WD_SALES_REGION
targetDb: TARGET_DB
sql: |
  SELECT region,
         SUM(amount) AS total_amount
  FROM   SALES_SUMMARY
  GROUP  BY region
  ORDER  BY total_amount DESC
uiSchema:
  visualization: comparison
  title:    Sales Revenue by Region
  priority: medium
  xAxis:
    field: region
    label: Region
  yAxis:
    label: Revenue (USD)
  series:
    - name:       Revenue
      valueField: total_amount
YAML

  # ── WD_SALES_PRODUCT — proportion (pie) ─────────────────────────────────
  cat > "$WIDGETS_DIR/WD_SALES_PRODUCT.yml" << 'YAML'
widgetId: WD_SALES_PRODUCT
targetDb: TARGET_DB
sql: |
  SELECT product AS category,
         SUM(amount) AS revenue
  FROM   SALES_SUMMARY
  GROUP  BY product
  ORDER  BY revenue DESC
uiSchema:
  visualization: proportion
  title:      Revenue Share by Product
  priority:   medium
  nameField:  category
  valueField: revenue
YAML

  # ── WD_SALES_TREND — trend (line) ───────────────────────────────────────
  cat > "$WIDGETS_DIR/WD_SALES_TREND.yml" << 'YAML'
widgetId: WD_SALES_TREND
targetDb: TARGET_DB
sql: |
  SELECT sale_date    AS trade_date,
         SUM(amount) AS daily_total
  FROM   SALES_SUMMARY
  GROUP  BY sale_date
  ORDER  BY sale_date ASC
uiSchema:
  visualization: trend
  title:    Monthly Revenue Trend
  priority: medium
  xAxis:
    field: trade_date
    label: Date
  yAxis:
    label: Revenue (USD)
  series:
    - name:       Revenue
      valueField: daily_total
YAML

  # ── WD_TRADE_DESK — proportion (pie) ────────────────────────────────────
  cat > "$WIDGETS_DIR/WD_TRADE_DESK.yml" << 'YAML'
widgetId: WD_TRADE_DESK
targetDb: TARGET_DB
sql: |
  SELECT desk,
         SUM(notional) AS total_notional
  FROM   TRADE_SUMMARY
  GROUP  BY desk
  ORDER  BY total_notional DESC
uiSchema:
  visualization: proportion
  title:      Notional by Trading Desk
  priority:   medium
  nameField:  desk
  valueField: total_notional
YAML

  # ── WD_RISK_VAR — comparison (bar) — high priority ──────────────────────
  cat > "$WIDGETS_DIR/WD_RISK_VAR.yml" << 'YAML'
widgetId: WD_RISK_VAR
targetDb: TARGET_DB
sql: |
  SELECT portfolio,
         SUM(var_amount) AS total_var
  FROM   RISK_SUMMARY
  GROUP  BY portfolio
  ORDER  BY total_var DESC
uiSchema:
  visualization: comparison
  title:    Value at Risk by Portfolio
  priority: high
  xAxis:
    field: portfolio
    label: Portfolio
  yAxis:
    label: VaR (USD)
  series:
    - name:       VaR
      valueField: total_var
YAML

  # ── WD_FX_TREND — trend (line) ──────────────────────────────────────────
  cat > "$WIDGETS_DIR/WD_FX_TREND.yml" << 'YAML'
widgetId: WD_FX_TREND
targetDb: TARGET_DB
sql: |
  SELECT rate_date,
         rate
  FROM   FX_RATE
  WHERE  pair = 'USD/KRW'
  ORDER  BY rate_date ASC
uiSchema:
  visualization: trend
  title:    "USD/KRW Exchange Rate Trend"
  priority: medium
  xAxis:
    field: rate_date
    label: Date
  yAxis:
    label: KRW per USD
  series:
    - name:       "USD/KRW"
      valueField: rate
YAML

  # ── WD_RISK_HEATMAP — distribution (heatmap) — high priority ────────────
  cat > "$WIDGETS_DIR/WD_RISK_HEATMAP.yml" << 'YAML'
widgetId: WD_RISK_HEATMAP
targetDb: TARGET_DB
sql: |
  SELECT desk,
         risk_type,
         score
  FROM   RISK_MATRIX
  ORDER  BY desk, risk_type
uiSchema:
  visualization: distribution
  title:      Risk Score Matrix by Desk
  priority:   high
  xField:     desk
  yField:     risk_type
  valueField: score
  xLabel:     Trading Desk
  yLabel:     Risk Category
YAML

  # ── WD_PORTFOLIO_RADAR — profile (radar) ────────────────────────────────
  cat > "$WIDGETS_DIR/WD_PORTFOLIO_RADAR.yml" << 'YAML'
widgetId: WD_PORTFOLIO_RADAR
targetDb: TARGET_DB
sql: |
  SELECT portfolio,
         market_score,
         credit_score,
         liquidity_score,
         op_score,
         compliance_score
  FROM   PORTFOLIO_SCORES
  ORDER  BY portfolio
uiSchema:
  visualization: profile
  title:     Portfolio Risk Profile
  priority:  medium
  nameField: portfolio
  indicators:
    - name: Market
      max:  100
    - name: Credit
      max:  100
    - name: Liquidity
      max:  100
    - name: Operational
      max:  100
    - name: Compliance
      max:  100
  valueFields:
    - market_score
    - credit_score
    - liquidity_score
    - op_score
    - compliance_score
YAML

  # ── WD_VAR_GAUGE — utilization (gauge) — critical priority ──────────────
  cat > "$WIDGETS_DIR/WD_VAR_GAUGE.yml" << 'YAML'
widgetId: WD_VAR_GAUGE
targetDb: TARGET_DB
sql: |
  SELECT portfolio,
         utilization
  FROM   VAR_UTILIZATION
  ORDER  BY portfolio
uiSchema:
  visualization: utilization
  title:      VaR Limit Utilization
  priority:   critical
  nameField:  portfolio
  valueField: utilization
  max:        100
  unit:       "%"
YAML

  # ── WD_ASSET_SCATTER — correlation (scatter) ────────────────────────────
  cat > "$WIDGETS_DIR/WD_ASSET_SCATTER.yml" << 'YAML'
widgetId: WD_ASSET_SCATTER
targetDb: TARGET_DB
sql: |
  SELECT asset,
         risk_pct,
         return_pct,
         volume
  FROM   ASSET_PERFORMANCE
  ORDER  BY volume DESC
uiSchema:
  visualization: correlation
  title:     "Risk vs Return  (bubble = AUM)"
  priority:  medium
  xField:    risk_pct
  yField:    return_pct
  sizeField: volume
  nameField: asset
  xLabel:    "Risk (%)"
  yLabel:    "Return (%)"
YAML

  # ── WD_AUM_TREEMAP — hierarchy (treemap) ────────────────────────────────
  cat > "$WIDGETS_DIR/WD_AUM_TREEMAP.yml" << 'YAML'
widgetId: WD_AUM_TREEMAP
targetDb: TARGET_DB
sql: |
  SELECT region,
         asset_class,
         aum
  FROM   PORTFOLIO_AUM
  ORDER  BY region, aum DESC
uiSchema:
  visualization: hierarchy
  title:      AUM by Region & Asset Class
  priority:   medium
  nameField:  asset_class
  valueField: aum
  groupField: region
YAML

  # ── WD_GLOBAL_MAP — geography (map) ─────────────────────────────────────
  cat > "$WIDGETS_DIR/WD_GLOBAL_MAP.yml" << 'YAML'
widgetId: WD_GLOBAL_MAP
targetDb: TARGET_DB
sql: |
  SELECT country,
         exposure
  FROM   GLOBAL_EXPOSURE
  ORDER  BY exposure DESC
uiSchema:
  visualization: geography
  title:      Global Credit Exposure
  priority:   medium
  nameField:  country
  valueField: exposure
YAML

  ok "12 files written"
}

# =============================================================================
# DATA SEEDING  (same data as setup.sh + setup-new-charts.sh combined)
# =============================================================================
seed_data() {

  step "Seeding SALES_SUMMARY (40 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/SALES_SUMMARY/rows/batch" -d '[
    {"region":"North",   "product":"Bonds",       "amount":12500000, "sale_date":"2024-01-15"},
    {"region":"North",   "product":"Equities",    "amount": 8750000, "sale_date":"2024-01-15"},
    {"region":"North",   "product":"FX",          "amount": 5200000, "sale_date":"2024-01-15"},
    {"region":"South",   "product":"Bonds",       "amount": 9800000, "sale_date":"2024-01-15"},
    {"region":"South",   "product":"Equities",    "amount":11200000, "sale_date":"2024-01-15"},
    {"region":"South",   "product":"Derivatives", "amount": 4100000, "sale_date":"2024-01-15"},
    {"region":"East",    "product":"Bonds",       "amount":15200000, "sale_date":"2024-01-15"},
    {"region":"East",    "product":"Equities",    "amount": 7300000, "sale_date":"2024-01-15"},
    {"region":"East",    "product":"Derivatives", "amount": 9600000, "sale_date":"2024-01-15"},
    {"region":"West",    "product":"Bonds",       "amount":11300000, "sale_date":"2024-01-15"},
    {"region":"West",    "product":"Commodities", "amount": 6800000, "sale_date":"2024-01-15"},
    {"region":"West",    "product":"FX",          "amount": 8400000, "sale_date":"2024-01-15"},
    {"region":"Central", "product":"Bonds",       "amount":18900000, "sale_date":"2024-01-15"},
    {"region":"Central", "product":"Equities",    "amount":14200000, "sale_date":"2024-01-15"},
    {"region":"Central", "product":"Derivatives", "amount": 7500000, "sale_date":"2024-01-15"},
    {"region":"North",   "product":"Bonds",       "amount":14200000, "sale_date":"2024-02-15"},
    {"region":"South",   "product":"Equities",    "amount":12400000, "sale_date":"2024-02-15"},
    {"region":"East",    "product":"Bonds",       "amount":16100000, "sale_date":"2024-02-15"},
    {"region":"West",    "product":"Commodities", "amount": 7200000, "sale_date":"2024-02-15"},
    {"region":"Central", "product":"Bonds",       "amount":21000000, "sale_date":"2024-02-15"},
    {"region":"North",   "product":"Equities",    "amount": 9800000, "sale_date":"2024-03-15"},
    {"region":"South",   "product":"Bonds",       "amount":10900000, "sale_date":"2024-03-15"},
    {"region":"East",    "product":"Derivatives", "amount":10500000, "sale_date":"2024-03-15"},
    {"region":"West",    "product":"FX",          "amount":10200000, "sale_date":"2024-03-15"},
    {"region":"Central", "product":"Equities",    "amount":16500000, "sale_date":"2024-03-15"},
    {"region":"North",   "product":"Bonds",       "amount":15000000, "sale_date":"2024-04-15"},
    {"region":"South",   "product":"Derivatives", "amount": 5400000, "sale_date":"2024-04-15"},
    {"region":"East",    "product":"Equities",    "amount": 9400000, "sale_date":"2024-04-15"},
    {"region":"West",    "product":"Bonds",       "amount":14500000, "sale_date":"2024-04-15"},
    {"region":"Central", "product":"Bonds",       "amount":22500000, "sale_date":"2024-04-15"},
    {"region":"North",   "product":"FX",          "amount": 5900000, "sale_date":"2024-05-15"},
    {"region":"South",   "product":"Equities",    "amount":11100000, "sale_date":"2024-05-15"},
    {"region":"East",    "product":"Bonds",       "amount":16800000, "sale_date":"2024-05-15"},
    {"region":"West",    "product":"Commodities", "amount": 7600000, "sale_date":"2024-05-15"},
    {"region":"Central", "product":"Derivatives", "amount": 8700000, "sale_date":"2024-05-15"},
    {"region":"North",   "product":"Bonds",       "amount":16200000, "sale_date":"2024-06-15"},
    {"region":"South",   "product":"Equities",    "amount":13600000, "sale_date":"2024-06-15"},
    {"region":"East",    "product":"Derivatives", "amount":12300000, "sale_date":"2024-06-15"},
    {"region":"West",    "product":"FX",          "amount":12100000, "sale_date":"2024-06-15"},
    {"region":"Central", "product":"Equities",    "amount":19200000, "sale_date":"2024-06-15"}
  ]'
  ok "40 rows"

  step "Seeding TRADE_SUMMARY (19 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/TRADE_SUMMARY/rows/batch" -d '[
    {"symbol":"KTB10Y",  "desk":"Rates",       "notional":45000000, "trade_date":"2024-01-15"},
    {"symbol":"KTB3Y",   "desk":"Rates",       "notional":32000000, "trade_date":"2024-01-15"},
    {"symbol":"HY_CORP", "desk":"Credit",      "notional":28000000, "trade_date":"2024-01-15"},
    {"symbol":"IG_CORP", "desk":"Credit",      "notional":51000000, "trade_date":"2024-01-15"},
    {"symbol":"KOSPI",   "desk":"Equities",    "notional":38000000, "trade_date":"2024-01-15"},
    {"symbol":"KOSDAQ",  "desk":"Equities",    "notional":19000000, "trade_date":"2024-01-15"},
    {"symbol":"USD/KRW", "desk":"FX",          "notional":62000000, "trade_date":"2024-01-15"},
    {"symbol":"EUR/KRW", "desk":"FX",          "notional":41000000, "trade_date":"2024-01-15"},
    {"symbol":"CRUDE",   "desk":"Commodities", "notional":24000000, "trade_date":"2024-01-15"},
    {"symbol":"KTB10Y",  "desk":"Rates",       "notional":48000000, "trade_date":"2024-02-15"},
    {"symbol":"HY_CORP", "desk":"Credit",      "notional":31000000, "trade_date":"2024-02-15"},
    {"symbol":"KOSPI",   "desk":"Equities",    "notional":42000000, "trade_date":"2024-02-15"},
    {"symbol":"USD/KRW", "desk":"FX",          "notional":67000000, "trade_date":"2024-02-15"},
    {"symbol":"CRUDE",   "desk":"Commodities", "notional":27000000, "trade_date":"2024-02-15"},
    {"symbol":"KTB3Y",   "desk":"Rates",       "notional":35000000, "trade_date":"2024-03-15"},
    {"symbol":"IG_CORP", "desk":"Credit",      "notional":55000000, "trade_date":"2024-03-15"},
    {"symbol":"KOSDAQ",  "desk":"Equities",    "notional":22000000, "trade_date":"2024-03-15"},
    {"symbol":"EUR/KRW", "desk":"FX",          "notional":44000000, "trade_date":"2024-03-15"},
    {"symbol":"GOLD",    "desk":"Commodities", "notional":18000000, "trade_date":"2024-03-15"}
  ]'
  ok "19 rows"

  step "Seeding RISK_SUMMARY (15 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/RISK_SUMMARY/rows/batch" -d '[
    {"portfolio":"Global Macro",      "var_amount":52000000, "report_date":"2024-01-31"},
    {"portfolio":"Credit",            "var_amount":35000000, "report_date":"2024-01-31"},
    {"portfolio":"EM",                "var_amount":42000000, "report_date":"2024-01-31"},
    {"portfolio":"Developed Markets", "var_amount":28000000, "report_date":"2024-01-31"},
    {"portfolio":"Alternatives",      "var_amount":21000000, "report_date":"2024-01-31"},
    {"portfolio":"Global Macro",      "var_amount":56000000, "report_date":"2024-02-29"},
    {"portfolio":"Credit",            "var_amount":38000000, "report_date":"2024-02-29"},
    {"portfolio":"EM",                "var_amount":45000000, "report_date":"2024-02-29"},
    {"portfolio":"Developed Markets", "var_amount":31000000, "report_date":"2024-02-29"},
    {"portfolio":"Alternatives",      "var_amount":24000000, "report_date":"2024-02-29"},
    {"portfolio":"Global Macro",      "var_amount":61000000, "report_date":"2024-03-31"},
    {"portfolio":"Credit",            "var_amount":41000000, "report_date":"2024-03-31"},
    {"portfolio":"EM",                "var_amount":49000000, "report_date":"2024-03-31"},
    {"portfolio":"Developed Markets", "var_amount":33000000, "report_date":"2024-03-31"},
    {"portfolio":"Alternatives",      "var_amount":26000000, "report_date":"2024-03-31"}
  ]'
  ok "15 rows"

  step "Seeding FX_RATE (12 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/FX_RATE/rows/batch" -d '[
    {"pair":"USD/KRW", "rate":1325.50, "rate_date":"2024-01-15"},
    {"pair":"USD/KRW", "rate":1332.20, "rate_date":"2024-02-15"},
    {"pair":"USD/KRW", "rate":1340.80, "rate_date":"2024-03-15"},
    {"pair":"USD/KRW", "rate":1355.40, "rate_date":"2024-04-15"},
    {"pair":"USD/KRW", "rate":1368.90, "rate_date":"2024-05-15"},
    {"pair":"USD/KRW", "rate":1380.20, "rate_date":"2024-06-15"},
    {"pair":"EUR/KRW", "rate":1428.30, "rate_date":"2024-01-15"},
    {"pair":"EUR/KRW", "rate":1435.60, "rate_date":"2024-02-15"},
    {"pair":"EUR/KRW", "rate":1441.20, "rate_date":"2024-03-15"},
    {"pair":"EUR/KRW", "rate":1452.80, "rate_date":"2024-04-15"},
    {"pair":"EUR/KRW", "rate":1461.50, "rate_date":"2024-05-15"},
    {"pair":"EUR/KRW", "rate":1469.90, "rate_date":"2024-06-15"}
  ]'
  ok "12 rows"

  step "Seeding RISK_MATRIX (25 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/RISK_MATRIX/rows/batch" -d '[
    {"desk":"Rates",       "risk_type":"Market Risk",      "score":72.5},
    {"desk":"Rates",       "risk_type":"Credit Risk",      "score":45.0},
    {"desk":"Rates",       "risk_type":"Liquidity Risk",   "score":33.0},
    {"desk":"Rates",       "risk_type":"Operational Risk", "score":28.0},
    {"desk":"Rates",       "risk_type":"Compliance",       "score":91.0},
    {"desk":"FX",          "risk_type":"Market Risk",      "score":88.5},
    {"desk":"FX",          "risk_type":"Credit Risk",      "score":52.0},
    {"desk":"FX",          "risk_type":"Liquidity Risk",   "score":61.0},
    {"desk":"FX",          "risk_type":"Operational Risk", "score":35.0},
    {"desk":"FX",          "risk_type":"Compliance",       "score":78.0},
    {"desk":"Equity",      "risk_type":"Market Risk",      "score":95.0},
    {"desk":"Equity",      "risk_type":"Credit Risk",      "score":38.0},
    {"desk":"Equity",      "risk_type":"Liquidity Risk",   "score":55.0},
    {"desk":"Equity",      "risk_type":"Operational Risk", "score":42.0},
    {"desk":"Equity",      "risk_type":"Compliance",       "score":85.0},
    {"desk":"Credit",      "risk_type":"Market Risk",      "score":58.0},
    {"desk":"Credit",      "risk_type":"Credit Risk",      "score":79.5},
    {"desk":"Credit",      "risk_type":"Liquidity Risk",   "score":48.0},
    {"desk":"Credit",      "risk_type":"Operational Risk", "score":31.0},
    {"desk":"Credit",      "risk_type":"Compliance",       "score":94.0},
    {"desk":"Commodities", "risk_type":"Market Risk",      "score":83.0},
    {"desk":"Commodities", "risk_type":"Credit Risk",      "score":41.0},
    {"desk":"Commodities", "risk_type":"Liquidity Risk",   "score":70.0},
    {"desk":"Commodities", "risk_type":"Operational Risk", "score":25.0},
    {"desk":"Commodities", "risk_type":"Compliance",       "score":88.5}
  ]'
  ok "25 rows"

  step "Seeding PORTFOLIO_SCORES (4 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/PORTFOLIO_SCORES/rows/batch" -d '[
    {"portfolio":"Alpha Fund", "market_score":85,"credit_score":72,"liquidity_score":90,"op_score":68,"compliance_score":95},
    {"portfolio":"Beta Fund",  "market_score":62,"credit_score":88,"liquidity_score":55,"op_score":74,"compliance_score":82},
    {"portfolio":"Gamma Fund", "market_score":78,"credit_score":65,"liquidity_score":83,"op_score":91,"compliance_score":70},
    {"portfolio":"Delta Fund", "market_score":92,"credit_score":58,"liquidity_score":71,"op_score":84,"compliance_score":88}
  ]'
  ok "4 rows"

  step "Seeding VAR_UTILIZATION (4 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/VAR_UTILIZATION/rows/batch" -d '[
    {"portfolio":"Alpha Fund", "utilization":73.5},
    {"portfolio":"Beta Fund",  "utilization":51.2},
    {"portfolio":"Gamma Fund", "utilization":88.9},
    {"portfolio":"Delta Fund", "utilization":35.7}
  ]'
  ok "4 rows"

  step "Seeding ASSET_PERFORMANCE (12 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/ASSET_PERFORMANCE/rows/batch" -d '[
    {"asset":"US Treasuries", "risk_pct": 2.1,"return_pct": 4.2,"volume":850000000},
    {"asset":"S&P 500 ETF",   "risk_pct":15.3,"return_pct":12.8,"volume":620000000},
    {"asset":"EUR/USD",       "risk_pct": 6.8,"return_pct": 3.1,"volume":1200000000},
    {"asset":"Gold",          "risk_pct":12.5,"return_pct": 7.9,"volume":430000000},
    {"asset":"Crude Oil",     "risk_pct":22.4,"return_pct":18.6,"volume":580000000},
    {"asset":"EM Bonds",      "risk_pct":18.7,"return_pct": 9.4,"volume":310000000},
    {"asset":"HY Credit",     "risk_pct":14.2,"return_pct": 8.1,"volume":275000000},
    {"asset":"MSCI EM",       "risk_pct":19.8,"return_pct":14.3,"volume":390000000},
    {"asset":"Nikkei 225",    "risk_pct":17.1,"return_pct":10.5,"volume":290000000},
    {"asset":"Bund 10Y",      "risk_pct": 3.4,"return_pct": 2.8,"volume":720000000},
    {"asset":"Bitcoin",       "risk_pct":65.2,"return_pct":48.7,"volume":150000000},
    {"asset":"Real Estate",   "risk_pct": 9.3,"return_pct": 6.6,"volume":410000000}
  ]'
  ok "12 rows"

  step "Seeding PORTFOLIO_AUM (20 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/PORTFOLIO_AUM/rows/batch" -d '[
    {"region":"Americas","asset_class":"Equities",       "aum":48500000000},
    {"region":"Americas","asset_class":"Fixed Income",   "aum":32100000000},
    {"region":"Americas","asset_class":"Derivatives",    "aum":15800000000},
    {"region":"Americas","asset_class":"Real Estate",    "aum": 9200000000},
    {"region":"Americas","asset_class":"FX/Commodities", "aum": 7400000000},
    {"region":"EMEA",    "asset_class":"Equities",       "aum":31200000000},
    {"region":"EMEA",    "asset_class":"Fixed Income",   "aum":41800000000},
    {"region":"EMEA",    "asset_class":"Derivatives",    "aum":12600000000},
    {"region":"EMEA",    "asset_class":"Real Estate",    "aum":18300000000},
    {"region":"EMEA",    "asset_class":"FX/Commodities", "aum": 6900000000},
    {"region":"APAC",    "asset_class":"Equities",       "aum":28700000000},
    {"region":"APAC",    "asset_class":"Fixed Income",   "aum":19400000000},
    {"region":"APAC",    "asset_class":"Derivatives",    "aum": 8900000000},
    {"region":"APAC",    "asset_class":"Real Estate",    "aum":22100000000},
    {"region":"APAC",    "asset_class":"FX/Commodities", "aum":11500000000},
    {"region":"LatAm",   "asset_class":"Equities",       "aum": 8200000000},
    {"region":"LatAm",   "asset_class":"Fixed Income",   "aum":11300000000},
    {"region":"LatAm",   "asset_class":"Derivatives",    "aum": 3100000000},
    {"region":"LatAm",   "asset_class":"Real Estate",    "aum": 4800000000},
    {"region":"LatAm",   "asset_class":"FX/Commodities", "aum": 2900000000}
  ]'
  ok "20 rows"

  step "Seeding GLOBAL_EXPOSURE (18 rows)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/GLOBAL_EXPOSURE/rows/batch" -d '[
    {"country":"United States",  "exposure":92500000000},
    {"country":"China",          "exposure":48300000000},
    {"country":"Japan",          "exposure":31700000000},
    {"country":"United Kingdom", "exposure":28900000000},
    {"country":"Germany",        "exposure":22400000000},
    {"country":"France",         "exposure":18600000000},
    {"country":"Canada",         "exposure":15200000000},
    {"country":"Australia",      "exposure":12800000000},
    {"country":"South Korea",    "exposure":11400000000},
    {"country":"India",          "exposure": 9700000000},
    {"country":"Singapore",      "exposure": 8300000000},
    {"country":"Switzerland",    "exposure": 7900000000},
    {"country":"Netherlands",    "exposure": 6400000000},
    {"country":"Brazil",         "exposure": 5800000000},
    {"country":"Spain",          "exposure": 4900000000},
    {"country":"Italy",          "exposure": 4200000000},
    {"country":"Russia",         "exposure": 3100000000},
    {"country":"Saudi Arabia",   "exposure": 2700000000}
  ]'
  ok "18 rows"

  step "Seeding FX_OHLC_DAILY (79 rows — USD/KRW daily OHLC, Jan–Apr 2026)"
  curl -sf -X POST -H "$H_JSON" "$BASE/api/v1/target/FX_OHLC_DAILY/rows/batch" -d '[
    {"pair":"USD/KRW","trade_date":"2026-01-05","open_price":1380.50,"close_price":1384.20,"low_price":1378.80,"high_price":1386.40,"trade_volume":9200000000},
    {"pair":"USD/KRW","trade_date":"2026-01-06","open_price":1384.20,"close_price":1381.60,"low_price":1379.90,"high_price":1386.30,"trade_volume":8340000000},
    {"pair":"USD/KRW","trade_date":"2026-01-07","open_price":1381.60,"close_price":1385.30,"low_price":1380.40,"high_price":1388.20,"trade_volume":9780000000},
    {"pair":"USD/KRW","trade_date":"2026-01-08","open_price":1385.30,"close_price":1388.90,"low_price":1383.80,"high_price":1391.50,"trade_volume":10670000000},
    {"pair":"USD/KRW","trade_date":"2026-01-09","open_price":1388.90,"close_price":1386.30,"low_price":1385.10,"high_price":1391.40,"trade_volume":9120000000},
    {"pair":"USD/KRW","trade_date":"2026-01-12","open_price":1386.30,"close_price":1390.00,"low_price":1385.20,"high_price":1392.80,"trade_volume":10240000000},
    {"pair":"USD/KRW","trade_date":"2026-01-13","open_price":1390.00,"close_price":1393.70,"low_price":1388.60,"high_price":1396.20,"trade_volume":11380000000},
    {"pair":"USD/KRW","trade_date":"2026-01-14","open_price":1393.70,"close_price":1391.10,"low_price":1389.80,"high_price":1396.10,"trade_volume":9870000000},
    {"pair":"USD/KRW","trade_date":"2026-01-15","open_price":1391.10,"close_price":1394.80,"low_price":1390.00,"high_price":1397.40,"trade_volume":10560000000},
    {"pair":"USD/KRW","trade_date":"2026-01-16","open_price":1394.80,"close_price":1398.50,"low_price":1393.40,"high_price":1401.20,"trade_volume":11890000000},
    {"pair":"USD/KRW","trade_date":"2026-01-19","open_price":1398.50,"close_price":1395.90,"low_price":1394.60,"high_price":1401.10,"trade_volume":10230000000},
    {"pair":"USD/KRW","trade_date":"2026-01-20","open_price":1395.90,"close_price":1399.60,"low_price":1394.80,"high_price":1402.40,"trade_volume":11120000000},
    {"pair":"USD/KRW","trade_date":"2026-01-21","open_price":1399.60,"close_price":1403.30,"low_price":1398.40,"high_price":1406.10,"trade_volume":12340000000},
    {"pair":"USD/KRW","trade_date":"2026-01-22","open_price":1403.30,"close_price":1400.70,"low_price":1399.40,"high_price":1406.20,"trade_volume":10780000000},
    {"pair":"USD/KRW","trade_date":"2026-01-23","open_price":1400.70,"close_price":1404.40,"low_price":1399.60,"high_price":1407.80,"trade_volume":11560000000},
    {"pair":"USD/KRW","trade_date":"2026-01-26","open_price":1404.40,"close_price":1408.10,"low_price":1403.20,"high_price":1410.90,"trade_volume":12780000000},
    {"pair":"USD/KRW","trade_date":"2026-01-27","open_price":1408.10,"close_price":1405.50,"low_price":1404.20,"high_price":1410.80,"trade_volume":11230000000},
    {"pair":"USD/KRW","trade_date":"2026-01-28","open_price":1405.50,"close_price":1409.20,"low_price":1404.40,"high_price":1412.60,"trade_volume":12100000000},
    {"pair":"USD/KRW","trade_date":"2026-01-29","open_price":1409.20,"close_price":1412.90,"low_price":1408.10,"high_price":1415.80,"trade_volume":13450000000},
    {"pair":"USD/KRW","trade_date":"2026-01-30","open_price":1412.90,"close_price":1410.30,"low_price":1409.20,"high_price":1415.60,"trade_volume":11870000000},
    {"pair":"USD/KRW","trade_date":"2026-02-02","open_price":1410.30,"close_price":1414.00,"low_price":1409.20,"high_price":1416.80,"trade_volume":12560000000},
    {"pair":"USD/KRW","trade_date":"2026-02-03","open_price":1414.00,"close_price":1417.70,"low_price":1412.80,"high_price":1420.50,"trade_volume":13780000000},
    {"pair":"USD/KRW","trade_date":"2026-02-04","open_price":1417.70,"close_price":1415.10,"low_price":1413.90,"high_price":1420.40,"trade_volume":12100000000},
    {"pair":"USD/KRW","trade_date":"2026-02-05","open_price":1415.10,"close_price":1418.80,"low_price":1414.00,"high_price":1421.70,"trade_volume":12890000000},
    {"pair":"USD/KRW","trade_date":"2026-02-06","open_price":1418.80,"close_price":1422.50,"low_price":1417.70,"high_price":1425.30,"trade_volume":14120000000},
    {"pair":"USD/KRW","trade_date":"2026-02-09","open_price":1422.50,"close_price":1419.90,"low_price":1418.80,"high_price":1425.20,"trade_volume":12670000000},
    {"pair":"USD/KRW","trade_date":"2026-02-10","open_price":1419.90,"close_price":1423.60,"low_price":1418.80,"high_price":1426.40,"trade_volume":13450000000},
    {"pair":"USD/KRW","trade_date":"2026-02-11","open_price":1423.60,"close_price":1427.30,"low_price":1422.40,"high_price":1430.10,"trade_volume":14780000000},
    {"pair":"USD/KRW","trade_date":"2026-02-12","open_price":1427.30,"close_price":1424.70,"low_price":1423.60,"high_price":1429.80,"trade_volume":13120000000},
    {"pair":"USD/KRW","trade_date":"2026-02-13","open_price":1424.70,"close_price":1428.40,"low_price":1423.60,"high_price":1431.20,"trade_volume":13990000000},
    {"pair":"USD/KRW","trade_date":"2026-02-16","open_price":1428.40,"close_price":1432.10,"low_price":1427.30,"high_price":1434.90,"trade_volume":15230000000},
    {"pair":"USD/KRW","trade_date":"2026-02-17","open_price":1432.10,"close_price":1429.50,"low_price":1428.40,"high_price":1434.80,"trade_volume":13560000000},
    {"pair":"USD/KRW","trade_date":"2026-02-18","open_price":1429.50,"close_price":1433.20,"low_price":1428.40,"high_price":1436.00,"trade_volume":14340000000},
    {"pair":"USD/KRW","trade_date":"2026-02-19","open_price":1433.20,"close_price":1436.90,"low_price":1432.10,"high_price":1439.70,"trade_volume":15780000000},
    {"pair":"USD/KRW","trade_date":"2026-02-20","open_price":1436.90,"close_price":1434.30,"low_price":1433.20,"high_price":1439.60,"trade_volume":14120000000},
    {"pair":"USD/KRW","trade_date":"2026-02-23","open_price":1434.30,"close_price":1438.00,"low_price":1433.20,"high_price":1440.80,"trade_volume":15100000000},
    {"pair":"USD/KRW","trade_date":"2026-02-24","open_price":1438.00,"close_price":1441.70,"low_price":1436.90,"high_price":1444.50,"trade_volume":16340000000},
    {"pair":"USD/KRW","trade_date":"2026-02-25","open_price":1441.70,"close_price":1439.10,"low_price":1438.00,"high_price":1444.40,"trade_volume":14780000000},
    {"pair":"USD/KRW","trade_date":"2026-02-26","open_price":1439.10,"close_price":1442.80,"low_price":1438.00,"high_price":1445.60,"trade_volume":15560000000},
    {"pair":"USD/KRW","trade_date":"2026-02-27","open_price":1442.80,"close_price":1446.50,"low_price":1441.70,"high_price":1449.30,"trade_volume":16890000000},
    {"pair":"USD/KRW","trade_date":"2026-03-02","open_price":1446.50,"close_price":1443.90,"low_price":1442.80,"high_price":1449.20,"trade_volume":15230000000},
    {"pair":"USD/KRW","trade_date":"2026-03-03","open_price":1443.90,"close_price":1447.60,"low_price":1442.80,"high_price":1450.40,"trade_volume":15990000000},
    {"pair":"USD/KRW","trade_date":"2026-03-04","open_price":1447.60,"close_price":1451.30,"low_price":1446.50,"high_price":1454.10,"trade_volume":17120000000},
    {"pair":"USD/KRW","trade_date":"2026-03-05","open_price":1451.30,"close_price":1448.70,"low_price":1447.60,"high_price":1453.80,"trade_volume":15670000000},
    {"pair":"USD/KRW","trade_date":"2026-03-06","open_price":1448.70,"close_price":1452.40,"low_price":1447.60,"high_price":1455.20,"trade_volume":16340000000},
    {"pair":"USD/KRW","trade_date":"2026-03-09","open_price":1452.40,"close_price":1456.10,"low_price":1451.30,"high_price":1458.90,"trade_volume":17560000000},
    {"pair":"USD/KRW","trade_date":"2026-03-10","open_price":1456.10,"close_price":1453.50,"low_price":1452.40,"high_price":1458.80,"trade_volume":15890000000},
    {"pair":"USD/KRW","trade_date":"2026-03-11","open_price":1453.50,"close_price":1457.20,"low_price":1452.40,"high_price":1460.00,"trade_volume":16780000000},
    {"pair":"USD/KRW","trade_date":"2026-03-12","open_price":1457.20,"close_price":1460.90,"low_price":1456.10,"high_price":1463.70,"trade_volume":18100000000},
    {"pair":"USD/KRW","trade_date":"2026-03-13","open_price":1460.90,"close_price":1458.30,"low_price":1457.20,"high_price":1463.60,"trade_volume":16450000000},
    {"pair":"USD/KRW","trade_date":"2026-03-16","open_price":1458.30,"close_price":1462.00,"low_price":1457.20,"high_price":1464.80,"trade_volume":17340000000},
    {"pair":"USD/KRW","trade_date":"2026-03-17","open_price":1462.00,"close_price":1465.70,"low_price":1460.90,"high_price":1468.50,"trade_volume":18670000000},
    {"pair":"USD/KRW","trade_date":"2026-03-18","open_price":1465.70,"close_price":1463.10,"low_price":1462.00,"high_price":1468.40,"trade_volume":16890000000},
    {"pair":"USD/KRW","trade_date":"2026-03-19","open_price":1463.10,"close_price":1466.80,"low_price":1462.00,"high_price":1469.60,"trade_volume":17780000000},
    {"pair":"USD/KRW","trade_date":"2026-03-20","open_price":1466.80,"close_price":1470.50,"low_price":1465.70,"high_price":1473.30,"trade_volume":19100000000},
    {"pair":"USD/KRW","trade_date":"2026-03-23","open_price":1470.50,"close_price":1467.90,"low_price":1466.80,"high_price":1473.20,"trade_volume":17450000000},
    {"pair":"USD/KRW","trade_date":"2026-03-24","open_price":1467.90,"close_price":1471.60,"low_price":1466.80,"high_price":1474.40,"trade_volume":18340000000},
    {"pair":"USD/KRW","trade_date":"2026-03-25","open_price":1471.60,"close_price":1475.30,"low_price":1470.50,"high_price":1478.10,"trade_volume":19670000000},
    {"pair":"USD/KRW","trade_date":"2026-03-26","open_price":1475.30,"close_price":1472.70,"low_price":1471.60,"high_price":1478.00,"trade_volume":17890000000},
    {"pair":"USD/KRW","trade_date":"2026-03-27","open_price":1472.70,"close_price":1476.40,"low_price":1471.60,"high_price":1479.20,"trade_volume":18780000000},
    {"pair":"USD/KRW","trade_date":"2026-03-30","open_price":1476.40,"close_price":1480.10,"low_price":1475.30,"high_price":1482.90,"trade_volume":20100000000},
    {"pair":"USD/KRW","trade_date":"2026-03-31","open_price":1480.10,"close_price":1477.50,"low_price":1476.40,"high_price":1482.80,"trade_volume":18340000000},
    {"pair":"USD/KRW","trade_date":"2026-04-01","open_price":1477.50,"close_price":1481.20,"low_price":1476.40,"high_price":1484.00,"trade_volume":19120000000},
    {"pair":"USD/KRW","trade_date":"2026-04-02","open_price":1481.20,"close_price":1484.90,"low_price":1480.10,"high_price":1487.70,"trade_volume":20450000000},
    {"pair":"USD/KRW","trade_date":"2026-04-03","open_price":1484.90,"close_price":1482.30,"low_price":1481.20,"high_price":1487.60,"trade_volume":18780000000},
    {"pair":"USD/KRW","trade_date":"2026-04-06","open_price":1482.30,"close_price":1486.00,"low_price":1481.20,"high_price":1488.80,"trade_volume":19560000000},
    {"pair":"USD/KRW","trade_date":"2026-04-07","open_price":1486.00,"close_price":1489.70,"low_price":1484.90,"high_price":1492.50,"trade_volume":20890000000},
    {"pair":"USD/KRW","trade_date":"2026-04-08","open_price":1489.70,"close_price":1487.10,"low_price":1486.00,"high_price":1492.40,"trade_volume":19120000000},
    {"pair":"USD/KRW","trade_date":"2026-04-09","open_price":1487.10,"close_price":1490.80,"low_price":1486.00,"high_price":1493.60,"trade_volume":20010000000},
    {"pair":"USD/KRW","trade_date":"2026-04-10","open_price":1490.80,"close_price":1494.50,"low_price":1489.70,"high_price":1497.30,"trade_volume":21340000000},
    {"pair":"USD/KRW","trade_date":"2026-04-13","open_price":1494.50,"close_price":1491.90,"low_price":1490.80,"high_price":1497.20,"trade_volume":19670000000},
    {"pair":"USD/KRW","trade_date":"2026-04-14","open_price":1491.90,"close_price":1495.60,"low_price":1490.80,"high_price":1498.40,"trade_volume":20560000000},
    {"pair":"USD/KRW","trade_date":"2026-04-15","open_price":1495.60,"close_price":1499.30,"low_price":1494.50,"high_price":1502.10,"trade_volume":21890000000},
    {"pair":"USD/KRW","trade_date":"2026-04-16","open_price":1499.30,"close_price":1496.70,"low_price":1495.60,"high_price":1502.00,"trade_volume":20120000000},
    {"pair":"USD/KRW","trade_date":"2026-04-17","open_price":1496.70,"close_price":1500.40,"low_price":1495.60,"high_price":1503.20,"trade_volume":20990000000},
    {"pair":"USD/KRW","trade_date":"2026-04-20","open_price":1500.40,"close_price":1504.10,"low_price":1499.30,"high_price":1506.90,"trade_volume":22230000000},
    {"pair":"USD/KRW","trade_date":"2026-04-21","open_price":1504.10,"close_price":1501.50,"low_price":1500.40,"high_price":1506.80,"trade_volume":20560000000},
    {"pair":"USD/KRW","trade_date":"2026-04-22","open_price":1501.50,"close_price":1505.20,"low_price":1500.40,"high_price":1508.00,"trade_volume":21450000000},
    {"pair":"USD/KRW","trade_date":"2026-04-23","open_price":1505.20,"close_price":1508.90,"low_price":1504.10,"high_price":1511.70,"trade_volume":22780000000},
    {"pair":"USD/KRW","trade_date":"2026-04-24","open_price":1508.90,"close_price":1506.30,"low_price":1505.20,"high_price":1511.60,"trade_volume":21100000000},
    {"pair":"USD/KRW","trade_date":"2026-04-25","open_price":1506.30,"close_price":1510.00,"low_price":1505.20,"high_price":1512.80,"trade_volume":21990000000}
  ]'
  ok "79 rows"
}

# =============================================================================
# BANNER
# =============================================================================
print_banner() {
  echo
  echo "╔══════════════════════════════════════════════════════════════════╗"
  echo "║         GitOps Widget Setup — Dashboard Engine                  ║"
  echo "║   Deploy YAML definitions via POST /api/v1/admin/widgets/deploy ║"
  echo "╚══════════════════════════════════════════════════════════════════╝"
  echo "  Backend : $BASE"
  echo "  YAMLs   : $WIDGETS_DIR/"
  echo
}

print_footer() {
  echo
  echo "══════════════════════════════════════════════════════════════════"
  echo "  Done.  Open http://localhost:5173 — all 12 widgets will render."
  echo
  echo "  Redeploy a single widget after editing its YAML:"
  echo "    ./setup-gitops.sh deploy-one WD_RISK_VAR"
  echo
  echo "  WD_GLOBAL_MAP requires world.json in frontend/public/:"
  echo "    curl -o frontend/public/world.json \\"
  echo '      "https://echarts.apache.org/examples/data/asset/geo/world.json"'
  echo "══════════════════════════════════════════════════════════════════"
}

# =============================================================================
# MAIN
# =============================================================================
case "${1:-setup}" in

  # --------------------------------------------------------------------------
  # setup (default): seed data + write YAMLs + deploy all
  # --------------------------------------------------------------------------
  setup)
    print_banner
    check_backend
    seed_data
    write_yamls
    deploy_all
    print_footer
    ;;

  # --------------------------------------------------------------------------
  # deploy: (re)deploy from existing widgets/ YAML files (no data seeding)
  # --------------------------------------------------------------------------
  deploy)
    print_banner
    check_backend
    if [[ ! -d "$WIDGETS_DIR" ]]; then
      echo "  ERROR: $WIDGETS_DIR/ not found. Run './setup-gitops.sh' first."
      exit 1
    fi
    deploy_all
    print_footer
    ;;

  # --------------------------------------------------------------------------
  # deploy-one <widgetId>: redeploy a single widget by ID
  # --------------------------------------------------------------------------
  deploy-one)
    if [[ -z "${2:-}" ]]; then
      echo "Usage: $0 deploy-one <widgetId>"
      echo "Example: $0 deploy-one WD_RISK_VAR"
      exit 1
    fi
    widget_id="${2:-}"
    yaml_file="$WIDGETS_DIR/$widget_id.yml"
    if [[ ! -f "$yaml_file" ]]; then
      echo "  ERROR: $yaml_file not found."
      echo "         Run './setup-gitops.sh' first to generate YAML files."
      exit 1
    fi
    check_backend
    echo
    echo "==> Deploying $widget_id"
    deploy_widget "$yaml_file"
    ;;

  # --------------------------------------------------------------------------
  # teardown: delete all 12 managed widgets (table data is kept)
  # --------------------------------------------------------------------------
  teardown)
    print_banner
    check_backend
    teardown
    echo
    echo "  Teardown complete. Table data was kept."
    ;;

  # --------------------------------------------------------------------------
  # reset: teardown + full setup
  # --------------------------------------------------------------------------
  reset)
    print_banner
    check_backend
    teardown
    seed_data
    write_yamls
    deploy_all
    print_footer
    ;;

  *)
    echo "Usage: $0 [setup|deploy|deploy-one <widgetId>|teardown|reset]"
    exit 1
    ;;
esac
