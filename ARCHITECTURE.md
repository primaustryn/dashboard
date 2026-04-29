# 서비스 로직 구성도

## 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CLIENT (Frontend / SPA)                          │
└────────────────────┬──────────────────────┬────────────────────────────────┘
                     │                      │
          ┌──────────▼──────────┐  ┌────────▼───────────────────────────────┐
          │   일반 API 요청       │  │        GitOps 배포 / 관리 API           │
          └──────────┬──────────┘  └────────┬───────────────────────────────┘
                     │                      │
┌────────────────────▼──────────────────────▼────────────────────────────────┐
│                          INFRASTRUCTURE LAYER                               │
│                                                                             │
│  SecurityHeaderFilter          AuditInterceptor          WebConfig(CORS)   │
│  (X-Frame-Options 등 5종)      (AUDIT_LOG 기록)           (Vite :5173)      │
└────────────────────┬──────────────────────┬────────────────────────────────┘
                     │                      │
┌────────────────────▼──────────────────────▼────────────────────────────────┐
│                            CONTROLLER LAYER                                 │
│                                                                             │
│  ┌─────────────────────┐  ┌──────────────────┐  ┌────────────────────────┐ │
│  │WidgetEngineController│  │WidgetDeployControl│  │WidgetAdminController   │ │
│  │GET /api/v1/widgets/  │  │POST /api/v1/admin │  │POST/PUT/DELETE         │ │
│  │      {id}/data       │  │/widgets/deploy    │  │/api/v1/admin/widgets   │ │
│  └──────────┬──────────┘  └────────┬─────────┘  └──────────┬─────────────┘ │
│             │                      │                        │               │
│  ┌──────────▼──────────┐           │             ┌──────────▼─────────────┐ │
│  │GenericDataController│           │             │  SalesDataController   │ │
│  │/api/v1/tables/{tbl} │           │             │  /api/v1/sales         │ │
│  └──────────┬──────────┘           │             └──────────┬─────────────┘ │
└─────────────┼──────────────────────┼────────────────────────┼───────────────┘
              │                      │                        │
┌─────────────▼──────────────────────▼────────────────────────▼───────────────┐
│                             SERVICE LAYER                                    │
│                                                                              │
│  ┌──────────────────────────┐   ┌──────────────────────────────────────────┐ │
│  │   WidgetEngineService    │   │         WidgetDeployService              │ │
│  │                          │   │                                          │ │
│  │  1. fetchCachedMeta()    │   │  1. Parse  (SnakeYAML)                   │ │
│  │     Redis → meta-DB      │   │  2. Validate (required fields, format)   │ │
│  │  2. parseUiSchema()      │   │  3. Dry-Run (SELECT * FROM (...) WHERE   │ │
│  │  3. resolveDataSource()  │   │             1=0 → syntax gate)           │ │
│  │  4. fetchCachedData()    │   │  4. Cost gate (EXPLAIN PLAN ≤ MAX_COST)  │ │
│  │     Redis → Target DB    │   │  5. Encode  (Base64 → 4KB chunks)        │ │
│  │  5. MDC widgetId 주입    │   │  6. Persist (WIDGET_PAYLOAD, UPSERT)     │ │
│  └──────────────────────────┘   │  7. Evict   (after TX commit → Redis)   │ │
│                                  └──────────────────────────────────────────┘ │
│  ┌───────────────────────┐   ┌──────────────────────┐  ┌───────────────────┐ │
│  │WidgetDefinitionService│   │  GenericDataService  │  │ SalesDataService  │ │
│  │(CRUD + audit 연동)     │   │  (table 동적 DML)    │  │ (sales 전용 DML)  │ │
│  └───────────────────────┘   └──────────────────────┘  └───────────────────┘ │
└──────────┬───────────────────────────┬──────────────────────────┬────────────┘
           │                           │                          │
┌──────────▼───────────────────────────▼──────────────────────────▼────────────┐
│                               DAO LAYER                                       │
│                                                                               │
│  ┌────────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐ │
│  │WidgetPayloadDao│  │DynamicWidgetDao│  │WidgetDefinition│  │WidgetAuditDao │ │
│  │(Base64 chunk   │  │(Target DB 실행 │  │     Dao        │  │(lifecycle     │ │
│  │ 조립/저장)      │  │ timeout 30s   │  │(CRUD + 삭제순서 │  │ 감사 기록)    │ │
│  │                │  │ max 10,000행) │  │ 보장)          │  │               │ │
│  └────────────────┘  └───────────────┘  └───────────────┘  └───────────────┘ │
│  ┌────────────────┐  ┌───────────────┐                                        │
│  │  GenericRowDao │  │ SalesRecordDao│                                        │
│  │(식별자 검증 +   │  │(ISO-8601 날짜 │                                        │
│  │ 테이블 존재확인) │  │ 변환)         │                                        │
│  └────────────────┘  └───────────────┘                                        │
└──────────┬────────────────────────────────────────────────────────────────────┘
           │
