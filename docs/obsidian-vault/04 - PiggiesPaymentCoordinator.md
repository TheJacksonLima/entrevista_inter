---
tags: [spp, coordinator]
---

# 04 — PiggiesPaymentCoordinator

Voltar: [[00 - Índice]] · Fluxo: [[02 - Arquitetura e Fluxo]]

## Responsabilidade
Ponto de entrada do App. **Registra a intenção** e **orquestra** de ponta a ponta. É o "serviço de borda" → **obrigatoriamente assíncrono**. Não publica no Kafka: quem publica é o Merchant ([[06 - PiggiesMerchantService]]).

## API (ver [[03 - Contratos (Contract-First)]])
- `POST /payments` → `202 Accepted` com `paymentId` (UUID gerado no Coordinator).
- `GET /payments/{id}` → status para polling.

## Modelo de dados — tabela `payment`
| Coluna | Tipo | Nota |
|--------|------|------|
| `id` | uuid PK | = paymentId |
| `client_id` | varchar | |
| `merchant_id` | varchar | |
| `amount` | bigint | inteiro de Piggies, `CHECK (amount > 0)` |
| `status` | varchar | `PENDING`, `CONFIRMED`, `REJECTED` |
| `failure_reason` | varchar null | `INSUFFICIENT_FUNDS`, `MERCHANT_INVALID`, `TIMEOUT`… |
| `idempotency_key` | varchar null UNIQUE | evita duplicar pagamento em retry do App |
| `created_at` / `updated_at` | timestamptz | |

## Orquestração (passo a passo)
1. Controller valida o body e gera `paymentId`.
2. **Grava a intenção `PENDING` (síncrono)** e devolve `202`.
3. Em background, **2 tarefas em paralelo** (`CompletableFuture.allOf` ou virtual threads):
   - `reserveClient()` → `POST /reserves` no Reserve
   - `validateMerchant()` → `GET /merchants/{merchantId}/status` no Merchant
4. Aguarda **as duas terminarem** (mesmo que uma falhe) para decidir com o quadro completo.
5. **Ambas OK** → `confirm` no Reserve (debita) → `POST /receivables` no Merchant (credita + publica evento) → `status = CONFIRMED`.
6. **Alguma falhou** → `release` da reserva se ela foi criada → `status = REJECTED` + `failure_reason`.
7. Timeout global por pagamento (ex.: 5–10 s) para nunca ficar `PENDING` para sempre.

## Pontos de atenção
- **Clientes HTTP**: `@Client` declarativo do Micronaut com `CompletableFuture`/`Publisher`, timeout curto e 1 retry.
- **Esperar as duas antes de decidir**: se a validação falhar rápido mas a reserva ainda estiver em voo, é preciso liberar a reserva quando ela terminar.
- **Passo crítico**: depois do débito confirmado não há volta (sem cancelamento pós-confirmação). Se o crédito falhar, fazer **retry idempotente** do crédito (o Merchant ignora duplicata).
- **Idempotência**: `Idempotency-Key` repetida → devolve o mesmo `paymentId`.
- **Recuperação** (stretch): job que retoma pagamentos `PENDING` antigos.

## Estrutura de pacotes sugerida
```
co.inter.piggies.coordinator
├── api        (PaymentController, DTOs, mapeamento de erros)
├── domain     (Payment, PaymentStatus)
├── service    (PaymentOrchestrator)
├── client     (ReserveClient, MerchantClient)
└── repository (PaymentRepository)
```

## Testes
- Unit: `PaymentOrchestrator` com clients mockados — sucesso, saldo insuficiente, merchant inválido + release, timeout, retry do crédito.
- Integração: `@MicronautTest` + Postgres Testcontainer + clients substituídos (`@Replaces`/WireMock). Ver [[07 - Estratégia de Testes]].
