# 교차 검토 리포트

> **검토 대상**: PRD v1.0 (`docs/prd.md`) vs API 설계서 v1.0 (`docs/api-spec.md`)
> **검토일**: 2026-03-23
> **검토자**: 정합성 검토 자동화

---

## 1. 검토 요약

- **전체 평가**: **Conditional Pass** (조건부 통과)
- **Critical 이슈**: 3건
- **Major 이슈**: 7건
- **Minor 이슈**: 6건

두 문서는 전반적으로 높은 수준의 정합성을 보이며, 핵심 기능(캠페인 CRUD, 키워드 관리, 입찰, 과금, 리포팅)이 모두 API로 매핑되어 있다. 그러나 **계정/인증 관련 API 부재**, **캠페인명 글자수 제한 불일치**, **Ad Extension Factor 계산 데이터 부재** 등 실무 구현 시 병목이 될 수 있는 이슈가 존재한다. Critical 이슈 3건을 해결하면 MVP 출시에 문제가 없을 것으로 판단한다.

---

## 2. 정합성 검토

### 2.1 기능 매핑 체크리스트

#### 캠페인 관리

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 캠페인 생성/수정/삭제 | O | POST/PATCH/DELETE /v1/campaigns 완비 |
| 캠페인 상태 관리 (활성/중지/예산소진/기간종료/검토중/제한/삭제) | O | POST /v1/campaigns/{id}/status, 상태 전이 표 정의됨 |
| 일일 예산 설정 (최소 1,000원) | O | 캠페인 생성/수정 시 daily_budget 필드, 유효성 검증 일치 |
| 월 예산 한도 (일일예산 x 30.4 자동계산) | **△** | monthly_budget 필드 존재하나 자동계산 로직 미명시, 수동 설정만 기술 |
| 노출 지역 (전국/시도/시군구) | O | region_targeting JSONB 필드 |
| 노출 디바이스 (PC/모바일, 입찰가중치 -90%~+900%) | O | device_targeting JSONB 필드, bid_adjustment 범위 일치 |
| 노출 시간대 (요일별 1시간 단위) | O | schedule JSONB 필드 |
| 캠페인 소프트 삭제 (30일 보관) | O | deleted_at + permanent_deletion_at 응답 포함 |
| 캠페인 삭제 시 하위 일괄 중지 | **△** | API 삭제 응답에 하위 엔티티 처리 여부 미명시 |
| 일정 설정 (시작일 필수, 종료일 선택) | O | start_date NOT NULL, end_date NULLABLE |
| 일괄 상태 변경 | O | POST /v1/campaigns/batch-status |

#### 광고그룹 관리

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 광고그룹 CRUD | O | 목록/단건/생성/수정/삭제/상태변경 API 완비 |

#### 키워드 관리

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 키워드 등록/수정/삭제 | O | 단건 및 일괄 API 완비 |
| 매치 타입 (정확/구문/확장/AI확장) | O | ENUM 정의 일치 |
| 키워드 제한 (광고그룹 5K, 캠페인 50K, 계정 500K) | O | 유효성 검증 항목에 명시 |
| 제외 키워드 (캠페인/광고그룹 단위) | O | 전용 API 섹션 4.4 |
| 공유 제외 키워드 목록 | O | 목록 CRUD + 캠페인 연결/해제 API |
| 키워드 추천 도구 | O | POST /v1/tools/keyword-suggestions |
| 검색량 조회 | O | POST /v1/tools/search-volume |
| 예상 성과 시뮬레이터 | O | POST /v1/tools/performance-forecast |
| 키워드 진단 | O | GET /v1/tools/keyword-diagnosis/{id} |

#### 광고 소재 관리

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 텍스트 광고 CRUD | O | 생성/수정/삭제/상태변경 API 완비 |
| 확장 소재 (사이트링크/전화/위치/콜아웃 등) | O | 전용 API 섹션 4.6 |
| 심사 프로세스 (자동→수동→승인/반려) | O | review_status ENUM, GET /v1/ads/{id}/review |
| 소재 수정 시 재심사 트리거 | O | "소재 수정 시 review_status가 PENDING으로 재설정" 명시 |
| AI 소재 생성 (Phase 2) | X | Phase 2이므로 미매핑은 정상, 단 placeholder API 없음 |

