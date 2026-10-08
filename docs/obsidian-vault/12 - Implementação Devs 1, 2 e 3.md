---
tags: [spp, implementacao, dev1, dev2, dev3]
---

# 12 — Implementação dos Devs 1, 2 e 3 (e fase C do Dev 4)

Voltar: [[00 - Índice]] · Base: [[11 - Implementação Dev 4]] · Plano: [[10 - Divisão da Equipe (4 devs)]]

> [!success] Estado
> Os três serviços estão **implementados e compilam**. Foram feitos por agentes em paralelo (um por dev), cada um dono de um módulo. `Placeholder.java` foi removido dos três.
> O build completo passa com **59 testes sem Docker** (`./mvnw test -Dtest='!*ApplicationTest,!PaymentControllerTest,!ReserveControllerIntegrationTest,!MerchantApiIntegrationTest'`).

> [!danger] O que NÃO foi executado
> **Nenhum teste que usa Postgres ou Kafka rodou** (Docker indisponível na máquina de desenvolvimento): `*ApplicationTest` ×3, `PaymentControllerTest`, `ReserveControllerIntegrationTest` (inclui concorrência) e `MerchantApiIntegrationTest`. Eles **compilam**, mas o SQL nativo, o DDL do Hibernate, o wiring do Kafka e o `./scripts/e2e.sh` nunca foram exercitados. Também só se compilou com JDK 21 (pom em 25). **A primeira execução com Docker é o teste real** — veja a lista de riscos no fim.

## Dev 1 — Coordinator (borda)  `coordinator/…/web`, `service`, `domain`
| Peça | Detalhe |
|------|---------|
| `Payment` (JPA) | tabela `payment`: status `PENDING/CONFIRMED/REJECTED`, `failure_reason`, `idempotency_key` UNIQUE |
| `PaymentController` | `POST /payments` → grava `PENDING` **commitado** → `202` + `Location` → chama `orchestrator.submit(...)`; `GET /payments/{id}` |
| `PaymentService` | idempotência por `Idempotency-Key`; corrida de duas requisições resolvida pela UNIQUE (devolve o pagamento da primeira) |
| `RepositoryPaymentStatusUpdater` | só transiciona a partir de `PENDING`; `CONFIRMED`/`REJECTED` nunca são sobrescritos |
| Testes | 6 + 5 unitários (passam) + `PaymentControllerTest` de integração (não executado) |

## Dev 2 — Reserve  `reserve/…`
| Peça | Detalhe |
|------|---------|
| Domínio | `ReserveRules` (decisão pura de transição), `ReserveService`, `ReserveStore` (interface) com `JpaReserveStore` (SQL nativo) |
| Seed | `AccountSeeder`: A=1000, B=50, POOR=0 (não sobrescreve saldos existentes) |
| API | `POST /reserves`, `GET /reserves/{id}`, `POST …/confirm`, `POST …/release`, erros em problem+json |
| Testes | 2 + 10 unitários (passam, com `InMemoryReserveStore`); integração + **concorrência** (não executados) |

**Atomicidade (decisão do agente, melhor que a sugestão original):**
1. `INSERT reservation … ON CONFLICT (payment_id) DO NOTHING` **primeiro** → se não inseriu, a reserva já existia: devolve 200 sem debitar (resolve a corrida do mesmo `paymentId` sem tratar exceção).
2. Se inseriu, `UPDATE account … WHERE available >= :v`; 0 linhas → saldo insuficiente → rollback desfaz o `INSERT`.
3. `confirm`/`release` usam `UPDATE reservation … WHERE status='PENDING'` e ajustam o saldo na mesma transação.

Escolhas: conta inexistente → **409** (como saldo insuficiente); mesmo `paymentId` com cliente/valor diferentes → devolve a reserva original com 200; sem FK de `reservation` para `account` (o `INSERT` vem antes).

