-- Phase 3：审核记录补充版本号字段（任务书要求记录操作时的乐观锁版本）
ALTER TABLE review_records
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER comment;
