#!/usr/bin/env bash
set -euo pipefail

wait_for() {
  for _ in $(seq 1 60); do
    curl -fsS "$1" >/dev/null && return 0
    sleep 2
  done
  echo "Timed out waiting for $1" >&2
  return 1
}

for port in 8081 8082 8084 8085 8086; do
  wait_for "http://localhost:$port/q/health"
done

order=$(curl -fsS -X POST http://localhost:8082/api/orders -H 'Content-Type: application/json' -d '{"customerEmail":"e2e@example.test","shippingAddress":"1 E2E Street, Hanoi","items":[{"productId":"11111111-1111-4111-8111-111111111111","quantity":1}]}')
id=$(jq -er '.id' <<<"$order")
checkout_key=$(jq -er '.checkoutKey' <<<"$order")
test "$(jq -r '.status' <<<"$order")" = CONFIRMED

payment=$(curl -fsS -X POST "http://localhost:8082/api/orders/$id/pay" -H 'Content-Type: application/json' -H "X-Checkout-Key: $checkout_key" -d '{"method":"CARD","simulateFailure":false}')
test "$(jq -r '.order.status' <<<"$payment")" = PAID
test "$(jq -r '.payment.status' <<<"$payment")" = AUTHORIZED
test "$(jq -r '.shipment.status' <<<"$payment")" = CREATED

key=${INTERNAL_API_KEY:?INTERNAL_API_KEY is required}
test "$(curl -fsS "http://localhost:8084/internal/payments/$id" -H "X-Internal-Key: $key" | jq -r '.status')" = AUTHORIZED
test "$(curl -fsS "http://localhost:8085/internal/shipments/$id" -H "X-Internal-Key: $key" | jq -r '.status')" = CREATED
test "$(curl -fsS "http://localhost:8086/internal/notifications/$id" -H "X-Internal-Key: $key" | jq 'length')" -eq 2

cancelled=$(curl -fsS -X POST "http://localhost:8082/api/orders/$id/cancel" -H "X-Checkout-Key: $checkout_key")
test "$(jq -r '.status' <<<"$cancelled")" = CANCELLED
test "$(curl -fsS "http://localhost:8084/internal/payments/$id" -H "X-Internal-Key: $key" | jq -r '.status')" = REFUNDED
test "$(curl -fsS "http://localhost:8085/internal/shipments/$id" -H "X-Internal-Key: $key" | jq -r '.status')" = CANCELLED
echo "commerce flow passed: $id"
