# 프론트엔드 스타일링 가이드

이 문서는 대시보드 위젯의 CSS/스타일을 수정하려는 프론트엔드 개발자를 위한 가이드입니다.

---

## 목차

1. [역할 분리: YAML vs 프론트엔드 코드](#1-역할-분리-yaml-vs-프론트엔드-코드)
2. [스타일링 레이어 구조](#2-스타일링-레이어-구조)
3. [Layer 1 — 전역 CSS](#3-layer-1--전역-css-indexcss)
4. [Layer 2 — 카드·레이아웃](#4-layer-2--카드레이아웃-dashboardboardjsx)
5. [Layer 3 — 차트 캔버스 크기](#5-layer-3--차트-캔버스-크기-echartswrapperjsx)
6. [Layer 4 — ECharts 옵션 (차트 내부 스타일)](#6-layer-4--echarts-옵션-차트-내부-스타일)
7. [priority 팔레트 활성화](#7-priority-팔레트-활성화)
8. [빌드 및 반영](#8-빌드-및-반영)
9. [새 차트 유형 추가](#9-새-차트-유형-추가)

---

## 1. 역할 분리: YAML vs 프론트엔드 코드

위젯 시스템은 **"무엇을 그릴지"** 와 **"어떻게 그릴지"** 를 명확히 분리합니다.

```
┌─────────────────────────────────────────────────────────────────────┐
│  YAML (widgets/*.yml)          │  프론트엔드 코드 (src/)            │
│  데이터/기획자 영역             │  개발자 영역                        │
├────────────────────────────────┼────────────────────────────────────┤
│  "어떤 차트 유형을 쓸지"        │  "그 차트가 실제로 어떻게 보일지"  │
│  "어떤 DB 컬럼을 어디에 쓸지"  │  "색상, 폰트, 애니메이션, 레이아웃" │
│  "제목이 무엇인지"              │  "캔버스 크기, 그리드 여백"         │
│  "우선순위가 무엇인지"          │  "우선순위 색상 팔레트 구현"        │
└────────────────────────────────┴────────────────────────────────────┘
```

### YAML `visualization` 필드의 역할

`visualization` 값은 `WidgetRenderer.jsx`의 `SEMANTIC_REGISTRY`에서 **React 컴포넌트를 선택하는 키**입니다.
데이터의 분석적 의도를 선언할 뿐, 시각적 스타일을 직접 제어하지 않습니다.

```
YAML                       WidgetRenderer.jsx              차트 컴포넌트
─────                      ──────────────────              ────────────
visualization: comparison  → SEMANTIC_REGISTRY['comparison'] → BarChart.jsx
visualization: trend       → SEMANTIC_REGISTRY['trend']       → LineChart.jsx
visualization: utilization → SEMANTIC_REGISTRY['utilization'] → GaugeChart.jsx
```

### 프론트엔드 개발자가 직접 제어하는 영역

| 항목 | 파일 |
|------|------|
| 전역 배경, 폰트, hover 효과 | `src/index.css` |
| 카드 배경·border·그리드 열 수·간격 | `src/pages/DashboardBoard.jsx` |
| 차트 캔버스 기본 높이/너비 | `src/components/EChartsWrapper.jsx` |
| 막대 색상·그라데이션·툴팁·축 스타일 | `src/components/charts/BarChart.jsx` |
| 라인 색상·면적 그라데이션·마커 | `src/components/charts/LineChart.jsx` |
| 게이지 임계값 색상 전환 기준 (60%/85%) | `src/components/charts/GaugeChart.jsx` |
| 파이 반경·도넛 두께·라벨 위치 | `src/components/charts/PieChart.jsx` |
| 히트맵 색상 구간 | `src/components/charts/HeatmapChart.jsx` |
| 레이더 면적 색상·투명도 | `src/components/charts/RadarChart.jsx` |
| 버블 크기 스케일 | `src/components/charts/ScatterChart.jsx` |
| 트리맵 텍스트 크기·색상 | `src/components/charts/TreemapChart.jsx` |
| 지도 색상 범위 | `src/components/charts/GeoChart.jsx` |
| priority 팔레트 색상값 | `src/components/WidgetRenderer.jsx` |

### YAML 작성자가 줄 수 있는 힌트 (프론트엔드 코드가 구현해야 효과가 있음)

| YAML 필드 | 의미 | 프론트엔드에서 구현 위치 |
|-----------|------|--------------------------|
| `priority: critical\|high\|medium\|low` | 색상 팔레트 힌트 | `WidgetRenderer.jsx` → `PRIORITY_PALETTES` → 각 차트의 `colors` prop |
| `title: "..."` | 차트 상단 제목 텍스트 | 각 차트의 `option.title.text` |
| `xAxis.label` / `yAxis.label` | 축 레이블 텍스트 | 각 차트의 `option.xAxis.name` |

`priority` 는 `WidgetRenderer`가 `colors` 배열로 변환해 차트에 전달하지만,
**각 차트 컴포넌트가 `colors` prop을 실제로 사용하도록 코딩되어 있어야** 시각적으로 반영됩니다.
현재 모든 차트는 자체 색상 상수(`GRADIENTS`, `LINE_COLORS`, `PALETTE`)를 사용하고 있습니다.

---

## 2. 스타일링 레이어 구조

```
┌─ src/index.css                          Layer 1: 전역 CSS
│   body, .widget-card:hover
│
├─ src/pages/DashboardBoard.jsx           Layer 2: 카드 껍데기 · 그리드 레이아웃
│   const styles = { card, grid, ... }
│
├─ src/components/EChartsWrapper.jsx      Layer 3: 차트 캔버스 크기
│   height: '420px'  ← 기본값
│
└─ src/components/charts/*.jsx            Layer 4: ECharts option 객체
    색상 상수, 툴팁, 축 스타일, 애니메이션
```

---

## 3. Layer 1 — 전역 CSS (`index.css`)

전역 폰트, 배경색, `.widget-card` hover 효과를 변경합니다.

```css
/* src/index.css */

body {
  font-family: 'Pretendard', -apple-system, sans-serif; /* 폰트 교체 */
  background: #0a0f1e;
}

/* 카드 hover 색상 변경 */
.widget-card:hover {
  border-color: rgba(168, 85, 247, 0.4) !important;
  box-shadow: 0 8px 40px rgba(168,85,247,0.3) !important;
}
```

---

## 4. Layer 2 — 카드·레이아웃 (`DashboardBoard.jsx`)

파일 하단 `styles` 객체에서 카드 배경, border, 그리드 열 수, 간격을 수정합니다.

### 그리드 열 수 / 간격

```js
// src/pages/DashboardBoard.jsx
grid: {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(420px, 1fr))', // 현재 580px → 더 좁게
  gap: '16px',                                                   // 현재 24px → 좁게
},
```

### 카드 배경 / border / 그림자

```js
card: {
  background: '#0d1b2a',
  borderRadius: '8px',
  border: '1px solid #1e3a5f',
  boxShadow: '0 2px 12px rgba(0,0,0,0.4)',
  overflow: 'hidden',
  transition: 'border-color 0.2s ease, box-shadow 0.2s ease',
},
```

### 특정 widgetId에만 다른 스타일 적용

```jsx
// DashboardBoard.jsx — WidgetCard 함수 내부
function WidgetCard({ widgetId, params }) {
  const isCritical = widgetId === 'WD_VAR_GAUGE'

  return (
    <div
      className="widget-card"
      style={{
        ...styles.card,
        ...(isCritical && {
          border: '1px solid #ef444466',
          boxShadow: '0 0 20px #ef444422',
        }),
      }}
    >
      {/* ... */}
    </div>
  )
}
```

---

## 5. Layer 3 — 차트 캔버스 크기 (`EChartsWrapper.jsx`)

기본 높이 `420px`는 `style` prop으로 덮어쓸 수 있습니다.

```jsx
// src/components/EChartsWrapper.jsx
export default function EChartsWrapper({ option, style }) {
  const mergedStyle = { height: '420px', width: '100%', ...style }
  // ...
}
```

특정 차트만 높이를 바꾸려면 해당 차트 파일 마지막 줄을 수정합니다.

```jsx
// src/components/charts/GaugeChart.jsx
return <EChartsWrapper option={option} style={{ height: '320px' }} />

// src/components/charts/BarChart.jsx
return <EChartsWrapper option={option} style={{ height: '500px' }} />
```

---

## 6. Layer 4 — ECharts 옵션 (차트 내부 스타일)

각 `charts/*.jsx` 파일 상단의 색상 상수와 `option` 객체 안에서 세부 스타일을 제어합니다.

### 공통 수정 포인트

| 수정 위치 | 역할 | 예시 속성 |
|-----------|------|-----------|
| 파일 상단 색상 상수 | 시리즈 색상 팔레트 | `GRADIENTS`, `LINE_COLORS`, `PALETTE` |
| `option.title.textStyle` | 차트 제목 폰트/색상 | `color`, `fontSize`, `fontWeight` |
| `option.tooltip` | 툴팁 배경·폰트 | `backgroundColor`, `borderColor`, `textStyle` |
| `option.grid` | 차트 내부 여백 | `left`, `right`, `top`, `bottom` |
| `option.xAxis.axisLabel` | X축 텍스트 스타일 | `color`, `fontSize`, `rotate` |
| `option.yAxis.splitLine` | Y축 격자선 스타일 | `lineStyle.color`, `lineStyle.type` |
| `seriesItems[].itemStyle` | 데이터 포인트 스타일 | `borderRadius`, `shadowBlur`, `color` |

### BarChart — 막대 색상을 단색으로 변경

```jsx
// src/components/charts/BarChart.jsx

// 기존: 그라데이션
itemStyle: {
  color: params => barGradient(GRADIENTS[params.dataIndex % GRADIENTS.length]),
  borderRadius: [6, 6, 0, 0],
}

// 변경: 단색
itemStyle: {
  color: '#3b82f6',
  borderRadius: [4, 4, 0, 0],
}
```

### BarChart — 평균선 제거

```jsx
// src/components/charts/BarChart.jsx
// markLine 블록 전체를 삭제하면 평균선이 사라집니다.
if (seriesItems[0]) {
  seriesItems[0].markLine = { ... }  // ← 이 블록 삭제
}
```

### LineChart — 면적 채우기 제거 (라인만 표시)

```jsx
// src/components/charts/LineChart.jsx
// areaStyle 줄을 제거합니다.
areaStyle: { color: areaGradient(color) },  // ← 이 줄 삭제
```

### GaugeChart — 색상 임계값 기준 변경

```jsx
// src/components/charts/GaugeChart.jsx

// 현재: 60% 미만 파랑, 85% 미만 노랑, 이상 빨강
function arcColor(ratio) {
  if (ratio < 0.6)  return '#00d4ff'
  if (ratio < 0.85) return '#fbbf24'
  return '#f43f5e'
}

// 변경 예시: 80% 미만 초록, 이상 빨강 (2단계)
function arcColor(ratio) {
  if (ratio < 0.8) return '#00ffaa'
  return '#f43f5e'
}
```

### PieChart — 도넛 두께 조정

```jsx
// src/components/charts/PieChart.jsx
series: [{
  type: 'pie',
  radius: ['42%', '68%'],  // ← ['내경', '외경'] 비율 조정
  // radius: ['0%', '68%'] 로 하면 일반 파이 차트
}]
```

---

## 7. priority 팔레트 활성화

현재 `WidgetRenderer.jsx`는 `priority` → `colors` 배열 변환 후 차트에 전달하지만,
각 차트 컴포넌트가 `colors` prop을 받지 않아 시각적 효과가 없습니다.
아래 두 단계로 연동할 수 있습니다.

**Step 1 — `WidgetRenderer.jsx` 확인** (이미 전달 중, 수정 불필요)

```jsx
// src/components/WidgetRenderer.jsx:116
return <ChartComponent uiSchema={uiSchema} data={data} colors={colors} />
```

**Step 2 — 원하는 차트 컴포넌트에서 `colors` prop 수신 및 적용**

```jsx
// src/components/charts/BarChart.jsx
export default function BarChart({ uiSchema, data, colors }) {
  //                                               ↑ prop 추가

  const seriesItems = (uiSchema.series ?? []).map((s, si) => ({
    // ...
    itemStyle: {
      color: params => colors[params.dataIndex % colors.length], // ← GRADIENTS 대신 colors 사용
      borderRadius: [6, 6, 0, 0],
    },
  }))
}
```

**priority 팔레트 색상값 변경**

```jsx
// src/components/WidgetRenderer.jsx
const PRIORITY_PALETTES = {
  critical: ['#ef4444', '#f87171', '#fca5a5', '#fee2e2'],
  high:     ['#f97316', '#fb923c', '#fdba74', '#ffedd5'],
  medium:   ['#3b82f6', '#60a5fa', '#93c5fd', '#dbeafe'], // 기본값
  low:      ['#6b7280', '#9ca3af', '#d1d5db', '#f3f4f6'],
}
```

---

## 8. 빌드 및 반영

소스 수정 후 빌드해야 `src/main/resources/static/` 에 반영됩니다.

```bash
cd frontend
npm run build      # 빌드 → ../src/main/resources/static/ 에 출력
```

개발 중에는 Vite dev 서버를 사용하면 핫 리로드가 됩니다.

```bash
cd frontend
npm run dev        # http://localhost:5173 에서 즉시 확인
```

> **주의**: `npm run dev` 실행 시 API 요청은 백엔드(`./run.sh`)가 함께 실행 중이어야 합니다.
> Vite 프록시 설정은 `frontend/vite.config.js` 에서 확인하세요.

---

## 9. 새 차트 유형 추가

새로운 `visualization` 키와 차트 컴포넌트를 추가하는 방법입니다.  
기존 9개 차트는 모두 이 패턴으로 만들어졌습니다.

### 흐름

```
Step 1.  src/components/charts/NewChart.jsx 생성   ← ECharts option 구현
Step 2.  src/components/WidgetRenderer.jsx 수정    ← SEMANTIC_REGISTRY에 등록
Step 3.  widgets/WD_NEW.yml 작성 + 배포            ← 새 visualization 키 사용
Step 4.  cd frontend && npm run build
```

---

### Step 1 — 차트 컴포넌트 생성

모든 차트 컴포넌트의 구조는 동일합니다.

```
props 수신 ({ uiSchema, data, colors })
      ↓
data → ECharts 형식으로 변환
      ↓
option 객체 구성
      ↓
<EChartsWrapper option={option} /> 반환
```

예시: 캔들스틱 차트 (OHLC) — `src/components/charts/CandlestickChart.jsx`

```jsx
import EChartsWrapper from '../EChartsWrapper'
import { escapeHtml as esc } from '../../utils/html'

export default function CandlestickChart({ uiSchema, data }) {
  const { dateField, openField, closeField, lowField, highField } = uiSchema

  const dates      = data.map(row => row[dateField])
  const ohlcValues = data.map(row => [
    row[openField],
    row[closeField],
    row[lowField],
    row[highField],
  ])

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title,
      left: 'center', top: 10,
      textStyle: { color: '#e0e8ff', fontSize: 15, fontWeight: 700 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor: '#00d4ff33', borderWidth: 1, padding: [10, 14],
      textStyle: { color: '#c0d0e8', fontSize: 12 },
      formatter: params => {
        const p = params[0]
        const [o, c, l, h] = p.data
        const color = c >= o ? '#00ffaa' : '#f43f5e'
        return `
          <div style="font-weight:700;color:#00d4ff;margin-bottom:6px">${esc(p.name)}</div>
          <div>Open:  <b style="color:${color}">${o}</b></div>
          <div>Close: <b style="color:${color}">${c}</b></div>
          <div>Low:   <b style="color:#607898">${l}</b></div>
          <div>High:  <b style="color:#607898">${h}</b></div>
        `
      },
    },
    grid: { left: '2%', right: '2%', top: '18%', bottom: '12%', containLabel: true },
    xAxis: {
      type: 'category', data: dates,
      axisLabel: { color: '#607898', fontSize: 10, rotate: 20 },
      axisLine:  { lineStyle: { color: '#1a2d4d' } },
      axisTick:  { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#607898', fontSize: 11 },
      splitLine: { lineStyle: { color: '#111e33', type: 'dashed' } },
      axisLine:  { show: false },
      axisTick:  { show: false },
    },
    series: [{
      type: 'candlestick',
      data: ohlcValues,
      itemStyle: {
        color:        '#00ffaa',   // 상승 캔들 (close > open)
        color0:       '#f43f5e',   // 하락 캔들 (close < open)
        borderColor:  '#00ffaa',
        borderColor0: '#f43f5e',
      },
    }],
  }

  return <EChartsWrapper option={option} />
}
```

**uiSchema 필드는 컴포넌트 작성자가 자유롭게 정의합니다.**  
백엔드는 `visualization` 키 존재 여부만 검증하므로, 나머지 구조에는 제약이 없습니다.

---

### Step 2 — WidgetRenderer.jsx에 등록

```jsx
// src/components/WidgetRenderer.jsx

// 1. import 추가
import CandlestickChart from './charts/CandlestickChart'   // ← 추가

// 2. SEMANTIC_REGISTRY에 키 등록
const SEMANTIC_REGISTRY = {
  comparison:   BarChart,
  proportion:   PieChart,
  trend:        LineChart,
  distribution: HeatmapChart,
  profile:      RadarChart,
  utilization:  GaugeChart,
  correlation:  ScatterChart,
  hierarchy:    TreemapChart,
  geography:    GeoChart,
  ohlc:         CandlestickChart,   // ← 추가 (키 이름 = YAML의 visualization 값)
}
```

---

### Step 3 — YAML 작성 및 배포

```yaml
# widgets/WD_FX_CANDLE.yml
widgetId: WD_FX_CANDLE
targetDb: TARGET_DB
sql: |
  SELECT trade_date,
         open_rate,
         close_rate,
         low_rate,
         high_rate
  FROM   FX_OHLC
  WHERE  pair = 'USD/KRW'
  ORDER  BY trade_date ASC
uiSchema:
  visualization: ohlc            # ← SEMANTIC_REGISTRY의 키와 반드시 일치
  title: "USD/KRW Daily OHLC"
  priority: medium
  dateField:  trade_date
  openField:  open_rate
  closeField: close_rate
  lowField:   low_rate
  highField:  high_rate
```

```bash
./setup-gitops.sh deploy-one WD_FX_CANDLE
```

---

### 체크리스트

```
□ src/components/charts/NewChart.jsx 생성
    ↳ props: { uiSchema, data, colors }
    ↳ 반환:  <EChartsWrapper option={...} />

□ src/components/WidgetRenderer.jsx 수정
    ↳ import NewChart from './charts/NewChart'
    ↳ SEMANTIC_REGISTRY에 { 새키: NewChart } 추가

□ widgets/WD_NEW.yml 작성
    ↳ visualization: 새키   (SEMANTIC_REGISTRY 키와 일치)
    ↳ uiSchema에 컴포넌트가 읽는 필드 정의

□ 빌드 및 배포
    ↳ cd frontend && npm run build
    ↳ ./setup-gitops.sh deploy-one WD_NEW
```

### 참고 — ECharts가 아닌 라이브러리 사용

`EChartsWrapper` 대신 직접 JSX를 반환해도 됩니다.  
`WidgetRenderer`는 컴포넌트를 선택하고 `uiSchema`·`data`·`colors`만 전달하므로 내부 구현에 제약이 없습니다.

```jsx
// D3, Recharts 등 다른 라이브러리도 동일한 패턴으로 사용 가능
export default function CustomChart({ uiSchema, data }) {
  return (
    <div style={{ padding: '20px' }}>
      {/* 자유롭게 구현 */}
    </div>
  )
}
```