#### 입찰/과금

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 수동 입찰 (Manual CPC) | O | 키워드별 bid_amount, 광고그룹 default_bid |
| 자동 입찰 - 클릭 최대화 (Phase 1) | O | bid_strategy_type MAXIMIZE_CLICKS |
| 자동 입찰 - 목표 CPA/ROAS (Phase 2) | O | ENUM 정의됨, Phase 2 표기 |
| CPC 과금 (GSP 경매) | O | 부록 6.2에 산출 공식 명시 |
| 품질지수 산정 | O | QualityScore 테이블 + 부록 6.2 |
| 최소 입찰가 50원 / 최대 100,000원 | O | BID_OUT_OF_RANGE 에러, 제약조건 일치 |
| 일일 예산 120% 초과 허용 정책 | **X** | PRD에 명시되어 있으나 API/데이터 모델에 반영 없음 |
| 설명 가능한 AI 자동입찰 (입찰 조정 사유) | **X** | PRD 핵심 차별화 기능이나 관련 API 부재 |

#### 결제

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 선불 충전 | O | POST /v1/billing/charge |
| 자동 충전 | O | PUT /v1/billing/auto-charge |
| 크레딧 관리 | O | GET /v1/billing/credits |
| 세금계산서 발행 | O | GET /v1/billing/invoices |
| 결제 수단 관리 (카드/계좌이체/가상계좌) | **△** | 카드 등록만 예시, 계좌이체/가상계좌 미상세 |
| 후불 정산 (Phase 2) | O | POSTPAID ENUM 정의됨 |
| 크레딧 사용 순서 (유효기간 임박순) | **△** | 크레딧 테이블에 expires_at 존재하나 차감 순서 로직 미명시 |
| 무효 클릭 환급 | **△** | COMPENSATION 크레딧 타입 존재하나 환급 프로세스 API 없음 |

#### 리포팅

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 캠페인/광고그룹/키워드 리포트 | O | /v1/reports/campaigns, adgroups, keywords |
| 검색어 리포트 | O | /v1/reports/search-terms |
| 소재 리포트 | O | /v1/reports/ads |
| 시간대 리포트 | O | /v1/reports/hourly |
| 디바이스 리포트 | O | /v1/reports/devices |
| 지역 리포트 (Phase 2) | O | /v1/reports/regions (Phase 2 표기 없이 구현됨 - 선행 구현으로 판단) |
| 리포트 다운로드 (CSV) | O | POST /v1/reports/download, 비동기 방식 |
| 자동 리포트 (이메일 발송) | **X** | PRD에 일간/주간/월간 자동 발송 언급, API 미구현 |
| 대시보드 | **△** | PRD에 대시보드 언급, 전용 API 미정의 (리포트 API 조합으로 대체 가능) |
| Excel 다운로드 | **△** | PRD에 CSV + Excel 지원 명시, API는 CSV만 예시 (format 필드에 EXCEL 미열거) |

#### 계정 관리

| PRD 기능 | API 매핑 여부 | 비고 |
|----------|:----------:|------|
| 회원가입/로그인 (P0) | **X** | API 설계서에 전혀 없음 |
| 사업자 인증 (P0) | **X** | API 설계서에 전혀 없음 |
| 권한 관리 (관리자/운영자/뷰어) (P1) | **X** | API 설계서에 전혀 없음 |

### 2.2 과금 모델 <-> 데이터 모델 일관성

#### 일치 항목

