create table replay_runs (
    replay_id uuid primary key,
    source_correlation_id uuid not null,
    checkpoint bigint not null check (checkpoint >= 0),
    seed bigint not null,
    clock_mode varchar(40) not null,
    status varchar(40) not null,
    created_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    output_summary jsonb,
    final_state jsonb,
    error_message varchar(1000)
);

create index replay_runs_source_idx on replay_runs(source_correlation_id, created_at);

create table replay_events (
    replay_id uuid not null references replay_runs(replay_id) on delete cascade,
    replay_order bigint not null,
    source_event_id uuid not null,
    replay_event_id uuid not null,
    envelope jsonb not null,
    primary key (replay_id, replay_order),
    unique (replay_id, source_event_id),
    unique (replay_id, replay_event_id)
);

create table replay_decisions (
    replay_id uuid not null references replay_runs(replay_id) on delete cascade,
    decision_order bigint not null,
    source_event_id uuid not null,
    replay_event_id uuid,
    decision_type varchar(60) not null,
    logical_time timestamptz not null,
    detail jsonb not null,
    primary key (replay_id, decision_order)
);
