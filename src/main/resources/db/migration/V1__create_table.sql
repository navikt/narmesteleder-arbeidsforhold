CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table narmesteleder
(
    narmeste_leder_id       VARCHAR primary key         not null,
    orgnummer               VARCHAR                     not null,
    bruker_fnr              VARCHAR                     not null,
    arbeidsforhold_tom_date DATE,
    last_update             TIMESTAMP with time zone
);

create index narmesteleder_last_update_idx on narmesteleder (last_update);
create index narmesteleder_arbeidsforhold_tom_date_idx on narmesteleder (arbeidsforhold_tom_date);
