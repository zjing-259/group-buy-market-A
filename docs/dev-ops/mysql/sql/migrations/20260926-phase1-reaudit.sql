-- Requires MySQL 8 with utf8mb4_0900_bin (verified on 8.0.32).
-- Apply after 20260926-phase1.sql, with old application writers stopped.
-- Preserve the original external ID exactly. 0900_bin is binary and NO PAD.
-- Preflight: audit exact duplicate (source, channel, out_trade_no COLLATE utf8mb4_0900_bin) keys.
ALTER TABLE group_buy_order_list
    MODIFY COLUMN out_trade_no VARCHAR(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Exact external transaction ID',
    MODIFY COLUMN out_trade_time DATETIME(3) NULL DEFAULT NULL COMMENT 'Payment fact with millisecond precision';

ALTER TABLE trade_request_guard
    MODIFY COLUMN out_trade_no VARCHAR(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL;

ALTER TABLE notify_task
    MODIFY COLUMN notify_url VARCHAR(512) NOT NULL COMMENT 'Callback URL; same limit as accepted lock requests';

-- Existing values are preserved. Lost historical milliseconds cannot be reconstructed.
