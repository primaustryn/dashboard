# Widget GitOps YAML 작성 가이드

이 폴더의 `.yml` 파일 하나가 대시보드 위젯 하나입니다.  
파일을 작성하고 `./setup-gitops.sh deploy-one <widgetId>` 를 실행하면 배포됩니다.

---

## 목차

1. [배포 흐름](#1-배포-흐름)
2. [파일명 규칙](#2-파일명-규칙)
3. [YAML 최상위 필드](#3-yaml-최상위-필드)
4. [uiSchema 공통 필드](#4-uischema-공통-필드)
5. [시각화 유형별 uiSchema](#5-시각화-유형별-uischema)
   - [comparison — 막대 차트](#51-comparison--막대-차트)
   - [proportion — 파이/도넛 차트](#52-proportion--파이도넛-차트)
   - [trend — 라인 차트](#53-trend--라인-차트)
   - [distribution — 히트맵](#54-distribution--히트맵)
   - [profile — 레이더 차트](#55-profile--레이더-차트)
   - [utilization — 게이지 차트](#56-utilization--게이지-차트)
   - [correlation — 버블/산점도 차트](#57-correlation--버블산점도-차트)
   - [hierarchy — 트리맵](#58-hierarchy--트리맵)
   - [geography — 지도](#59-geography--지도)
   - [ohlc — 캔들스틱 차트](#510-ohlc--캔들스틱-차트)
6. [priority 색상 팔레트](#6-priority-색상-팔레트)
7. [SQL 작성 규칙](#7-sql-작성-규칙)
8. [에러 응답 해석](#8-에러-응답-해석)
9. [등록된 위젯 목록](#9-등록된-위젯-목록)

---

## 1. 배포 흐름

```
.yml 파일 작성
      │
      ▼
1. Parse    — YAML 구문 파싱
      │
      ▼
2. Validate — 필수 필드, widgetId 형식, targetDb 등록 여부 검사
      │
      ▼
3. Dry-Run  — 실제 DB에 "SELECT * FROM ({sql}) WHERE 1=0" 실행 (데이터 미조회)
      │         ← 여기서 실패하면 HTTP 422 반환, DB에 아무것도 기록되지 않음
      ▼
4. Encode   — SQL과 uiSchema를 Base64 인코딩 후 4,000자 단위 청크 분할
      │
      ▼
5. Persist  — WIDGET_PAYLOAD 테이블에 원자적 저장 (같은 widgetId 재배포 가능)
      │
      ▼
      HTTP 201 Created
```

---

## 2. 파일명 규칙

- 파일명은 **`<widgetId>.yml`** 형식이어야 합니다.
- `widgetId` 필드 값과 파일명(확장자 제외)이 일치해야 합니다.

```
widgets/
├── WD_SALES_REGION.yml   ← widgetId: WD_SALES_REGION
├── WD_RISK_VAR.yml       ← widgetId: WD_RISK_VAR
└── WD_MY_WIDGET.yml      ← widgetId: WD_MY_WIDGET   ✅
    WD_MY_WIDGET.yml      ← widgetId: WD_OTHER_NAME  ❌ (불일치)
```

---

## 3. YAML 최상위 필드

| 필드 | 필수 | 설명 |
|------|------|------|
| `widgetId` | ✅ | 위젯 고유 ID. 대문자 영문 시작, 대문자/숫자/`_` 허용, 2~50자 |
| `targetDb` | ✅ | SQL을 실행할 데이터소스 키. 현재 등록된 값: `TARGET_DB` |
| `sql` | ✅ | 실행할 SELECT 문. `\|` block scalar로 작성 |
| `uiSchema` | ✅ | 시각화 설정 맵. 반드시 `visualization` 키 포함 |

### widgetId 규칙

```
[A-Z][A-Z0-9_]{1,49}
```

- 첫 글자: 대문자 영문자
- 이후: 대문자 영문자, 숫자, 언더스코어(`_`)
- 전체 길이: 2 ~ 50자

```yaml
widgetId: WD_SALES_REGION    ✅
widgetId: WD_RISK_2024       ✅
widgetId: wd_sales_region    ❌  (소문자 불가)
widgetId: WD-RISK            ❌  (하이픈 불가)
widgetId: W                  ❌  (1자 불가)
```

---

## 4. uiSchema 공통 필드

모든 시각화 유형에서 사용하는 공통 필드입니다.

| 필드 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `visualization` | ✅ | — | 시각화 유형 (아래 9가지 중 하나) |
| `title` | 권장 | — | 위젯 카드 상단 제목 |
| `priority` | ✗ | `medium` | 색상 팔레트: `critical` \| `high` \| `medium` \| `low` |

---

## 5. 시각화 유형별 uiSchema

### 5.1 comparison — 막대 차트

여러 항목의 수치를 막대로 비교합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `xAxis.field` | ✅ | X축 카테고리 컬럼명 |
| `xAxis.label` | ✗ | X축 레이블 |
| `yAxis.label` | ✗ | Y축 레이블 |
| `series[].name` | ✅ | 시리즈(범례) 이름 |
| `series[].valueField` | ✅ | Y축 수치 컬럼명 |

**예시:**

```yaml
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
```

---

### 5.2 proportion — 파이/도넛 차트

전체 대비 각 항목의 비율을 표시합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `nameField` | ✅ | 항목 이름 컬럼명 |
| `valueField` | ✅ | 비율 수치 컬럼명 |

**예시:**

```yaml
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
```

---

### 5.3 trend — 라인 차트

시간 흐름에 따른 추세를 표시합니다.  
X축은 보통 날짜/시간 컬럼이며, `ORDER BY` 는 시간 오름차순으로 작성하세요.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `xAxis.field` | ✅ | X축 시간 컬럼명 |
| `xAxis.label` | ✗ | X축 레이블 |
| `yAxis.label` | ✗ | Y축 레이블 |
| `series[].name` | ✅ | 시리즈 이름 |
| `series[].valueField` | ✅ | Y축 수치 컬럼명 |

여러 시리즈(멀티라인)가 필요하면 `series` 배열에 항목을 추가하세요.

**예시 (단일 라인):**

```yaml
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
```

**예시 (멀티라인):**

```yaml
sql: |
  SELECT trade_date, revenue, cost
  FROM   DAILY_PNL
  ORDER  BY trade_date ASC
uiSchema:
  visualization: trend
  title: Daily P&L
  xAxis:
    field: trade_date
    label: Date
  yAxis:
    label: Amount (USD)
  series:
    - name: Revenue
      valueField: revenue
    - name: Cost
      valueField: cost
```

---

### 5.4 distribution — 히트맵

두 범주 축의 교차점에서 수치 강도를 색상으로 표현합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `xField` | ✅ | X축 범주 컬럼명 |
| `yField` | ✅ | Y축 범주 컬럼명 |
| `valueField` | ✅ | 색상 강도 수치 컬럼명 |
| `xLabel` | ✗ | X축 레이블 |
| `yLabel` | ✗ | Y축 레이블 |

**예시:**

```yaml
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
```

---

### 5.5 profile — 레이더 차트

하나의 엔티티(포트폴리오, 부서 등)를 여러 평가 항목으로 시각화합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `nameField` | ✅ | 엔티티 이름 컬럼명 |
| `indicators[].name` | ✅ | 각 축의 레이블 |
| `indicators[].max` | ✅ | 각 축의 최대값 (척도 기준) |
| `valueFields[]` | ✅ | 각 축에 매핑되는 컬럼명 목록 (`indicators`와 순서 일치) |

`indicators`와 `valueFields`의 **항목 수와 순서가 반드시 일치**해야 합니다.

**예시:**

```yaml
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
```

---

### 5.6 utilization — 게이지 차트

한도 대비 현재 사용률을 게이지로 표시합니다. 리스크 한도 모니터링에 적합합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `nameField` | ✅ | 항목 이름 컬럼명 |
| `valueField` | ✅ | 현재 사용률 수치 컬럼명 |
| `max` | ✅ | 게이지 최대값 (한도 기준값) |
| `unit` | ✗ | 표시 단위 (예: `%`, `USD`) |

**예시:**

```yaml
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
```

---

### 5.7 correlation — 버블/산점도 차트

두 수치 변수의 상관관계를 표시합니다. `sizeField`로 버블 크기를 지정하면 버블 차트가 됩니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `xField` | ✅ | X축 수치 컬럼명 |
| `yField` | ✅ | Y축 수치 컬럼명 |
| `nameField` | ✅ | 점(버블) 레이블 컬럼명 |
| `sizeField` | ✗ | 버블 크기 수치 컬럼명 (없으면 동일 크기) |
| `xLabel` | ✗ | X축 레이블 |
| `yLabel` | ✗ | Y축 레이블 |

**예시:**

```yaml
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
```

---

### 5.8 hierarchy — 트리맵

계층적 구조의 크기 비율을 중첩 사각형으로 표시합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `nameField` | ✅ | 하위 항목 이름 컬럼명 |
| `valueField` | ✅ | 크기 수치 컬럼명 |
| `groupField` | ✗ | 상위 그룹 컬럼명 (그룹핑이 필요한 경우) |

**예시:**

```yaml
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
```

---

### 5.9 geography — 지도

국가/지역 코드 또는 이름으로 세계 지도에 수치를 색상으로 표시합니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `nameField` | ✅ | 국가명 또는 ISO 코드 컬럼명 |
| `valueField` | ✅ | 색상 강도 수치 컬럼명 |

**예시:**

```yaml
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
```

### 5.10 ohlc — 캔들스틱 차트

주가·환율 등 OHLC(시가/고가/저가/종가) 데이터를 캔들스틱으로 표시합니다.  
이동평균선(MA)과 거래량 서브차트를 함께 표시할 수 있습니다.

**추가 필드:**

| 필드 | 필수 | 설명 |
|------|------|------|
| `xAxis.field` | ✅ | 날짜/시간 컬럼명 |
| `openField` | ✅ | 시가 컬럼명 |
| `closeField` | ✅ | 종가 컬럼명 |
| `lowField` | ✅ | 저가 컬럼명 |
| `highField` | ✅ | 고가 컬럼명 |
| `volumeField` | ❌ | 거래량 컬럼명 (지정 시 하단 볼륨 차트 표시) |
| `maPeriods` | ❌ | 이동평균 기간 배열 (기본값: `[5, 20]`) |

**색상:**
- 양봉(상승): 청색(`#00d4ff`)
- 음봉(하락): 적색(`#f43f5e`)

**예시:**

```yaml
widgetId: WD_FX_CANDLE
targetDb: TARGET_DB
sql: |
  SELECT trade_date,
         open_price,
         close_price,
         low_price,
         high_price,
         trade_volume
  FROM   FX_OHLC_DAILY
  WHERE  pair      = 'USD/KRW'
    AND  trade_date >= ADD_MONTHS(SYSDATE, -3)
  ORDER  BY trade_date ASC
uiSchema:
  visualization: ohlc
  title:    "USD/KRW OHLC (최근 3개월)"
  priority: high
  xAxis:
    field: trade_date
    label: "거래일"
  yAxis:
    label: "환율 (KRW)"
  openField:   open_price
  closeField:  close_price
  lowField:    low_price
  highField:   high_price
  volumeField: trade_volume
  maPeriods:   [5, 20]
```

---

## 6. priority 색상 팔레트

| 값 | 색상 | 용도 |
|----|------|------|
| `critical` | 빨강 | 리스크 한도 초과 등 즉각 대응 필요 |
| `high` | 주황 | 당일 대응 필요 |
| `medium` | 파랑 (기본값) | 일반 모니터링 |
| `low` | 회색 | 참고용 정보 |

`priority` 미기재 시 `medium`이 적용됩니다.

---

## 7. SQL 작성 규칙

### 필수 사항

| 규칙 | 이유 |
|------|------|
| 단일 SELECT 문만 허용 | 세미콜론(`;`)이 포함되면 HTTP 400 반환 |
| DML 금지 (`INSERT`/`UPDATE`/`DELETE`) | dry-run이 `SELECT * FROM ({sql}) WHERE 1=0`으로 감싸기 때문에 구문 오류 발생 |
| 테이블/컬럼명 정확히 작성 | dry-run 시 실제 DB에서 검증. 오류 시 HTTP 422 반환 |

### 권장 사항

```sql
-- ✅ Good: 명시적 컬럼, ORDER BY, 별칭 사용
SELECT region,
       SUM(amount) AS total_amount
FROM   SALES_SUMMARY
GROUP  BY region
ORDER  BY total_amount DESC

-- ✅ 동적 파라미터: :paramName 형태 사용 (dry-run 시 NULL로 대체됨)
SELECT *
FROM   ORDERS
WHERE  desk = :deskFilter

-- ❌ Bad: SELECT * 는 uiSchema의 컬럼명과 불일치 위험
SELECT * FROM SALES_SUMMARY

-- ❌ Bad: 세미콜론 금지
SELECT region FROM SALES_SUMMARY;
```

### 한글/특수문자

SQL 내 한글 리터럴은 그대로 사용 가능합니다.  
배포 시 Base64 인코딩으로 처리되므로 멀티바이트 문자가 분리되지 않습니다.

```sql
SELECT desk_name, amount
FROM   TRADE_DESK
WHERE  desk_name = '서울데스크'
```

---

## 8. 에러 응답 해석

| HTTP 코드 | 원인 | 해결 방법 |
|-----------|------|-----------|
| `201 Created` | 정상 배포 | — |
| `400 Bad Request` | YAML 구문 오류, 필수 필드 누락, `widgetId` 형식 위반, 미등록 `targetDb`, `visualization` 누락, 세미콜론 포함 | 응답 `detail` 메시지 확인 |
| `422 Unprocessable Entity` | SQL dry-run 실패 (테이블 없음, 컬럼명 오류 등) | 응답 `detail`의 DB 오류 메시지 확인 후 SQL 수정 |
| `500 Internal Server Error` | 서버 내부 오류 | 서버 로그 확인 |

**응답 예시 (400):**

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "widgetId must be 2–50 uppercase alphanumeric/underscore characters; got: 'wd_my_widget'"
}
```

**응답 예시 (422):**

```json
{
  "status": 422,
  "title": "SQL Dry-Run Failed",
  "detail": "Widget [WD_MY_WIDGET]: Table \"MY_TABLE\" not found; SQL statement: ..."
}
```

---

## 9. 등록된 위젯 목록

| 파일 | widgetId | visualization | priority |
|------|----------|---------------|----------|
| WD_SALES_REGION.yml | WD_SALES_REGION | comparison | medium |
| WD_SALES_PRODUCT.yml | WD_SALES_PRODUCT | proportion | medium |
| WD_SALES_TREND.yml | WD_SALES_TREND | trend | medium |
| WD_TRADE_DESK.yml | WD_TRADE_DESK | proportion | medium |
| WD_RISK_VAR.yml | WD_RISK_VAR | comparison | high |
| WD_FX_TREND.yml | WD_FX_TREND | trend | medium |
| WD_RISK_HEATMAP.yml | WD_RISK_HEATMAP | distribution | high |
| WD_PORTFOLIO_RADAR.yml | WD_PORTFOLIO_RADAR | profile | medium |
| WD_VAR_GAUGE.yml | WD_VAR_GAUGE | utilization | critical |
| WD_ASSET_SCATTER.yml | WD_ASSET_SCATTER | correlation | medium |
| WD_AUM_TREEMAP.yml | WD_AUM_TREEMAP | hierarchy | medium |
| WD_GLOBAL_MAP.yml | WD_GLOBAL_MAP | geography | medium |
| WD_FX_CANDLE.yml | WD_FX_CANDLE | ohlc | high |
