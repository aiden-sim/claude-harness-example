---
name: keyword-ad-team
description: "키워드 광고 팀 오케스트레이터. '키워드 광고 상품 개발', '광고 상품 만들어줘', '검색 광고 기획' 요청 시 사용."
---

# Keyword Ad Team — 오케스트레이터

키워드 검색 광고 상품 개발 팀을 조율하는 통합 스킬.

## 팀 구성

| 에이전트 | 역할 | 사용 스킬 |
|---------|------|----------|
| market-analyst | 시장·경쟁사 분석 | market-analysis |
| product-planner | 상품 기획 (PRD, 과금, 정책) | product-planning |
| api-designer | API 스펙·데이터 모델 설계 | api-design |
| reviewer | 기획서↔설계 교차 검토 | design-review |

## 워크플로우

```
Phase 1: market-analyst → 시장·경쟁사 분석 (market-analysis)
Phase 2: product-planner → PRD·과금 모델·정책 기획 (product-planning)
Phase 3: api-designer → API 스펙·데이터 모델 설계 (api-design)
Phase 4: reviewer → 기획서↔설계 정합성 검토 (design-review)
Phase 5: (조건부) Critical 이슈 발견 시 Phase 2~3 재수행 (최대 1회)
```

## 실행 방법

### Phase 1 — 시장·경쟁사 분석
Agent 도구로 `market-analyst` 에이전트를 호출한다:
- subagent_type: `market-analyst`
- prompt에 다음을 포함:
  - "키워드 검색 광고 시장·경쟁사 분석을 수행하라"
  - market-analysis 스킬의 전체 워크플로우
  - 산출물을 `docs/market-analysis.md`에 저장할 것

### Phase 2 — 상품 기획
Phase 1 완료 후, Agent 도구로 `product-planner` 에이전트를 호출한다:
- subagent_type: `product-planner`
- prompt에 다음을 포함:
  - "키워드 검색 광고 상품 기획서(PRD)를 작성하라"
  - `docs/market-analysis.md`의 내용 (Phase 1 결과)
  - product-planning 스킬의 전체 워크플로우
  - 산출물을 `docs/prd.md`에 저장할 것

### Phase 3 — API 설계
Phase 2 완료 후, Agent 도구로 `api-designer` 에이전트를 호출한다:
- subagent_type: `api-designer`
- prompt에 다음을 포함:
  - "키워드 검색 광고 API 스펙과 데이터 모델을 설계하라"
  - `docs/prd.md`의 내용 (Phase 2 결과)
  - api-design 스킬의 전체 워크플로우
  - 산출물을 `docs/api-spec.md`에 저장할 것

### Phase 4 — 교차 검토
Phase 3 완료 후, Agent 도구로 `reviewer` 에이전트를 호출한다:
- subagent_type: `reviewer`
- prompt에 다음을 포함:
  - "기획서와 API 설계서의 정합성을 교차 검토하라"
  - `docs/prd.md`와 `docs/api-spec.md`의 내용
  - design-review 스킬의 전체 워크플로우
  - 산출물을 `docs/review-report.md`에 저장할 것

### Phase 5 — 수정 반영 (조건부)
Phase 4의 검토 결과에서 **Critical 이슈**가 있는 경우:
- product-planner 또는 api-designer를 다시 호출하여 해당 이슈를 수정
- 수정 후 reviewer를 한 번 더 호출하여 재검토 (최대 1회)
- Critical 이슈가 없으면 이 단계를 건너뛴다

## 최종 산출물
- `docs/market-analysis.md` — 시장·경쟁사 분석 보고서
- `docs/prd.md` — 상품 기획서 (PRD, 과금 모델, 광고 정책)
- `docs/api-spec.md` — API 스펙 및 데이터 모델 설계서
- `docs/review-report.md` — 교차 검토 리포트