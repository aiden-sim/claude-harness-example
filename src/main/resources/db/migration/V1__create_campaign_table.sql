CREATE TABLE campaign (
    id                             BIGSERIAL       PRIMARY KEY,
    account_id                     BIGINT          NOT NULL,
    name                           VARCHAR(200)    NOT NULL,
    type                           VARCHAR(20)     NOT NULL,
    status                         VARCHAR(20)     NOT NULL DEFAULT 'PAUSED',
    daily_budget                   DECIMAL(15, 2)  NOT NULL,
    monthly_budget                 DECIMAL(15, 2),
    start_date                     DATE            NOT NULL,
    end_date                       DATE,
    bid_strategy_type              VARCHAR(20)     NOT NULL DEFAULT 'MANUAL_CPC',
    target_cpa                     DECIMAL(15, 2),
    target_roas                    DECIMAL(5, 2),
    device_targeting               JSONB           NOT NULL DEFAULT '{"pc": true, "mobile": true, "pc_bid_adjustment": 0, "mobile_bid_adjustment": 0}',
    region_targeting               JSONB,
    schedule                       JSONB,
    daily_budget_overdelivery_rate DECIMAL(3, 2)   NOT NULL DEFAULT 1.20,
    monthly_budget_cap             DECIMAL(15, 2),
    created_at                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at                     TIMESTAMP,

    CONSTRAINT chk_daily_budget_min   CHECK (daily_budget >= 1000),
    CONSTRAINT chk_type               CHECK (type IN ('SEARCH', 'SHOPPING_SEARCH')),
    CONSTRAINT chk_status             CHECK (status IN ('ACTIVE', 'PAUSED', 'BUDGET_EXHAUSTED', 'ENDED', 'UNDER_REVIEW', 'LIMITED', 'DELETED')),
    CONSTRAINT chk_bid_strategy       CHECK (bid_strategy_type IN ('MANUAL_CPC', 'MAXIMIZE_CLICKS', 'TARGET_CPA', 'TARGET_ROAS'))
);

CREATE INDEX idx_campaign_account_status ON campaign (account_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_campaign_account_type   ON campaign (account_id, type)   WHERE deleted_at IS NULL;
CREATE INDEX idx_campaign_deleted_at     ON campaign (deleted_at)         WHERE deleted_at IS NOT NULL;