| 과금 요소 | PRD 정의 | 데이터 모델 반영 | 상태 |
|----------|----------|--------------|:----:|
| CPC 과금 방식 | 유효 클릭 시 차감 | ClickLog.cost, AuctionLog.actual_cpc | O |
| 최소 입찰가 50원 | 키워드당 최소 50원 | Keyword.bid_amount 제약 50~100000 | O |
| 최대 입찰가 100,000원 | 키워드당 최대 100,000원 | Keyword.bid_amount 제약 50~100000 | O |
| 일일 예산 최소 1,000원 | 캠페인당 최소 1,000원 | Campaign.daily_budget MIN 1000 | O |
| 월 예산 | 일일 x 30.4 또는 수동 | Campaign.monthly_budget NULLABLE | O |
| 소프트 삭제 30일 | 삭제 후 30일 보관 | deleted_at TIMESTAMP NULLABLE | O |
| GSP 경매 | Actual CPC = (하위 Ad Rank / 내 QS) + 1 | AuctionLog에 bid_amount, quality_score, ad_rank, actual_cpc 모두 포함 | O |
| 품질지수 1~10 | 세 가지 구성 요소 | QualityScore 테이블에 score, expected_ctr, ad_relevance, landing_page_experience | O |
| 크레딧 유형 | 충전/프로모션/보상 | Credit.type ENUM: CHARGE, PROMOTION, COMPENSATION | O |
| 크레딧 유효기간 | 유형별 상이 | Credit.expires_at | O |
| 결제 방식 | 선불/후불/자동충전 | Account.payment_type ENUM: PREPAID, POSTPAID, AUTO_CHARGE | O |

#### 불일치/미반영 항목

| 과금 요소 | PRD 정의 | 데이터 모델 현황 | 상태 |
|----------|----------|--------------|:----:|
| Ad Extension Factor | 1.0~1.2 범위의 보너스 | 계산에 필요한 데이터 필드 없음 (AdExtension 성과 데이터 부재) | X |
| 일일 예산 120% 초과 허용 | 일일 예산의 최대 120%까지 일시 초과 | 데이터 모델/API에 over-delivery 관련 필드 없음 | X |
| 크레딧 차감 순서 | 유효기간 임박순 > 프로모션 > 보상 > 충전 | 로직 레벨 구현이나, 우선순위 필드 미정의 | △ |
| 최소 충전 금액 10,000원 | 선불 충전 시 최소 10,000원 | 충전 API 유효성 검증에 최소 금액 미명시 | X |
| 최대 일일 예산 1억 원 | 캠페인당 최대 1억 원 | 데이터 모델 DECIMAL(15,2)로 수용 가능하나 MAX 제약 미명시 | △ |

---

## 3. 이슈 목록

