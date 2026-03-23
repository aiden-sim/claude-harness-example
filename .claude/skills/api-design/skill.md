---
name: api-design
description: "키워드 광고 API 스펙·데이터 모델 설계 스킬. api-designer 에이전트가 사용하는 스킬."
---

# API Design — 키워드 광고 API 스펙·데이터 모델 설계

## 워크플로우

### Step 1: PRD 분석
- `docs/prd.md` 파일을 읽고 기능 요구사항 목록 추출
- 각 기능을 API 리소스·액션으로 매핑

### Step 2: 데이터 모델 설계
- 핵심 엔티티 정의:
  - Account (광고주 계정)
  - Campaign (캠페인)
  - AdGroup (광고그룹)
  - Keyword (키워드)
  - Ad (광고 소재)
  - Budget (예산)
  - BidHistory (입찰 이력)
  - Report (리포트)
- 엔티티 간 관계(1:N, N:M) 정의
- ERD 텍스트 다이어그램 작성

### Step 3: API 엔드포인트 설계
- 캠페인 관리 API: CRUD, 상태 변경, 일정 설정
- 광고그룹 관리 API: CRUD, 타겟팅 설정
- 키워드 관리 API: CRUD, 매치 타입, 제외 키워드, 입찰가 설정
- 광고 소재 관리 API: CRUD, 심사 상태 조회
- 입찰 API: 입찰가 조회/변경, 추천 입찰가
- 리포팅 API: 일별/기간별 성과 조회, 키워드별 성과
- 각 엔드포인트별 HTTP 메서드, 경로, 요청/응답 스키마 정의

### Step 4: 공통 규약 정의
- 인증 방식 (API Key + OAuth 2.0)
- 페이지네이션 (커서 기반)
- 에러 응답 형식 및 코드 체계
- Rate Limit 정책
- API 버전 관리 전략

### Step 5: 시스템 아키텍처 개요
- 주요 컴포넌트: API Gateway, Campaign Service, Bidding Engine, Reporting Service
- 데이터 흐름 다이어그램
- 외부 연동 포인트 (결제, 검색 엔진, 광고 심사)

### Step 6: 산출물 작성
- `docs/api-spec.md` 파일로 API 설계서 작성
- 출력 형식은 api-designer 에이전트의 출력 형식을 따른다