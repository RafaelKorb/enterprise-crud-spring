#!/usr/bin/env bash
# Smoke test against a running stack (docker compose up -d --build --wait).
# Exercises open, credit, idempotent replay of a debit and the error contract through real HTTP.
#
# Usage: scripts/smoke-test.sh [base-url]    default: http://localhost:8080
set -euo pipefail

base="${1:-http://localhost:8080}"
accounts="$base/api/v1/accounts"
run="$(date +%s%N)"
# 11 digits, unique per run so the test can be repeated against the same database
document="$(printf '%011d' "$((10#${run: -11}))")"

fail() { echo "FAIL: $*" >&2; exit 1; }

request() { # request <method> <url> [key] [json] -> sets STATUS, BODY, HEADERS
  local method="$1" url="$2" key="${3:-}" json="${4:-}"
  local args=(-s -D - -X "$method" "$url")
  [[ -n "$key" ]] && args+=(-H "Idempotency-Key: $key")
  [[ -n "$json" ]] && args+=(-H 'Content-Type: application/json' -d "$json")
  local response
  response="$(curl "${args[@]}")"
  HEADERS="$(sed '/^\r$/q' <<<"$response")"
  BODY="$(sed '1,/^\r$/d' <<<"$response")"
  STATUS="$(head -n 1 <<<"$HEADERS" | awk '{print $2}')"
}

expect_status() { [[ "$STATUS" == "$1" ]] || fail "$2: expected HTTP $1, got $STATUS: $BODY"; echo "ok  $2 ($STATUS)"; }

request GET "$base/actuator/health"
expect_status 200 "health"

request POST "$accounts" "open-$run" "{\"documentNumber\":\"$document\"}"
expect_status 201 "open account"
id="$(sed -E 's/.*"id":"([^"]+)".*/\1/' <<<"$BODY")"
grep -qi "^location: .*/api/v1/accounts/$id" <<<"$HEADERS" || fail "open account: missing Location header"

request POST "$accounts/$id/credits" "credit-$run" '{"amount":100.00}'
expect_status 200 "credit 100.00"

request POST "$accounts/$id/debits" "debit-$run" '{"amount":30.00}'
expect_status 200 "debit 30.00"
request POST "$accounts/$id/debits" "debit-$run" '{"amount":30.00}'
expect_status 200 "retried debit"
grep -qi '^idempotent-replayed: true' <<<"$HEADERS" || fail "retried debit was not replayed"

request POST "$accounts/$id/debits" "debit-$run" '{"amount":50.00}'
expect_status 422 "same key, different amount"

request POST "$accounts/$id/debits" "" '{"amount":1.00}'
expect_status 400 "missing Idempotency-Key"

request GET "$accounts/$id"
expect_status 200 "get account"
grep -q '"balance":70.00' <<<"$BODY" || fail "expected balance 70.00 after one debit, got: $BODY"
echo "ok  balance is 70.00 (retry applied once)"

echo "Smoke test passed"
