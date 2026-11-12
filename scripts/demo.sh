#!/usr/bin/env bash
# Walks through PayLedger end to end against the Compose stack:
#   docker compose -f deploy/compose/docker-compose.yml --profile apps up -d --build
# Needs curl and jq.
set -euo pipefail

API=${API:-http://localhost:8080}
KEYCLOAK=${KEYCLOAK:-http://localhost:8180}
COMPOSE="docker compose -f $(dirname "$0")/../deploy/compose/docker-compose.yml"

bold=$'\e[1m'; dim=$'\e[2m'; reset=$'\e[0m'
step() { printf '\n%s== %s ==%s\n' "$bold" "$1" "$reset"; }
note() { printf '%s%s%s\n' "$dim" "$1" "$reset"; }
money() { awk -v m="$1" 'BEGIN { printf "%.2f", m / 100 }'; }

token() {
  curl -sf "$KEYCLOAK/realms/payledger/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=payledger-cli -d username="$1" -d password="$1-demo" \
    | jq -r .access_token
}
api() {
  local method=$1 path=$2 token=$3; shift 3
  curl -s -X "$method" "$API$path" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' "$@"
}

for _ in $(seq 1 60); do
  curl -sf -o /dev/null "$KEYCLOAK/realms/payledger/.well-known/openid-configuration" && break
  sleep 2
done

step "1. Log in through Keycloak"
AYESHA=$(token ayesha); RAHIM=$(token rahim); ADMIN=$(token ops-admin)
note "ayesha and rahim are customers (USER); ops-admin is an operator (ADMIN)."
printf 'Without a token: HTTP %s\n' "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/accounts")"

step "2. Each customer opens a BDT account"
FROM=$(api POST /api/v1/accounts "$AYESHA" -d '{"currency":"BDT"}' | jq -r .id)
TO=$(api POST /api/v1/accounts "$RAHIM" -d '{"currency":"BDT"}' | jq -r .id)
echo "ayesha: $FROM"; echo "rahim:  $TO"

step "3. The operator deposits 1,000.00 BDT into ayesha's account"
api POST /api/v1/deposits "$ADMIN" -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"accountId\":\"$FROM\",\"amountMinor\":100000,\"reference\":\"Top-up\"}" | jq -c '{type, status, amountMinor}'
note "A customer cannot deposit: HTTP $(api POST /api/v1/deposits "$AYESHA" -o /dev/null -w '%{http_code}' \
  -H "Idempotency-Key: $(uuidgen)" -d "{\"accountId\":\"$FROM\",\"amountMinor\":1}")"

step "4. ayesha sends 250.00 BDT to rahim"
KEY=$(uuidgen)
TRANSFER=$(api POST /api/v1/transfers "$AYESHA" -H "Idempotency-Key: $KEY" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amountMinor\":25000,\"reference\":\"Rent\"}")
echo "$TRANSFER" | jq -c '{id, status, amountMinor}'

step "5. The network drops the response, so the client retries with the same key"
api POST /api/v1/transfers "$AYESHA" -H "Idempotency-Key: $KEY" -D - -o /dev/null \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amountMinor\":25000,\"reference\":\"Rent\"}" \
  | grep -i 'idempotent-replayed' || true
note "Same response replayed; no second transfer."
echo "ayesha: $(money "$(api GET "/api/v1/accounts/$FROM" "$AYESHA" | jq .balanceMinor)") BDT"
echo "rahim:  $(money "$(api GET "/api/v1/accounts/$TO" "$RAHIM" | jq .balanceMinor)") BDT"

step "6. Rules the ledger enforces"
api POST /api/v1/transfers "$AYESHA" -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amountMinor\":99999999}" | jq -c '{status, title, detail}'
note "rahim reading ayesha's account (404, so account ids cannot be probed):"
api GET "/api/v1/accounts/$FROM" "$RAHIM" | jq -c '{status, title}'

step "7. ayesha's statement: append-only double-entry ledger"
api GET "/api/v1/accounts/$FROM/statement" "$AYESHA" \
  | jq -r '.entries[] | "\(.direction)\t\(.amountMinor)\t\(.type)\t\(.reference)"' | column -t -s $'\t'

step "8. Kafka -> notification-service (transactional outbox, idempotent consumer)"
sleep 2
$COMPOSE exec -T postgres psql -U postgres -d notifications -tA -F ' | ' \
  -c "select kind, message from notifications where message like '%${FROM: -4}%' or message like '%${TO: -4}%' order by created_at"