┌──────────▼────────────────────────────────────────────────────────────────────┐
│                            PERSISTENCE / EXTERNAL                             │
│                                                                               │
│   ┌───────────────────────────┐        ┌──────────────────────────────────┐   │
│   │       META DB (H2)        │        │         TARGET DB (H2/Oracle)    │   │
│   │                           │        │                                  │   │
│   │  WIDGET_MASTER            │        │  SALES_SUMMARY                   │   │
│   │  WIDGET_PAYLOAD  ◄──────┐ │        │  TRADE_SUMMARY                   │   │
│   │  WIDGET_QUERY (legacy)  │ │        │  RISK_SUMMARY / RISK_MATRIX      │   │
│   │  WIDGET_CONFIG (legacy) │ │        │  FX_RATE / FX_OHLC_DAILY         │   │
│   │  WIDGET_AUDIT           │ │        │  PORTFOLIO_SCORES / AUM          │   │
│   │  AUDIT_LOG              │ │        │  GLOBAL_EXPOSURE                 │   │
│   └───────────────────────────┘        └──────────────────────────────────┘   │
│                                                                               │
│   ┌───────────────────────────────────────────────────────────────────────┐   │
│   │                      Redis (Distributed Cache)                        │   │
│   │                                                                       │   │
│   │   widgetMetadataCache  TTL: 24h  ← WidgetMeta (SQL + uiSchema)       │   │
│   │   widgetDataCache      TTL:  5m  ← List<Map> (Target DB 쿼리 결과)    │   │
│   └───────────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────────────────┘


┌───────────────────────────────────────────────────────────────────────────────┐
│                          EXCEPTION HANDLING (횡단관심사)                       │
│                                                                               │
│  GlobalExceptionHandler (@RestControllerAdvice)                               │
│   SqlDryRunException      → 422 Unprocessable Entity (SQL 검증 실패)           │
│   WidgetNotFoundException → 404 Not Found                                    │
│   IllegalArgumentException → 400 Bad Request                                 │
│   DataAccessException     → 500 (DB 오류 상세내용 숨김, 스키마 노출 방지)       │
│   Exception               → 500 (generic, 스택트레이스 로그만 기록)            │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 핵심 데이터 흐름

### 위젯 데이터 조회 (런타임)

```
GET /api/v1/widgets/{id}/data
  → WidgetEngineService
      → [Redis Hit]  widgetMetadataCache  →  SQL + uiSchema 반환
      → [Redis Miss] meta-DB 조회 (WIDGET_PAYLOAD → legacy EAV fallback)
      → [Redis Hit]  widgetDataCache      →  쿼리 결과 반환
      → [Redis Miss] Target DB 실행 (timeout 30s, max 10,000행)
  → WidgetResponse { widgetId, uiSchema, rows, truncated }
```

### 위젯 GitOps 배포

```
POST /api/v1/admin/widgets/deploy (YAML body)
  → WidgetDeployService
      → Parse    (SnakeYAML)
      → Validate (필수 필드 검사)
      → Dry-Run  (SELECT * FROM (...) WHERE 1=0 → Target DB)
      → Cost Gate (EXPLAIN PLAN ≤ MAX_COST → Target DB)
      → Base64 encode → 4KB chunk 분할
      → @Transactional 범위 내 WIDGET_PAYLOAD UPSERT
      → TX commit 완료 후 → Redis 캐시 eviction (best-effort)
  → 201 Created / 422 (SQL 오류) / 400 (구조 오류)
```

---

## DB 역할 분리

| 구분 | Meta DB | Target DB |
|------|---------|-----------|
| 역할 | 위젯 정의 / 감사 로그 저장 | 실제 비즈니스 데이터 저장 |
| 접근 시점 | 배포 시 + 캐시 미스 시 | 런타임 쿼리 실행 시 |
| 주요 테이블 | WIDGET_MASTER, WIDGET_PAYLOAD, AUDIT_LOG | SALES_SUMMARY, FX_OHLC_DAILY 등 |
| 환경 | H2 (dev) | H2 (dev) / Oracle·Tibero (prod) |

## Redis 캐시 전략

| Cache 이름 | TTL | 저장 내용 |
|-----------|-----|---------|
| `widgetMetadataCache` | 24시간 | WidgetMeta (SQL + uiSchema) — 배포 시에만 변경 |
| `widgetDataCache` | 5분 | Target DB 쿼리 결과 — DB 부하 방어 |

> Redis 장애 시 자동으로 DB fallthrough — 캐시 없이도 서비스 정상 동작 (resilience 설계)
