---
tags: [spp, testes]
---

# 07 — Estratégia de Testes

Voltar: [[00 - Índice]]

> [!important] Obrigatório pelo README
> Unitários **e** integração. O scaffold já tem `AbstractIntegrationTest` (Postgres 16 + Kafka via Testcontainers) — reaproveite.

## Pirâmide
| Nível | Ferramenta | O que cobre | Velocidade |
|-------|-----------|-------------|-----------|
| Unitário | JUnit 5 + AssertJ + Mockito* | Regras de negócio, transições, orquestração com mocks | ms |
| Integração por serviço | `@MicronautTest` + Testcontainers | HTTP → service → Postgres; Kafka real | segundos |
| Ponta a ponta (stretch) | 3 apps + containers | Pagamento completo A → X | lento |

\* Mockito não está no pom; adicione `mockito-core` (test) ou use `@MockBean` do Micronaut Test.

## Casos mínimos por serviço
### Coordinator
- [ ] POST válido → 202 + `paymentId`; GET depois → `CONFIRMED` (Awaitility).
- [ ] `amount` 0, negativo ou ausente → 400 problem+json.
- [ ] Saldo insuficiente → `REJECTED/INSUFFICIENT_FUNDS`.
- [ ] Merchant inativo → `REJECTED/MERCHANT_INVALID` **e `release` chamado**.
- [ ] Reserve lento (> timeout) → `REJECTED/TIMEOUT`.
- [ ] Mesma `Idempotency-Key` → mesmo `paymentId`.

### Reserve
- [ ] Reservar/confirmar/liberar felizes + saldos corretos.
- [ ] Reservar 2x o mesmo `paymentId` → 1 reserva só.
- [ ] Confirmar após liberar → 409; liberar após confirmar → 409.
- [ ] **Concorrência**: 20 threads × 10 Piggies em saldo 100 → 10 sucessos.

### Merchant
- [ ] Validação ativo / inativo / inexistente.
- [ ] `POST /receivables` → recebível gravado → `PagamentoConfirmado` publicado (consumir do tópico no teste).
- [ ] Crédito repetido (mesmo `paymentId`) → 1 recebível, 200 no retry.
- [ ] Evento publicado valida contra o JSON Schema.

### Contratos
- [ ] Eventos serializados validam contra o JSON Schema em `contracts/events`.

## Dicas práticas
- **Awaitility** (já no pom) para esperar efeitos assíncronos; nunca `Thread.sleep`.
- Subir containers **uma vez** (static + `PER_CLASS`, como no scaffold) — é o que mantém a suíte rápida.
- Se o Docker não estiver disponível na máquina da entrevista, os unitários devem rodar sozinhos: separe por convenção (`*Test` vs `*IT`) e plugin Surefire/Failsafe.
- Rodar: `./mvnw test` (Windows: `mvnw.bat test`).
