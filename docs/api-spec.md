# Keyword Search Advertising (KSA) API Specification v1.0

---

## 1. 시스템 아키텍처 개요

### 1.1 주요 컴포넌트

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client (Web/SDK)                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTPS (TLS 1.3)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     API Gateway (Rate Limit, Auth)                  │
│                     OAuth 2.0 Bearer Token + MFA                    │
└───┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬───────────────┘
    │      │      │      │      │      │      │      │
    ▼      ▼      ▼      ▼      ▼      ▼      ▼      ▼
┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────────┐
│Campaig││AdGrp ││Keywrd││Ad    ││Bid   ││Report││Pay   ││Review    │
│Service││Svc   ││Svc   ││Svc   ││Engine││Svc   ││Svc   ││Svc      │
└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───────┘
   │       │       │       │       │       │       │       │
   ▼       ▼       ▼       ▼       ▼       ▼       ▼       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Message Queue (Kafka)                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────────────┐
│  PostgreSQL  │    │  Redis       │    │  ClickHouse          │
│  (Primary)   │    │  (Cache/     │    │  (Analytics/         │
│              │    │   Session)   │    │   Reporting)         │
└──────────────┘    └──────────────┘    └──────────────────────┘
```

### 1.2 데이터 흐름

1. **광고 관리 흐름**: Client -> API Gateway -> Campaign/AdGroup/Keyword/Ad Service -> PostgreSQL
2. **입찰/경매 흐름**: 검색 요청 -> Ad Serving -> Bidding Engine(Redis) -> 경매 결과 반환
3. **클릭 과금 흐름**: 클릭 이벤트 -> Kafka -> Click Processor -> ClickLog(ClickHouse) + Payment Service
4. **리포팅 흐름**: ClickHouse(집계) -> Reporting Service -> Client
5. **심사 흐름**: Ad/Keyword 생성 -> Review Service(자동 10s -> 수동 24h) -> 상태 업데이트

### 1.3 외부 연동 포인트

| 연동 대상 | 프로토콜 | 용도 |
|-----------|---------|------|
| PG(결제 대행사) | REST/Webhook | 선불 충전, 후불 결제 |
| 검색 엔진 | gRPC | 검색어-키워드 매칭, 검색량 데이터 |
| 랜딩페이지 크롤러 | Internal | 품질지수 산출용 랜딩페이지 분석 |
| 외부 전환 추적 | JavaScript SDK/Postback | 전환 데이터 수집 |

---

## 2. 데이터 모델

### 2.1 ERD (텍스트 다이어그램)

```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│   Account    │1    N │    Campaign       │1    N │    AdGroup       │
│──────────────│───────│──────────────────│───────│──────────────────│
│ PK id        │       │ PK id            │       │ PK id            │
│ name         │       │ FK account_id    │       │ FK campaign_id   │
│ status       │       │ name             │       │ name             │
│ currency     │       │ type             │       │ status           │
│ timezone     │       │ status           │       │ default_bid      │
│ owner_user_id│       │ daily_budget     │       │ bid_strategy_type│
│ created_at   │       │ monthly_budget   │       │ target_cpa       │
│ updated_at   │       │ start_date       │       │ target_roas      │
│ deleted_at   │       │ end_date         │       │ device_bid_adj   │
└──────────────┘       │ device_targeting │       │ created_at       │
        │              │ region_targeting │       │ updated_at       │
        │              │ schedule         │       │ deleted_at       │
        │              │ bid_strategy_type│       └──────┬───────────┘
        │              │ created_at       │              │1
        │              │ updated_at       │              │
        │              │ deleted_at       │         N┌───┴──┐N
        │              └──────────────────┘          │      │
        │                                    ┌───────┴─┐  ┌─┴────────┐
        │                                    │ Keyword  │  │    Ad    │
        │                                    │──────────│  │──────────│
        │                                    │PK id     │  │PK id     │
        │                                    │FK adgrp  │  │FK adgrp  │
        │                                    │text      │  │headline_1│
        │                                    │match_type│  │headline_2│
        │                                    │bid_amount│  │headline_3│
        │                                    │status    │  │desc_1    │
        │                                    │review_st │  │desc_2    │
        │                                    │quality   │  │display_url│
        │                                    │created_at│  │final_url │
        │                                    │updated_at│  │mobile_url│
        │                                    │deleted_at│  │status    │
        │                                    └──────────┘  │review_st │
        │                                                  │created_at│
        │                                                  │updated_at│
        │                                                  │deleted_at│
        │                                                  └──────────┘
        │
        │1     N┌──────────────────┐
        ├───────│ NegativeKeyword  │     ┌──────────────────────────┐
        │       │──────────────────│     │  NegativeKeywordList     │
        │       │PK id             │     │──────────────────────────│
        │       │FK campaign_id    │     │PK id                     │
        │       │FK adgroup_id     │     │FK account_id             │
        │       │FK neg_kw_list_id │     │name                      │
        │       │text              │     │created_at                │
        │       │match_type        │     │updated_at                │
        │       └──────────────────┘     └──────────────────────────┘
        │
        │1     N┌──────────────────┐
        ├───────│  AdExtension     │
        │       │──────────────────│
        │       │PK id             │
        │       │FK account_id     │
        │       │FK campaign_id    │
        │       │FK adgroup_id     │
        │       │type              │
        │       │data (JSONB)      │
        │       │status            │
        │       │review_status     │
        │       │created_at        │
        │       └──────────────────┘
        │
        │1     N┌──────────────────┐     ┌──────────────────────────┐
        ├───────│   Payment        │     │   Credit                 │
        │       │──────────────────│     │──────────────────────────│
        │       │PK id             │     │PK id                     │
        │       │FK account_id     │     │FK account_id             │
        │       │type              │     │type                      │
        │       │amount            │     │amount                    │
        │       │status            │     │balance                   │
        │       │pg_transaction_id │     │expires_at                │
        │       │created_at        │     │created_at                │
        │       └──────────────────┘     └──────────────────────────┘
        │
        │       ┌──────────────────┐     ┌──────────────────────────┐
        │       │  QualityScore    │     │   AuctionLog             │
        │       │──────────────────│     │──────────────────────────│
        │       │PK id             │     │PK id                     │
        │       │FK keyword_id     │     │FK keyword_id             │
        │       │score (1-10)      │     │FK ad_id                  │
        │       │expected_ctr      │     │search_query              │
        │       │ad_relevance      │     │bid_amount                │
        │       │landing_page      │     │quality_score             │
        │       │calculated_at     │     │ad_rank                   │
        │       └──────────────────┘     │position                  │
        │                                │actual_cpc                │
        │       ┌──────────────────┐     │created_at                │
        │       │   ClickLog       │     └──────────────────────────┘
        │       │──────────────────│
        │       │PK id             │
        │       │FK auction_log_id │
        │       │FK keyword_id     │
        │       │FK ad_id          │
        │       │FK campaign_id    │
        │       │cost              │
        │       │device            │
        │       │region            │
        │       │is_conversion     │
        │       │conversion_value  │
        │       │clicked_at        │
        │       └──────────────────┘
```

### 2.2 핵심 엔티티 상세 정의

#### Account

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 계정 고유 ID |
| name | VARCHAR(100) | NOT NULL | 계정명 |
| status | ENUM | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, SUSPENDED, CLOSED |
| currency | CHAR(3) | NOT NULL, DEFAULT 'KRW' | 통화 코드 |
| timezone | VARCHAR(50) | NOT NULL, DEFAULT 'Asia/Seoul' | 시간대 |
| owner_user_id | BIGINT | NOT NULL, FK | 소유자 사용자 ID |
| balance | DECIMAL(15,2) | NOT NULL, DEFAULT 0 | 잔액 |
| credit_balance | DECIMAL(15,2) | NOT NULL, DEFAULT 0 | 크레딧 잔액 |
| payment_type | ENUM | NOT NULL | PREPAID, POSTPAID, AUTO_CHARGE |
| auto_charge_threshold | DECIMAL(15,2) | NULLABLE | 자동충전 임계값 |
| auto_charge_amount | DECIMAL(15,2) | NULLABLE | 자동충전 금액 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

#### Campaign

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 캠페인 고유 ID |
| account_id | BIGINT | NOT NULL, FK | 계정 ID |
| name | VARCHAR(200) | NOT NULL | 캠페인명 |
| type | ENUM | NOT NULL | SEARCH, SHOPPING_SEARCH |
| status | ENUM | NOT NULL, DEFAULT 'PAUSED' | ACTIVE, PAUSED, BUDGET_EXHAUSTED, ENDED, UNDER_REVIEW, LIMITED, DELETED |
| daily_budget | DECIMAL(15,2) | NOT NULL, MIN 1000 | 일일 예산 (원) |
| monthly_budget | DECIMAL(15,2) | NULLABLE | 월 예산 (원) |
| start_date | DATE | NOT NULL | 시작일 |
| end_date | DATE | NULLABLE | 종료일 |
| device_targeting | JSONB | NOT NULL | {"pc": true, "mobile": true, "pc_bid_adj": 0, "mobile_bid_adj": 0} |
| region_targeting | JSONB | NULLABLE | ["서울특별시", "서울특별시>강남구", ...] |
| schedule | JSONB | NULLABLE | 요일별 시간대 설정 |
| bid_strategy_type | ENUM | NOT NULL, DEFAULT 'MANUAL_CPC' | MANUAL_CPC, MAXIMIZE_CLICKS, TARGET_CPA, TARGET_ROAS |
| target_cpa | DECIMAL(15,2) | NULLABLE | 목표 CPA |
| target_roas | DECIMAL(5,2) | NULLABLE | 목표 ROAS (%) |
| daily_budget_overdelivery_rate | DECIMAL(3,2) | NOT NULL, DEFAULT 1.20 | 일일 예산 초과허용 비율 (기본 120%) |
| monthly_budget_cap | DECIMAL(15,2) | NULLABLE | 월간 예산 상한 (일일 예산 x 30.4 기준, NULL이면 자동 계산) |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |
| deleted_at | TIMESTAMP | NULLABLE | 삭제일시 (소프트삭제, 30일 보관) |

> **Over-delivery 정책:** 일일 예산의 최대 120%까지 일시적 초과 소진이 허용됩니다. 이는 트래픽 변동에 따른 유연한 광고 노출을 위한 것이며, 월간 기준으로 `daily_budget x 30.4`를 초과하지 않도록 시스템이 자동 보정합니다. `daily_budget_overdelivery_rate`는 광고주가 조정할 수 없으며, 시스템 기본값(1.20)이 적용됩니다.

#### AdGroup

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 광고그룹 고유 ID |
| campaign_id | BIGINT | NOT NULL, FK | 캠페인 ID |
| name | VARCHAR(200) | NOT NULL | 광고그룹명 |
| status | ENUM | NOT NULL, DEFAULT 'PAUSED' | ACTIVE, PAUSED, DELETED |
| default_bid | DECIMAL(15,2) | NOT NULL, 50~100000 | 기본 입찰가 (원) |
| bid_strategy_type | ENUM | NULLABLE | 광고그룹 레벨 입찰전략 (캠페인 상속 가능) |
| target_cpa | DECIMAL(15,2) | NULLABLE | 광고그룹 목표 CPA |
| target_roas | DECIMAL(5,2) | NULLABLE | 광고그룹 목표 ROAS |
| device_bid_adjustments | JSONB | NULLABLE | {"pc": 0, "mobile": 100} (%, -90~+900) |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |
| deleted_at | TIMESTAMP | NULLABLE | 삭제일시 |

#### Keyword

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 키워드 고유 ID |
| adgroup_id | BIGINT | NOT NULL, FK | 광고그룹 ID |
| text | VARCHAR(50) | NOT NULL | 키워드 텍스트 (1~50자) |
| match_type | ENUM | NOT NULL | EXACT, PHRASE, BROAD, AI_BROAD |
| bid_amount | DECIMAL(15,2) | NULLABLE, 50~100000 | 개별 입찰가 (NULL이면 광고그룹 기본값) |
| status | ENUM | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, PAUSED, DELETED |
| review_status | ENUM | NOT NULL, DEFAULT 'PENDING' | PENDING, APPROVED, REJECTED |
| review_reject_reason | VARCHAR(500) | NULLABLE | 반려 사유 |
| quality_score | SMALLINT | NULLABLE, 1~10 | 최신 품질지수 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |
| deleted_at | TIMESTAMP | NULLABLE | 삭제일시 |

**제약조건:**
- UNIQUE (adgroup_id, text, match_type) WHERE deleted_at IS NULL
- 광고그룹당 최대 5,000개
- 캠페인당 최대 50,000개
- 계정당 최대 500,000개

#### Ad

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 광고 소재 고유 ID |
| adgroup_id | BIGINT | NOT NULL, FK | 광고그룹 ID |
| headline_1 | VARCHAR(30) | NOT NULL | 제목 1 (최대 30자) |
| headline_2 | VARCHAR(30) | NULLABLE | 제목 2 |
| headline_3 | VARCHAR(30) | NULLABLE | 제목 3 |
| description_1 | VARCHAR(90) | NOT NULL | 설명 1 (최대 90자) |
| description_2 | VARCHAR(90) | NULLABLE | 설명 2 |
| display_url | VARCHAR(255) | NOT NULL | 표시 URL |
| final_url | VARCHAR(2048) | NOT NULL | 최종 URL |
| mobile_url | VARCHAR(2048) | NULLABLE | 모바일 URL |
| status | ENUM | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, PAUSED, DELETED |
| review_status | ENUM | NOT NULL, DEFAULT 'PENDING' | PENDING, AUTO_APPROVED, MANUAL_REVIEW, APPROVED, REJECTED |
| review_reject_reason | VARCHAR(500) | NULLABLE | 반려 사유 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |
| deleted_at | TIMESTAMP | NULLABLE | 삭제일시 |

#### AdExtension

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 확장 소재 고유 ID |
| account_id | BIGINT | NOT NULL, FK | 계정 ID |
| campaign_id | BIGINT | NULLABLE, FK | 캠페인 ID (NULL이면 계정 레벨) |
| adgroup_id | BIGINT | NULLABLE, FK | 광고그룹 ID |
| type | ENUM | NOT NULL | SITELINK, CALLOUT, PHONE, LOCATION, PRICE, PROMOTION, APP_INSTALL, STRUCTURED_SNIPPET |
| data | JSONB | NOT NULL | 유형별 상세 데이터 |
| status | ENUM | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, PAUSED, DELETED |
| review_status | ENUM | NOT NULL, DEFAULT 'PENDING' | PENDING, APPROVED, REJECTED |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

#### NegativeKeyword

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 제외 키워드 고유 ID |
| campaign_id | BIGINT | NULLABLE, FK | 캠페인 ID |
| adgroup_id | BIGINT | NULLABLE, FK | 광고그룹 ID |
| negative_keyword_list_id | BIGINT | NULLABLE, FK | 공유 제외 키워드 목록 ID |
| text | VARCHAR(50) | NOT NULL | 키워드 텍스트 |
| match_type | ENUM | NOT NULL | EXACT, PHRASE, BROAD |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

#### NegativeKeywordList

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 목록 고유 ID |
| account_id | BIGINT | NOT NULL, FK | 계정 ID |
| name | VARCHAR(200) | NOT NULL | 목록명 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

#### NegativeKeywordListCampaign (연결 테이블)

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| negative_keyword_list_id | BIGINT | PK, FK | 목록 ID |
| campaign_id | BIGINT | PK, FK | 캠페인 ID |

#### QualityScore

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 ID |
| keyword_id | BIGINT | NOT NULL, FK | 키워드 ID |
| score | SMALLINT | NOT NULL, 1~10 | 종합 품질지수 |
| expected_ctr | ENUM | NOT NULL | BELOW_AVERAGE, AVERAGE, ABOVE_AVERAGE |
| ad_relevance | ENUM | NOT NULL | BELOW_AVERAGE, AVERAGE, ABOVE_AVERAGE |
| landing_page_experience | ENUM | NOT NULL | BELOW_AVERAGE, AVERAGE, ABOVE_AVERAGE |
| calculated_at | TIMESTAMP | NOT NULL | 산출 일시 |

#### Payment

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 결제 고유 ID |
| account_id | BIGINT | NOT NULL, FK | 계정 ID |
| type | ENUM | NOT NULL | CHARGE, REFUND, AUTO_CHARGE |
| amount | DECIMAL(15,2) | NOT NULL | 금액 |
| status | ENUM | NOT NULL | PENDING, COMPLETED, FAILED, CANCELLED |
| pg_transaction_id | VARCHAR(100) | NULLABLE | PG사 거래 ID |
| payment_method | VARCHAR(50) | NULLABLE | 결제 수단 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| completed_at | TIMESTAMP | NULLABLE | 완료일시 |

#### Credit

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|---------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 크레딧 고유 ID |
| account_id | BIGINT | NOT NULL, FK | 계정 ID |
| type | ENUM | NOT NULL | CHARGE, PROMOTION, COMPENSATION |
| amount | DECIMAL(15,2) | NOT NULL | 크레딧 금액 |
| balance | DECIMAL(15,2) | NOT NULL | 잔여 금액 |
| description | VARCHAR(500) | NULLABLE | 설명 |
| expires_at | TIMESTAMP | NULLABLE | 만료일시 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

#### AuctionLog (ClickHouse)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID | 경매 고유 ID |
| keyword_id | BIGINT | 키워드 ID |
| ad_id | BIGINT | 광고 소재 ID |
| campaign_id | BIGINT | 캠페인 ID |
| adgroup_id | BIGINT | 광고그룹 ID |
| account_id | BIGINT | 계정 ID |
| search_query | String | 실제 검색어 |
| bid_amount | Decimal(15,2) | 입찰가 |
| quality_score | UInt8 | 품질지수 |
| ad_rank | Decimal(15,4) | Ad Rank |
| position | UInt8 | 노출 순위 |
| placement | Enum | TOP, BOTTOM |
| actual_cpc | Decimal(15,2) | 실제 CPC |
| device | Enum | PC, MOBILE |
| region | String | 노출 지역 |
| is_clicked | UInt8 | 클릭 여부 |
| is_conversion | UInt8 | 전환 여부 |
| conversion_value | Decimal(15,2) | 전환 금액 |
| created_at | DateTime | 생성일시 |

---

## 3. 공통 규약

### 3.1 기본 URL

```
https://api.ksa.example.com/v1
```

### 3.2 인증

모든 API 요청에 OAuth 2.0 Bearer Token을 포함해야 합니다.

```http
Authorization: Bearer {access_token}
X-KSA-Account-Id: {account_id}
```

| 헤더 | 필수 | 설명 |
|------|------|------|
| Authorization | Y | OAuth 2.0 Bearer Token |
| X-KSA-Account-Id | Y | 대상 광고 계정 ID |
| X-Request-Id | N | 클라이언트 요청 추적 ID (미전송시 서버 자동생성) |
| Accept-Language | N | ko, en (기본: ko) |

### 3.3 페이지네이션 (커서 기반)

```json
// 요청
GET /v1/campaigns?limit=20&cursor=eyJpZCI6MTIzNH0

