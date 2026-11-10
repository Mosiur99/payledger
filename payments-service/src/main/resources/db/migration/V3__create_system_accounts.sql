CREATE UNIQUE INDEX ux_accounts_one_system_per_currency ON accounts (currency) WHERE type = 'SYSTEM';

INSERT INTO accounts (id, owner_id, currency, type, status, balance_minor, created_at)
SELECT gen_random_uuid(), 'system', currency, 'SYSTEM', 'ACTIVE', 0, now()
FROM (VALUES ('BDT'), ('EUR'), ('USD')) AS supported (currency);
