-- Apply once after 20261002-ab-v2.sql on databases that already have ab_waiting_dependency.
-- It aligns waiting-task identity with ab_inbox, so unrelated producers can reuse an eventId.
ALTER TABLE ab_waiting_dependency
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (consumer, producer, event_id);
