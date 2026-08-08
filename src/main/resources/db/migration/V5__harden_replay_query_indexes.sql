create index replay_runs_recent_idx on replay_runs(created_at desc);
create index replay_runs_active_idx on replay_runs(status, created_at) where status in ('QUEUED', 'RUNNING');
