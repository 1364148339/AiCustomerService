ALTER TABLE t_device ADD COLUMN IF NOT EXISTS alias VARCHAR(128);
UPDATE t_device SET alias = device_code WHERE alias IS NULL OR alias = '';
