#!/usr/bin/env bash
# =============================================================================
# Dashboard Engine — Full Setup & Lifecycle Demo
#
# Usage:
#   ./setup.sh              run full setup (tables → data → widgets)
#   ./setup.sh scenarios    run lifecycle scenarios after setup
# =============================================================================
set -euo pipefail

BASE="http://localhost:8081"
H="Content-Type: application/json"

ok()   { echo "  [OK]  $*"; }
step() { echo; echo "==> $*"; }

# ── Helpers ──────────────────────────────────────────────────────────────────

post()   { curl -sf -X POST   -H "$H" -d "$2" "$BASE/$1"; }
put()    { curl -sf -X PUT    -H "$H" -d "$2" "$BASE/$1"; }
patch()  { curl -sf -X PATCH          "$BASE/$1"; }
delete() { curl -sf -X DELETE         "$BASE/$1"; }
get()    { curl -sf            "$BASE/$1"; }

# =============================================================================
# STEP 1 — SEED DATA
# (All tables are created automatically at startup via db/target/schema.sql)
# =============================================================================
seed_data() {
  step "Seeding SALES_SUMMARY"
  post "api/v1/target/SALES_SUMMARY/rows/batch" '[
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

  step "Seeding TRADE_SUMMARY"
  post "api/v1/target/TRADE_SUMMARY/rows/batch" '[
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

  step "Seeding RISK_SUMMARY"
  post "api/v1/target/RISK_SUMMARY/rows/batch" '[
    {"portfolio":"Global Macro",       "var_amount":52000000, "report_date":"2024-01-31"},
    {"portfolio":"Credit",             "var_amount":35000000, "report_date":"2024-01-31"},
    {"portfolio":"EM",                 "var_amount":42000000, "report_date":"2024-01-31"},
    {"portfolio":"Developed Markets",  "var_amount":28000000, "report_date":"2024-01-31"},
    {"portfolio":"Alternatives",       "var_amount":21000000, "report_date":"2024-01-31"},
    {"portfolio":"Global Macro",       "var_amount":56000000, "report_date":"2024-02-29"},
    {"portfolio":"Credit",             "var_amount":38000000, "report_date":"2024-02-29"},
    {"portfolio":"EM",                 "var_amount":45000000, "report_date":"2024-02-29"},
    {"portfolio":"Developed Markets",  "var_amount":31000000, "report_date":"2024-02-29"},
    {"portfolio":"Alternatives",       "var_amount":24000000, "report_date":"2024-02-29"},
    {"portfolio":"Global Macro",       "var_amount":61000000, "report_date":"2024-03-31"},
    {"portfolio":"Credit",             "var_amount":41000000, "report_date":"2024-03-31"},
    {"portfolio":"EM",                 "var_amount":49000000, "report_date":"2024-03-31"},
    {"portfolio":"Developed Markets",  "var_amount":33000000, "report_date":"2024-03-31"},
    {"portfolio":"Alternatives",       "var_amount":26000000, "report_date":"2024-03-31"}
  ]'
  ok "15 rows"

  step "Seeding FX_RATE"
  post "api/v1/target/FX_RATE/rows/batch" '[
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
}

# =============================================================================
# STEP 2 — REGISTER 6 WIDGETS
# =============================================================================
register_widgets() {
  step "Registering widgets"

  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_SALES_REGION",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT region, SUM(amount) AS total_amount FROM SALES_SUMMARY GROUP BY region ORDER BY total_amount DESC",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"Sales Revenue by Region\",\"xAxis\":{\"field\":\"region\",\"label\":\"Region\"},\"yAxis\":{\"label\":\"Revenue (USD)\"},\"series\":[{\"name\":\"Revenue\",\"valueField\":\"total_amount\"}]}"
  }'
  ok "WD_SALES_REGION  (bar  — SALES_SUMMARY)"

  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_SALES_PRODUCT",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT product AS category, SUM(amount) AS revenue FROM SALES_SUMMARY GROUP BY product ORDER BY revenue DESC",
    "dynamicConfig": "{\"chart_type\":\"pie\",\"title\":\"Revenue Share by Product\",\"nameField\":\"category\",\"valueField\":\"revenue\"}"
  }'
  ok "WD_SALES_PRODUCT (pie  — SALES_SUMMARY)"

  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_SALES_TREND",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT sale_date AS trade_date, SUM(amount) AS daily_total FROM SALES_SUMMARY GROUP BY sale_date ORDER BY sale_date ASC",
    "dynamicConfig": "{\"chart_type\":\"line\",\"title\":\"Monthly Revenue Trend\",\"xAxis\":{\"field\":\"trade_date\",\"label\":\"Date\"},\"yAxis\":{\"label\":\"Revenue (USD)\"},\"series\":[{\"name\":\"Revenue\",\"valueField\":\"daily_total\"}]}"
  }'
  ok "WD_SALES_TREND   (line — SALES_SUMMARY)"

  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_TRADE_DESK",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT desk, SUM(notional) AS total_notional FROM TRADE_SUMMARY GROUP BY desk ORDER BY total_notional DESC",
    "dynamicConfig": "{\"chart_type\":\"pie\",\"title\":\"Notional by Trading Desk\",\"nameField\":\"desk\",\"valueField\":\"total_notional\"}"
  }'
  ok "WD_TRADE_DESK    (pie  — TRADE_SUMMARY)"

  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_RISK_VAR",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT portfolio, SUM(var_amount) AS total_var FROM RISK_SUMMARY GROUP BY portfolio ORDER BY total_var DESC",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"Value at Risk by Portfolio\",\"xAxis\":{\"field\":\"portfolio\",\"label\":\"Portfolio\"},\"yAxis\":{\"label\":\"VaR (USD)\"},\"series\":[{\"name\":\"VaR\",\"valueField\":\"total_var\"}]}"
  }'
  ok "WD_RISK_VAR      (bar  — RISK_SUMMARY)"

  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_FX_TREND",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT rate_date, rate FROM FX_RATE WHERE pair='\''USD/KRW'\'' ORDER BY rate_date ASC",
    "dynamicConfig": "{\"chart_type\":\"line\",\"title\":\"USD/KRW Exchange Rate Trend\",\"xAxis\":{\"field\":\"rate_date\",\"label\":\"Date\"},\"yAxis\":{\"label\":\"KRW per USD\"},\"series\":[{\"name\":\"USD/KRW\",\"valueField\":\"rate\"}]}"
  }'
  ok "WD_FX_TREND      (line — FX_RATE)"
}

