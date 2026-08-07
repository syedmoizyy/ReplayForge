alter table replay_runs
    add column violations jsonb,
    add column divergence_report jsonb,
    add column divergence_report_markdown text;