| # | 심각도 | 영역 | 설명 | 권장 조치 |
|---|:------:|------|------|----------|
| 1 | **Critical** | 계정 관리 | PRD Phase 1 P0 기능인 회원가입/로그인, 사업자 인증, 권한 관리 API가 API 설계서에 전혀 포함되어 있지 않다. OAuth 2.0 Bearer Token 인증만 언급되어 있으나, 토큰 발급/갱신/폐기 플로우와 사용자 관리 API가 없다. | 계정 관리 API 섹션을 신규 추가해야 한다. 최소한 `POST /v1/auth/signup`, `POST /v1/auth/login`, `POST /v1/auth/token/refresh`, `GET/PUT /v1/account/profile`, `POST /v1/account/business-verification`, `GET/POST/DELETE /v1/account/members` 엔드포인트가 필요하다. |
| 2 | **Critical** | 과금/입찰 | PRD 핵심 차별화 기능인 "설명 가능한 AI 자동입찰"(입찰 조정 사유 투명 제공, 입찰 변동 이력, 주간 성과 요약)에 대한 API가 전혀 설계되어 있지 않다. 이는 경쟁사 대비 핵심 차별점이다. | `GET /v1/bid-insights/keywords/{keyword_id}` (입찰 조정 사유 조회), `GET /v1/bid-insights/history` (입찰 변동 이력), `GET /v1/bid-insights/weekly-summary` (주간 전략 성과 요약) 등을 추가해야 한다. |
| 3 | **Critical** | 과금 정책 | PRD에 명시된 "일일 예산 120% 초과 허용 정책"(월간 기준 일일예산 x 30.4 미초과)이 API 및 데이터 모델에 반영되어 있지 않다. 예산 관리 로직의 핵심 규칙이 누락되면 과금 사고로 이어질 수 있다. | Campaign 테이블에 `daily_budget_overdelivery_ratio` 필드 추가를 검토하고, 예산 관련 API 응답에 `today_spend_limit`(일일예산 x 1.2) 필드를 포함시켜야 한다. 서버 사이드 예산 체크 로직 설계도 필요하다. |
| 4 | **Major** | 캠페인 관리 | PRD에서 캠페인명은 "최대 100자"로 명시되어 있으나, API 데이터 모델에서 Campaign.name은 VARCHAR(200)으로 정의되어 있다. 유효성 검증에서도 "1~200자"로 기술되어 있어 불일치한다. | PRD와 API 설계서 간에 어느 쪽이 맞는지 확인 후 통일해야 한다. UI 레이아웃 영향도 고려 시 100자가 적절할 수 있으므로, 양쪽을 100자로 맞추는 것을 권장한다. |
| 5 | **Major** | 과금 모델 | Ad Rank 공식에 사용되는 Ad Extension Factor(1.0~1.2)의 산출 근거 데이터가 모델에 없다. 확장 소재의 성과(CTR 기여도 등)를 측정/저장하는 테이블이나 필드가 없어 Factor 계산이 불가능하다. | AdExtension 테이블에 `performance_score` 또는 별도의 `AdExtensionPerformance` 테이블(extension_id, ctr_uplift, click_count 등)을 추가하여 Extension Factor 산출 데이터를 확보해야 한다. |
| 6 | **Major** | 과금 정책 | PRD에 명시된 최소 충전 금액 10,000원이 충전 API(`POST /v1/billing/charge`)의 유효성 검증 항목에 포함되어 있지 않다. | 충전 API의 유효성 검증 규칙에 `amount >= 10000` 조건을 추가하고, 에러 코드 `CHARGE_AMOUNT_TOO_LOW`를 정의해야 한다. |
| 7 | **Major** | 리포팅 | PRD에서 "다운로드: CSV, Excel 형식 지원"으로 명시되어 있으나, 리포트 다운로드 API의 format 필드 예시가 CSV만 있다. EXCEL 포맷 지원 여부가 불명확하다. | 리포트 다운로드 API의 format 필드 허용값을 `CSV, XLSX`로 명시적으로 열거해야 한다. |
| 8 | **Major** | 리포팅 | PRD에서 "자동 리포트: 일간/주간/월간 자동 발송 (이메일)"이 Phase 1 리포팅 기능 범위에는 미포함이나, PRD 2.4.3절에서는 기능으로 언급하고 있다. 관련 API(자동 리포트 스케줄 설정/조회)가 없다. | Phase 범위를 명확히 하고, Phase 1 범위라면 `POST/GET/DELETE /v1/reports/schedules` API를 추가해야 한다. Phase 2라면 PRD에 명시적으로 Phase 표기를 해야 한다. |
| 9 | **Major** | 키워드 정책 | PRD에서 제외 키워드는 "정확 일치, 구문 일치" 매치 타입만 지원한다고 명시되어 있으나, API 설계서의 NegativeKeyword 데이터 모델에서는 match_type ENUM이 `EXACT, PHRASE, BROAD`로 BROAD까지 포함되어 있다. API 예시에서도 제외 키워드에 BROAD를 사용하고 있다. | PRD와 API 설계서 간 정합성을 맞춰야 한다. 제외 키워드에 BROAD를 지원하려면 PRD를 업데이트하고, 지원하지 않으려면 API에서 BROAD를 제거해야 한다. 실무적으로는 BROAD 제외 키워드가 유용하므로 PRD에 추가하는 것을 권장한다. |
| 10 | **Major** | 소재 관리 | PRD에서 표시 URL은 "도메인 자동 추출 + 경로 2개 (각 15자)"로 구성 요소를 상세히 정의하고 있으나, API 데이터 모델의 display_url은 단일 VARCHAR(255) 필드이다. 경로(path) 분리 필드가 없어 "도메인 자동 추출 + 경로" 규격 검증이 어렵다. | display_url을 `display_url_domain`(자동 추출) + `display_url_path_1`(VARCHAR(15)) + `display_url_path_2`(VARCHAR(15))로 분리하거나, 최소한 API 유효성 검증에 경로 길이 제한 규칙을 추가해야 한다. |
| 11 | **Minor** | 데이터 모델 | PRD에서 캠페인 상태 중 "검토중(Under Review)"은 소재 심사 진행 중 상태인데, 캠페인 레벨에서 검토중 상태가 발생하는 시나리오가 불명확하다. 소재/키워드 개별로 review_status가 있으므로 캠페인 레벨의 UNDER_REVIEW는 중복될 수 있다. | 캠페인 최초 생성 시 하위 소재가 모두 심사 중인 경우에 UNDER_REVIEW로 설정하는 로직인지 명확히 하고, 상태 전이 조건을 보완해야 한다. |
| 12 | **Minor** | 과금 모델 | PRD에서 "동일 Ad Rank 시 먼저 등록된 광고에 우선순위 부여"라고 명시되어 있으나, AuctionLog 테이블에서 Tie-breaking 기준이 되는 광고 등록 시점(created_at)을 경매 시점에 어떻게 활용하는지 로직이 미정의이다. | Bidding Engine 설계 문서에 Tie-breaking 로직을 명시하고, 필요 시 Keyword.created_at 또는 Ad.created_at을 경매 입력 데이터로 포함시켜야 한다. |
| 13 | **Minor** | 데이터 모델 | Account 테이블의 ERD에는 `deleted_at` 필드가 있으나 상세 정의 테이블에는 누락되어 있다. PRD에서 "계정 정보: 탈퇴 후 30일 보관"을 지원하려면 필요하다. | Account 상세 정의에 `deleted_at TIMESTAMP NULLABLE` 필드를 추가해야 한다. |
| 14 | **Minor** | 데이터 모델 | Campaign 테이블의 ERD에는 `bid_strategy_type`이 있으나, 이미 AdGroup에도 동일 필드가 있다. 두 레벨의 입찰 전략 우선순위(상속 관계)가 API 응답에서 명확히 표현되지 않는다. | 캠페인 레벨 bid_strategy와 광고그룹 레벨 override의 우선순위 규칙을 API 문서에 명시해야 한다. |
| 15 | **Minor** | 키워드 생성 | API 키워드 생성 시 match_type에 AI_BROAD가 포함되어 있으나, PRD에서 AI 확장(AI Broad)은 Phase 2 기능이다. Phase 1 API에서 AI_BROAD를 허용할지 차단할지 정책이 불명확하다. | Phase 1에서 AI_BROAD 매치 타입을 사용 불가하도록 유효성 검증에서 차단하거나, MVP 범위를 조정해야 한다. |
| 16 | **Minor** | 리포팅 | PRD에서 "데이터 갱신 주기: 실시간 (최대 3시간 지연), 일간 집계는 익일 09:00 확정"이라는 규칙이 있으나, 리포팅 API 응답에 데이터 freshness 관련 메타 정보(last_updated, data_is_final 등)가 없다. | 리포트 API meta 응답에 `data_freshness` (REALTIME / DAILY_FINAL), `last_updated_at` 필드를 추가해야 한다. |

