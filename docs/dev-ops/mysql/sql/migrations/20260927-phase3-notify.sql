-- Apply once to each schema after phase-one migrations, before starting the new application.
-- Existing success/failure states and counts are preserved; terminal legacy tasks need manual review.
ALTER TABLE notify_task
  MODIFY COLUMN notify_status tinyint(1) NOT NULL COMMENT '0 pending, 1 delivered, 2 retry, 3 exhausted, 4 processing',
  ADD COLUMN claim_token varchar(36) NULL,
  ADD COLUMN lease_until datetime(3) NULL,
  ADD COLUMN next_attempt_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN last_error varchar(256) NULL,
  ADD INDEX idx_notify_due (notify_status, next_attempt_time, id);
