create table reservation_projection (
    reservation_id uuid primary key,
    correlation_id uuid not null,
    status varchar(40) not null,
    deposit_amount bigint not null,
    currency char(3) not null,
    auto_payout boolean not null default false,
    deposit_authorized boolean not null default false,
    refund_status varchar(40) not null default 'NONE',
    payout_status varchar(40) not null default 'NONE',
    last_sequence_number bigint not null,
    updated_at timestamptz not null
);

create index reservation_projection_correlation_idx on reservation_projection(correlation_id);

create table consumer_receipts (
    consumer_name varchar(80) not null,
    event_id uuid not null,
    processed_at timestamptz not null default now(),
    primary key (consumer_name, event_id)
);

create table workflow_outbox (
    event_id uuid primary key references events(event_id),
    envelope jsonb not null,
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create index workflow_outbox_pending_idx on workflow_outbox(created_at) where published_at is null;