// 응답
{
  "data": [...],
  "paging": {
    "cursors": {
      "after": "eyJpZCI6MTI1NH0"
    },
    "has_next": true,
    "total_count": 156
  }
}
```

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| limit | integer | 20 | 페이지 크기 (최대 100) |
| cursor | string | null | 커서 토큰 |

### 3.4 에러 응답 형식

```json
{
  "error": {
    "code": "CAMPAIGN_NOT_FOUND",
    "message": "Campaign with id 12345 not found.",
    "status": 404,
    "details": [
      {
        "field": "campaign_id",
        "reason": "NOT_FOUND",
        "message": "The specified campaign does not exist or has been deleted."
      }
    ],
    "request_id": "req_abc123def456"
  }
}
```

#### 에러 코드 체계

| HTTP 상태 | 에러 코드 | 설명 |
|-----------|----------|------|
| 400 | INVALID_PARAMETER | 잘못된 파라미터 |
| 400 | VALIDATION_ERROR | 유효성 검증 실패 |
| 400 | BUDGET_TOO_LOW | 최소 예산 미달 (일일 1,000원) |
| 400 | BID_OUT_OF_RANGE | 입찰가 범위 초과 (50~100,000원) |
| 400 | KEYWORD_TOO_LONG | 키워드 50자 초과 |
| 400 | KEYWORD_LIMIT_EXCEEDED | 키워드 수 제한 초과 |
| 400 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 |
| 400 | EMAIL_ALREADY_EXISTS | 이미 등록된 이메일 |
| 400 | BUSINESS_ALREADY_VERIFIED | 이미 사업자 인증 완료 |
| 400 | LAST_ADMIN_CANNOT_REMOVE | 마지막 ADMIN 멤버 제거 불가 |
| 400 | MONTHLY_BUDGET_CAP_REACHED | 월간 예산 상한 도달 |
| 401 | UNAUTHORIZED | 인증 실패 |
| 401 | TOKEN_EXPIRED | 토큰 만료 |
| 401 | INVALID_REFRESH_TOKEN | 유효하지 않은 리프레시 토큰 |
| 403 | FORBIDDEN | 권한 없음 |
| 403 | INSUFFICIENT_ROLE | 역할 권한 부족 (VIEWER가 쓰기 시도 등) |
| 403 | ACCOUNT_SUSPENDED | 계정 정지 |
| 404 | NOT_FOUND | 리소스 없음 |
| 409 | DUPLICATE_KEYWORD | 중복 키워드 |
| 409 | CONFLICT | 상태 충돌 |
| 429 | RATE_LIMIT_EXCEEDED | Rate Limit 초과 |
| 500 | INTERNAL_ERROR | 서버 내부 오류 |
| 503 | SERVICE_UNAVAILABLE | 서비스 일시 불가 |

### 3.5 Rate Limit 정책

| 계층 | 제한 | 윈도우 |
|------|------|-------|
| 읽기 API (GET) | 1,000 req | 1분 |
| 쓰기 API (POST/PUT/PATCH/DELETE) | 200 req | 1분 |
| 리포팅 API | 100 req | 1분 |
| 키워드 도구 API | 50 req | 1분 |

응답 헤더:
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 998
X-RateLimit-Reset: 1672531260
```

### 3.6 공통 응답 필드 규약

- 날짜/시간: ISO 8601 형식 (`2026-03-23T09:00:00+09:00`)
- 금액: 정수 또는 소수점 2자리 (`1000`, `1234.56`)
- ID: 정수형 (`12345`)
- Boolean: `true` / `false`
- Null 가능 필드: 값이 없으면 `null` 반환

---

## 4. API 엔드포인트 설계

### 4.0 계정/인증 관리 API (Account Management)

#### 4.0.1 회원가입

```
POST /v1/accounts
```

**요청 예시:**

```json
{
  "email": "advertiser@example.com",
  "password": "SecureP@ssw0rd!",
  "name": "홍길동",
  "company_name": "예시 주식회사",
  "phone": "010-1234-5678",
  "timezone": "Asia/Seoul",
  "currency": "KRW",
  "terms_agreed": true,
  "privacy_agreed": true
}
```

**응답: `201 Created`**

```json
{
  "data": {
    "id": 5001,
    "email": "advertiser@example.com",
    "name": "홍길동",
    "company_name": "예시 주식회사",
    "phone": "010-1234-5678",
    "status": "ACTIVE",
    "timezone": "Asia/Seoul",
    "currency": "KRW",
    "business_verified": false,
    "role": "ADMIN",
    "created_at": "2026-03-23T10:00:00+09:00",
    "updated_at": "2026-03-23T10:00:00+09:00"
  }
}
```

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| email | 필수, 유효한 이메일 형식, 중복 불가 |
| password | 필수, 8자 이상, 영문+숫자+특수문자 포함 |
| name | 필수, 1~50자 |
| company_name | 필수, 1~100자 |
| phone | 필수, 유효한 전화번호 형식 |
| terms_agreed | 필수, true |
| privacy_agreed | 필수, true |

#### 4.0.2 로그인 (OAuth 2.0 토큰 발급)

```
POST /v1/auth/login
```

**요청 예시:**

```json
{
  "grant_type": "password",
  "email": "advertiser@example.com",
  "password": "SecureP@ssw0rd!"
}
```

**응답: `200 OK`**

```json
{
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
    "refresh_expires_in": 2592000,
    "scope": "ads.manage billing.manage reports.read",
    "account_id": 5001
  }
}
```

#### 4.0.3 토큰 갱신

```
POST /v1/auth/refresh
```

**요청 예시:**