## Dev 3 — Merchant e Kafka  `merchant/…`
| Peça | Detalhe |
|------|---------|
| Domínio | `Merchant`, `Receivable` (com `event_id`, `event_published`), `MerchantSeeder` (X ativo, Y inativo) |
| Serviço | `ReceivableService` com publicação atrás da interface `PagamentoConfirmadoPublisher` (testável sem Kafka) |
| Kafka | `@KafkaClient` (`acks=all`), chave = `paymentId`, tópico `piggies.pagamento-confirmado.v1`, payload conforme o schema |
| API | `GET /merchants/{id}/status`, `GET /merchants/{id}/receivables`, `POST /receivables` |
| Testes | 9 + 2 unitários (passam; o JSON do evento **valida contra o JSON Schema**); integração com consumer Kafka (não executados) |

Decisões: publica **depois** do commit; se a publicação falha, o recebível fica gravado e a resposta é **503** — o retry do mesmo `paymentId` **republica com o mesmo `eventId`** (consumidores deduplicam); mesmo `paymentId` com outro merchant/valor → **409**; corrida no mesmo `paymentId` → o perdedor devolve 200 sem publicar.

## Fase C (Dev 4)
- [x] `contracts/merchant.openapi.yaml` atualizado com **409** (paymentId reutilizado) e **503** (retentável)
- [x] `scripts/e2e.sh` (bash + curl) cobre: caminho feliz (A→X, reserva `CONFIRMED`, recebível em X), saldo insuficiente, merchant inativo (reserva vira `RELEASED`), merchant inexistente, idempotência, `400` e `404`
- [x] README com instruções
- [ ] **Executar** o e2e e os testes de integração com Docker
- [ ] Teste e2e em JUnit — descartado: os três módulos têm `application.yml` conflitantes no classpath, então subir os três numa JVM só é frágil; o script contra processos reais é mais fiel

Rodando o e2e:
```bash
docker compose up -d
./mvnw -pl coordinator -am mn:run    # :8080   (um terminal por serviço)
./mvnw -pl reserve -am mn:run        # :8081
./mvnw -pl merchant -am mn:run       # :8082
./scripts/e2e.sh
```

## Verificação cruzada feita
Conferi que os corpos e rotas que o Coordinator envia (`HttpReserveGateway`/`HttpMerchantGateway`) batem com os controllers de Reserve e Merchant: `POST /reserves {paymentId, clientId, amount}`, `POST /reserves/{id}/confirm|release`, `GET /merchants/{id}/status → {merchantId, active}`, `POST /receivables {paymentId, merchantId, amount}`.

## Riscos para a primeira execução com Docker
Em ordem de probabilidade de dar problema:
1. **Reserve/Merchant:** `EntityManager` injetado por construtor/`@PersistenceContext` com `@Transactional` do Micronaut — compilou, mas não foi visto funcionando em runtime.
2. **SQL nativo** (`ON CONFLICT DO NOTHING`, parâmetros `UUID`/`Instant`) e tipos devolvidos pelas queries nativas.
3. **DDL do Hibernate:** `CHECK (>= 0)` em `columnDefinition`; FK dupla de `merchant_id` no recebível.
4. **Merchant/Kafka:** retorno `RecordMetadata` do `@KafkaClient` bloqueante e `@Topic("${…}")`.
5. **problem+json** no 400/404/409/503 (conteúdo e `Content-Type`) e `Location` do `202`.
6. **Contrato × implementação:** `additionalProperties: false` **não é imposto** (o Micronaut Serde ignora campos extras e não devolve 400).
7. Pool Hikari (10 conexões) × 20 threads do teste de concorrência.

Se algo falhar, comece por esta lista.

## Divergências e dívidas registradas
- `hbm2ddl.auto: update` serve para dev; produção pediria Flyway.
- Contrato do Coordinator/Reserve não define: conta inexistente (hoje 409) e reuso de `paymentId` com dados diferentes no Reserve (hoje 200 com a reserva original; no Merchant é 409 — **inconsistência a alinhar**).
- Falta job de reprocessamento de pagamentos `PENDING` com débito já confirmado ([[09 - Decisões e Riscos]], ADR-11).
- O orquestrador trata `503` do crédito como falha retentável (já retenta N vezes).
