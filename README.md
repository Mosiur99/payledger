# PayLedger

A double-entry ledger and payments platform built with Java and Spring Boot: accounts, deposits and transfers with idempotency keys, a transactional outbox to Kafka, and a separate notification service.

> Work in progress. The design and build plan are being implemented in small, tested parts.

## Modules

| Module | Port | Purpose |
| --- | --- | --- |
| `payments-service` | 8080 | Accounts, ledger, transfers |
| `notification-service` | 8081 | Reacts to payment events |

## Run locally

Requirements: Java 25 and Docker. Maven is not needed; the wrapper downloads it.

```sh
docker compose -f deploy/compose/docker-compose.yml up -d
./mvnw -pl payments-service spring-boot:run
./mvnw -pl notification-service spring-boot:run
```

Check health at http://localhost:8080/actuator/health and http://localhost:8081/actuator/health.

## API

Until authentication is added, the caller is identified by the `X-Owner-Id` header.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/accounts` | Open an account in `BDT`, `EUR` or `USD` |
| `GET` | `/api/v1/accounts` | List the caller's accounts, oldest first |
| `GET` | `/api/v1/accounts/{id}` | Read one of the caller's accounts |
| `GET` | `/api/v1/accounts/{id}/statement?limit=50&before={entryId}` | Ledger entries, newest first, with a cursor for older pages |
| `POST` | `/api/v1/deposits` | Fund an account from the system account of its currency |
| `POST` | `/api/v1/transfers` | Move money from one of the caller's accounts to another account in the same currency |

```sh
curl -X POST localhost:8080/api/v1/accounts \
  -H 'X-Owner-Id: ayesha' -H 'Content-Type: application/json' \
  -d '{"currency":"BDT"}'
```

Amounts are whole numbers in the currency's smallest unit (paisa, cents), so `balanceMinor: 50000` means 500.00.

## How money is recorded

Every movement is a transaction with exactly two ledger entries: a debit on one account and a credit on another, for the same amount. Entries are append-only. Deposits debit a system account (one per currency, allowed to go negative) and credit the user, so the sum of all balances in each currency is always zero. Accounts are locked in id order before any balance changes, which prevents lost updates and deadlocks. A user account can never go below zero: the domain refuses the debit and a database constraint backs it up.

## Safe retries

`POST /api/v1/transfers` and `POST /api/v1/deposits` require an `Idempotency-Key` header (1 to 64 characters, unique per request, for example a UUID). Keys are scoped to the caller.

| Situation | Response |
| --- | --- |
| New key | The request runs and its response is stored |
| Same key, same request, already finished | The stored response, with `Idempotent-Replayed: true` |
| Same key, still running | `409` |
| Same key, different request | `422` |
| Server error (`5xx`) | The key is released so the client can retry |

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details response (`application/problem+json`) with a stable `type`:

```json
{
  "type": "/problems/insufficient-funds",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "Account 0199a… has 12000 available; 50000 requested.",
  "instance": "/api/v1/transfers",
  "accountId": "0199a…",
  "availableMinor": 12000,
  "requestedMinor": 50000
}
```

| Type | Status | When |
| --- | --- | --- |
| `/problems/validation` | 400 | Invalid or missing fields, headers or parameters; listed under `errors` |
| `/problems/idempotency-key-required` | 400 | Missing `Idempotency-Key` |
| `/problems/account-not-found` | 404 | Unknown account, or one the caller does not own |
| `/problems/account-frozen` | 409 | A frozen account |
| `/problems/idempotency-in-progress` | 409 | The first request with this key is still running |
| `/problems/insufficient-funds` | 422 | Not enough money |
| `/problems/currency-mismatch` | 422 | Accounts in different currencies |
| `/problems/same-account-transfer` | 422 | Source and destination are the same |
| `/problems/idempotency-key-reused` | 422 | The key was used for a different request |
| `/problems/internal` | 500 | Unexpected failure; no internal details are exposed |

## API docs and demo

- Swagger UI: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`)
- Postman: import `docs/postman/PayLedger.postman_collection.json` and run it top to bottom. It opens two accounts, deposits, transfers, shows an insufficient-funds error and reads the statement.

## Test

```sh
./mvnw verify
```

Integration tests start PostgreSQL in Docker with Testcontainers.
