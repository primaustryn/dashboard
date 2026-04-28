import EChartsWrapper from '../EChartsWrapper'
import { escapeHtml as esc } from '../../utils/html'

const RISE_COLOR = '#00d4ff'   // 상승 캔들 (양봉)
const FALL_COLOR = '#f43f5e'   // 하락 캔들 (음봉)
const VOL_RISE   = '#00d4ff44'
const VOL_FALL   = '#f43f5e44'
const MA_COLORS  = ['#fbbf24', '#a855f7', '#34d399']

function calcMA(data, period) {
  return data.map((_, i) => {
    if (i < period - 1) return null
    const slice = data.slice(i - period + 1, i + 1)
    const sum   = slice.reduce((acc, v) => acc + v, 0)
    return +(sum / period).toFixed(4)
  })
}

function fmtNum(v) {
  if (v == null) return ''
  return Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

export default function CandlestickChart({ uiSchema, data }) {
  const xField     = uiSchema.xAxis?.field
  const openField  = uiSchema.openField
  const closeField = uiSchema.closeField
  const lowField   = uiSchema.lowField
  const highField  = uiSchema.highField
  const volField   = uiSchema.volumeField
  const maPeriods  = uiSchema.maPeriods ?? [5, 20]  // 기본 MA5, MA20

  const xData    = data.map(r => r[xField])
  // ECharts candlestick data order: [open, close, low, high]
  const ohlcData = data.map(r => [
    Number(r[openField]),
    Number(r[closeField]),
    Number(r[lowField]),
    Number(r[highField]),
  ])
  const closeVals = data.map(r => Number(r[closeField]))
  const volData   = volField ? data.map(r => Number(r[volField])) : null

  const hasVolume = !!volData

  // Moving Average series
  const maSeries = maPeriods.map((period, i) => ({
    name:        `MA${period}`,
    type:        'line',
    data:        calcMA(closeVals, period),
    smooth:      true,
    symbol:      'none',
    lineStyle:   { color: MA_COLORS[i % MA_COLORS.length], width: 1.5, opacity: 0.8 },
    xAxisIndex:  0,
    yAxisIndex:  0,
  }))

  const series = [
    {
      name:       'OHLC',
      type:       'candlestick',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data:       ohlcData,
      itemStyle: {
        color:        RISE_COLOR,
        color0:       FALL_COLOR,
        borderColor:  RISE_COLOR,
        borderColor0: FALL_COLOR,
        borderWidth:  1.5,
      },
      emphasis: {
        itemStyle: {
          shadowBlur:  12,
          shadowColor: RISE_COLOR + '66',
        },
      },
    },
    ...maSeries,
    ...(hasVolume ? [{
      name:       'Volume',
      type:       'bar',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data:       volData.map((v, i) => ({
        value: v,
        itemStyle: {
          color: ohlcData[i][1] >= ohlcData[i][0] ? VOL_RISE : VOL_FALL,
        },
      })),
      barMaxWidth: 12,
    }] : []),
  ]

  const gridMain = hasVolume
    ? { left: '2%', right: '2%', top: '18%', bottom: '30%', containLabel: true }
    : { left: '2%', right: '2%', top: '18%', bottom: '12%', containLabel: true }

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: uiSchema.title,
      left: 'center', top: 10,
      textStyle: {
        color: '#e0e8ff', fontSize: 15, fontWeight: 700,
        textShadowBlur: 8, textShadowColor: '#00d4ff44',
      },
    },
    tooltip: {
      trigger:         'axis',
      axisPointer:     { type: 'cross', crossStyle: { color: '#1a2d4d' } },
      backgroundColor: 'rgba(7,12,24,0.95)',
      borderColor:     '#00d4ff33',
      borderWidth:     1,
      padding:         [10, 14],
      textStyle:       { color: '#c0d0e8', fontSize: 12 },
      formatter: params => {
        const date  = esc(params[0]?.axisValue ?? '')
        const ohlc  = params.find(p => p.seriesName === 'OHLC')
        const vol   = params.find(p => p.seriesName === 'Volume')
        const mas   = params.filter(p => p.seriesName.startsWith('MA'))

        const isRise = ohlc && ohlc.value[1] >= ohlc.value[0]
        const bodyColor = isRise ? RISE_COLOR : FALL_COLOR

        let html = `<div style="font-weight:700;color:${bodyColor};font-size:13px;margin-bottom:8px">${date}</div>`
        if (ohlc) {
          html += `
            <div style="display:grid;grid-template-columns:auto auto;gap:2px 16px;margin-bottom:6px">
              <span style="color:#607898">Open</span>  <b style="color:#fff">${fmtNum(ohlc.value[0])}</b>
              <span style="color:#607898">Close</span> <b style="color:${bodyColor}">${fmtNum(ohlc.value[1])}</b>
              <span style="color:#607898">Low</span>   <b style="color:#fff">${fmtNum(ohlc.value[2])}</b>
              <span style="color:#607898">High</span>  <b style="color:#fff">${fmtNum(ohlc.value[3])}</b>
            </div>`
        }
        mas.forEach(m => {
          html += `<div style="margin:2px 0">${m.marker}${esc(m.seriesName)}: <b style="color:#fff">${fmtNum(m.value)}</b></div>`
        })
        if (vol) {
          html += `<div style="margin-top:4px;color:#607898">Volume: <b style="color:#fff">${Number(vol.value).toLocaleString()}</b></div>`
        }
        return html
      },
    },
    legend: {
      data:      ['OHLC', ...maPeriods.map(p => `MA${p}`), ...(hasVolume ? ['Volume'] : [])],
      bottom:    4,
      textStyle: { color: '#607898', fontSize: 11 },
      inactiveColor: '#2a3a55',
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    dataZoom: [
      {
        type:       'inside',
        xAxisIndex: hasVolume ? [0, 1] : [0],
        start:      60,
        end:        100,
      },
      {
        type:        'slider',
        xAxisIndex:  hasVolume ? [0, 1] : [0],
        bottom:      hasVolume ? '21%' : '4%',
        height:      18,
        borderColor: '#1a2d4d',
        fillerColor: '#00d4ff1a',
        handleStyle: { color: '#00d4ff' },
        textStyle:   { color: '#607898', fontSize: 10 },
      },
    ],
    grid: [
      gridMain,
      ...(hasVolume ? [{
        left: '2%', right: '2%', bottom: '6%', height: '18%', containLabel: true,
      }] : []),
    ],
    xAxis: [
      {
        type:          'category',
        data:          xData,
        name:          uiSchema.xAxis?.label,
        nameTextStyle: { color: '#607898', fontSize: 10 },
        axisLabel:     { color: '#607898', fontSize: 10, rotate: 20 },
        axisLine:      { lineStyle: { color: '#1a2d4d' } },
        axisTick:      { show: false },
        splitLine:     { show: false },
        gridIndex:     0,
      },
      // Volume sub-chart needs its own xAxis in grid[1]
      ...(hasVolume ? [{
        type:      'category',
        data:      xData,
        gridIndex: 1,
        axisLabel: { show: false },
        axisLine:  { lineStyle: { color: '#1a2d4d' } },
        axisTick:  { show: false },
        splitLine: { show: false },
      }] : []),
    ],
    yAxis: [
      {
        type:          'value',
        name:          uiSchema.yAxis?.label ?? 'Price',
        nameTextStyle: { color: '#607898', fontSize: 10 },
        axisLabel:     { color: '#607898', fontSize: 10, formatter: fmtNum },
        axisLine:      { show: false },
        axisTick:      { show: false },
        splitLine:     { lineStyle: { color: '#111e33', type: 'dashed' } },
        gridIndex:     0,
        scale:         true,
      },
      ...(hasVolume ? [{
        type:       'value',
        gridIndex:  1,
        axisLabel:  { show: false },
        axisLine:   { show: false },
        axisTick:   { show: false },
        splitLine:  { show: false },
      }] : []),
    ],
    series,
  }

  return <EChartsWrapper option={option} style={{ height: hasVolume ? '500px' : '420px' }} />
}
