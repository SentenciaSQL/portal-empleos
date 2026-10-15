ALTER TABLE users
    ADD COLUMN locale VARCHAR(5) NOT NULL DEFAULT 'es';

ALTER TABLE job_alerts
    ADD COLUMN municipality_id INTEGER REFERENCES municipalities (id),
    ADD COLUMN contract_type VARCHAR(32),
    ADD COLUMN salary_min NUMERIC(12, 2),
    ADD COLUMN salary_max NUMERIC(12, 2),
    ADD COLUMN salary_currency VARCHAR(3) NOT NULL DEFAULT 'DOP',
    ADD COLUMN frequency VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    ADD COLUMN last_notified_at TIMESTAMPTZ;

ALTER TABLE job_alerts
    ADD CONSTRAINT chk_job_alerts_salary CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max);

CREATE TABLE job_alert_deliveries (
    alert_id UUID NOT NULL REFERENCES job_alerts (id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    notified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, job_id)
);

ALTER TABLE company_subscriptions
    ADD COLUMN provider VARCHAR(32),
    ADD COLUMN provider_subscription_id VARCHAR(80),
    ADD COLUMN provider_plan_id VARCHAR(80);

CREATE INDEX idx_company_subscriptions_provider ON company_subscriptions (provider, provider_subscription_id);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    plan_id INTEGER REFERENCES subscription_plans (id),
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(80),
    provider_payment_id VARCHAR(80),
    provider_subscription_id VARCHAR(80),
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_type VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_payment_provider_event ON payment_transactions (provider, provider_event_id) WHERE provider_event_id IS NOT NULL;
CREATE INDEX idx_payment_transactions_subscription ON payment_transactions (provider, provider_subscription_id);

CREATE TABLE payment_provider_refs (
    provider VARCHAR(32) NOT NULL,
    ref_key VARCHAR(40) NOT NULL,
    ref_value VARCHAR(80) NOT NULL,
    PRIMARY KEY (provider, ref_key)
);
