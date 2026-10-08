---
tags: [spp, equipe, planejamento]
---

# 10 — Divisão da Equipe (4 devs)

Voltar: [[00 - Índice]] · Cronograma geral: [[08 - Cronograma 1h30]]

> [!abstract] Ideia central
> Três serviços = três donos, mais **um quarto dev de plataforma/contratos** que destrava os outros no começo e, depois, assume o **orquestrador** (a parte mais densa do Coordinator). Cada dev trabalha num módulo/pacote próprio → quase zero conflito de merge.

## Papéis

| Dev | Papel | Dono de | Depende de |
|-----|-------|---------|-----------|
| **Dev 1** | Coordinator — borda | `POST/GET /payments`, tabela `payment`, idempotência, validação, Problem JSON | Contrato do Coordinator (Dev 4) |
| **Dev 2** | Reserve | Todo o `PiggiesReserveService` ([[05 - PiggiesReserveService]]) | Contrato do Reserve (Dev 4) |
| **Dev 3** | Merchant | Todo o `PiggiesMerchantService` + Kafka ([[06 - PiggiesMerchantService]]) | Contrato do Merchant e do evento (Dev 4) |
| **Dev 4** | Plataforma + orquestração | Setup multi-módulo, **contratos**, depois clients HTTP + `PaymentOrchestrator` + e2e | Nada no início; depois Devs 2 e 3 |

## Detalhe por dev

### Dev 1 — Coordinator: borda e persistência
- [ ] Módulo `coordinator` (porta 8080), entidade `Payment` + repositório
- [ ] `POST /payments`: valida body, gera `paymentId`, grava `PENDING`, devolve `202` + `Location`
- [ ] `GET /payments/{id}`: `200`/`404`
- [ ] `Idempotency-Key` (UNIQUE) → mesmo `paymentId`
- [ ] Aciona `PaymentOrchestrator.process(paymentId)` de forma assíncrona (interface combinada com o Dev 4)
- [ ] Atualização de status (`CONFIRMED`/`REJECTED` + `failure_reason`) exposta como `PaymentService`
- [ ] Testes: unit (controller/serviço) + integração (Postgres Testcontainer, orquestrador mockado)

### Dev 2 — Reserve
- [ ] Módulo `reserve` (porta 8081), `account` + `reservation`, seed (`A`=1000, `B`=50, `POOR`=0)
- [ ] `POST /reserves` com `UPDATE ... WHERE available >= v` (409 se insuficiente)
- [ ] `POST /reserves/{id}/confirm` e `/release`, idempotentes
- [ ] `GET /reserves/{id}`
- [ ] Testes: transições, idempotência e **concorrência** (20 threads × 10 em saldo 100)

### Dev 3 — Merchant e Kafka
- [ ] Módulo `merchant` (porta 8082), `merchant` + `receivable`, seed (`X` ativo, `Y` inativo)
- [ ] `GET /merchants/{merchantId}/status`
- [ ] `POST /receivables` idempotente (UNIQUE `payment_id`; 201 novo / 200 repetido)
- [ ] Publicar `PagamentoConfirmado` (`@KafkaClient`, key=`paymentId`, `acks=all`) após gravar
- [ ] Testes: unit; integração com Postgres + Kafka Testcontainers, validando o evento contra o JSON Schema

### Dev 4 — Plataforma, contratos e orquestração
**Fase A (0:00–0:25), destravando todos**
- [ ] Converter em multi-módulo (`pom` pai + `coordinator`, `reserve`, `merchant`), remover `Piggy`/`Test_jacksonTest`, mover `AbstractIntegrationTest` para um módulo `testing-support` (ou copiar por módulo)
- [ ] Publicar a estrutura numa branch `main` **antes** dos outros começarem a codar
- [ ] Escrever/validar os 3 OpenAPI + 1 JSON Schema em `contracts/` ([[03 - Contratos (Contract-First)]])
- [ ] Criar `docs/ai/` e `docker-compose.yml` (Postgres + Kafka) para rodar manualmente

**Fase B (0:25–1:00), o coração do Coordinator**
- [ ] `ReserveClient` e `MerchantClient` (`@Client` declarativo, timeout curto, 1 retry)
- [ ] `PaymentOrchestrator`: reserva + validação em paralelo → confirm → crédito → status; recusas → `REJECTED`
- [ ] Compensação `release` e timeout global ([[09 - Decisões e Riscos]])
- [ ] Testes unit do orquestrador com todos os ramos (clients mockados)

**Fase C (1:00–1:30), integração**
- [ ] Teste ponta a ponta com os 3 serviços e script `curl` no README
- [ ] Revisão de contratos × implementação