---

## 4. 엣지 케이스 시뮬레이션 결과

### 4.1 예산 소진 시나리오

**시나리오**: 일일 예산 50,000원 캠페인에서 오전 중 예산이 모두 소진되는 경우

| 단계 | 예상 흐름 | API 지원 여부 | 이슈 |
|------|----------|:----------:|------|
| 1. 예산 소진 감지 | 클릭 과금 시 daily_budget 대비 today_spend 비교 | O | Bidding Engine 내부 처리 |
| 2. 캠페인 상태 BUDGET_EXHAUSTED로 전환 | 시스템 자동 전환 | O | 상태 전이표에 포함 |
| 3. 광고 노출 즉시 중단 | 서빙 시스템에서 제외 | - | API 범위 밖 (서빙 시스템) |
| 4. 익일 00:00 자동 ACTIVE 전환 | 자동 재개 | **△** | 자동 재개 메커니즘이 API에 미정의. 상태 전이표에서 BUDGET_EXHAUSTED -> ACTIVE 자동 전이 명시 필요 |
| 5. 120% 초과 허용 | 예산의 120%까지 일시 초과 가능 | **X** | over-delivery 정책이 API/모델에 반영 안 됨 (Critical #3) |

### 4.2 동일 입찰가 경쟁 시나리오

**시나리오**: 광고주 A, B가 동일 키워드에 동일 Max CPC와 동일 품질지수로 입찰

| 단계 | 예상 흐름 | API 지원 여부 | 이슈 |
|------|----------|:----------:|------|
| 1. Ad Rank 동점 발생 | A, B 모두 Ad Rank 동일 | O | AuctionLog에 ad_rank 필드 |
| 2. Tie-breaking: 먼저 등록된 광고 우선 | created_at 비교 | **△** | 경매 로직에서 created_at 참조 방법 미정의 (Minor #12) |
| 3. Extension Factor 차이로 분리 가능 | 확장 소재 보유 시 Factor 차등 | **△** | Extension Factor 산출 데이터 부재 (Major #5) |

### 4.3 광고 심사 반려 후 재등록 시나리오

**시나리오**: 광고 소재가 수동 심사에서 반려되고, 수정 후 재심사하는 경우

| 단계 | 예상 흐름 | API 지원 여부 | 이슈 |
|------|----------|:----------:|------|
| 1. 소재 등록 | POST /v1/adgroups/{id}/ads | O | review_status: PENDING |
| 2. 자동 심사 보류 | review_status: MANUAL_REVIEW | O | ReviewStatus ENUM 포함 |
| 3. 수동 심사 반려 | review_status: REJECTED | O | reject_reason, reject_policy_codes 제공 |
| 4. 반려 사유 확인 | GET /v1/ads/{id}/review | O | 상세 반려 사유 응답 |
| 5. 소재 수정 | PATCH /v1/ads/{id} | O | 수정 시 review_status PENDING 재설정 |
| 6. 재심사 진행 | 자동으로 재심사 트리거 | O | API 명세에 재심사 트리거 명시 |
| 7. 기존 승인 소재 유지 | 수정 전 소재로 노출 지속 | **△** | PRD에 "기존 승인 소재는 수정 전까지 유지"라 했으나, 수정과 동시에 PENDING이 되므로 수정 즉시 노출 중단 가능성. 소재 버전 관리(이전 승인 버전 유지) 메커니즘이 없음 |

### 4.4 캠페인 일시정지/재개 시나리오

**시나리오**: 운영 중인 캠페인을 일시정지 후 2주 뒤 재개

| 단계 | 예상 흐름 | API 지원 여부 | 이슈 |
|------|----------|:----------:|------|
| 1. 캠페인 일시정지 | POST /v1/campaigns/{id}/status {status: "PAUSED"} | O | ACTIVE -> PAUSED 전이 허용 |
| 2. 하위 광고그룹/키워드/소재 노출 중단 | 캠페인 중지 시 하위 일괄 중단 | **△** | 하위 엔티티 상태 처리 방식 미명시 (하위 개별 상태는 ACTIVE 유지하되 캠페인 상태로 노출 차단인지, 하위도 일괄 PAUSED인지 불명확) |
| 3. 캠페인 재개 | POST /v1/campaigns/{id}/status {status: "ACTIVE"} | O | PAUSED -> ACTIVE 전이 허용 |
| 4. 하위 엔티티 자동 재개 | 기존 ACTIVE였던 하위 엔티티 자동 복원 | **△** | 복원 로직 미정의 |
| 5. 일괄 상태 변경 | POST /v1/campaigns/batch-status | O | 여러 캠페인 일괄 처리 가능 |

---

## 5. 개선 권장사항

### 5.1 단기 개선 (MVP 반영 필요)

1. **[Critical] 계정/인증 API 추가**: 회원가입, 로그인, 토큰 관리, 사업자 인증, 권한 관리 API를 설계해야 한다. 이 없이는 MVP 출시가 불가능하다.

2. **[Critical] 설명 가능한 AI 자동입찰 API 설계**: PRD의 핵심 차별화 기능이므로 Phase 1에서 최소한 입찰 조정 사유 조회 API는 포함되어야 한다.

3. **[Critical] 예산 120% 초과 허용 정책 반영**: 과금 사고를 방지하려면 over-delivery 한도를 데이터 모델과 예산 관리 로직에 반드시 반영해야 한다.

4. **[Major] 캠페인명 글자수 제한 통일**: PRD(100자)와 API(200자) 불일치를 해소해야 한다.

5. **[Major] 충전 최소 금액 유효성 검증 추가**: 10,000원 최소 충전 규칙을 API에 반영해야 한다.

6. **[Major] 제외 키워드 매치 타입 정합성 확보**: PRD(정확+구문)와 API(정확+구문+확장) 불일치를 해소해야 한다.

7. **[Major] 표시 URL 구조 상세화**: display_url의 도메인+경로 분리 구조를 API에 반영해야 한다.

### 5.2 중장기 개선 (이후 Phase 반영)

1. **자동 리포트 스케줄 API**: 일간/주간/월간 자동 발송 기능을 위한 스케줄 관리 API를 Phase 2에서 설계한다.

2. **소재 버전 관리 메커니즘**: 소재 수정 시 이전 승인 버전을 유지하는 버전 관리 시스템을 도입한다. 수정 중 기존 승인 소재 노출 유지를 위해 필요하다.

3. **Ad Extension Factor 산출 기반 마련**: 확장 소재 성과 데이터 수집 테이블을 설계하여 Extension Factor(1.0~1.2)를 데이터 기반으로 산출할 수 있도록 한다.

4. **Excel 다운로드 지원 명시**: 리포트 다운로드 format에 XLSX를 추가한다.

5. **리포트 데이터 freshness 메타 추가**: 실시간 데이터와 확정 데이터를 구분할 수 있는 메타 필드를 리포트 응답에 추가한다.

6. **캠페인/광고그룹 상위-하위 상태 연동 정책 문서화**: 캠페인 중지 시 하위 엔티티 처리 방식, 재개 시 복원 방식을 명확히 정의한다.

7. **Phase 1/2 경계 기능 정리**: AI_BROAD 매치 타입, 지역 리포트 등 Phase 경계에 있는 기능의 MVP 포함 여부를 확정한다.

---

## 6. 종합 의견

본 PRD와 API 설계서는 전체적으로 높은 완성도를 보인다. 핵심 도메인 모델(캠페인 계층, GSP 경매, 품질지수, 크레딧 체계)이 일관되게 설계되어 있으며, 데이터 모델과 API 엔드포인트 간의 대응이 체계적이다.

다만, **계정/인증 영역의 API 전면 누락**은 MVP 출시를 차단하는 블로커이며, PRD의 핵심 차별화 포인트인 **설명 가능한 AI 자동입찰**에 대한 API 미설계는 상품 경쟁력 측면에서 반드시 보완해야 한다. 과금 관련 **120% 초과 허용 정책** 미반영은 재무적 리스크를 수반하므로 우선적으로 해결되어야 한다.

Critical 3건을 해결하고 Major 이슈를 순차적으로 보완하면, 이 설계를 기반으로 안정적인 MVP 출시가 가능할 것으로 판단한다.
