#!/usr/bin/env bash
# =============================================================================
# New Chart Types — Setup Script
#
# Creates tables, seeds data, and registers 6 widgets for the new chart types:
#   heatmap | radar | gauge | scatter | treemap | map
#
# Usage:
#   ./setup-new-charts.sh            full setup
#   ./setup-new-charts.sh reset      delete existing widgets then re-run setup
#   ./setup-new-charts.sh teardown   delete all 6 widgets (keeps table data)
# =============================================================================
set -euo pipefail

BASE="http://localhost:8081"
H="Content-Type: application/json"

ok()   { echo "  [OK]  $*"; }
fail() { echo "  [!!]  $*" >&2; }
step() { echo; echo "==> $*"; }

post()   { curl -sf -X POST   -H "$H" -d "$2" "$BASE/$1"; }
put()    { curl -sf -X PUT    -H "$H" -d "$2" "$BASE/$1"; }
patch()  { curl -sf -X PATCH                  "$BASE/$1"; }
delete() { curl -sf -X DELETE                 "$BASE/$1"; }
get()    { curl -sf                            "$BASE/$1"; }

WIDGET_IDS=(
  WD_RISK_HEATMAP
  WD_PORTFOLIO_RADAR
  WD_VAR_GAUGE
  WD_ASSET_SCATTER
  WD_AUM_TREEMAP
  WD_GLOBAL_MAP
)

