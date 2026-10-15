CREATE TABLE ad_settings (
    id SMALLINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    publisher_id VARCHAR(32) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ad_settings_singleton CHECK (id = 1)
);

INSERT INTO ad_settings (id, enabled, publisher_id) VALUES (1, FALSE, '');

CREATE TABLE ad_slots (
    slot_key VARCHAR(40) PRIMARY KEY,
    page_key VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ad_unit_id VARCHAR(20) NOT NULL DEFAULT '',
    impressions BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO ad_slots (slot_key, page_key) VALUES
    ('home-top', 'home'),
    ('home-feed', 'home'),
    ('job-search-inline', 'search'),
    ('job-detail-bottom', 'job'),
    ('category-content', 'category'),
    ('info-content', 'info'),
    ('resource-content', 'resources');