```json
{
  "grant_type": "refresh_token",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

**응답: `200 OK`**

```json
{
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.newtoken...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "bmV3IHJlZnJlc2ggdG9rZW4...",
    "refresh_expires_in": 2592000
  }
}
```

#### 4.0.4 계정 정보 조회

```
GET /v1/accounts/{accountId}
```

**응답: `200 OK`**

```json
{
  "data": {
    "id": 5001,
    "email": "advertiser@example.com",
    "name": "홍길동",
    "company_name": "예시 주식회사",
    "phone": "010-1234-5678",
    "status": "ACTIVE",
    "timezone": "Asia/Seoul",
    "currency": "KRW",
    "business_verified": true,
    "business_registration_number": "123-45-67890",
    "payment_type": "PREPAID",
    "balance": 350000,
    "credit_balance": 50000,
    "role": "ADMIN",
    "created_at": "2026-03-01T10:00:00+09:00",
    "updated_at": "2026-03-20T14:30:00+09:00"
  }
}
```

#### 4.0.5 계정 정보 수정

```
PUT /v1/accounts/{accountId}
```

**요청 예시:**

```json
{
  "name": "홍길동 (수정)",
  "company_name": "예시 주식회사",
  "phone": "010-9876-5432",
  "timezone": "Asia/Seoul"
}
```

**응답: `200 OK`** (수정된 계정 전체 데이터)

#### 4.0.6 사업자 인증

```
POST /v1/accounts/{accountId}/business-verification
```

**요청 예시:**

```json
{
  "business_registration_number": "123-45-67890",
  "representative_name": "홍길동",
  "business_type": "법인사업자",
  "business_category": "전자상거래",
  "business_registration_document_url": "https://upload.ksa.example.com/docs/biz_reg_5001.pdf"
}
```

**응답: `200 OK`**

```json
{
  "data": {
    "account_id": 5001,
    "verification_status": "PENDING",
    "business_registration_number": "123-45-67890",
    "representative_name": "홍길동",
    "submitted_at": "2026-03-23T10:00:00+09:00",
    "estimated_completion": "2026-03-24T10:00:00+09:00",
    "message": "사업자 인증 서류가 접수되었습니다. 영업일 기준 1~2일 내 처리됩니다."
  }
}
```

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| business_registration_number | 필수, XXX-XX-XXXXX 형식 |
| representative_name | 필수, 1~50자 |
| business_type | 필수, 개인사업자 / 법인사업자 |
| business_category | 필수, 1~100자 |
| business_registration_document_url | 필수, 유효한 URL (사전 업로드된 파일) |

#### 4.0.7 멤버 목록 조회

```
GET /v1/accounts/{accountId}/members
```

**응답: `200 OK`**

```json
{
  "data": [
    {
      "id": 1001,
      "account_id": 5001,
      "email": "advertiser@example.com",
      "name": "홍길동",
      "role": "ADMIN",
      "status": "ACTIVE",
      "last_login_at": "2026-03-23T09:00:00+09:00",
      "created_at": "2026-03-01T10:00:00+09:00"
    },
    {
      "id": 1002,
      "account_id": 5001,
      "email": "operator@example.com",
      "name": "김운영",
      "role": "OPERATOR",
      "status": "ACTIVE",
      "last_login_at": "2026-03-22T18:30:00+09:00",
      "created_at": "2026-03-05T10:00:00+09:00"
    },
    {
      "id": 1003,
      "account_id": 5001,
      "email": "viewer@example.com",
      "name": "이조회",
      "role": "VIEWER",
      "status": "ACTIVE",
      "last_login_at": "2026-03-21T14:00:00+09:00",
      "created_at": "2026-03-10T10:00:00+09:00"
    }
  ],
  "paging": {
    "cursors": { "after": null },
    "has_next": false,
    "total_count": 3
  }
}
```

**권한 역할 정의:**

| 역할 | 설명 | 주요 권한 |
|------|------|----------|
| ADMIN | 관리자 | 모든 권한 (계정 설정, 결제, 멤버 관리, 캠페인 관리, 리포팅) |
| OPERATOR | 운영자 | 캠페인/광고 관리, 리포팅 조회 (계정 설정, 결제, 멤버 관리 불가) |
| VIEWER | 뷰어 | 읽기 전용 (캠페인/광고/리포팅 조회만 가능) |

#### 4.0.8 멤버 초대

```
POST /v1/accounts/{accountId}/members
```

**요청 예시:**

```json
{
  "email": "newmember@example.com",
  "name": "박신입",
  "role": "OPERATOR"
}
```

**응답: `201 Created`**

```json
{
  "data": {
    "id": 1004,
    "account_id": 5001,
    "email": "newmember@example.com",
    "name": "박신입",
    "role": "OPERATOR",
    "status": "INVITED",
    "invited_at": "2026-03-23T10:00:00+09:00",
    "invitation_expires_at": "2026-03-30T10:00:00+09:00"
  }
}
```

#### 4.0.9 멤버 권한 변경

```
PUT /v1/accounts/{accountId}/members/{memberId}
```

**요청 예시:**

```json
{
  "role": "VIEWER"
}
```

**응답: `200 OK`**

```json
{
  "data": {
    "id": 1002,
    "account_id": 5001,
    "email": "operator@example.com",
    "name": "김운영",
    "previous_role": "OPERATOR",
    "role": "VIEWER",
    "updated_at": "2026-03-23T10:30:00+09:00"
  }
}
```

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| role | 필수, ADMIN / OPERATOR / VIEWER |
| 제약사항 | ADMIN 권한을 가진 멤버가 최소 1명 이상이어야 함 |
| 권한 | ADMIN만 멤버 권한 변경 가능 |

#### 4.0.10 멤버 제거

```
DELETE /v1/accounts/{accountId}/members/{memberId}
```

**응답: `200 OK`**

```json
{
  "data": {
    "id": 1002,
    "account_id": 5001,
    "email": "operator@example.com",
    "removed_at": "2026-03-23T10:30:00+09:00",
    "message": "멤버가 계정에서 제거되었습니다."
  }
}
```

**제약사항:**
- ADMIN 권한을 가진 멤버만 다른 멤버를 제거할 수 있습니다.
- 계정 소유자(owner)는 제거할 수 없습니다.
- 마지막 ADMIN 멤버는 제거할 수 없습니다.

---

### 4.1 캠페인 관리 API

#### 4.1.1 캠페인 목록 조회

```
GET /v1/campaigns
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | N | 상태 필터 (쉼표 구분) |
| type | string | N | 캠페인 유형 (SEARCH, SHOPPING_SEARCH) |
| limit | integer | N | 페이지 크기 (기본 20, 최대 100) |
| cursor | string | N | 페이지네이션 커서 |

**응답 예시:**

```json
{
  "data": [
    {
      "id": 10001,
      "account_id": 5001,
      "name": "봄 시즌 검색 광고",
      "type": "SEARCH",
      "status": "ACTIVE",
      "daily_budget": 50000,
      "monthly_budget": 1500000,
      "start_date": "2026-03-01",
      "end_date": "2026-05-31",
      "bid_strategy_type": "MANUAL_CPC",
      "device_targeting": {
        "pc": true,
        "mobile": true,
        "pc_bid_adjustment": 0,
        "mobile_bid_adjustment": 50
      },
      "region_targeting": [
        "서울특별시",
        "경기도>성남시"
      ],
      "schedule": {
        "monday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
        "tuesday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
        "wednesday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
        "thursday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
        "friday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
        "saturday": [],
        "sunday": []
      },
      "daily_budget_overdelivery_rate": 1.20,
      "monthly_budget_cap": 1520000,
      "today_spend": 12350,
      "created_at": "2026-02-28T10:00:00+09:00",
      "updated_at": "2026-03-20T14:30:00+09:00"
    }
  ],
  "paging": {
    "cursors": {
      "after": "eyJpZCI6MTAwMDJ9"
    },
    "has_next": true,
    "total_count": 45
  }
}
```

#### 4.1.2 캠페인 단건 조회

```
GET /v1/campaigns/{campaign_id}
```

**응답 예시:**

```json
{
  "data": {
    "id": 10001,
    "account_id": 5001,
    "name": "봄 시즌 검색 광고",
    "type": "SEARCH",
    "status": "ACTIVE",
    "daily_budget": 50000,
    "monthly_budget": 1500000,
    "start_date": "2026-03-01",
    "end_date": "2026-05-31",
    "bid_strategy_type": "MANUAL_CPC",
    "target_cpa": null,
    "target_roas": null,
    "device_targeting": {
      "pc": true,
      "mobile": true,
      "pc_bid_adjustment": 0,
      "mobile_bid_adjustment": 50
    },
    "region_targeting": [
      "서울특별시",
      "경기도>성남시"
    ],
    "schedule": {
      "monday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
      "tuesday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
      "wednesday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
      "thursday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
      "friday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
      "saturday": [],
      "sunday": []
    },
    "daily_budget_overdelivery_rate": 1.20,
    "monthly_budget_cap": 1520000,
    "today_spend": 12350,
    "adgroup_count": 8,
    "keyword_count": 1250,
    "created_at": "2026-02-28T10:00:00+09:00",
    "updated_at": "2026-03-20T14:30:00+09:00",
    "deleted_at": null
  }
}
```

#### 4.1.3 캠페인 생성

```
POST /v1/campaigns
```

**요청 예시:**

```json
{
  "name": "봄 시즌 검색 광고",
  "type": "SEARCH",
  "daily_budget": 50000,
  "monthly_budget": 1500000,
  "start_date": "2026-03-01",
  "end_date": "2026-05-31",
  "bid_strategy_type": "MANUAL_CPC",
  "device_targeting": {
    "pc": true,
    "mobile": true,
    "pc_bid_adjustment": 0,
    "mobile_bid_adjustment": 50
  },
  "region_targeting": [
    "서울특별시",
    "경기도>성남시"
  ],
  "schedule": {
    "monday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    "tuesday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    "wednesday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    "thursday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    "friday": [9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    "saturday": [],
    "sunday": []
  }
}
```

**응답: `201 Created`** (캠페인 단건 조회와 동일한 형식)

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| name | 필수, 1~200자 |
| type | 필수, SEARCH / SHOPPING_SEARCH |
| daily_budget | 필수, 최소 1,000원 |
| monthly_budget | 선택, daily_budget * 30 이상 권장 |
| daily_budget_overdelivery_rate | 시스템 고정값 1.20 (요청 시 무시됨) |
| monthly_budget_cap | 선택, 미지정 시 daily_budget x 30.4로 자동 계산 |
| start_date | 필수, 오늘 이후 |
| end_date | 선택, start_date 이후 |
| bid_strategy_type | 필수, MANUAL_CPC / MAXIMIZE_CLICKS (Phase 1) |
| device_targeting.{device}_bid_adjustment | -90 ~ +900 (%) |

#### 4.1.4 캠페인 수정

```
PATCH /v1/campaigns/{campaign_id}
```

**요청 예시 (부분 수정):**

```json
{
  "daily_budget": 80000,
  "device_targeting": {
    "pc": true,
    "mobile": true,
    "pc_bid_adjustment": 0,
    "mobile_bid_adjustment": 100
  }
}
```

**응답: `200 OK`** (수정된 캠페인 전체 데이터)

#### 4.1.5 캠페인 상태 변경

```
POST /v1/campaigns/{campaign_id}/status
```

**요청 예시:**

```json
{
  "status": "PAUSED"
}
```

**응답 예시:**

```json
{
  "data": {
    "id": 10001,
    "previous_status": "ACTIVE",
    "status": "PAUSED",
    "updated_at": "2026-03-23T10:30:00+09:00"
  }
}
```

**허용 상태 전이:**

| 현재 상태 | 전이 가능 상태 |
|-----------|---------------|
| ACTIVE | PAUSED, DELETED |
| PAUSED | ACTIVE, DELETED |
| BUDGET_EXHAUSTED | PAUSED, DELETED (예산 추가시 자동 ACTIVE) |
| ENDED | DELETED |
| UNDER_REVIEW | PAUSED, DELETED |
| LIMITED | ACTIVE, PAUSED, DELETED |

#### 4.1.6 캠페인 삭제 (소프트 삭제)

```
DELETE /v1/campaigns/{campaign_id}
```

**응답: `200 OK`**

```json
{
  "data": {
    "id": 10001,
    "status": "DELETED",
    "deleted_at": "2026-03-23T10:30:00+09:00",
    "permanent_deletion_at": "2026-04-22T10:30:00+09:00"
  }
}
```

#### 4.1.7 캠페인 일괄 상태 변경

```
POST /v1/campaigns/batch-status
```

**요청 예시:**

```json
{
  "operations": [
    { "campaign_id": 10001, "status": "PAUSED" },
    { "campaign_id": 10002, "status": "PAUSED" },
    { "campaign_id": 10003, "status": "ACTIVE" }
  ]
}
```

**응답 예시:**

```json
{
  "data": {
    "success_count": 2,
    "failure_count": 1,
    "results": [
      { "campaign_id": 10001, "status": "PAUSED", "success": true },
      { "campaign_id": 10002, "status": "PAUSED", "success": true },
      {
        "campaign_id": 10003,
        "success": false,
        "error": {
          "code": "INVALID_STATUS_TRANSITION",
          "message": "Cannot transition from ENDED to ACTIVE."
        }
      }
    ]
  }
}
```

---

### 4.2 광고그룹 관리 API

#### 4.2.1 광고그룹 목록 조회

```
GET /v1/campaigns/{campaign_id}/adgroups
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | N | 상태 필터 |
| limit | integer | N | 페이지 크기 (기본 20) |
| cursor | string | N | 커서 |

**응답 예시:**

```json
{
  "data": [
    {
      "id": 20001,
      "campaign_id": 10001,
      "name": "브랜드 키워드",
      "status": "ACTIVE",
      "default_bid": 500,
      "bid_strategy_type": null,
      "device_bid_adjustments": {
        "pc": 0,
        "mobile": 100
      },
      "keyword_count": 150,
      "ad_count": 5,
      "created_at": "2026-03-01T10:00:00+09:00",
      "updated_at": "2026-03-20T14:30:00+09:00"
    }
  ],
  "paging": {
    "cursors": {
      "after": "eyJpZCI6MjAwMDJ9"
    },
    "has_next": true,
    "total_count": 8
  }
}
```

#### 4.2.2 광고그룹 단건 조회

```
GET /v1/adgroups/{adgroup_id}
```

**응답 예시:**

```json
{
  "data": {
    "id": 20001,
    "campaign_id": 10001,
    "name": "브랜드 키워드",
    "status": "ACTIVE",
    "default_bid": 500,
    "bid_strategy_type": null,
    "target_cpa": null,
    "target_roas": null,
    "device_bid_adjustments": {
      "pc": 0,
      "mobile": 100
    },
    "keyword_count": 150,
    "ad_count": 5,
    "created_at": "2026-03-01T10:00:00+09:00",
    "updated_at": "2026-03-20T14:30:00+09:00",
    "deleted_at": null
  }
}
```

#### 4.2.3 광고그룹 생성

```
POST /v1/campaigns/{campaign_id}/adgroups
```

**요청 예시:**

```json
{
  "name": "브랜드 키워드",
  "default_bid": 500,
  "device_bid_adjustments": {
    "pc": 0,
    "mobile": 100
  }
}
```

**응답: `201 Created`**

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| name | 필수, 1~200자 |
| default_bid | 필수, 50~100,000원 |
| device_bid_adjustments.{device} | -90 ~ +900 (%) |

#### 4.2.4 광고그룹 수정

```
PATCH /v1/adgroups/{adgroup_id}
```

**요청 예시:**

```json
{
  "name": "브랜드 키워드 (수정)",
  "default_bid": 700,
  "device_bid_adjustments": {
    "pc": -30,
    "mobile": 200
  }
}
```

**응답: `200 OK`** (수정된 광고그룹 전체 데이터)

#### 4.2.5 광고그룹 상태 변경

```
POST /v1/adgroups/{adgroup_id}/status
```

**요청 예시:**

```json
{
  "status": "PAUSED"
}
```

#### 4.2.6 광고그룹 삭제

```
DELETE /v1/adgroups/{adgroup_id}
```

---

### 4.3 키워드 관리 API

#### 4.3.1 키워드 목록 조회

```
GET /v1/adgroups/{adgroup_id}/keywords
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | N | 상태 필터 |
| match_type | string | N | 매치 타입 필터 |
| review_status | string | N | 심사 상태 필터 |
| q | string | N | 키워드 텍스트 검색 |
| limit | integer | N | 페이지 크기 (기본 50, 최대 500) |
| cursor | string | N | 커서 |

**응답 예시:**