# =============================================================================
# STEP 1 — SEED DATA
# (All tables are created automatically at startup via db/target/schema.sql)
# =============================================================================
seed_data() {

  # ── RISK_MATRIX ─────────────────────────────────────────────────────────────
  step "Seeding RISK_MATRIX (5 desks × 5 risk types = 25 cells)"
  post "api/v1/target/RISK_MATRIX/rows/batch" '[
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

  # ── PORTFOLIO_SCORES ─────────────────────────────────────────────────────────
  step "Seeding PORTFOLIO_SCORES (4 portfolios × 5 risk axes)"
  post "api/v1/target/PORTFOLIO_SCORES/rows/batch" '[
    {"portfolio":"Alpha Fund",  "market_score":85, "credit_score":72, "liquidity_score":90, "op_score":68, "compliance_score":95},
    {"portfolio":"Beta Fund",   "market_score":62, "credit_score":88, "liquidity_score":55, "op_score":74, "compliance_score":82},
    {"portfolio":"Gamma Fund",  "market_score":78, "credit_score":65, "liquidity_score":83, "op_score":91, "compliance_score":70},
    {"portfolio":"Delta Fund",  "market_score":92, "credit_score":58, "liquidity_score":71, "op_score":84, "compliance_score":88}
  ]'
  ok "4 portfolios"

  # ── VAR_UTILIZATION ──────────────────────────────────────────────────────────
  step "Seeding VAR_UTILIZATION (4 gauges)"
  post "api/v1/target/VAR_UTILIZATION/rows/batch" '[
    {"portfolio":"Alpha Fund",  "utilization":73.5},
    {"portfolio":"Beta Fund",   "utilization":51.2},
    {"portfolio":"Gamma Fund",  "utilization":88.9},
    {"portfolio":"Delta Fund",  "utilization":35.7}
  ]'
  ok "4 rows (renders as 4-gauge grid)"

  # ── ASSET_PERFORMANCE ────────────────────────────────────────────────────────
  step "Seeding ASSET_PERFORMANCE (12 assets)"
  post "api/v1/target/ASSET_PERFORMANCE/rows/batch" '[
    {"asset":"US Treasuries",  "risk_pct": 2.1, "return_pct": 4.2, "volume":850000000},
    {"asset":"S&P 500 ETF",    "risk_pct":15.3, "return_pct":12.8, "volume":620000000},
    {"asset":"EUR/USD",        "risk_pct": 6.8, "return_pct": 3.1, "volume":1200000000},
    {"asset":"Gold",           "risk_pct":12.5, "return_pct": 7.9, "volume":430000000},
    {"asset":"Crude Oil",      "risk_pct":22.4, "return_pct":18.6, "volume":580000000},
    {"asset":"EM Bonds",       "risk_pct":18.7, "return_pct": 9.4, "volume":310000000},
    {"asset":"HY Credit",      "risk_pct":14.2, "return_pct": 8.1, "volume":275000000},
    {"asset":"MSCI EM",        "risk_pct":19.8, "return_pct":14.3, "volume":390000000},
    {"asset":"Nikkei 225",     "risk_pct":17.1, "return_pct":10.5, "volume":290000000},
    {"asset":"Bund 10Y",       "risk_pct": 3.4, "return_pct": 2.8, "volume":720000000},
    {"asset":"Bitcoin",        "risk_pct":65.2, "return_pct":48.7, "volume":150000000},
    {"asset":"Real Estate",    "risk_pct": 9.3, "return_pct": 6.6, "volume":410000000}
  ]'
  ok "12 assets"

  # ── PORTFOLIO_AUM ────────────────────────────────────────────────────────────
  step "Seeding PORTFOLIO_AUM (4 regions × 5 asset classes = 20 rows)"
  post "api/v1/target/PORTFOLIO_AUM/rows/batch" '[
    {"region":"Americas", "asset_class":"Equities",        "aum":48500000000},
    {"region":"Americas", "asset_class":"Fixed Income",    "aum":32100000000},
    {"region":"Americas", "asset_class":"Derivatives",     "aum":15800000000},
    {"region":"Americas", "asset_class":"Real Estate",     "aum": 9200000000},
    {"region":"Americas", "asset_class":"FX/Commodities",  "aum": 7400000000},
    {"region":"EMEA",     "asset_class":"Equities",        "aum":31200000000},
    {"region":"EMEA",     "asset_class":"Fixed Income",    "aum":41800000000},
    {"region":"EMEA",     "asset_class":"Derivatives",     "aum":12600000000},
    {"region":"EMEA",     "asset_class":"Real Estate",     "aum":18300000000},
    {"region":"EMEA",     "asset_class":"FX/Commodities",  "aum": 6900000000},
    {"region":"APAC",     "asset_class":"Equities",        "aum":28700000000},
    {"region":"APAC",     "asset_class":"Fixed Income",    "aum":19400000000},
    {"region":"APAC",     "asset_class":"Derivatives",     "aum": 8900000000},
    {"region":"APAC",     "asset_class":"Real Estate",     "aum":22100000000},
    {"region":"APAC",     "asset_class":"FX/Commodities",  "aum":11500000000},
    {"region":"LatAm",    "asset_class":"Equities",        "aum": 8200000000},
    {"region":"LatAm",    "asset_class":"Fixed Income",    "aum":11300000000},
    {"region":"LatAm",    "asset_class":"Derivatives",     "aum": 3100000000},
    {"region":"LatAm",    "asset_class":"Real Estate",     "aum": 4800000000},
    {"region":"LatAm",    "asset_class":"FX/Commodities",  "aum": 2900000000}
  ]'
  ok "20 rows"

  # ── GLOBAL_EXPOSURE ──────────────────────────────────────────────────────────
  step "Seeding GLOBAL_EXPOSURE (18 countries)"
  # Country names must match ECharts world GeoJSON exactly.
  post "api/v1/target/GLOBAL_EXPOSURE/rows/batch" '[
    {"country":"United States",       "exposure":92500000000},
    {"country":"China",               "exposure":48300000000},
    {"country":"Japan",               "exposure":31700000000},
    {"country":"United Kingdom",      "exposure":28900000000},
    {"country":"Germany",             "exposure":22400000000},
    {"country":"France",              "exposure":18600000000},
    {"country":"Canada",              "exposure":15200000000},
    {"country":"Australia",           "exposure":12800000000},
    {"country":"South Korea",         "exposure":11400000000},
    {"country":"India",               "exposure": 9700000000},
    {"country":"Singapore",           "exposure": 8300000000},
    {"country":"Switzerland",         "exposure": 7900000000},
    {"country":"Netherlands",         "exposure": 6400000000},
    {"country":"Brazil",              "exposure": 5800000000},
    {"country":"Spain",               "exposure": 4900000000},
    {"country":"Italy",               "exposure": 4200000000},
    {"country":"Russia",              "exposure": 3100000000},
    {"country":"Saudi Arabia",        "exposure": 2700000000}
  ]'
  ok "18 countries"
}

