#!/usr/bin/env bash
# Teste ponta a ponta do SPP contra os três serviços JÁ em execução.
#
#   docker compose up -d
#   ./mvnw -pl coordinator -am mn:run     # terminal 1  (:8080)
#   ./mvnw -pl reserve -am mn:run         # terminal 2  (:8081)
#   ./mvnw -pl merchant -am mn:run        # terminal 3  (:8082)
#   ./scripts/e2e.sh
#
# Usa as contas/merchants de seed: clientes A (1000), B (50), POOR (0); merchants X (ativo), Y (inativo).
# Só depende de bash + curl. Sai com código != 0 se algum cenário falhar.
set -u

COORD=${COORDINATOR_URL:-http://localhost:8080}
RESERVE=${RESERVE_URL:-http://localhost:8081}
MERCHANT=${MERCHANT_URL:-http://localhost:8082}

FAILS=0
ok()   { echo "  [OK]   $1"; }
fail() { echo "  [FAIL] $1"; FAILS=$((FAILS + 1)); }

# json_field <campo> -> lê o valor (string/número/bool) de um JSON simples em stdin
json_field() { sed -n "s/.*\"$1\" *: *\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -n1; }

# pay <clientId> <merchantId> <amount> [idempotency-key] -> imprime o paymentId
pay() {
  local headers=(-H "Content-Type: application/json")
  [ -n "${4:-}" ] && headers+=(-H "Idempotency-Key: $4")
  curl -s -X POST "$COORD/payments" "${headers[@]}" \
    -d "{\"clientId\":\"$1\",\"merchantId\":\"$2\",\"amount\":$3}" | json_field paymentId
}

# wait_status <paymentId> -> espera sair de PENDING (até ~10s) e imprime o JSON final
wait_status() {
  local body status
  for _ in $(seq 1 50); do
    body=$(curl -s "$COORD/payments/$1")
    status=$(echo "$body" | json_field status)
    [ "$status" != "PENDING" ] && [ -n "$status" ] && break
    sleep 0.2
  done
  echo "$body"
}

expect_payment() { # <descrição> <paymentId> <status esperado> [reason esperado]
  local body status reason
  body=$(wait_status "$2")
  status=$(echo "$body" | json_field status)
  reason=$(echo "$body" | json_field reason)
  if [ "$status" = "$3" ] && { [ -z "${4:-}" ] || [ "$reason" = "$4" ]; }; then
    ok "$1 -> $status${reason:+ ($reason)}"
  else
    fail "$1 -> esperado $3${4:+/$4}, obtido: $body"
  fi
}

expect_http() { # <descrição> <status esperado> <curl args...>
  local desc=$1 want=$2; shift 2
  local got
  got=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  [ "$got" = "$want" ] && ok "$desc -> HTTP $got" || fail "$desc -> esperado HTTP $want, obtido $got"
}

echo "== Saúde dos serviços"
for url in "$COORD" "$RESERVE" "$MERCHANT"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$url/health")
  [ "$code" = "200" ] && ok "$url/health" || { fail "$url/health -> HTTP $code (serviço no ar?)"; }
done
[ "$FAILS" -gt 0 ] && { echo "Serviços indisponíveis; abortando."; exit 1; }

echo "== 1. Caminho feliz: A paga 100 ao merchant X"
ID=$(pay A X 100)
[ -n "$ID" ] && ok "202 + paymentId=$ID" || fail "POST /payments não devolveu paymentId"
expect_payment "pagamento" "$ID" CONFIRMED
RES=$(curl -s "$RESERVE/reserves/$ID")
[ "$(echo "$RES" | json_field status)" = "CONFIRMED" ] && ok "reserva CONFIRMED (débito efetuado)" || fail "reserva: $RES"
REC=$(curl -s "$MERCHANT/merchants/X/receivables")
echo "$REC" | grep -q "$ID" && ok "recebível de X registrado" || fail "recebível não encontrado: $REC"

echo "== 2. Saldo insuficiente: POOR paga 10"
ID=$(pay POOR X 10)
expect_payment "pagamento" "$ID" REJECTED INSUFFICIENT_FUNDS

echo "== 3. Merchant inativo: A paga 50 a Y (reserva deve ser liberada)"
ID=$(pay A Y 50)
expect_payment "pagamento" "$ID" REJECTED MERCHANT_INVALID
sleep 0.5
RES=$(curl -s "$RESERVE/reserves/$ID")
[ "$(echo "$RES" | json_field status)" = "RELEASED" ] && ok "reserva RELEASED (saldo devolvido)" || fail "reserva deveria estar RELEASED: $RES"

echo "== 4. Merchant inexistente: A paga 50 a Z"
ID=$(pay A Z 50)
expect_payment "pagamento" "$ID" REJECTED MERCHANT_INVALID

echo "== 5. Idempotência: mesma Idempotency-Key duas vezes"
KEY="e2e-$(date +%s)-$RANDOM"
ID1=$(pay A X 1 "$KEY"); ID2=$(pay A X 1 "$KEY")
[ -n "$ID1" ] && [ "$ID1" = "$ID2" ] && ok "mesmo paymentId ($ID1)" || fail "ids diferentes: '$ID1' vs '$ID2'"
expect_payment "pagamento" "$ID1" CONFIRMED

echo "== 6. Validação do body e consulta inexistente"
expect_http "amount 0 é rejeitado" 400 -X POST "$COORD/payments" -H 'Content-Type: application/json' \
  -d '{"clientId":"A","merchantId":"X","amount":0}'
expect_http "paymentId desconhecido" 404 "$COORD/payments/00000000-0000-0000-0000-000000000000"

echo
if [ "$FAILS" -eq 0 ]; then echo "TODOS OS CENÁRIOS PASSARAM"; else echo "$FAILS verificação(ões) falharam"; exit 1; fi
