# PayLedger

[![CI](https://github.com/Mosiur99/payledger/actions/workflows/ci.yml/badge.svg)](https://github.com/Mosiur99/payledger/actions/workflows/ci.yml)

A double-entry ledger and payments platform built with Java and Spring Boot: accounts, deposits and transfers with idempotency keys, a transactional outbox to Kafka, and a separate notification service.

> Work in progress. The design and build plan are being implemented in small, tested parts.

## Modules

| Module | Port | Purpose |
| --- | --- | --- |
| `payments-service` | 8080 | Accounts, ledger, transfers |
| `notification-service` | 8081 | Consumes payment events and stores notifications for the people involved |

## Run locally

Requirements: Java 25 and Docker. Maven is not needed; the wrapper downloads it.

```sh
docker compose -f deploy/compose/docker-compose.yml up -d
./mvnw -pl payments-service spring-boot:run
./mvnw -pl notification-service spring-boot:run
```

Compose starts PostgreSQL, Kafka (localhost:9092) and Keycloak (http://localhost:8180, admin console login `admin` / `admin`). Check health at http://localhost:8080/actuator/health and http://localhost:8081/actuator/health.

### Run everything in Docker

```sh
docker compose -f deploy/compose/docker-compose.yml --profile apps up -d --build
```

This builds both services from the root `Dockerfile` and runs them next to the infrastructure on the same ports, so the curl examples, Postman collection and Prometheus scraping work unchanged. Add `--profile observability` to send traces to Jaeger as well.

Inside Compose the services reach Kafka at `kafka:19092`; Kafka also advertises `localhost:9092` for services started with Maven. Keycloak signs tokens with the issuer `http://localhost:8180/...` that clients see, so payments-service keeps that as the expected issuer and fetches the signing keys from `http://keycloak:8180` over the Compose network.

## Container images

One multi-stage `Dockerfile` builds either service (`--build-arg SERVICE=payments-service` or `notification-service`):

- The build stage packages the jar with the Maven wrapper and splits it into Spring Boot layers: dependencies, loader, snapshot dependencies and application code. A code change rebuilds only the last, small layer.
- The runtime stage is a JRE on Alpine, runs as an unprivileged user (uid 10001) and sizes the heap from the container's memory limit (`MaxRAMPercentage=75`).

## Kubernetes (Helm on kind)

Requirements: Docker, [kind](https://kind.sigs.k8s.io/), kubectl and Helm.

```sh
deploy/kind/up.sh     # cluster, images, infrastructure and the Helm release
deploy/kind/down.sh   # delete the cluster
```

The script creates a one-node kind cluster, builds both images and loads them into it, starts PostgreSQL, Kafka and Keycloak from `deploy/kind/infra.yaml` and installs the chart in `deploy/helm/payledger` with `deploy/kind/values-kind.yaml`. The services, and Keycloak, are reachable on the same localhost ports as with Compose, so the curl examples and the Postman collection work unchanged.

The infrastructure manifests are for development only; in production PostgreSQL, Kafka and the identity provider would be managed services, and the chart only needs their addresses.

What the chart sets up for each service:

| Concern | How |
| --- | --- |
| Configuration | A ConfigMap per service; database credentials in a Secret (or `database.existingSecret`) |
| Probes | Startup and liveness use `/actuator/health/liveness`. Readiness uses `/actuator/health/readiness`, which includes the database, so a pod that loses its database stops getting traffic instead of being restarted |
| Shutdown | A 5 second `preStop` sleep lets the Service stop routing to the pod before Spring's graceful shutdown starts |
| Resources | Memory requests and limits; the JVM heap is 75% of the limit |
| Security | Non-root user, read-only root filesystem (with a writable `/tmp`), no privilege escalation, all capabilities dropped, no service account token |
| Rollouts | A hash of the ConfigMap and Secret in the pod template, so a config change restarts the pods |

`payments-service` runs two replicas by default; the outbox relays share the work through `FOR UPDATE SKIP LOCKED`.

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and every push to `main`:

1. `./mvnw verify`: all unit and Testcontainers integration tests, using the runner's Docker. Test reports are uploaded when something fails. In parallel, `helm lint` checks the chart with its default and kind values.
2. Both images are built. On `main` they are pushed to GitHub Container Registry as `ghcr.io/mosiur99/payledger-payments-service` and `ghcr.io/mosiur99/payledger-notification-service`, tagged `latest` and `sha-<commit>`. Pull requests only build them.

## Security

payments-service is an OAuth2 resource server. Every API call needs a Keycloak access token; the service checks its signature, issuer, expiry and audience (`payledger-api`). The caller's id is the token's `sub` claim, so nobody can act as another user by changing a header or a field.

| Role | Can do |
| --- | --- |
| `USER` | Open and read their own accounts, transfer from them, read statements |
| `ADMIN` | Deposit money into any account |

The realm is imported from `deploy/keycloak/payledger-realm.json` with three demo users: `ayesha` / `ayesha-demo` and `rahim` / `rahim-demo` (USER), and `ops-admin` / `ops-admin-demo` (ADMIN). These passwords are for local demos only. The demo client `payledger-cli` uses the password grant to keep curl and Postman simple; a real frontend would use the authorization code flow with PKCE.

```sh
TOKEN=$(curl -s localhost:8180/realms/payledger/protocol/openid-connect/token \
  -d grant_type=password -d client_id=payledger-cli -d username=ayesha -d password=ayesha-demo \
  | jq -r .access_token)
```

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/accounts` | Open an account in `BDT`, `EUR` or `USD` |
| `GET` | `/api/v1/accounts` | List the caller's accounts, oldest first |
| `GET` | `/api/v1/accounts/{id}` | Read one of the caller's accounts |
| `GET` | `/api/v1/accounts/{id}/statement?limit=50&before={entryId}` | Ledger entries, newest first, with a cursor for older pages |
| `POST` | `/api/v1/deposits` | Fund an account from the system account of its currency (ADMIN only) |
| `POST` | `/api/v1/transfers` | Move money from one of the caller's accounts to another account in the same currency |

```sh
curl -X POST localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
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

## Events: transactional outbox

Every committed ledger transaction (deposit or transfer) is published to the Kafka topic `payledger.transactions` as a `TransactionPosted` event, keyed by transaction id.

Writing to the database and sending to Kafka directly could leave them out of sync: a crash between the two loses the event, and a rollback after sending publishes money that never moved. So the ledger writes the event to an `outbox_events` table in the same database transaction as the entries. A relay then publishes unpublished rows and stamps `published_at` once Kafka acknowledges them.

| Property | How |
| --- | --- |
| No lost events | The event row commits or rolls back with the money movement |
| No events for rolled-back work | Tested: a rolled-back transfer leaves no row and no message |
| Several relay instances | Batches are claimed with `FOR UPDATE SKIP LOCKED`, so instances take different rows |
| Delivery | At least once: a crash after sending but before stamping re-sends the event. Each message carries an `eventId` header so consumers can deduplicate |
| Producer | `acks=all` with idempotence enabled |

Set `OUTBOX_RELAY_ENABLED=false` to run the service without Kafka.

## Notifications: idempotent consumer and dead letters

`notification-service` consumes `payledger.transactions` in the consumer group `notification-service` and stores a message for each person involved: the sender and receiver of a transfer, or the customer who got a deposit. It has its own database (`notifications`) and its own copy of the event contract, so the two services share only the JSON shape.

| Concern | How |
| --- | --- |
| Duplicates (the outbox delivers at least once) | The `eventId` header is inserted into `processed_events` with `ON CONFLICT DO NOTHING` in the same transaction as the notifications. A second delivery finds the row and does nothing. |
| Temporary failures (database busy) | Retried in place with backoff: 0.5 s, 1 s, 2 s |
| Permanent failures (bad JSON, missing fields or header) | Not retried. Sent straight to the dead letter topic |
| Failures that outlast the retries | Sent to `payledger.transactions.DLT` with the original topic, partition, offset and exception in headers, then the consumer moves on, so one bad message never blocks its partition |
| Unknown event types | Skipped, so a newer producer does not break this consumer |

## Observability

Both services expose Prometheus metrics at `/actuator/prometheus` and send traces over OTLP. Start the optional stack (Jaeger, Prometheus, Grafana) with:

```sh
docker compose -f deploy/compose/docker-compose.yml --profile observability up -d
```

| Tool | URL | What you see |
| --- | --- | --- |
| Grafana | http://localhost:3000 | The **PayLedger Overview** dashboard opens as the home page (anonymous view; `admin` / `admin` to edit) |
| Prometheus | http://localhost:9090 | Raw metrics and queries |
| Jaeger | http://localhost:16686 | Traces across both services |

### Metrics

| Metric | Service | Meaning |
| --- | --- | --- |
| `payledger_ledger_transactions_total{type,currency}` | payments | Committed deposits and transfers. Counted after commit, so rolled-back work never shows up |
| `payledger_ledger_volume_minor_total{type,currency}` | payments | Money moved, in minor units |
| `payledger_outbox_pending` | payments | Events committed but not yet on Kafka |
| `payledger_outbox_oldest_pending_age_seconds` | payments | Age of the oldest unsent event. The single best alert: it grows when the relay or Kafka is stuck |
| `payledger_outbox_published_total`, `payledger_outbox_publish_failures_total` | payments | Relay sends acknowledged and failed |
| `http_server_requests_seconds_bucket` | payments | Request latency histogram, for p95 per endpoint |
| `payledger_notifications_stored_total{kind}` | notification | Notifications stored |
| `payledger_events_duplicates_total` | notification | Redelivered events ignored by deduplication |
| `payledger_events_dead_lettered_total` | notification | Records moved to the dead letter topic |
| `kafka_consumer_fetch_manager_records_lag_max` | notification | Consumer lag, from the Kafka client |

`/actuator/prometheus` is open so Prometheus can scrape it. In production it would be reachable only from inside the cluster network.

### Tracing through the outbox

A transfer is one trace: the HTTP request, the database work, the Kafka send by the relay, and the notification consumer. Because the relay sends events later on another thread, the request's W3C `traceparent` is saved in the outbox row (`trace_parent`). The relay resumes that trace before sending, so Kafka's `traceparent` header, and the consumer's spans, belong to the original request. Log lines carry the same trace id. In Spring Boot 4, trace propagation is only active when trace export is enabled, so `management.tracing.export.enabled=false` also stops the header.

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
| `/problems/unauthenticated` | 401 | Missing, expired or invalid access token |
| `/problems/forbidden` | 403 | The caller's role does not allow the operation |
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
- Postman: import `docs/postman/PayLedger.postman_collection.json` and run it top to bottom. It logs in the three demo users, opens two accounts, deposits, transfers, shows an insufficient-funds error and reads the statement.

## Test

```sh
./mvnw verify
```

Integration tests start PostgreSQL in Docker with Testcontainers. API tests sign tokens with a key created for the test run; `KeycloakSecurityIT` starts the real Keycloak realm and checks the 401, 403 and 404 cases end to end. `OutboxKafkaIT` starts Kafka and checks that each committed transaction is published exactly once and a rolled-back one never is; `OutboxRelayIT` runs four relays at once over the same rows. `ObservabilityIT` checks that committed transfers move the counters, rejected or rolled-back ones do not, and Prometheus can scrape the endpoint; `OutboxKafkaIT` also checks that the request's trace id arrives in the Kafka `traceparent` header. `TransactionEventsIT` (notification-service) checks that a redelivered event is handled once, a poison message lands in the dead letter topic without blocking the next one, a temporary failure succeeds on retry, and a lasting failure is parked after three retries.
