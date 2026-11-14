ALTER TABLE activity ADD COLUMN published_at DATETIME NULL;

UPDATE activity
SET published_at = updated_at, updated_at = updated_at
WHERE status = 'PUBLISHED';
