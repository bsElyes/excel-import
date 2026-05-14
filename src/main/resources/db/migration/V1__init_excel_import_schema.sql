create sequence if not exists customer_record_seq start with 1 increment by 50;

create table if not exists customer_records (
    id bigint primary key,
    business_key varchar(64) not null,
    name varchar(120) not null,
    email varchar(180) not null,
    amount decimal(19, 2) not null,
    last_import_id varchar(36),
    updated_at timestamp not null,
    version bigint not null default 0,
    constraint uk_customer_records_business_key unique (business_key)
);

create index if not exists idx_customer_records_business_key on customer_records(business_key);

create table if not exists import_jobs (
    import_id varchar(36) primary key,
    import_type varchar(80) not null,
    file_name varchar(255),
    status varchar(40) not null,
    message varchar(1000),
    total_rows int not null default 0,
    created_rows int not null default 0,
    updated_rows int not null default 0,
    skipped_rows int not null default 0,
    blocking_error_count int not null default 0,
    warning_count int not null default 0,
    duration_millis bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists import_issues (
    import_id varchar(36) not null,
    severity varchar(40) not null,
    row_number int not null,
    field_name varchar(120),
    code varchar(120) not null,
    message varchar(1000) not null
);

create index if not exists idx_import_issues_import_id on import_issues(import_id);

create table if not exists customer_import_staging (
    import_id varchar(36) not null,
    row_number int not null,
    business_key varchar(64),
    name varchar(120),
    email varchar(180),
    email_invalid boolean not null,
    amount decimal(19, 2),
    amount_invalid boolean not null
);

create index if not exists idx_customer_import_staging_import_id
    on customer_import_staging(import_id);

create index if not exists idx_customer_import_staging_business_key
    on customer_import_staging(import_id, business_key);