# =============================================================================
# LIFECYCLE SCENARIOS
# =============================================================================
scenario_deactivate() {
  step "SCENARIO: Deactivate WD_RISK_VAR (hidden from dashboard, data preserved)"
  patch "api/v1/admin/widgets/WD_RISK_VAR/deactivate"
  ok "WD_RISK_VAR deactivated — disappears from dashboard on next refresh"

  echo "  Current active widgets:"
  get "api/v1/admin/widgets?activeOnly=true" | python3 -c \
    "import sys,json; [print('   -', w['widgetId']) for w in json.load(sys.stdin)]" 2>/dev/null \
    || get "api/v1/admin/widgets?activeOnly=true"
}

scenario_activate() {
  step "SCENARIO: Re-activate WD_RISK_VAR"
  patch "api/v1/admin/widgets/WD_RISK_VAR/activate"
  ok "WD_RISK_VAR activated — reappears on dashboard"
}

scenario_update() {
  step "SCENARIO: Update WD_FX_TREND — switch from USD/KRW to EUR/KRW"
  put "api/v1/admin/widgets/WD_FX_TREND" '{
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT rate_date, rate FROM FX_RATE WHERE pair='\''EUR/KRW'\'' ORDER BY rate_date ASC",
    "dynamicConfig": "{\"chart_type\":\"line\",\"title\":\"EUR/KRW Exchange Rate Trend\",\"xAxis\":{\"field\":\"rate_date\",\"label\":\"Date\"},\"yAxis\":{\"label\":\"KRW per EUR\"},\"series\":[{\"name\":\"EUR/KRW\",\"valueField\":\"rate\"}]}"
  }'
  ok "WD_FX_TREND updated — now shows EUR/KRW on next refresh"
}

scenario_create() {
  step "SCENARIO: Create a new widget on the fly — no code change"
  post "api/v1/admin/widgets" '{
    "widgetId":      "WD_TRADE_SYMBOL",
    "targetDb":      "TARGET_DB",
    "querySql":      "SELECT symbol, SUM(notional) AS total_notional FROM TRADE_SUMMARY GROUP BY symbol ORDER BY total_notional DESC",
    "dynamicConfig": "{\"chart_type\":\"bar\",\"title\":\"Notional by Symbol\",\"xAxis\":{\"field\":\"symbol\",\"label\":\"Symbol\"},\"yAxis\":{\"label\":\"Notional (USD)\"},\"series\":[{\"name\":\"Notional\",\"valueField\":\"total_notional\"}]}"
  }'
  ok "WD_TRADE_SYMBOL created — appears immediately on dashboard"
}

scenario_delete() {
  step "SCENARIO: Permanently delete WD_TRADE_SYMBOL"
  delete "api/v1/admin/widgets/WD_TRADE_SYMBOL"
  ok "WD_TRADE_SYMBOL deleted — removed from WIDGET_MASTER"
}

# =============================================================================
# MAIN
# =============================================================================
if [[ "${1:-}" == "scenarios" ]]; then
  echo "Running lifecycle scenarios (assumes setup already done)..."
  scenario_deactivate
  scenario_activate
  scenario_update
  scenario_create
  scenario_delete
  echo
  echo "Done. All scenarios complete."
else
  echo "Dashboard Engine — Full Setup"
  echo "Backend: $BASE"
  seed_data
  register_widgets
  echo
  echo "Setup complete. Open the dashboard — all 6 widgets will render automatically."
  echo "Run './setup.sh scenarios' to demo lifecycle operations."
fi
