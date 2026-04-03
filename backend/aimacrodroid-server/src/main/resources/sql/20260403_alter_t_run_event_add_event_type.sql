ALTER TABLE t_run_event
ADD COLUMN IF NOT EXISTS event_type VARCHAR(64);

ALTER TABLE t_run_event
ADD COLUMN IF NOT EXISTS event_type_desc VARCHAR(64);

UPDATE t_run_event
SET event_type = 'UNKNOWN'
WHERE event_type IS NULL OR event_type = '';

UPDATE t_run_event
SET event_type_desc = CASE event_type
    WHEN 'STARTED' THEN '任务开始'
    WHEN 'STEP_STARTED' THEN '步骤开始'
    WHEN 'THINKING_UPDATED' THEN '思考更新'
    WHEN 'ACTION_EXECUTED' THEN '动作执行'
    WHEN 'COMPLETED' THEN '任务完成'
    WHEN 'FAILED' THEN '任务失败'
    WHEN 'SCREENSHOT_STARTED' THEN '截图开始'
    WHEN 'SCREENSHOT_COMPLETED' THEN '截图完成'
    ELSE '未知事件'
END
WHERE event_type_desc IS NULL OR event_type_desc = '';