```json
{
  "data": [
    {
      "id": 30001,
      "adgroup_id": 20001,
      "text": "봄 원피스",
      "match_type": "PHRASE",
      "bid_amount": 800,
      "effective_bid": 800,
      "status": "ACTIVE",
      "review_status": "APPROVED",
      "review_reject_reason": null,
      "quality_score": 7,
      "quality_score_detail": {
        "expected_ctr": "ABOVE_AVERAGE",
        "ad_relevance": "AVERAGE",
        "landing_page_experience": "ABOVE_AVERAGE"
      },
      "created_at": "2026-03-01T10:00:00+09:00",
      "updated_at": "2026-03-20T14:30:00+09:00"
    },
    {
      "id": 30002,
      "adgroup_id": 20001,
      "text": "여성 원피스",
      "match_type": "BROAD",
      "bid_amount": null,
      "effective_bid": 500,
      "status": "ACTIVE",
      "review_status": "APPROVED",
      "review_reject_reason": null,
      "quality_score": 5,
      "quality_score_detail": {
        "expected_ctr": "AVERAGE",
        "ad_relevance": "AVERAGE",
        "landing_page_experience": "BELOW_AVERAGE"
      },
      "created_at": "2026-03-01T10:05:00+09:00",
      "updated_at": "2026-03-18T09:00:00+09:00"
    }
  ],
  "paging": {
    "cursors": {
      "after": "eyJpZCI6MzAwMDN9"
    },
    "has_next": true,
    "total_count": 150
  }
}
```

#### 4.3.2 키워드 단건 조회

```
GET /v1/keywords/{keyword_id}
```

#### 4.3.3 키워드 생성 (일괄)

```
POST /v1/adgroups/{adgroup_id}/keywords
```

**요청 예시:**

```json
{
  "keywords": [
    {
      "text": "봄 원피스",
      "match_type": "PHRASE",
      "bid_amount": 800
    },
    {
      "text": "여성 원피스",
      "match_type": "BROAD",
      "bid_amount": null
    },
    {
      "text": "[플라워 원피스]",
      "match_type": "EXACT",
      "bid_amount": 1200
    },
    {
      "text": "+봄 신상 원피스",
      "match_type": "AI_BROAD",
      "bid_amount": 600
    }
  ]
}
```

**응답: `201 Created`**

```json
{
  "data": {
    "success_count": 4,
    "failure_count": 0,
    "results": [
      {
        "text": "봄 원피스",
        "match_type": "PHRASE",
        "success": true,
        "keyword": {
          "id": 30001,
          "adgroup_id": 20001,
          "text": "봄 원피스",
          "match_type": "PHRASE",
          "bid_amount": 800,
          "status": "ACTIVE",
          "review_status": "PENDING",
          "created_at": "2026-03-23T10:00:00+09:00"
        }
      },
      {
        "text": "여성 원피스",
        "match_type": "BROAD",
        "success": true,
        "keyword": {
          "id": 30002,
          "adgroup_id": 20001,
          "text": "여성 원피스",
          "match_type": "BROAD",
          "bid_amount": null,
          "status": "ACTIVE",
          "review_status": "PENDING",
          "created_at": "2026-03-23T10:00:00+09:00"
        }
      },
      {
        "text": "플라워 원피스",
        "match_type": "EXACT",
        "success": true,
        "keyword": {
          "id": 30003,
          "adgroup_id": 20001,
          "text": "플라워 원피스",
          "match_type": "EXACT",
          "bid_amount": 1200,
          "status": "ACTIVE",
          "review_status": "PENDING",
          "created_at": "2026-03-23T10:00:00+09:00"
        }
      },
      {
        "text": "봄 신상 원피스",
        "match_type": "AI_BROAD",
        "success": true,
        "keyword": {
          "id": 30004,
          "adgroup_id": 20001,
          "text": "봄 신상 원피스",
          "match_type": "AI_BROAD",
          "bid_amount": 600,
          "status": "ACTIVE",
          "review_status": "PENDING",
          "created_at": "2026-03-23T10:00:00+09:00"
        }
      }
    ]
  }
}
```

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| text | 필수, 1~50자 |
| match_type | 필수, EXACT / PHRASE / BROAD / AI_BROAD |
| bid_amount | 선택, 50~100,000원 (NULL이면 광고그룹 기본 입찰가) |
| 일괄 등록 | 최대 1,000개/요청 |
| 광고그룹당 제한 | 최대 5,000개 |
| 캠페인당 제한 | 최대 50,000개 |
| 계정당 제한 | 최대 500,000개 |

#### 4.3.4 키워드 수정

```
PATCH /v1/keywords/{keyword_id}
```

**요청 예시:**

```json
{
  "bid_amount": 1000,
  "status": "PAUSED"
}
```

#### 4.3.5 키워드 일괄 수정

```
POST /v1/keywords/batch-update
```

**요청 예시:**

```json
{
  "operations": [
    { "keyword_id": 30001, "bid_amount": 1000 },
    { "keyword_id": 30002, "bid_amount": 700 },
    { "keyword_id": 30003, "status": "PAUSED" }
  ]
}
```

**응답 예시:**

```json
{
  "data": {
    "success_count": 3,
    "failure_count": 0,
    "results": [
      { "keyword_id": 30001, "success": true },
      { "keyword_id": 30002, "success": true },
      { "keyword_id": 30003, "success": true }
    ]
  }
}
```

#### 4.3.6 키워드 삭제

```
DELETE /v1/keywords/{keyword_id}
```

#### 4.3.7 키워드 일괄 삭제

```
POST /v1/keywords/batch-delete
```

**요청 예시:**

```json
{
  "keyword_ids": [30001, 30002, 30003]
}
```

---

### 4.4 제외 키워드 관리 API

#### 4.4.1 제외 키워드 목록 조회 (캠페인 레벨)

```
GET /v1/campaigns/{campaign_id}/negative-keywords
```

**응답 예시:**

```json
{
  "data": [
    {
      "id": 40001,
      "campaign_id": 10001,
      "adgroup_id": null,
      "text": "무료",
      "match_type": "BROAD",
      "created_at": "2026-03-01T10:00:00+09:00"
    },
    {
      "id": 40002,
      "campaign_id": 10001,
      "adgroup_id": null,
      "text": "중고",
      "match_type": "PHRASE",
      "created_at": "2026-03-01T10:00:00+09:00"
    }
  ],
  "paging": {
    "cursors": { "after": null },
    "has_next": false,
    "total_count": 2
  }
}
```

#### 4.4.2 제외 키워드 목록 조회 (광고그룹 레벨)

```
GET /v1/adgroups/{adgroup_id}/negative-keywords
```

#### 4.4.3 제외 키워드 추가

```
POST /v1/campaigns/{campaign_id}/negative-keywords
```

**요청 예시:**

```json
{
  "negative_keywords": [
    { "text": "무료", "match_type": "BROAD" },
    { "text": "중고", "match_type": "PHRASE" }
  ]
}
```

#### 4.4.4 제외 키워드 삭제

```
DELETE /v1/negative-keywords/{negative_keyword_id}
```

#### 4.4.5 공유 제외 키워드 목록 관리

```
GET    /v1/negative-keyword-lists
POST   /v1/negative-keyword-lists
PATCH  /v1/negative-keyword-lists/{list_id}
DELETE /v1/negative-keyword-lists/{list_id}
```

**공유 목록 생성 요청 예시:**

```json
{
  "name": "공통 제외 키워드",
  "keywords": [
    { "text": "무료", "match_type": "BROAD" },
    { "text": "중고", "match_type": "BROAD" },
    { "text": "가품", "match_type": "EXACT" }
  ]
}
```

**응답: `201 Created`**

```json
{
  "data": {
    "id": 50001,
    "account_id": 5001,
    "name": "공통 제외 키워드",
    "keyword_count": 3,
    "linked_campaign_count": 0,
    "created_at": "2026-03-23T10:00:00+09:00",
    "updated_at": "2026-03-23T10:00:00+09:00"
  }
}
```

#### 4.4.6 공유 목록에 키워드 추가/삭제

```
POST   /v1/negative-keyword-lists/{list_id}/keywords
DELETE /v1/negative-keyword-lists/{list_id}/keywords/{keyword_id}
```

#### 4.4.7 공유 목록-캠페인 연결/해제

```
POST   /v1/negative-keyword-lists/{list_id}/campaigns
DELETE /v1/negative-keyword-lists/{list_id}/campaigns/{campaign_id}
```

**연결 요청 예시:**

```json
{
  "campaign_ids": [10001, 10002, 10003]
}
```

---

### 4.5 광고 소재 관리 API

#### 4.5.1 광고 목록 조회

```
GET /v1/adgroups/{adgroup_id}/ads
```

**응답 예시:**

```json
{
  "data": [
    {
      "id": 60001,
      "adgroup_id": 20001,
      "headline_1": "봄 원피스 최대 50% 할인",
      "headline_2": "신상품 매일 업데이트",
      "headline_3": "무료배송 & 무료반품",
      "description_1": "2026년 봄 신상 원피스를 지금 만나보세요. 플라워, 체크, 무지 다양한 스타일 보유.",
      "description_2": "첫 구매 고객 15% 추가 할인. 오늘 주문하면 내일 도착!",
      "display_url": "www.example.com/spring-dress",
      "final_url": "https://www.example.com/category/spring-dress?utm_source=ksa",
      "mobile_url": "https://m.example.com/category/spring-dress?utm_source=ksa",
      "status": "ACTIVE",
      "review_status": "APPROVED",
      "review_reject_reason": null,
      "created_at": "2026-03-01T10:00:00+09:00",
      "updated_at": "2026-03-15T09:00:00+09:00"
    }
  ],
  "paging": {
    "cursors": { "after": null },
    "has_next": false,
    "total_count": 3
  }
}
```

#### 4.5.2 광고 단건 조회

```
GET /v1/ads/{ad_id}
```

#### 4.5.3 광고 생성

```
POST /v1/adgroups/{adgroup_id}/ads
```

**요청 예시:**

```json
{
  "headline_1": "봄 원피스 최대 50% 할인",
  "headline_2": "신상품 매일 업데이트",
  "headline_3": "무료배송 & 무료반품",
  "description_1": "2026년 봄 신상 원피스를 지금 만나보세요. 플라워, 체크, 무지 다양한 스타일 보유.",
  "description_2": "첫 구매 고객 15% 추가 할인. 오늘 주문하면 내일 도착!",
  "display_url": "www.example.com/spring-dress",
  "final_url": "https://www.example.com/category/spring-dress?utm_source=ksa",
  "mobile_url": "https://m.example.com/category/spring-dress?utm_source=ksa"
}
```

**응답: `201 Created`**

```json
{
  "data": {
    "id": 60001,
    "adgroup_id": 20001,
    "headline_1": "봄 원피스 최대 50% 할인",
    "headline_2": "신상품 매일 업데이트",
    "headline_3": "무료배송 & 무료반품",
    "description_1": "2026년 봄 신상 원피스를 지금 만나보세요. 플라워, 체크, 무지 다양한 스타일 보유.",
    "description_2": "첫 구매 고객 15% 추가 할인. 오늘 주문하면 내일 도착!",
    "display_url": "www.example.com/spring-dress",
    "final_url": "https://www.example.com/category/spring-dress?utm_source=ksa",
    "mobile_url": "https://m.example.com/category/spring-dress?utm_source=ksa",
    "status": "ACTIVE",
    "review_status": "PENDING",
    "review_reject_reason": null,
    "created_at": "2026-03-23T10:00:00+09:00",
    "updated_at": "2026-03-23T10:00:00+09:00"
  }
}
```

**유효성 검증:**

| 필드 | 규칙 |
|------|------|
| headline_1 | 필수, 1~30자 |
| headline_2 | 선택, 1~30자 |
| headline_3 | 선택, 1~30자 |
| description_1 | 필수, 1~90자 |
| description_2 | 선택, 1~90자 |
| display_url | 필수, 유효한 도메인 형식 |
| final_url | 필수, 유효한 HTTPS URL |
| mobile_url | 선택, 유효한 HTTPS URL |

#### 4.5.4 광고 수정

```
PATCH /v1/ads/{ad_id}
```

**요청 예시:**

```json
{
  "headline_1": "봄 원피스 최대 70% 할인",
  "description_1": "2026년 봄 시즌 마지막 세일! 원피스 전 상품 최대 70% 할인 중."
}
```

> 소재 수정 시 review_status가 PENDING으로 재설정되어 재심사 진행

#### 4.5.5 광고 상태 변경

```
POST /v1/ads/{ad_id}/status
```

**요청 예시:**

```json
{
  "status": "PAUSED"
}
```

#### 4.5.6 광고 삭제

```
DELETE /v1/ads/{ad_id}
```

#### 4.5.7 광고 심사 상태 조회

```
GET /v1/ads/{ad_id}/review
```

**응답 예시:**

```json
{
  "data": {
    "ad_id": 60001,
    "review_status": "REJECTED",
    "review_type": "MANUAL",
    "reject_reason": "광고 제목에 과장 표현이 포함되어 있습니다. '최대'와 함께 구체적인 조건을 명시해 주세요.",
    "reject_policy_codes": ["AD_POLICY_003", "AD_POLICY_007"],
    "reviewed_at": "2026-03-23T11:30:00+09:00",
    "submitted_at": "2026-03-23T10:00:00+09:00"
  }
}
```

---

### 4.6 확장 소재 관리 API

#### 4.6.1 확장 소재 목록 조회

