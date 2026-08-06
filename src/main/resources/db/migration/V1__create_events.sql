create table events (
    insertion_order bigint generated always as identity primary key,
    event_id uuid not null unique,
    event_type varchar(160) not null,
    schema_version integer not null check (schema_version > 0),
    aggregate_id uuid not null,
    correlation_id uuid not null,
    causation_id uuid,
    idempotency_key varchar(255) not null,
    sequence_number bigint not null check (sequence_number > 0),
    occurred_at timestamptz not null,
    recorded_at timestamptz not null,
    payload jsonb not null,
    metadata jsonb not null default '{}'::jsonb,
    unique (aggregate_id, sequence_number),
    unique (aggregate_id, idempotency_key)
);

create index events_aggregate_sequence_idx on events (aggregate_id, sequence_number);
create index events_correlation_order_idx on events (correlation_id, insertion_order);
create index events_recorded_at_idx on events (recorded_at, insertion_order);

create or replace function reject_event_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'events is append-only';
end;
$$;

create trigger events_reject_update_delete before update or delete on events
for each row execute function reject_event_mutation();