## Combinados de interface (fechar nos primeiros 10 min)

```java
// Dev 4 entrega, Dev 1 chama (stub inicial no minuto 0:25)
public interface PaymentOrchestrator {
    void process(UUID paymentId);   // assíncrono, não lança; atualiza status via PaymentService
}

// Dev 1 entrega, Dev 4 chama
public interface PaymentService {
    Payment get(UUID id);
    void markConfirmed(UUID id);
    void markRejected(UUID id, String reason);
}
```
Reserve e Merchant não têm dependência de código entre si nem do Coordinator: só do **contrato HTTP**. Isso permite que o Dev 4 teste o orquestrador com clients mockados antes de os serviços existirem.

## Linha do tempo cruzada

| Janela | Dev 1 | Dev 2 | Dev 3 | Dev 4 |
|--------|-------|-------|-------|-------|
| 0:00–0:10 | Lê contratos em rascunho, combina interfaces | idem | idem | **Estrutura multi-módulo** e push |
| 0:10–0:25 | Entidade `Payment`, esqueleto do controller | Entidades `account/reservation` + seed | Entidades `merchant/receivable` + seed | **Contratos** (OpenAPI + Schema); cada dev revisa o seu |
| 0:25–0:55 | `POST/GET /payments`, idempotência, testes | Reservar/confirmar/liberar + testes | Status, recebível idempotente, Kafka + testes | **Clients + Orchestrator** + testes unit |
| 0:55–1:10 | Integra com orquestrador real | Teste de concorrência | Teste de integração Kafka | Integra Coordinator ↔ Reserve ↔ Merchant |
| 1:10–1:25 | Cenários de erro/timeout | Revisa/expira reservas (stretch) | DLQ/`event_published` (stretch) | e2e + `curl` no README |
| 1:25–1:30 | Todos: `mvnw test`, prompts de IA em `docs/ai/`, commit final | | | |

## Regras para trabalhar em 4 sem conflito
1. **Dono por módulo**: só o dono edita o módulo; mudança em módulo alheio vai por PR/aviso.
2. **Arquivos compartilhados** (`pom` pai, `contracts/`, `README.md`) têm um único dono: **Dev 4**. Os demais pedem a mudança.
3. **Branches curtas**: `feat/coordinator-api`, `feat/reserve`, `feat/merchant`, `feat/orchestrator`; merge em `main` a cada fatia funcionando (mínimo a cada ~20 min).
4. **Contrato mudou?** O Dev 4 atualiza o YAML primeiro e avisa o canal; implementação depois.
5. **Mocks primeiro**: ninguém espera outro serviço — Dev 1 usa orquestrador stub, Dev 4 usa clients mockados, Devs 2/3 só falam HTTP/DB.
6. **Revisão cruzada** (para ninguém ficar com "a IA que fez"): Dev 1 ↔ Dev 4 (Coordinator), Dev 2 ↔ Dev 3 (Reserve/Merchant). Cada autor explica o código ao revisor.
7. **Prompts de IA** vão para `docs/ai/` no mesmo commit do código gerado.

## Pontos de sincronização (hard checkpoints)
| Hora | Checkpoint | Critério |
|------|-----------|----------|
| 0:10 | Estrutura no `main` | `mvnw -q compile` passa nos 3 módulos |
| 0:25 | Contratos aprovados | 4 devs deram OK; sem mudança sem aviso |
| 0:55 | Cada serviço sobe isolado | Endpoints respondem; testes unit verdes |
| 1:10 | Fluxo feliz ponta a ponta | `POST /payments` → `GET` retorna `CONFIRMED` |
| 1:25 | Congelamento | Só correções; `mvnw test` verde |

## Se o tempo apertar (ordem de corte)
1. Cortar stretch (DLQ, expiração, Outbox, Flyway).
2. Devs 2 e 3 terminam cedo → ajudam o Dev 4 no orquestrador e no e2e.
3. Mantém sempre: caminho feliz, saldo insuficiente, merchant inválido, idempotência, testes unit + 1 integração por serviço.

## Riscos específicos da divisão
| Risco | Mitigação |
|-------|-----------|
| Dev 4 vira gargalo na fase A | Timebox 10 min para a estrutura; contratos em rascunho já circulam desde 0:00 |
| Orquestrador chega tarde | Dev 1 começa com stub; Devs 2/3 liberados ajudam o Dev 4 |
| Conflito no `pom` pai | Só o Dev 4 edita; dependências novas por pedido |
| Integração só no fim | Checkpoint 0:55 com serviços reais subindo (docker-compose) |