```
GET /v1/ad-extensions
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| type | string | N | 확장 유형 필터 (SITELINK, CALLOUT, PHONE, LOCATION, ...) |
| campaign_id | integer | N | 캠페인 필터 |
| adgroup_id | integer | N | 광고그룹 필터 |
| level | string | N | ACCOUNT, CAMPAIGN, ADGROUP |
| status | string | N | 상태 필터 |

**응답 예시:**

```json
{
  "data": [
    {
      "id": 70001,
      "account_id": 5001,
      "campaign_id": 10001,
      "adgroup_id": null,
      "type": "SITELINK",
      "data": {
        "link_text": "봄 신상 보기",
        "final_url": "https://www.example.com/new-arrivals",
        "description_1": "매주 신상품 업데이트",
        "description_2": "지금 확인하세요"
      },
      "status": "ACTIVE",
      "review_status": "APPROVED",
      "created_at": "2026-03-01T10:00:00+09:00",
      "updated_at": "2026-03-15T09:00:00+09:00"
    },
    {
      "id": 70002,
      "account_id": 5001,
      "campaign_id": 10001,
      "adgroup_id": null,
      "type": "CALLOUT",
      "data": {
        "callout_text": "무료배송"
      },
      "status": "ACTIVE",
      "review_status": "APPROVED",
      "created_at": "2026-03-01T10:00:00+09:00",
      "updated_at": "2026-03-15T09:00:00+09:00"
    },
    {
      "id": 70003,
      "account_id": 5001,
      "campaign_id": null,
      "adgroup_id": null,
      "type": "PHONE",
      "data": {
        "phone_number": "02-1234-5678",
        "country_code": "KR"
      },
      "status": "ACTIVE",
      "review_status": "APPROVED",
      "created_at": "2026-03-01T10:00:00+09:00",
      "updated_at": "2026-03-15T09:00:00+09:00"
    }
  ],
  "paging": {
    "cursors": { "after": "eyJpZCI6NzAwMDR9" },
    "has_next": false,
    "total_count": 3
  }
}
```

#### 4.6.2 확장 소재 생성

```
POST /v1/ad-extensions
```

**요청 예시 (사이트링크):**

```json
{
  "campaign_id": 10001,
  "adgroup_id": null,
  "type": "SITELINK",
  "data": {
    "link_text": "봄 신상 보기",
    "final_url": "https://www.example.com/new-arrivals",
    "description_1": "매주 신상품 업데이트",
    "description_2": "지금 확인하세요"
  }
}
```

**요청 예시 (콜아웃):**

```json
{
  "campaign_id": 10001,
  "type": "CALLOUT",
  "data": {
    "callout_text": "무료배송"
  }
}
```

**요청 예시 (전화번호):**

```json
{
  "type": "PHONE",
  "data": {
    "phone_number": "02-1234-5678",
    "country_code": "KR"
  }
}
```

**요청 예시 (위치정보):**

```json
{
  "campaign_id": 10001,
  "type": "LOCATION",
  "data": {
    "business_name": "예시 매장 강남점",
    "address": "서울특별시 강남구 테헤란로 123",
    "phone_number": "02-555-1234",
    "latitude": 37.5013,
    "longitude": 127.0396
  }
}
```

**요청 예시 (가격 - Phase 2):**

```json
{
  "campaign_id": 10001,
  "type": "PRICE",
  "data": {
    "price_qualifier": "FROM",
    "items": [
      {
        "header": "봄 원피스",
        "description": "플라워 패턴",
        "price": 29900,
        "unit": "개",
        "final_url": "https://www.example.com/dress/spring"
      },
      {
        "header": "여름 원피스",
        "description": "린넨 소재",
        "price": 35900,
        "unit": "개",
        "final_url": "https://www.example.com/dress/summer"
      }
    ]
  }
}
```

#### 4.6.3 확장 소재 수정

```
PATCH /v1/ad-extensions/{extension_id}
```

#### 4.6.4 확장 소재 삭제

```
DELETE /v1/ad-extensions/{extension_id}
```

---

### 4.7 입찰 관리 API

#### 4.7.1 키워드 입찰가 조회

```
GET /v1/keywords/{keyword_id}/bid
```

**응답 예시:**

```json
{
  "data": {
    "keyword_id": 30001,
    "text": "봄 원피스",
    "match_type": "PHRASE",
    "bid_amount": 800,
    "effective_bid": 800,
    "adgroup_default_bid": 500,
    "bid_source": "KEYWORD",
    "device_adjustments": {
      "pc": { "adjustment": 0, "effective_bid": 800 },
      "mobile": { "adjustment": 100, "effective_bid": 1600 }
    },
    "quality_score": 7,
    "estimated_ad_rank": 5600,
    "estimated_position": {
      "top": { "min_bid": 650, "first_page_bid": 200 },
      "first_position_bid": 1200
    }
  }
}
```

#### 4.7.2 키워드 입찰가 변경

```
PUT /v1/keywords/{keyword_id}/bid
```

**요청 예시:**

```json
{
  "bid_amount": 1000
}
```

**응답 예시:**

```json
{
  "data": {
    "keyword_id": 30001,
    "previous_bid": 800,
    "bid_amount": 1000,
    "effective_bid": 1000,
    "updated_at": "2026-03-23T10:30:00+09:00"
  }
}
```

#### 4.7.3 입찰가 일괄 변경

```
POST /v1/keywords/batch-bid
```

**요청 예시:**

```json
{
  "operations": [
    { "keyword_id": 30001, "bid_amount": 1000 },
    { "keyword_id": 30002, "bid_amount": 700 },
    { "keyword_id": 30003, "bid_amount": 1500 }
  ]
}
```

**응답 예시:**

```json
{
  "data": {
    "success_count": 3,
    "failure_count": 0,
    "results": [
      { "keyword_id": 30001, "previous_bid": 800, "bid_amount": 1000, "success": true },
      { "keyword_id": 30002, "previous_bid": 500, "bid_amount": 700, "success": true },
      { "keyword_id": 30003, "previous_bid": 1200, "bid_amount": 1500, "success": true }
    ]
  }
}
```

#### 4.7.4 추천 입찰가 조회

```
GET /v1/keywords/{keyword_id}/bid-recommendation
```

**응답 예시:**

```json
{
  "data": {
    "keyword_id": 30001,
    "text": "봄 원피스",
    "match_type": "PHRASE",
    "current_bid": 800,
    "recommendations": {
      "first_page": {
        "bid": 200,
        "estimated_impressions": "5,000~8,000",
        "estimated_clicks": "150~250"
      },
      "top_of_page": {
        "bid": 650,
        "estimated_impressions": "3,000~5,000",
        "estimated_clicks": "200~350"
      },
      "first_position": {
        "bid": 1200,
        "estimated_impressions": "3,000~5,000",
        "estimated_clicks": "350~500"
      }
    },
    "competition_level": "HIGH",
    "estimated_quality_score": 7
  }
}
```

#### 4.7.5 광고그룹 입찰 전략 변경

```
PUT /v1/adgroups/{adgroup_id}/bid-strategy
```

**요청 예시 (수동 CPC):**

```json
{
  "bid_strategy_type": "MANUAL_CPC",
  "default_bid": 500
}
```

**요청 예시 (클릭 최대화):**

```json
{
  "bid_strategy_type": "MAXIMIZE_CLICKS",
  "max_cpc_limit": 2000
}
```

**요청 예시 (목표 CPA - Phase 2):**

```json
{
  "bid_strategy_type": "TARGET_CPA",
  "target_cpa": 5000
}
```

---

### 4.7a AI 입찰 인사이트 API (AI Bidding Insights)

> PRD 핵심 차별화 기능인 "설명 가능한 AI 자동입찰"을 지원하는 API입니다. AI가 입찰가를 자동 조정할 때 그 이유와 근거를 투명하게 제공하여, 광고주가 자동입찰 전략의 동작 원리를 이해하고 신뢰할 수 있도록 합니다.

#### 4.7a.1 입찰 조정 사유 조회

```
GET /v1/campaigns/{campaignId}/bid-insights
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| adgroup_id | integer | N | 광고그룹 ID (미지정 시 캠페인 전체) |
| keyword_id | integer | N | 키워드 ID (특정 키워드 필터) |
| date | string | N | 조회 날짜 (YYYY-MM-DD, 기본: 오늘) |
| limit | integer | N | 페이지 크기 (기본 20, 최대 100) |
| cursor | string | N | 페이지네이션 커서 |

**응답: `200 OK`**

```json
{
  "data": {
    "campaign_id": 10001,
    "bid_strategy_type": "MAXIMIZE_CLICKS",
    "insights": [
      {
        "keyword_id": 30001,
        "keyword_text": "봄 원피스",
        "match_type": "PHRASE",
        "base_bid": 800,
        "adjusted_bid": 1120,
        "adjustment_rate": 1.40,
        "adjustment_reason": "시간대 및 디바이스 요인에 의해 입찰가가 상향 조정되었습니다.",
        "factors": [
          {
            "factor_type": "TIME_OF_DAY",
            "factor_value": "10:00~12:00",
            "impact_score": 0.25,
            "direction": "UP",
            "description": "해당 시간대에 전환율이 평균 대비 40% 높아 입찰가를 상향 조정합니다."
          },
          {
            "factor_type": "DEVICE",
            "factor_value": "MOBILE",
            "impact_score": 0.15,
            "direction": "UP",
            "description": "모바일 기기에서의 클릭률이 높아 입찰가를 추가 상향합니다."
          },
          {
            "factor_type": "REGION",
            "factor_value": "서울특별시>강남구",
            "impact_score": 0.10,
            "direction": "UP",
            "description": "해당 지역의 전환 성과가 우수하여 입찰가를 상향 조정합니다."
          },
          {
            "factor_type": "COMPETITION",
            "factor_value": "HIGH",
            "impact_score": -0.10,
            "direction": "DOWN",
            "description": "경쟁 강도가 높아 비용 효율을 위해 소폭 하향 조정합니다."
          }
        ],
        "confidence_score": 0.85,
        "calculated_at": "2026-03-23T10:00:00+09:00"
      },
      {
        "keyword_id": 30002,
        "keyword_text": "여성 원피스",
        "match_type": "BROAD",
        "base_bid": 500,
        "adjusted_bid": 380,
        "adjustment_rate": 0.76,
        "adjustment_reason": "낮은 전환율과 높은 경쟁으로 입찰가가 하향 조정되었습니다.",
        "factors": [
          {
            "factor_type": "CONVERSION_RATE",
            "factor_value": "1.2%",
            "impact_score": -0.15,
            "direction": "DOWN",
            "description": "최근 7일 전환율이 목표 대비 낮아 입찰가를 하향 조정합니다."
          },
          {
            "factor_type": "TIME_OF_DAY",
            "factor_value": "15:00~17:00",
            "impact_score": -0.09,
            "direction": "DOWN",
            "description": "해당 시간대에 성과가 저조하여 입찰가를 하향 조정합니다."
          }
        ],
        "confidence_score": 0.72,
        "calculated_at": "2026-03-23T10:00:00+09:00"
      }
    ]
  },
  "paging": {
    "cursors": { "after": "eyJpZCI6MzAwMDN9" },
    "has_next": true,
    "total_count": 150
  }
}
```

**factor_type 정의:**

| factor_type | 설명 |
|-------------|------|
| TIME_OF_DAY | 시간대별 성과 기반 조정 |
| DEVICE | 디바이스(PC/모바일)별 성과 기반 조정 |
| REGION | 지역별 성과 기반 조정 |
| COMPETITION | 경쟁 강도 기반 조정 |
| CONVERSION_RATE | 전환율 기반 조정 |
| SEARCH_VOLUME | 검색량 변동 기반 조정 |
| WEATHER | 날씨/계절 요인 기반 조정 |
| DAY_OF_WEEK | 요일별 성과 기반 조정 |
| AUDIENCE | 잠재 고객 세그먼트 기반 조정 |

#### 4.7a.2 입찰 변동 이력 로그

```
GET /v1/campaigns/{campaignId}/bid-history
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| keyword_id | integer | N | 키워드 ID (특정 키워드 필터) |
| start_date | string | Y | 시작일 (YYYY-MM-DD) |
| end_date | string | Y | 종료일 (YYYY-MM-DD) |
| change_source | string | N | 변경 주체 필터 (AI_AUTO, MANUAL, RULE) |
| limit | integer | N | 페이지 크기 (기본 50, 최대 500) |
| cursor | string | N | 페이지네이션 커서 |

**응답: `200 OK`**

```json
{
  "data": {
    "campaign_id": 10001,
    "history": [
      {
        "id": "bh_001",
        "keyword_id": 30001,
        "keyword_text": "봄 원피스",
        "match_type": "PHRASE",
        "previous_bid": 800,
        "new_bid": 1120,
        "change_rate": 1.40,
        "change_source": "AI_AUTO",
        "bid_strategy_type": "MAXIMIZE_CLICKS",
        "primary_reason": "시간대 성과 최적화",
        "factors": [
          {
            "factor_type": "TIME_OF_DAY",
            "impact_score": 0.25,
            "direction": "UP"
          },
          {
            "factor_type": "DEVICE",
            "impact_score": 0.15,
            "direction": "UP"
          }
        ],
        "changed_at": "2026-03-23T10:00:00+09:00"
      },
      {
        "id": "bh_002",
        "keyword_id": 30001,
        "keyword_text": "봄 원피스",
        "match_type": "PHRASE",
        "previous_bid": 1120,
        "new_bid": 950,
        "change_rate": 0.85,
        "change_source": "AI_AUTO",
        "bid_strategy_type": "MAXIMIZE_CLICKS",
        "primary_reason": "경쟁 강도 변화 반영",
        "factors": [
          {
            "factor_type": "COMPETITION",
            "impact_score": -0.10,
            "direction": "DOWN"
          },
          {
            "factor_type": "CONVERSION_RATE",
            "impact_score": -0.05,
            "direction": "DOWN"
          }
        ],
        "changed_at": "2026-03-23T14:00:00+09:00"
      },
      {
        "id": "bh_003",
        "keyword_id": 30002,
        "keyword_text": "여성 원피스",
        "match_type": "BROAD",
        "previous_bid": 500,
        "new_bid": 700,
        "change_rate": 1.40,
        "change_source": "MANUAL",
        "bid_strategy_type": null,
        "primary_reason": "광고주 수동 변경",
        "factors": [],
        "changed_at": "2026-03-23T11:30:00+09:00"
      }
    ]
  },
  "paging": {
    "cursors": { "after": "eyJpZCI6ImJoXzAwNCJ9" },
    "has_next": true,
    "total_count": 1250
  }
}
```

#### 4.7a.3 입찰 전략 성과 요약

```
GET /v1/campaigns/{campaignId}/bid-strategy-report
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| period | string | N | WEEKLY, MONTHLY (기본: WEEKLY) |
| start_date | string | Y | 시작일 (YYYY-MM-DD) |
| end_date | string | Y | 종료일 (YYYY-MM-DD) |