# =============================================================================
# STEP 2 — REGISTER WIDGETS
# =============================================================================
register_widgets() {
  step "Registering 6 new chart-type widgets"

  # ── HEATMAP ──────────────────────────────────────────────────────────────────
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_RISK_HEATMAP",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT desk, risk_type, score FROM RISK_MATRIX ORDER BY desk, risk_type",
    "dynamicConfig": "{\"chart_type\":\"heatmap\",\"title\":\"Risk Score Matrix by Desk\",\"xField\":\"desk\",\"yField\":\"risk_type\",\"valueField\":\"score\",\"xLabel\":\"Trading Desk\",\"yLabel\":\"Risk Category\"}"
  }'
  ok "WD_RISK_HEATMAP     (heatmap  — RISK_MATRIX)"

  # ── RADAR ────────────────────────────────────────────────────────────────────
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_PORTFOLIO_RADAR",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT portfolio, market_score, credit_score, liquidity_score, op_score, compliance_score FROM PORTFOLIO_SCORES ORDER BY portfolio",
    "dynamicConfig": "{\"chart_type\":\"radar\",\"title\":\"Portfolio Risk Profile\",\"nameField\":\"portfolio\",\"indicators\":[{\"name\":\"Market\",\"max\":100},{\"name\":\"Credit\",\"max\":100},{\"name\":\"Liquidity\",\"max\":100},{\"name\":\"Operational\",\"max\":100},{\"name\":\"Compliance\",\"max\":100}],\"valueFields\":[\"market_score\",\"credit_score\",\"liquidity_score\",\"op_score\",\"compliance_score\"]}"
  }'
  ok "WD_PORTFOLIO_RADAR  (radar    — PORTFOLIO_SCORES)"

  # ── GAUGE ────────────────────────────────────────────────────────────────────
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_VAR_GAUGE",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT portfolio, utilization FROM VAR_UTILIZATION ORDER BY portfolio",
    "dynamicConfig": "{\"chart_type\":\"gauge\",\"title\":\"VaR Limit Utilization\",\"nameField\":\"portfolio\",\"valueField\":\"utilization\",\"max\":100,\"unit\":\"%\"}"
  }'
  ok "WD_VAR_GAUGE        (gauge    — VAR_UTILIZATION)"

  # ── SCATTER ──────────────────────────────────────────────────────────────────
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_ASSET_SCATTER",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT asset, risk_pct, return_pct, volume FROM ASSET_PERFORMANCE ORDER BY volume DESC",
    "dynamicConfig": "{\"chart_type\":\"scatter\",\"title\":\"Risk vs Return  (bubble = AUM)\",\"xField\":\"risk_pct\",\"yField\":\"return_pct\",\"sizeField\":\"volume\",\"nameField\":\"asset\",\"xLabel\":\"Risk (%)\",\"yLabel\":\"Return (%)\"}"
  }'
  ok "WD_ASSET_SCATTER    (scatter  — ASSET_PERFORMANCE)"

  # ── TREEMAP ──────────────────────────────────────────────────────────────────
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_AUM_TREEMAP",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT region, asset_class, aum FROM PORTFOLIO_AUM ORDER BY region, aum DESC",
    "dynamicConfig": "{\"chart_type\":\"treemap\",\"title\":\"AUM by Region & Asset Class\",\"nameField\":\"asset_class\",\"valueField\":\"aum\",\"groupField\":\"region\"}"
  }'
  ok "WD_AUM_TREEMAP      (treemap  — PORTFOLIO_AUM)"

  # ── MAP ──────────────────────────────────────────────────────────────────────
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_GLOBAL_MAP",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT country, exposure FROM GLOBAL_EXPOSURE ORDER BY exposure DESC",
    "dynamicConfig": "{\"chart_type\":\"map\",\"title\":\"Global Credit Exposure\",\"nameField\":\"country\",\"valueField\":\"exposure\"}"
  }'
  ok "WD_GLOBAL_MAP       (map      — GLOBAL_EXPOSURE)"
}

# =============================================================================
# TEARDOWN — delete all 6 widgets (table data is kept)
# =============================================================================
teardown() {
  step "Deleting all new-chart widgets"
  for id in "${WIDGET_IDS[@]}"; do
    if delete "api/v1/admin/widgets/$id" 2>/dev/null; then
      ok "Deleted $id"
    else
      echo "  [--]  $id not found (skipped)"
    fi
  done
}

# =============================================================================
# MAIN
# =============================================================================
print_banner() {
  echo
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║         New Chart Types — Dashboard Setup Script            ║"
  echo "║   heatmap │ radar │ gauge │ scatter │ treemap │ map          ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo
}

case "${1:-setup}" in
  setup)
    print_banner
    seed_data
    register_widgets
    echo
    echo "══════════════════════════════════════════════════════════════"
    echo "  Done! 6 widgets registered. Refresh http://localhost:5173"
    echo
    echo "  NOTE: WD_GLOBAL_MAP needs world.json in frontend/public/"
    echo "  curl -o frontend/public/world.json \\"
    echo '    "https://echarts.apache.org/en/documents/resource/asset/geo/world.json"'
    echo "══════════════════════════════════════════════════════════════"
    ;;

  reset)
    print_banner
    step "Reset: deleting existing widgets before re-seeding"
    teardown
    seed_data
    register_widgets
    echo
    echo "  Reset complete."
    ;;

  teardown)
    print_banner
    teardown
    echo
    echo "  Teardown complete. Tables and data were kept."
    ;;

  *)
    echo "Usage: $0 [setup|reset|teardown]"
    exit 1
    ;;
esac
