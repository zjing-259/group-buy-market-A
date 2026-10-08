-- Apply once after the duplicate-key audit. Additive migration; does not rewrite historical orders.
-- Stop old application instances before migration and deploy the matching application afterward.
-- Business key: (source, channel, out_trade_no); user_id is an immutable attribute, not an isolation key.
ALTER TABLE group_buy_order_list
    ADD COLUMN pay_price DECIMAL(8,2) NULL COMMENT 'Actual payable snapshot; NULL for unaudited legacy orders',
    ADD COLUMN request_team_id VARCHAR(8) NULL COMMENT 'Empty for new team; NULL means unknown legacy request',
    ADD COLUMN notify_url VARCHAR(512) NULL COMMENT 'Immutable request callback',
    ADD UNIQUE KEY uq_sc_out_trade (source, channel, out_trade_no);

ALTER TABLE group_buy_order
    ADD COLUMN goods_id VARCHAR(16) NULL COMMENT 'Team SKU; NULL legacy teams cannot accept new participants';

CREATE TABLE trade_request_guard (
    source VARCHAR(8) NOT NULL,
    channel VARCHAR(8) NOT NULL,
    out_trade_no VARCHAR(12) NOT NULL,
    PRIMARY KEY (source, channel, out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trade_quota_guard (
    user_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