**응답: `200 OK`**

```json
{
  "data": {
    "campaign_id": 10001,
    "campaign_name": "봄 시즌 검색 광고",
    "bid_strategy_type": "MAXIMIZE_CLICKS",
    "period": "WEEKLY",
    "report": [
      {
        "period_start": "2026-03-17",
        "period_end": "2026-03-23",
        "performance": {
          "impressions": 125000,
          "clicks": 4500,
          "ctr": 3.60,
          "avg_cpc": 520,
          "cost": 2340000,
          "conversions": 180,
          "cvr": 4.00,
          "roas": 385.47
        },
        "ai_optimization_summary": {
          "total_bid_adjustments": 8520,
          "upward_adjustments": 5120,
          "downward_adjustments": 3400,
          "avg_adjustment_rate": 1.18,
          "estimated_savings": 156000,
          "estimated_additional_clicks": 320
        },
        "top_factors": [
          {
            "factor_type": "TIME_OF_DAY",
            "frequency": 3200,
            "avg_impact_score": 0.22,
            "description": "시간대 최적화가 가장 빈번하게 적용되었습니다. 오전 10~12시에 입찰가 상향이 집중되었습니다."
          },
          {
            "factor_type": "DEVICE",
            "frequency": 2800,
            "avg_impact_score": 0.18,
            "description": "모바일 디바이스에서의 성과가 우수하여 모바일 입찰가가 평균 18% 상향 조정되었습니다."
          },
          {
            "factor_type": "REGION",
            "frequency": 1500,
            "avg_impact_score": 0.12,
            "description": "서울/경기 지역에서의 전환 성과가 우수하여 해당 지역 입찰가가 상향 조정되었습니다."
          }
        ],
        "recommendations": [
          "모바일 디바이스 비중이 높으므로 모바일 전용 소재 작성을 권장합니다.",
          "오전 10~12시 시간대의 예산을 추가 배분하면 전환 증가가 예상됩니다.",
          "전환율이 낮은 확장 매칭 키워드 3개에 대해 제외 키워드 추가를 검토하세요."
        ]
      }
    ]
  },
  "meta": {
    "start_date": "2026-03-17",
    "end_date": "2026-03-23",
    "period": "WEEKLY",
    "currency": "KRW",
    "timezone": "Asia/Seoul"
  }
}
```

---

### 4.7b 예산 현황 API (Budget Status)

> 일일 예산 120% 초과허용(over-delivery) 정책에 따른 실시간 예산 소진 현황을 제공합니다. 월간 기준으로 `daily_budget x 30.4`를 초과하지 않도록 시스템이 자동 보정합니다.

#### 4.7b.1 캠페인 예산 현황 조회

```
GET /v1/campaigns/{campaignId}/budget-status
```

**응답: `200 OK`**

```json
{
  "data": {
    "campaign_id": 10001,
    "campaign_name": "봄 시즌 검색 광고",
    "daily_budget": 50000,
    "monthly_budget": 1500000,
    "daily_budget_overdelivery_rate": 1.20,
    "monthly_budget_cap": 1520000,
    "today": {
      "date": "2026-03-23",
      "spend": 42350,
      "effective_daily_limit": 60000,
      "remaining": 17650,
      "utilization_rate": 0.847,
      "is_overdelivery_active": false,
      "overdelivery_amount": 0,
      "status": "DELIVERING"
    },
    "this_month": {
      "month": "2026-03",
      "total_spend": 1125000,
      "monthly_budget_cap": 1520000,
      "remaining": 395000,
      "utilization_rate": 0.740,
      "days_elapsed": 23,
      "days_remaining": 8,
      "avg_daily_spend": 48913,
      "projected_monthly_spend": 1516103,
      "overdelivery_days": 5,
      "total_overdelivery_amount": 35000,
      "status": "ON_TRACK"
    },
    "budget_alerts": [
      {
        "type": "PROJECTION_WARNING",
        "message": "현재 소진 속도로 3월 29일경 월간 예산 상한에 도달할 것으로 예상됩니다.",
        "severity": "WARNING"
      }
    ]
  }
}
```

**today.status 값:**

| 값 | 설명 |
|---|------|
| DELIVERING | 정상 노출 중 |
| OVERDELIVERING | 일일 예산 초과 소진 중 (120% 이내) |
| DAILY_BUDGET_REACHED | 일일 예산 100% 소진 완료 |
| DAILY_LIMIT_REACHED | 일일 초과허용 한도(120%) 도달, 노출 중단 |

**this_month.status 값:**

| 값 | 설명 |
|---|------|
| ON_TRACK | 월간 예산 소진 정상 |
| PACING_FAST | 예상보다 빠르게 소진 중 |
| PACING_SLOW | 예상보다 느리게 소진 중 |
| MONTHLY_CAP_NEAR | 월간 상한 임박 (90% 이상) |
| MONTHLY_CAP_REACHED | 월간 상한 도달, 잔여 기간 노출 중단 |

---

### 4.8 리포팅 API

#### 4.8.1 캠페인 성과 리포트

```
GET /v1/reports/campaigns
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_ids | string | N | 캠페인 ID (쉼표 구분, 미지정시 전체) |
| start_date | string | Y | 시작일 (YYYY-MM-DD) |
| end_date | string | Y | 종료일 (YYYY-MM-DD) |
| granularity | string | N | DAILY, WEEKLY, MONTHLY (기본: DAILY) |
| metrics | string | N | 조회할 지표 (쉼표 구분, 기본: 전체) |

**응답 예시:**

```json
{
  "data": {
    "summary": {
      "impressions": 125000,
      "clicks": 4500,
      "ctr": 3.60,
      "avg_cpc": 520,
      "cost": 2340000,
      "conversions": 180,
      "cvr": 4.00,
      "cpa": 13000,
      "roas": 385.47,
      "avg_position": 2.3,
      "impression_share": 68.5
    },
    "rows": [
      {
        "date": "2026-03-21",
        "campaign_id": 10001,
        "campaign_name": "봄 시즌 검색 광고",
        "impressions": 42000,
        "clicks": 1520,
        "ctr": 3.62,
        "avg_cpc": 515,
        "cost": 782800,
        "conversions": 62,
        "cvr": 4.08,
        "cpa": 12626,
        "roas": 395.12,
        "avg_position": 2.1,
        "impression_share": 72.3
      },
      {
        "date": "2026-03-22",
        "campaign_id": 10001,
        "campaign_name": "봄 시즌 검색 광고",
        "impressions": 43500,
        "clicks": 1560,
        "ctr": 3.59,
        "avg_cpc": 525,
        "cost": 819000,
        "conversions": 60,
        "cvr": 3.85,
        "cpa": 13650,
        "roas": 372.89,
        "avg_position": 2.3,
        "impression_share": 70.1
      },
      {
        "date": "2026-03-23",
        "campaign_id": 10001,
        "campaign_name": "봄 시즌 검색 광고",
        "impressions": 39500,
        "clicks": 1420,
        "ctr": 3.59,
        "avg_cpc": 520,
        "cost": 738200,
        "conversions": 58,
        "cvr": 4.08,
        "cpa": 12728,
        "roas": 390.54,
        "avg_position": 2.5,
        "impression_share": 63.2
      }
    ]
  },
  "meta": {
    "start_date": "2026-03-21",
    "end_date": "2026-03-23",
    "granularity": "DAILY",
    "currency": "KRW",
    "timezone": "Asia/Seoul"
  }
}
```

#### 4.8.2 광고그룹 성과 리포트

```
GET /v1/reports/adgroups
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | N | 캠페인 ID |
| adgroup_ids | string | N | 광고그룹 ID (쉼표 구분) |
| start_date | string | Y | 시작일 |
| end_date | string | Y | 종료일 |
| granularity | string | N | DAILY, WEEKLY, MONTHLY |

#### 4.8.3 키워드 성과 리포트

```
GET /v1/reports/keywords
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | N | 캠페인 ID |
| adgroup_id | integer | N | 광고그룹 ID |
| start_date | string | Y | 시작일 |
| end_date | string | Y | 종료일 |
| granularity | string | N | DAILY, WEEKLY, MONTHLY |
| sort_by | string | N | 정렬 기준 (cost, clicks, impressions, conversions) |
| sort_order | string | N | ASC, DESC (기본: DESC) |

**응답 예시:**

```json
{
  "data": {
    "rows": [
      {
        "date": "2026-03-23",
        "keyword_id": 30001,
        "keyword_text": "봄 원피스",
        "match_type": "PHRASE",
        "adgroup_id": 20001,
        "adgroup_name": "브랜드 키워드",
        "impressions": 3200,
        "clicks": 128,
        "ctr": 4.00,
        "avg_cpc": 750,
        "cost": 96000,
        "conversions": 8,
        "cvr": 6.25,
        "cpa": 12000,
        "roas": 416.67,
        "avg_position": 1.8,
        "quality_score": 7
      }
    ]
  },
  "meta": {
    "start_date": "2026-03-23",
    "end_date": "2026-03-23",
    "granularity": "DAILY",
    "currency": "KRW",
    "timezone": "Asia/Seoul"
  }
}
```

#### 4.8.4 검색어 리포트

```
GET /v1/reports/search-terms
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | N | 캠페인 ID |
| adgroup_id | integer | N | 광고그룹 ID |
| keyword_id | integer | N | 키워드 ID |
| start_date | string | Y | 시작일 |
| end_date | string | Y | 종료일 |

**응답 예시:**

```json
{
  "data": {
    "rows": [
      {
        "search_term": "봄 원피스 추천",
        "matched_keyword_id": 30001,
        "matched_keyword_text": "봄 원피스",
        "match_type": "PHRASE",
        "impressions": 850,
        "clicks": 45,
        "ctr": 5.29,
        "avg_cpc": 680,
        "cost": 30600,
        "conversions": 3,
        "added_status": "NONE"
      },
      {
        "search_term": "봄 원피스 코디",
        "matched_keyword_id": 30001,
        "matched_keyword_text": "봄 원피스",
        "match_type": "PHRASE",
        "impressions": 620,
        "clicks": 28,
        "ctr": 4.52,
        "avg_cpc": 720,
        "cost": 20160,
        "conversions": 1,
        "added_status": "NONE"
      },
      {
        "search_term": "봄 원피스 무료나눔",
        "matched_keyword_id": 30002,
        "matched_keyword_text": "여성 원피스",
        "match_type": "BROAD",
        "impressions": 300,
        "clicks": 15,
        "ctr": 5.00,
        "avg_cpc": 500,
        "cost": 7500,
        "conversions": 0,
        "added_status": "EXCLUDED"
      }
    ]
  }
}
```

#### 4.8.5 소재 성과 리포트

```
GET /v1/reports/ads
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | N | 캠페인 ID |
| adgroup_id | integer | N | 광고그룹 ID |
| start_date | string | Y | 시작일 |
| end_date | string | Y | 종료일 |

#### 4.8.6 디바이스별 성과 리포트

```
GET /v1/reports/devices
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | N | 캠페인 ID |
| start_date | string | Y | 시작일 |
| end_date | string | Y | 종료일 |

**응답 예시:**

```json
{
  "data": {
    "rows": [
      {
        "device": "PC",
        "impressions": 45000,
        "clicks": 1350,
        "ctr": 3.00,
        "avg_cpc": 600,
        "cost": 810000,
        "conversions": 65,
        "cvr": 4.81
      },
      {
        "device": "MOBILE",
        "impressions": 80000,
        "clicks": 3150,
        "ctr": 3.94,
        "avg_cpc": 486,
        "cost": 1530000,
        "conversions": 115,
        "cvr": 3.65
      }
    ]
  }
}
```

#### 4.8.7 시간대별 성과 리포트

```
GET /v1/reports/hourly
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | Y | 캠페인 ID |
| date | string | Y | 날짜 (YYYY-MM-DD) |

**응답 예시:**

```json
{
  "data": {
    "rows": [
      { "hour": 0, "impressions": 500, "clicks": 12, "cost": 6000 },
      { "hour": 1, "impressions": 300, "clicks": 8, "cost": 4000 },
      { "hour": 9, "impressions": 5200, "clicks": 195, "cost": 101400 },
      { "hour": 10, "impressions": 6100, "clicks": 230, "cost": 119600 }
    ]
  }
}
```

#### 4.8.8 지역별 성과 리포트

```
GET /v1/reports/regions
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| campaign_id | integer | N | 캠페인 ID |
| start_date | string | Y | 시작일 |
| end_date | string | Y | 종료일 |
| region_level | string | N | CITY, DISTRICT (기본: CITY) |

**응답 예시:**

```json
{
  "data": {
    "rows": [
      {
        "region": "서울특별시",
        "impressions": 52000,
        "clicks": 2100,
        "ctr": 4.04,
        "avg_cpc": 550,
        "cost": 1155000,
        "conversions": 95
      },
      {
        "region": "경기도",
        "impressions": 38000,
        "clicks": 1400,
        "ctr": 3.68,
        "avg_cpc": 480,
        "cost": 672000,
        "conversions": 55
      }
    ]
  }
}
```

#### 4.8.9 리포트 다운로드

```
POST /v1/reports/download
```

**요청 예시:**

```json
{
  "report_type": "KEYWORD",
  "start_date": "2026-01-01",
  "end_date": "2026-03-23",
  "granularity": "DAILY",
  "format": "CSV",
  "campaign_ids": [10001, 10002],
  "metrics": [
    "impressions", "clicks", "ctr", "avg_cpc", "cost",
    "conversions", "cvr", "cpa", "roas"
  ]
}
```

