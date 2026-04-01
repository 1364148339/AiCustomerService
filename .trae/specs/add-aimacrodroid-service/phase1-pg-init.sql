begin;

create table if not exists t_scenario (
  id bigint generated always as identity primary key,
  scenario_key varchar(64) not null,
  scenario_name varchar(128) not null,
  scenario_desc varchar(500),
  status varchar(16) not null default 'DRAFT',
  version_no bigint not null default 1,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_scenario_key unique (scenario_key),
  constraint ck_t_scenario_status check (status in ('DRAFT','ACTIVE','DEPRECATED')),
  constraint ck_t_scenario_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_scenario_step (
  id bigint generated always as identity primary key,
  scenario_id bigint not null,
  step_no int not null,
  step_name varchar(128) not null,
  action_code varchar(64) not null,
  action_params jsonb not null default '{}'::jsonb,
  timeout_ms int not null default 5000,
  retry_max int not null default 0,
  retry_backoff_ms int not null default 1000,
  is_enabled smallint not null default 1,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_scenario_step_order unique (scenario_id, step_no, is_deleted),
  constraint ck_t_scenario_step_no check (step_no > 0),
  constraint ck_t_scenario_step_timeout check (timeout_ms > 0),
  constraint ck_t_scenario_step_retry_max check (retry_max >= 0),
  constraint ck_t_scenario_step_backoff check (retry_backoff_ms >= 0),
  constraint ck_t_scenario_step_enabled check (is_enabled in (0,1)),
  constraint ck_t_scenario_step_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_device (
  id bigint generated always as identity primary key,
  device_code varchar(128) not null,
  brand varchar(64) not null,
  model varchar(64) not null,
  android_version varchar(32) not null,
  resolution varchar(32) not null,
  capability_json jsonb not null default '[]'::jsonb,
  token_hash varchar(256) not null,
  device_status varchar(16) not null default 'OFFLINE',
  last_seen_at timestamptz,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_device_code unique (device_code),
  constraint ck_t_device_status check (device_status in ('ONLINE','OFFLINE','UNAVAILABLE')),
  constraint ck_t_device_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_device_readiness (
  id bigint generated always as identity primary key,
  device_id bigint not null,
  foreground_pkg varchar(256),
  battery_pct int,
  network_type varchar(32),
  is_charging smallint not null default 0,
  is_shizuku_available smallint not null default 0,
  is_overlay_granted smallint not null default 0,
  is_keyboard_enabled smallint not null default 0,
  is_sse_supported smallint not null default 0,
  heartbeat_at timestamptz not null default now(),
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_device_readiness_device unique (device_id, is_deleted),
  constraint ck_t_device_readiness_battery check (battery_pct is null or (battery_pct >= 0 and battery_pct <= 100)),
  constraint ck_t_device_readiness_charging check (is_charging in (0,1)),
  constraint ck_t_device_readiness_shizuku check (is_shizuku_available in (0,1)),
  constraint ck_t_device_readiness_overlay check (is_overlay_granted in (0,1)),
  constraint ck_t_device_readiness_keyboard check (is_keyboard_enabled in (0,1)),
  constraint ck_t_device_readiness_sse check (is_sse_supported in (0,1)),
  constraint ck_t_device_readiness_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_task (
  id bigint generated always as identity primary key,
  task_no varchar(64) not null,
  scenario_id bigint not null,
  scenario_key varchar(64) not null,
  scenario_name varchar(128) not null,
  status varchar(16) not null default 'QUEUED',
  priority int not null default 5,
  task_constraints jsonb not null default '{}'::jsonb,
  observability jsonb not null default '{}'::jsonb,
  total_device_count int not null default 0,
  success_device_count int not null default 0,
  fail_device_count int not null default 0,
  started_at timestamptz,
  finished_at timestamptz,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_task_no unique (task_no),
  constraint ck_t_task_status check (status in ('QUEUED','DISPATCHING','RUNNING','SUCCESS','FAIL','CANCELED')),
  constraint ck_t_task_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_task_device_run (
  id bigint generated always as identity primary key,
  task_id bigint not null,
  device_id bigint not null,
  run_status varchar(16) not null default 'PENDING',
  current_step_no int,
  retry_count int not null default 0,
  error_code varchar(64),
  error_message varchar(500),
  started_at timestamptz,
  finished_at timestamptz,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_task_device_run unique (task_id, device_id, is_deleted),
  constraint ck_t_task_device_run_status check (run_status in ('PENDING','RUNNING','SUCCESS','FAIL')),
  constraint ck_t_task_device_run_retry check (retry_count >= 0),
  constraint ck_t_task_device_run_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_step_instance (
  id bigint generated always as identity primary key,
  task_id bigint not null,
  source_step_id bigint,
  step_no int not null,
  step_name varchar(128) not null,
  action_code varchar(64) not null,
  action_params jsonb not null default '{}'::jsonb,
  timeout_ms int not null default 5000,
  retry_max int not null default 0,
  retry_backoff_ms int not null default 1000,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_step_instance_order unique (task_id, step_no, is_deleted),
  constraint ck_t_step_instance_no check (step_no > 0),
  constraint ck_t_step_instance_timeout check (timeout_ms > 0),
  constraint ck_t_step_instance_retry_max check (retry_max >= 0),
  constraint ck_t_step_instance_backoff check (retry_backoff_ms >= 0),
  constraint ck_t_step_instance_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_run_event (
  id bigint generated always as identity primary key,
  event_no varchar(64) not null,
  task_id bigint not null,
  run_id bigint not null,
  step_instance_id bigint,
  event_status varchar(16) not null,
  duration_ms int,
  error_code varchar(64),
  error_message varchar(500),
  trace_json jsonb not null default '[]'::jsonb,
  thinking_text text,
  is_sensitive_screen smallint not null default 0,
  progress_json jsonb not null default '{}'::jsonb,
  screenshot_url varchar(500),
  occurred_at timestamptz not null,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_run_event_no unique (event_no),
  constraint ck_t_run_event_status check (event_status in ('RUNNING','SUCCESS','FAIL')),
  constraint ck_t_run_event_sensitive check (is_sensitive_screen in (0,1)),
  constraint ck_t_run_event_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_snapshot (
  id bigint generated always as identity primary key,
  task_id bigint not null,
  run_id bigint not null,
  event_id bigint,
  screenshot_url varchar(500) not null,
  element_json jsonb not null default '[]'::jsonb,
  foreground_pkg varchar(256),
  captured_at timestamptz not null,
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint ck_t_snapshot_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_alert (
  id bigint generated always as identity primary key,
  alert_no varchar(64) not null,
  task_id bigint not null,
  run_id bigint,
  step_instance_id bigint,
  alert_level varchar(16) not null,
  alert_type varchar(32) not null,
  alert_status varchar(16) not null default 'OPEN',
  error_code varchar(64),
  detail_json jsonb not null default '{}'::jsonb,
  first_occur_at timestamptz not null default now(),
  last_occur_at timestamptz not null default now(),
  close_reason varchar(500),
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint uk_t_alert_no unique (alert_no),
  constraint ck_t_alert_level check (alert_level in ('LOW','MEDIUM','HIGH')),
  constraint ck_t_alert_type check (alert_type in ('FAIL','TIMEOUT','RETRY_EXCEEDED')),
  constraint ck_t_alert_status check (alert_status in ('OPEN','ACK','CLOSED')),
  constraint ck_t_alert_is_deleted check (is_deleted in (0,1))
);

create table if not exists t_audit_log (
  id bigint generated always as identity primary key,
  trace_id varchar(64),
  operator_id varchar(64) not null,
  operator_role varchar(32) not null,
  action_code varchar(64) not null,
  biz_type varchar(32) not null,
  biz_id varchar(128) not null,
  request_ip inet,
  request_payload jsonb not null default '{}'::jsonb,
  result_code varchar(32) not null default 'SUCCESS',
  result_message varchar(500),
  creator varchar(64) not null default 'system',
  modifier varchar(64) not null default 'system',
  gmt_create timestamptz not null default now(),
  gmt_modified timestamptz not null default now(),
  is_deleted smallint not null default 0,
  deleter varchar(64),
  gmt_deleted timestamptz,
  constraint ck_t_audit_log_is_deleted check (is_deleted in (0,1))
);

create index if not exists idx_t_scenario_status on t_scenario(status, gmt_create desc);
create index if not exists idx_t_scenario_step_scenario on t_scenario_step(scenario_id, step_no);
create index if not exists idx_t_device_status on t_device(device_status, gmt_modified desc);
create index if not exists idx_t_device_readiness_hb on t_device_readiness(device_id, heartbeat_at desc);
create index if not exists idx_t_task_status_priority on t_task(status, priority desc, gmt_create desc);
create index if not exists idx_t_task_scenario on t_task(scenario_id, gmt_create desc);
create index if not exists idx_t_task_device_run_task on t_task_device_run(task_id, run_status);
create index if not exists idx_t_task_device_run_device on t_task_device_run(device_id, gmt_create desc);
create index if not exists idx_t_step_instance_task on t_step_instance(task_id, step_no);
create index if not exists idx_t_run_event_run_time on t_run_event(run_id, occurred_at);
create index if not exists idx_t_run_event_task_time on t_run_event(task_id, occurred_at);
create index if not exists idx_t_snapshot_run_time on t_snapshot(run_id, captured_at desc);
create index if not exists idx_t_alert_task_status on t_alert(task_id, alert_status, gmt_create desc);
create index if not exists idx_t_alert_level_time on t_alert(alert_level, gmt_create desc);
create index if not exists idx_t_audit_log_biz on t_audit_log(biz_type, biz_id, gmt_create desc);

comment on table t_scenario is '场景定义表';
comment on column t_scenario.id is '主键ID';
comment on column t_scenario.scenario_key is '场景唯一标识Key';
comment on column t_scenario.scenario_name is '场景名称';
comment on column t_scenario.scenario_desc is '场景描述';
comment on column t_scenario.status is '场景状态（DRAFT/ACTIVE/DEPRECATED）';
comment on column t_scenario.version_no is '场景版本号（每次发布递增）';
comment on column t_scenario.creator is '创建人';
comment on column t_scenario.modifier is '修改人';
comment on column t_scenario.gmt_create is '创建时间';
comment on column t_scenario.gmt_modified is '修改时间';
comment on column t_scenario.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_scenario.deleter is '删除人';
comment on column t_scenario.gmt_deleted is '删除时间';

comment on table t_scenario_step is '场景步骤表';
comment on column t_scenario_step.id is '主键ID';
comment on column t_scenario_step.scenario_id is '场景ID（逻辑关联）';
comment on column t_scenario_step.step_no is '步骤序号（从1开始）';
comment on column t_scenario_step.step_name is '步骤名称';
comment on column t_scenario_step.action_code is '动作编码';
comment on column t_scenario_step.action_params is '动作参数JSON';
comment on column t_scenario_step.timeout_ms is '步骤超时时间（毫秒）';
comment on column t_scenario_step.retry_max is '最大重试次数';
comment on column t_scenario_step.retry_backoff_ms is '重试退避时间（毫秒）';
comment on column t_scenario_step.is_enabled is '是否启用（0禁用，1启用）';
comment on column t_scenario_step.creator is '创建人';
comment on column t_scenario_step.modifier is '修改人';
comment on column t_scenario_step.gmt_create is '创建时间';
comment on column t_scenario_step.gmt_modified is '修改时间';
comment on column t_scenario_step.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_scenario_step.deleter is '删除人';
comment on column t_scenario_step.gmt_deleted is '删除时间';

comment on table t_device is '设备主表';
comment on column t_device.id is '主键ID';
comment on column t_device.device_code is '设备唯一编码';
comment on column t_device.brand is '设备品牌';
comment on column t_device.model is '设备型号';
comment on column t_device.android_version is 'Android系统版本';
comment on column t_device.resolution is '屏幕分辨率';
comment on column t_device.capability_json is '设备能力集合JSON';
comment on column t_device.token_hash is '设备鉴权Token哈希值';
comment on column t_device.device_status is '设备状态（ONLINE/OFFLINE/UNAVAILABLE）';
comment on column t_device.last_seen_at is '最近心跳时间';
comment on column t_device.creator is '创建人';
comment on column t_device.modifier is '修改人';
comment on column t_device.gmt_create is '创建时间';
comment on column t_device.gmt_modified is '修改时间';
comment on column t_device.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_device.deleter is '删除人';
comment on column t_device.gmt_deleted is '删除时间';

comment on table t_device_readiness is '设备就绪快照表';
comment on column t_device_readiness.id is '主键ID';
comment on column t_device_readiness.device_id is '设备ID（逻辑关联）';
comment on column t_device_readiness.foreground_pkg is '前台应用包名';
comment on column t_device_readiness.battery_pct is '电量百分比（0-100）';
comment on column t_device_readiness.network_type is '网络类型（WIFI/4G/5G）';
comment on column t_device_readiness.is_charging is '是否充电（0否，1是）';
comment on column t_device_readiness.is_shizuku_available is 'Shizuku是否可用（0否，1是）';
comment on column t_device_readiness.is_overlay_granted is '悬浮窗权限是否授予（0否，1是）';
comment on column t_device_readiness.is_keyboard_enabled is '辅助输入是否启用（0否，1是）';
comment on column t_device_readiness.is_sse_supported is '设备是否支持SSE（0否，1是）';
comment on column t_device_readiness.heartbeat_at is '最近一次心跳时间';
comment on column t_device_readiness.creator is '创建人';
comment on column t_device_readiness.modifier is '修改人';
comment on column t_device_readiness.gmt_create is '创建时间';
comment on column t_device_readiness.gmt_modified is '修改时间';
comment on column t_device_readiness.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_device_readiness.deleter is '删除人';
comment on column t_device_readiness.gmt_deleted is '删除时间';

comment on table t_task is '任务主表';
comment on column t_task.id is '主键ID';
comment on column t_task.task_no is '任务编号';
comment on column t_task.scenario_id is '场景ID（逻辑关联）';
comment on column t_task.scenario_key is '场景Key快照';
comment on column t_task.scenario_name is '场景名称快照';
comment on column t_task.status is '任务状态（QUEUED/DISPATCHING/RUNNING/SUCCESS/FAIL/CANCELED）';
comment on column t_task.priority is '优先级（数值越大优先级越高）';
comment on column t_task.task_constraints is '任务约束JSON';
comment on column t_task.observability is '观测配置JSON';
comment on column t_task.total_device_count is '目标设备总数';
comment on column t_task.success_device_count is '成功设备数';
comment on column t_task.fail_device_count is '失败设备数';
comment on column t_task.started_at is '任务开始时间';
comment on column t_task.finished_at is '任务结束时间';
comment on column t_task.creator is '创建人';
comment on column t_task.modifier is '修改人';
comment on column t_task.gmt_create is '创建时间';
comment on column t_task.gmt_modified is '修改时间';
comment on column t_task.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_task.deleter is '删除人';
comment on column t_task.gmt_deleted is '删除时间';

comment on table t_task_device_run is '任务设备运行表';
comment on column t_task_device_run.id is '主键ID';
comment on column t_task_device_run.task_id is '任务ID（逻辑关联）';
comment on column t_task_device_run.device_id is '设备ID（逻辑关联）';
comment on column t_task_device_run.run_status is '运行状态（PENDING/RUNNING/SUCCESS/FAIL）';
comment on column t_task_device_run.current_step_no is '当前执行步骤序号';
comment on column t_task_device_run.retry_count is '已重试次数';
comment on column t_task_device_run.error_code is '错误码';
comment on column t_task_device_run.error_message is '错误描述';
comment on column t_task_device_run.started_at is '开始时间';
comment on column t_task_device_run.finished_at is '结束时间';
comment on column t_task_device_run.creator is '创建人';
comment on column t_task_device_run.modifier is '修改人';
comment on column t_task_device_run.gmt_create is '创建时间';
comment on column t_task_device_run.gmt_modified is '修改时间';
comment on column t_task_device_run.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_task_device_run.deleter is '删除人';
comment on column t_task_device_run.gmt_deleted is '删除时间';

comment on table t_step_instance is '任务步骤快照表';
comment on column t_step_instance.id is '主键ID';
comment on column t_step_instance.task_id is '任务ID（逻辑关联）';
comment on column t_step_instance.source_step_id is '来源步骤ID（逻辑关联）';
comment on column t_step_instance.step_no is '步骤序号';
comment on column t_step_instance.step_name is '步骤名称';
comment on column t_step_instance.action_code is '动作编码';
comment on column t_step_instance.action_params is '动作参数JSON';
comment on column t_step_instance.timeout_ms is '步骤超时时间（毫秒）';
comment on column t_step_instance.retry_max is '最大重试次数';
comment on column t_step_instance.retry_backoff_ms is '重试退避时间（毫秒）';
comment on column t_step_instance.creator is '创建人';
comment on column t_step_instance.modifier is '修改人';
comment on column t_step_instance.gmt_create is '创建时间';
comment on column t_step_instance.gmt_modified is '修改时间';
comment on column t_step_instance.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_step_instance.deleter is '删除人';
comment on column t_step_instance.gmt_deleted is '删除时间';

comment on table t_run_event is '运行事件表';
comment on column t_run_event.id is '主键ID';
comment on column t_run_event.event_no is '事件编号';
comment on column t_run_event.task_id is '任务ID（逻辑关联）';
comment on column t_run_event.run_id is '运行实例ID（逻辑关联）';
comment on column t_run_event.step_instance_id is '步骤快照ID（逻辑关联）';
comment on column t_run_event.event_status is '事件状态（RUNNING/SUCCESS/FAIL）';
comment on column t_run_event.duration_ms is '耗时（毫秒）';
comment on column t_run_event.error_code is '错误码';
comment on column t_run_event.error_message is '错误描述';
comment on column t_run_event.trace_json is '执行轨迹JSON';
comment on column t_run_event.thinking_text is '思考摘要文本';
comment on column t_run_event.is_sensitive_screen is '是否命中敏感页面（0否，1是）';
comment on column t_run_event.progress_json is '执行进度JSON';
comment on column t_run_event.screenshot_url is '截图地址';
comment on column t_run_event.occurred_at is '事件发生时间';
comment on column t_run_event.creator is '创建人';
comment on column t_run_event.modifier is '修改人';
comment on column t_run_event.gmt_create is '创建时间';
comment on column t_run_event.gmt_modified is '修改时间';
comment on column t_run_event.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_run_event.deleter is '删除人';
comment on column t_run_event.gmt_deleted is '删除时间';

comment on table t_snapshot is '截图快照表';
comment on column t_snapshot.id is '主键ID';
comment on column t_snapshot.task_id is '任务ID（逻辑关联）';
comment on column t_snapshot.run_id is '运行实例ID（逻辑关联）';
comment on column t_snapshot.event_id is '事件ID（逻辑关联）';
comment on column t_snapshot.screenshot_url is '截图地址';
comment on column t_snapshot.element_json is '页面元素JSON';
comment on column t_snapshot.foreground_pkg is '前台应用包名';
comment on column t_snapshot.captured_at is '截图采集时间';
comment on column t_snapshot.creator is '创建人';
comment on column t_snapshot.modifier is '修改人';
comment on column t_snapshot.gmt_create is '创建时间';
comment on column t_snapshot.gmt_modified is '修改时间';
comment on column t_snapshot.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_snapshot.deleter is '删除人';
comment on column t_snapshot.gmt_deleted is '删除时间';

comment on table t_alert is '告警表';
comment on column t_alert.id is '主键ID';
comment on column t_alert.alert_no is '告警编号';
comment on column t_alert.task_id is '任务ID（逻辑关联）';
comment on column t_alert.run_id is '运行实例ID（逻辑关联）';
comment on column t_alert.step_instance_id is '步骤快照ID（逻辑关联）';
comment on column t_alert.alert_level is '告警级别（LOW/MEDIUM/HIGH）';
comment on column t_alert.alert_type is '告警类型（FAIL/TIMEOUT/RETRY_EXCEEDED）';
comment on column t_alert.alert_status is '告警状态（OPEN/ACK/CLOSED）';
comment on column t_alert.error_code is '错误码';
comment on column t_alert.detail_json is '告警详情JSON';
comment on column t_alert.first_occur_at is '首次发生时间';
comment on column t_alert.last_occur_at is '最近发生时间';
comment on column t_alert.close_reason is '关闭原因';
comment on column t_alert.creator is '创建人';
comment on column t_alert.modifier is '修改人';
comment on column t_alert.gmt_create is '创建时间';
comment on column t_alert.gmt_modified is '修改时间';
comment on column t_alert.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_alert.deleter is '删除人';
comment on column t_alert.gmt_deleted is '删除时间';

comment on table t_audit_log is '审计日志表';
comment on column t_audit_log.id is '主键ID';
comment on column t_audit_log.trace_id is '追踪ID';
comment on column t_audit_log.operator_id is '操作人ID';
comment on column t_audit_log.operator_role is '操作人角色';
comment on column t_audit_log.action_code is '操作编码';
comment on column t_audit_log.biz_type is '业务对象类型';
comment on column t_audit_log.biz_id is '业务对象ID';
comment on column t_audit_log.request_ip is '请求IP';
comment on column t_audit_log.request_payload is '请求载荷JSON';
comment on column t_audit_log.result_code is '处理结果编码';
comment on column t_audit_log.result_message is '处理结果描述';
comment on column t_audit_log.creator is '创建人';
comment on column t_audit_log.modifier is '修改人';
comment on column t_audit_log.gmt_create is '创建时间';
comment on column t_audit_log.gmt_modified is '修改时间';
comment on column t_audit_log.is_deleted is '逻辑删除标记（0未删除，1已删除）';
comment on column t_audit_log.deleter is '删除人';
comment on column t_audit_log.gmt_deleted is '删除时间';

commit;
