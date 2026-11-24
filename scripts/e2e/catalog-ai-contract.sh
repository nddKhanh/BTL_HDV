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

wait_for http://localhost:8081/q/health
wait_for http://localhost:8083/q/health
feed=$(curl -fsS 'http://localhost:8081/internal/ai/products?offset=0&limit=50' -H "X-Internal-Key: ${INTERNAL_API_KEY:?}")
test "$(jq '.items | length' <<<"$feed")" -eq 3
test "$(jq '[.items[] | has("priceVnd") or has("availableStock")] | any' <<<"$feed")" = false
test "$(jq '[.items[] | (.id != null and .name != null and .sku != null)] | all' <<<"$feed")" = true
test "$(curl -fsS http://localhost:8083/api/ping)" = 'Hello from AI RAG Service'
echo 'catalog-to-ai C5 contract passed'