**응답 예시:**

```json
{
  "data": {
    "job_id": "rpt_abc123def456",
    "status": "PROCESSING",
    "estimated_completion_seconds": 30,
    "created_at": "2026-03-23T10:00:00+09:00"
  }
}
```

#### 4.8.10 리포트 다운로드 상태 확인 및 파일 수령

```
GET /v1/reports/download/{job_id}
```

**응답 예시 (완료):**

```json
{
  "data": {
    "job_id": "rpt_abc123def456",
    "status": "COMPLETED",
    "format": "CSV",
    "file_url": "https://reports.ksa.example.com/downloads/rpt_abc123def456.csv",
    "file_size_bytes": 245760,
    "expires_at": "2026-03-24T10:00:00+09:00",
    "created_at": "2026-03-23T10:00:00+09:00",
    "completed_at": "2026-03-23T10:00:25+09:00"
  }
}
```

**기간 제한:** 최대 90일

---

### 4.9 결제/크레딧 API

#### 4.9.1 계정 잔액 조회

```
GET /v1/billing/balance
```

**응답 예시:**

```json
{
  "data": {
    "account_id": 5001,
    "balance": 350000,
    "credit_balance": 50000,
    "total_available": 400000,
    "payment_type": "PREPAID",
    "auto_charge_enabled": true,
    "auto_charge_threshold": 100000,
    "auto_charge_amount": 500000,
    "today_spend": 45230,
    "this_month_spend": 1250000,
    "currency": "KRW"
  }
}
```

#### 4.9.2 충전 (선불)

```
POST /v1/billing/charge
```

**요청 예시:**

```json
{
  "amount": 500000,
  "payment_method_id": "pm_card_12345"
}
```

**응답 예시:**

```json
{
  "data": {
    "payment_id": 80001,
    "type": "CHARGE",
    "amount": 500000,
    "status": "COMPLETED",
    "balance_after": 850000,
    "pg_transaction_id": "pg_txn_abc123",
    "payment_method": "신한카드 **** 1234",
    "created_at": "2026-03-23T10:00:00+09:00",
    "completed_at": "2026-03-23T10:00:02+09:00"
  }
}
```

#### 4.9.3 결제 수단 관리

```
GET    /v1/billing/payment-methods
POST   /v1/billing/payment-methods
DELETE /v1/billing/payment-methods/{method_id}
```

**결제 수단 등록 요청:**

```json
{
  "type": "CREDIT_CARD",
  "card_token": "tok_abc123def456",
  "is_default": true
}
```

#### 4.9.4 자동 충전 설정

```
PUT /v1/billing/auto-charge
```

**요청 예시:**

```json
{
  "enabled": true,
  "threshold": 100000,
  "charge_amount": 500000,
  "payment_method_id": "pm_card_12345"
}
```

#### 4.9.5 결제 내역 조회

```
GET /v1/billing/payments
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| start_date | string | N | 시작일 |
| end_date | string | N | 종료일 |
| type | string | N | CHARGE, REFUND, AUTO_CHARGE |
| status | string | N | PENDING, COMPLETED, FAILED, CANCELLED |
| limit | integer | N | 페이지 크기 |
| cursor | string | N | 커서 |

**응답 예시:**

```json
{
  "data": [
    {
      "payment_id": 80001,
      "type": "CHARGE",
      "amount": 500000,
      "status": "COMPLETED",
      "payment_method": "신한카드 **** 1234",
      "pg_transaction_id": "pg_txn_abc123",
      "created_at": "2026-03-23T10:00:00+09:00",
      "completed_at": "2026-03-23T10:00:02+09:00"
    },
    {
      "payment_id": 80000,
      "type": "AUTO_CHARGE",
      "amount": 500000,
      "status": "COMPLETED",
      "payment_method": "신한카드 **** 1234",
      "pg_transaction_id": "pg_txn_xyz789",
      "created_at": "2026-03-20T03:00:00+09:00",
      "completed_at": "2026-03-20T03:00:01+09:00"
    }
  ],
  "paging": {
    "cursors": { "after": "eyJpZCI6Nzk5OTl9" },
    "has_next": true,
    "total_count": 25
  }
}
```

#### 4.9.6 크레딧 목록 조회

```
GET /v1/billing/credits
```

**응답 예시:**

```json
{
  "data": [
    {
      "id": 90001,
      "type": "PROMOTION",
      "amount": 100000,
      "balance": 50000,
      "description": "신규 가입 프로모션 크레딧",
      "expires_at": "2026-06-30T23:59:59+09:00",
      "created_at": "2026-03-01T00:00:00+09:00"
    },
    {
      "id": 90002,
      "type": "COMPENSATION",
      "amount": 10000,
      "balance": 10000,
      "description": "시스템 장애 보상 크레딧",
      "expires_at": "2026-09-23T23:59:59+09:00",
      "created_at": "2026-03-15T00:00:00+09:00"
    }
  ],
  "paging": {
    "cursors": { "after": null },
    "has_next": false,
    "total_count": 2
  }
}
```

#### 4.9.7 세금계산서/영수증 조회

```
GET /v1/billing/invoices
```

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| year | integer | Y | 연도 |
| month | integer | N | 월 (미지정시 연간) |

---

### 4.10 키워드 도구 API

#### 4.10.1 키워드 추천

```
POST /v1/tools/keyword-suggestions
```

**요청 예시:**

```json
{
  "seed_keywords": ["원피스", "봄 옷"],
  "url": "https://www.example.com/category/dress",
  "include_search_volume": true,
  "language": "ko",
  "limit": 50
}
```

**응답 예시:**

```json
{
  "data": {
    "suggestions": [
      {
        "keyword": "봄 원피스",
        "avg_monthly_searches": 74000,
        "competition": "HIGH",
        "competition_index": 85,
        "suggested_bid": {
          "low": 400,
          "high": 1200
        },
        "trend": [
          { "month": "2026-01", "searches": 45000 },
          { "month": "2026-02", "searches": 62000 },
          { "month": "2026-03", "searches": 74000 }
        ]
      },
      {
        "keyword": "봄 원피스 추천",
        "avg_monthly_searches": 22000,
        "competition": "MEDIUM",
        "competition_index": 62,
        "suggested_bid": {
          "low": 300,
          "high": 800
        },
        "trend": [
          { "month": "2026-01", "searches": 12000 },
          { "month": "2026-02", "searches": 18000 },
          { "month": "2026-03", "searches": 22000 }
        ]
      },
      {
        "keyword": "플라워 원피스",
        "avg_monthly_searches": 18500,
        "competition": "MEDIUM",
        "competition_index": 55,
        "suggested_bid": {
          "low": 250,
          "high": 700
        },
        "trend": [
          { "month": "2026-01", "searches": 8000 },
          { "month": "2026-02", "searches": 14000 },
          { "month": "2026-03", "searches": 18500 }
        ]
      }
    ],
    "total_suggestions": 50
  }
}
```

#### 4.10.2 검색량 조회

```
POST /v1/tools/search-volume
```

**요청 예시:**

```json
{
  "keywords": ["봄 원피스", "여성 원피스", "플라워 원피스"],
  "start_month": "2025-04",
  "end_month": "2026-03"
}
```

**응답 예시:**

```json
{
  "data": {
    "results": [
      {
        "keyword": "봄 원피스",
        "avg_monthly_searches": 55000,
        "competition": "HIGH",
        "monthly_data": [
          { "month": "2025-04", "searches": 68000 },
          { "month": "2025-05", "searches": 42000 },
          { "month": "2025-06", "searches": 15000 },
          { "month": "2025-07", "searches": 8000 },
          { "month": "2025-08", "searches": 9000 },
          { "month": "2025-09", "searches": 18000 },
          { "month": "2025-10", "searches": 22000 },
          { "month": "2025-11", "searches": 15000 },
          { "month": "2025-12", "searches": 12000 },
          { "month": "2026-01", "searches": 45000 },
          { "month": "2026-02", "searches": 62000 },
          { "month": "2026-03", "searches": 74000 }
        ]
      },
      {
        "keyword": "여성 원피스",
        "avg_monthly_searches": 42000,
        "competition": "HIGH",
        "monthly_data": [
          { "month": "2025-04", "searches": 45000 },
          { "month": "2025-05", "searches": 40000 },
          { "month": "2025-06", "searches": 35000 },
          { "month": "2025-07", "searches": 30000 },
          { "month": "2025-08", "searches": 32000 },
          { "month": "2025-09", "searches": 38000 },
          { "month": "2025-10", "searches": 40000 },
          { "month": "2025-11", "searches": 42000 },
          { "month": "2025-12", "searches": 44000 },
          { "month": "2026-01", "searches": 46000 },
          { "month": "2026-02", "searches": 48000 },
          { "month": "2026-03", "searches": 50000 }
        ]
      }
    ]
  }
}
```

#### 4.10.3 예상 성과 시뮬레이터

```
POST /v1/tools/performance-forecast
```

**요청 예시:**

```json
{
  "keywords": [
    { "text": "봄 원피스", "match_type": "PHRASE", "bid_amount": 800 },
    { "text": "여성 원피스", "match_type": "BROAD", "bid_amount": 600 }
  ],
  "daily_budget": 50000,
  "device_targeting": {
    "pc": true,
    "mobile": true,
    "mobile_bid_adjustment": 50
  },
  "region_targeting": ["서울특별시"],
  "forecast_days": 30
}
```

**응답 예시:**

```json
{
  "data": {
    "forecast_period": {
      "start_date": "2026-03-24",
      "end_date": "2026-04-22",
      "days": 30
    },
    "estimated_performance": {
      "impressions": {
        "low": 85000,
        "mid": 125000,
        "high": 165000
      },
      "clicks": {
        "low": 3000,
        "mid": 4500,
        "high": 6200
      },
      "ctr": {
        "low": 3.2,
        "mid": 3.6,
        "high": 4.1
      },
      "avg_cpc": {
        "low": 450,
        "mid": 580,
        "high": 720
      },
      "total_cost": {
        "low": 1350000,
        "mid": 1500000,
        "high": 1500000
      },
      "avg_position": {
        "low": 3.5,
        "mid": 2.3,
        "high": 1.5
      }
    },
    "keyword_breakdown": [
      {
        "keyword": "봄 원피스",
        "match_type": "PHRASE",
        "bid_amount": 800,
        "estimated_impressions": 85000,
        "estimated_clicks": 3200,
        "estimated_cost": 1024000,
        "estimated_position": 2.1
      },
      {
        "keyword": "여성 원피스",
        "match_type": "BROAD",
        "bid_amount": 600,
        "estimated_impressions": 40000,
        "estimated_clicks": 1300,
        "estimated_cost": 476000,
        "estimated_position": 2.8
      }
    ]
  }
}
```

#### 4.10.4 키워드 진단

```
GET /v1/tools/keyword-diagnosis/{keyword_id}
```

**응답 예시:**

```json
{
  "data": {
    "keyword_id": 30001,
    "text": "봄 원피스",
    "match_type": "PHRASE",
    "status": "ACTIVE",
    "serving_status": "ELIGIBLE",
    "diagnosis": {
      "is_serving": true,
      "quality_score": 7,
      "quality_detail": {
        "expected_ctr": "ABOVE_AVERAGE",
        "ad_relevance": "AVERAGE",
        "landing_page_experience": "ABOVE_AVERAGE"
      },
      "bid_status": "ABOVE_FIRST_PAGE",
      "current_bid": 800,
      "first_page_bid_estimate": 200,
      "top_page_bid_estimate": 650,
      "first_position_bid_estimate": 1200,
      "issues": [],
      "recommendations": [
        "광고 소재의 키워드 관련성을 높이면 품질지수 향상이 가능합니다.",
        "입찰가를 1,200원으로 올리면 1순위 노출이 예상됩니다."
      ]
    }
  }
}
```

**진단 이슈 유형(issues):**

```json
{
  "issues": [
    {
      "type": "LOW_QUALITY_SCORE",
      "severity": "WARNING",
      "message": "품질지수가 3점으로 낮습니다. 소재 관련성과 랜딩페이지를 개선하세요."
    },
    {
      "type": "BID_BELOW_FIRST_PAGE",
      "severity": "ERROR",
      "message": "입찰가(100원)가 첫 페이지 노출 최소 입찰가(200원)보다 낮습니다."
    },
    {
      "type": "AD_DISAPPROVED",
      "severity": "ERROR",
      "message": "연결된 광고 소재가 반려 상태입니다. 소재를 수정하세요."
    },
    {
      "type": "CAMPAIGN_PAUSED",
      "severity": "INFO",
      "message": "캠페인이 일시중지 상태입니다."
    }
  ]
}
```

---

## 5. API 엔드포인트 요약표

### 5.0 계정/인증 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /v1/accounts | 회원가입 |
| POST | /v1/auth/login | 로그인 (OAuth 2.0 토큰 발급) |
| POST | /v1/auth/refresh | 토큰 갱신 |
| GET | /v1/accounts/{accountId} | 계정 정보 조회 |
| PUT | /v1/accounts/{accountId} | 계정 정보 수정 |
| POST | /v1/accounts/{accountId}/business-verification | 사업자 인증 |
| GET | /v1/accounts/{accountId}/members | 멤버 목록 조회 |
| POST | /v1/accounts/{accountId}/members | 멤버 초대 |
| PUT | /v1/accounts/{accountId}/members/{memberId} | 멤버 권한 변경 (ADMIN/OPERATOR/VIEWER) |
| DELETE | /v1/accounts/{accountId}/members/{memberId} | 멤버 제거 |

### 5.1 캠페인 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/campaigns | 캠페인 목록 조회 |
| GET | /v1/campaigns/{campaign_id} | 캠페인 단건 조회 |
| POST | /v1/campaigns | 캠페인 생성 |
| PATCH | /v1/campaigns/{campaign_id} | 캠페인 수정 |
| POST | /v1/campaigns/{campaign_id}/status | 캠페인 상태 변경 |
| DELETE | /v1/campaigns/{campaign_id} | 캠페인 삭제 (소프트) |
| POST | /v1/campaigns/batch-status | 캠페인 일괄 상태 변경 |

### 5.2 광고그룹 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/campaigns/{campaign_id}/adgroups | 광고그룹 목록 조회 |
| GET | /v1/adgroups/{adgroup_id} | 광고그룹 단건 조회 |
| POST | /v1/campaigns/{campaign_id}/adgroups | 광고그룹 생성 |
| PATCH | /v1/adgroups/{adgroup_id} | 광고그룹 수정 |
| POST | /v1/adgroups/{adgroup_id}/status | 광고그룹 상태 변경 |
| DELETE | /v1/adgroups/{adgroup_id} | 광고그룹 삭제 |

### 5.3 키워드 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/adgroups/{adgroup_id}/keywords | 키워드 목록 조회 |
| GET | /v1/keywords/{keyword_id} | 키워드 단건 조회 |
| POST | /v1/adgroups/{adgroup_id}/keywords | 키워드 일괄 생성 |
| PATCH | /v1/keywords/{keyword_id} | 키워드 수정 |
| POST | /v1/keywords/batch-update | 키워드 일괄 수정 |
| DELETE | /v1/keywords/{keyword_id} | 키워드 삭제 |
| POST | /v1/keywords/batch-delete | 키워드 일괄 삭제 |

### 5.4 제외 키워드 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/campaigns/{campaign_id}/negative-keywords | 캠페인 제외 키워드 조회 |
| POST | /v1/campaigns/{campaign_id}/negative-keywords | 캠페인 제외 키워드 추가 |
| GET | /v1/adgroups/{adgroup_id}/negative-keywords | 광고그룹 제외 키워드 조회 |
| POST | /v1/adgroups/{adgroup_id}/negative-keywords | 광고그룹 제외 키워드 추가 |
| DELETE | /v1/negative-keywords/{id} | 제외 키워드 삭제 |
| GET | /v1/negative-keyword-lists | 공유 제외 목록 조회 |
| POST | /v1/negative-keyword-lists | 공유 제외 목록 생성 |
| PATCH | /v1/negative-keyword-lists/{list_id} | 공유 제외 목록 수정 |
| DELETE | /v1/negative-keyword-lists/{list_id} | 공유 제외 목록 삭제 |
| POST | /v1/negative-keyword-lists/{list_id}/keywords | 목록에 키워드 추가 |
| DELETE | /v1/negative-keyword-lists/{list_id}/keywords/{kw_id} | 목록에서 키워드 삭제 |
| POST | /v1/negative-keyword-lists/{list_id}/campaigns | 목록-캠페인 연결 |
| DELETE | /v1/negative-keyword-lists/{list_id}/campaigns/{camp_id} | 목록-캠페인 해제 |

### 5.5 광고 소재 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/adgroups/{adgroup_id}/ads | 광고 목록 조회 |
| GET | /v1/ads/{ad_id} | 광고 단건 조회 |
| POST | /v1/adgroups/{adgroup_id}/ads | 광고 생성 |
| PATCH | /v1/ads/{ad_id} | 광고 수정 |
| POST | /v1/ads/{ad_id}/status | 광고 상태 변경 |
| DELETE | /v1/ads/{ad_id} | 광고 삭제 |
| GET | /v1/ads/{ad_id}/review | 광고 심사 상태 조회 |

### 5.6 확장 소재 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/ad-extensions | 확장 소재 목록 조회 |
| POST | /v1/ad-extensions | 확장 소재 생성 |
| PATCH | /v1/ad-extensions/{id} | 확장 소재 수정 |
| DELETE | /v1/ad-extensions/{id} | 확장 소재 삭제 |

### 5.7 입찰 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/keywords/{keyword_id}/bid | 키워드 입찰가 조회 |
| PUT | /v1/keywords/{keyword_id}/bid | 키워드 입찰가 변경 |
| POST | /v1/keywords/batch-bid | 입찰가 일괄 변경 |
| GET | /v1/keywords/{keyword_id}/bid-recommendation | 추천 입찰가 조회 |
| PUT | /v1/adgroups/{adgroup_id}/bid-strategy | 입찰 전략 변경 |

### 5.7a AI 입찰 인사이트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/campaigns/{campaignId}/bid-insights | 입찰 조정 사유 조회 |
| GET | /v1/campaigns/{campaignId}/bid-history | 입찰 변동 이력 로그 |
| GET | /v1/campaigns/{campaignId}/bid-strategy-report | 입찰 전략 성과 요약 |

### 5.7b 예산 현황

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/campaigns/{campaignId}/budget-status | 캠페인 예산 현황 조회 (일일/월간 소진, 초과허용 상태) |

### 5.8 리포팅

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/reports/campaigns | 캠페인 성과 리포트 |
| GET | /v1/reports/adgroups | 광고그룹 성과 리포트 |
| GET | /v1/reports/keywords | 키워드 성과 리포트 |
| GET | /v1/reports/search-terms | 검색어 리포트 |
| GET | /v1/reports/ads | 소재 성과 리포트 |
| GET | /v1/reports/devices | 디바이스별 성과 리포트 |
| GET | /v1/reports/hourly | 시간대별 성과 리포트 |
| GET | /v1/reports/regions | 지역별 성과 리포트 |
| POST | /v1/reports/download | 리포트 다운로드 요청 |
| GET | /v1/reports/download/{job_id} | 리포트 다운로드 상태 확인 |

### 5.9 결제/크레딧

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /v1/billing/balance | 잔액 조회 |
| POST | /v1/billing/charge | 충전 |
| GET | /v1/billing/payment-methods | 결제 수단 목록 |
| POST | /v1/billing/payment-methods | 결제 수단 등록 |
| DELETE | /v1/billing/payment-methods/{id} | 결제 수단 삭제 |
| PUT | /v1/billing/auto-charge | 자동 충전 설정 |
| GET | /v1/billing/payments | 결제 내역 조회 |
| GET | /v1/billing/credits | 크레딧 목록 조회 |
| GET | /v1/billing/invoices | 세금계산서 조회 |

### 5.10 키워드 도구

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /v1/tools/keyword-suggestions | 키워드 추천 |
| POST | /v1/tools/search-volume | 검색량 조회 |
| POST | /v1/tools/performance-forecast | 예상 성과 시뮬레이터 |
| GET | /v1/tools/keyword-diagnosis/{keyword_id} | 키워드 진단 |

---

## 6. 부록

### 6.1 ENUM 정의

#### CampaignType
| 값 | 설명 |
|---|------|
| SEARCH | 검색 광고 (Phase 1) |
| SHOPPING_SEARCH | 쇼핑 검색 (Phase 2) |

#### CampaignStatus
| 값 | 설명 |
|---|------|
| ACTIVE | 운영 중 |
| PAUSED | 일시중지 |
| BUDGET_EXHAUSTED | 예산 소진 |
| ENDED | 종료 |
| UNDER_REVIEW | 심사 중 |
| LIMITED | 제한적 노출 |
| DELETED | 삭제됨 (소프트, 30일 보관) |

#### MatchType
| 값 | 표기 | 설명 |
|---|------|------|
| EXACT | [키워드] | 정확히 일치 |
| PHRASE | "키워드" | 구문 포함 |
| BROAD | 키워드 | 확장 매칭 |
| AI_BROAD | +키워드 | AI 확장 매칭 |

#### ReviewStatus
| 값 | 설명 |
|---|------|
| PENDING | 심사 대기 |
| AUTO_APPROVED | 자동 승인 (10초 내) |
| MANUAL_REVIEW | 수동 심사 중 (최대 24시간) |
| APPROVED | 승인 |
| REJECTED | 반려 |

#### BidStrategyType
| 값 | Phase | 설명 |
|---|-------|------|
| MANUAL_CPC | 1 | 수동 CPC |
| MAXIMIZE_CLICKS | 1 | 클릭 최대화 |
| TARGET_CPA | 2 | 목표 CPA |
| TARGET_ROAS | 2 | 목표 ROAS |

#### AdExtensionType
| 값 | Phase | 설명 |
|---|-------|------|
| SITELINK | 1 | 사이트링크 |
| CALLOUT | 1 | 콜아웃 |
| PHONE | 1 | 전화번호 |
| LOCATION | 1 | 위치정보 |
| PRICE | 2 | 가격 |
| PROMOTION | 2 | 프로모션 |
| APP_INSTALL | 2 | 앱 설치 |
| STRUCTURED_SNIPPET | 2 | 구조화 스니펫 |

#### MemberRole
| 값 | 설명 |
|---|------|
| ADMIN | 관리자 - 모든 권한 (계정 설정, 결제, 멤버 관리, 캠페인 관리, 리포팅) |
| OPERATOR | 운영자 - 캠페인/광고 관리, 리포팅 조회 (계정 설정, 결제, 멤버 관리 불가) |
| VIEWER | 뷰어 - 읽기 전용 (캠페인/광고/리포팅 조회만 가능) |

#### MemberStatus
| 값 | 설명 |
|---|------|
| INVITED | 초대됨 (수락 대기) |
| ACTIVE | 활성 |
| DEACTIVATED | 비활성 |

#### BusinessVerificationStatus
| 값 | 설명 |
|---|------|
| NONE | 미신청 |
| PENDING | 심사 중 |
| VERIFIED | 인증 완료 |
| REJECTED | 반려 |

#### BidChangeSource
| 값 | 설명 |
|---|------|
| AI_AUTO | AI 자동입찰 시스템에 의한 변경 |
| MANUAL | 광고주 수동 변경 |
| RULE | 규칙 기반 자동 변경 |

#### BudgetDailyStatus
| 값 | 설명 |
|---|------|
| DELIVERING | 정상 노출 중 |
| OVERDELIVERING | 일일 예산 초과 소진 중 (120% 이내) |
| DAILY_BUDGET_REACHED | 일일 예산 100% 소진 완료 |
| DAILY_LIMIT_REACHED | 일일 초과허용 한도(120%) 도달, 노출 중단 |

#### BudgetMonthlyStatus
| 값 | 설명 |
|---|------|
| ON_TRACK | 월간 예산 소진 정상 |
| PACING_FAST | 예상보다 빠르게 소진 중 |
| PACING_SLOW | 예상보다 느리게 소진 중 |
| MONTHLY_CAP_NEAR | 월간 상한 임박 (90% 이상) |
| MONTHLY_CAP_REACHED | 월간 상한 도달, 잔여 기간 노출 중단 |

#### CreditType
| 값 | 설명 |
|---|------|
| CHARGE | 충전 크레딧 |
| PROMOTION | 프로모션 크레딧 |
| COMPENSATION | 보상 크레딧 |

#### PaymentType
| 값 | 설명 |
|---|------|
| PREPAID | 선불 |
| POSTPAID | 후불 |
| AUTO_CHARGE | 자동 충전 |

### 6.2 품질지수 산출 로직

```
Quality Score (1~10) = f(Expected CTR, Ad Relevance, Landing Page Experience)

