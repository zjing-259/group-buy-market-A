-- Apply once to the A database before enabling the v2 Dubbo and RocketMQ adapters.
-- Existing facts remain unchanged; only the shared activity mapping and tag member ID capacity grow.
ALTER TABLE sc_sku_activity MODIFY goods_id varchar(64) NOT NULL;
ALTER TABLE crowd_tags_detail MODIFY user_id varchar(64) NOT NULL;
CREATE TABLE IF NOT EXISTS ab_product_projection (
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  goods_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  product_version bigint unsigned NOT NULL, goods_name varchar(128) NOT NULL,
  original_price decimal(8,2) NOT NULL, currency char(3) NOT NULL,
  sale_status varchar(16) NOT NULL, content_hash char(64) NOT NULL,
  updated_at datetime(3) NOT NULL,
  PRIMARY KEY(source,channel,goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_team (
  team_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  activity_id bigint NOT NULL, goods_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  target_count int NOT NULL, reserved_count int NOT NULL, paid_count int NOT NULL,
  team_status varchar(24) NOT NULL, team_version bigint unsigned NOT NULL,
  valid_start_time datetime(3) NOT NULL, valid_end_time datetime(3) NOT NULL,
  reconciliation_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  roster_version varchar(64) DEFAULT NULL, roster_json text DEFAULT NULL,
  completion_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  completion_hash char(64) DEFAULT NULL, resolved_at datetime(3) DEFAULT NULL,
  PRIMARY KEY(team_id), KEY idx_channel_state_end(source,channel,team_status,valid_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_trade_order (
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  out_trade_no varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  record_kind varchar(16) NOT NULL, order_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  user_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  goods_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  activity_id bigint DEFAULT NULL, product_version bigint unsigned DEFAULT NULL,
  quantity int DEFAULT NULL, request_team_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  team_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  original_price decimal(8,2) DEFAULT NULL, deduction_price decimal(8,2) DEFAULT NULL,
  pay_price decimal(8,2) DEFAULT NULL, currency char(3) DEFAULT NULL,
  reserved_at datetime(3) DEFAULT NULL, payment_deadline datetime(3) DEFAULT NULL,
  order_status varchar(16) NOT NULL, record_version bigint unsigned NOT NULL,
  payment_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  paid_amount decimal(8,2) DEFAULT NULL, paid_at datetime(3) DEFAULT NULL,
  close_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  close_hash char(64) DEFAULT NULL, closed_at datetime(3) DEFAULT NULL,
  refund_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  refund_status varchar(16) DEFAULT NULL, refund_version bigint unsigned DEFAULT NULL,
  refund_amount decimal(8,2) DEFAULT NULL, refund_hash char(64) DEFAULT NULL,
  PRIMARY KEY(source,channel,out_trade_no), UNIQUE KEY uq_ab_order_id(order_id),
  KEY idx_ab_team(team_id), KEY idx_ab_user_activity(user_id,activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_payment_guard (
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  payment_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  out_trade_no varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  PRIMARY KEY(source,channel,payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_trade_guard (
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  out_trade_no varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  PRIMARY KEY(source,channel,out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_inbox (
  consumer varchar(32) NOT NULL, producer varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  event_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  event_type varchar(48) NOT NULL, body_hash char(64) NOT NULL,
  processing_status varchar(24) NOT NULL, outcome varchar(16) DEFAULT NULL,
  result_code varchar(64) DEFAULT NULL, result_info varchar(256) DEFAULT NULL,
  result_json text DEFAULT NULL, processed_at datetime(3) DEFAULT NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(consumer,producer,event_id), KEY idx_ab_inbox_event(event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_outbox (
  event_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  topic varchar(64) NOT NULL, event_type varchar(48) NOT NULL,
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  body mediumtext NOT NULL, body_hash char(64) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING', attempts int NOT NULL DEFAULT 0,
  next_attempt_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  claim_token varchar(64) DEFAULT NULL, lease_until datetime(3) DEFAULT NULL,
  last_error varchar(256) DEFAULT NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at datetime(3) DEFAULT NULL,
  PRIMARY KEY(event_id), KEY idx_ab_outbox_due(status,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_waiting_dependency (
  event_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  consumer varchar(32) NOT NULL, producer varchar(64) NOT NULL,
  body mediumtext NOT NULL, body_hash char(64) NOT NULL,
  next_check_at datetime(3) NOT NULL, attempts int NOT NULL DEFAULT 0,
  PRIMARY KEY(consumer,producer,event_id), KEY idx_ab_waiting_due(next_check_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_quarantine (
  id bigint unsigned NOT NULL AUTO_INCREMENT,
  event_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  reason varchar(128) NOT NULL, body_hash char(64) NOT NULL,
  body mediumtext NOT NULL, created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), KEY idx_ab_quarantine_event(event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ab_exception_payment (
  source varchar(8) COLLATE utf8mb4_0900_bin NOT NULL, channel varchar(8) COLLATE utf8mb4_0900_bin NOT NULL,
  out_trade_no varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  payment_id varchar(64) COLLATE utf8mb4_0900_bin NOT NULL,
  paid_amount decimal(8,2) NOT NULL, paid_at datetime(3) NOT NULL,
  refund_id varchar(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  PRIMARY KEY(source,channel,out_trade_no), UNIQUE KEY uq_ab_exception_payment(source,channel,payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