가중치:
  - Expected CTR:            40%
  - Ad Relevance:            30%
  - Landing Page Experience:  30%

Ad Rank 산출:
  Ad Rank = Max CPC x Quality Score x Extension Factor

  Extension Factor: 1.0 ~ 1.2 (확장 소재 적용 시 가산)

실제 CPC 산출 (GSP 경매):
  Actual CPC = (아래 순위 Ad Rank / 내 Quality Score) + 1원
  Actual CPC = min(Actual CPC, Max CPC)
  Actual CPC = max(Actual CPC, 50원)  // 최소 CPC
```

### 6.3 노출 슬롯

| 위치 | 최대 슬롯 수 |
|------|-------------|
| 상단 (Top) | 4개 |
| 하단 (Bottom) | 3개 |

상단 노출을 위한 최소 Ad Rank 임계값이 별도로 존재하며, 이를 충족하지 못하는 광고는 하단에 노출됩니다.

### 6.4 API 버전 관리 정책

- URL prefix 방식: `/v1/`, `/v2/`
- 하위 호환성: 기존 필드 삭제 불가, 신규 필드 추가만 허용
- Deprecation 예고: 최소 6개월 전 공지
- Sunset 헤더: `Sunset: Sat, 01 Jan 2028 00:00:00 GMT`

### 6.5 Idempotency (멱등성)

쓰기 API(POST)에 대해 `Idempotency-Key` 헤더를 지원합니다.

```http
POST /v1/campaigns
Idempotency-Key: unique-client-request-id-abc123
```

- 동일한 Idempotency-Key로 재요청 시 최초 응답을 그대로 반환
- Key 유효 기간: 24시간
- 적용 대상: 모든 POST, PUT 요청

### 6.6 Webhook (이벤트 알림)

광고주가 등록한 콜백 URL로 주요 이벤트를 실시간 전달합니다.

```
POST /v1/webhooks
```

**요청 예시:**

```json
{
  "url": "https://advertiser.example.com/ksa-webhook",
  "events": [
    "ad.review.approved",
    "ad.review.rejected",
    "campaign.budget_exhausted",
    "billing.auto_charge.completed",
    "billing.auto_charge.failed",
    "billing.balance_low"
  ],
  "secret": "webhook_secret_key_abc123"
}
```

**Webhook 페이로드 예시 (광고 심사 완료):**

```json
{
  "event": "ad.review.rejected",
  "timestamp": "2026-03-23T11:30:00+09:00",
  "data": {
    "ad_id": 60001,
    "adgroup_id": 20001,
    "campaign_id": 10001,
    "account_id": 5001,
    "review_status": "REJECTED",
    "reject_reason": "광고 제목에 과장 표현이 포함되어 있습니다.",
    "reject_policy_codes": ["AD_POLICY_003"]
  }
}
```

서명 검증: `X-KSA-Signature: sha256=HMAC(secret, body)`
