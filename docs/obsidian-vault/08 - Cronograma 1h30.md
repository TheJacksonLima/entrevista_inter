---
tags: [spp, cronograma, planejamento]
---

# 08 — Cronograma 1h30

Voltar: [[00 - Índice]]

> [!quote] Princípio
> "Uma entrega parcial que faz alguma coisa vale mais do que um monte de código que não realiza nada." → entregar **fatias verticais** funcionando, não camadas pela metade.

## Prioridade (MoSCoW)
- **Must**: contratos, caminho feliz ponta a ponta, saldo insuficiente, merchant inválido + release, idempotência básica, testes unitários + 1 integração por serviço.
- **Should**: Kafka com schema validado, concorrência testada, timeout.
- **Could**: outbox, DLQ, expiração de reservas, Flyway, Docker Compose.

## Linha do tempo

| Janela | Fase | Entregável | Quem (4 pessoas) |
|--------|------|-----------|------------------|
| 0:00–0:10 | **Setup** | Java já configurado (25) mantido, estrutura multi-módulo compilando, `mvnw` ok, branches, `docs/ai/` criado | Todos (um dirige) |
| 0:10–0:25 | **Contratos** | 3 OpenAPI + 1 JSON Schema (PagamentoConfirmado) commitados | Todos revisam; cada um escreve o do seu serviço |
| 0:25–0:55 | **Implementação paralela** | Cada serviço com endpoints + persistência + unit tests | Dev1: Coordinator (borda) · Dev2: Reserve · Dev3: Merchant · Dev4: contratos + orquestrador |
| 0:55–1:10 | **Integração** | Coordinator chamando Reserve/Merchant reais; crédito Coordinator→Merchant + evento Kafka | A + B + C |
| 1:10–1:25 | **Testes e falhas** | Cenários de erro, idempotência, concorrência, teste e2e | Todos |
| 1:25–1:30 | **Fechamento** | README com como rodar, prompts de IA commitados, commit final | Todos |

> [!info] Time de 4
> Divisão detalhada por dev, interfaces e checkpoints em [[10 - Divisão da Equipe (4 devs)]].

> [!tip] Com 2 pessoas
> A faz Coordinator; B faz Reserve e depois Merchant. Corte DLQ/outbox e deixe o e2e para um teste só.
> **Com 1 pessoa:** ordem Reserve → Merchant → Coordinator.

## Checklist de acompanhamento

### Setup
- [ ] Confirmar que o JDK 25 do `mise.toml` está instalado (`java -version`) e o build compila *(build verificado só com JDK 21 — ver [[11 - Implementação Dev 4]])*
- [x] Decidir estrutura (multi-módulo — ver [[09 - Decisões e Riscos]]) e implementar
- [x] Remover `Piggy.java` / `Test_jacksonTest` placeholders; renomear `artifactId`
- [x] Criar `docs/ai/` (falta registrar os prompts usados)

### Contratos ([[03 - Contratos (Contract-First)]])
- [x] `coordinator.openapi.yaml`
- [x] `reserve.openapi.yaml`
- [x] `merchant.openapi.yaml`
- [x] `pagamento-confirmado.v1.schema.json`

### Reserve ([[05 - PiggiesReserveService]])
- [ ] Entidades + repositórios
- [ ] Reservar com `UPDATE` condicional
- [ ] Confirmar / liberar / idempotência
- [ ] Testes unit + integração (incl. concorrência)

### Merchant ([[06 - PiggiesMerchantService]])
- [ ] Entidades + seed (X ativo, Y inativo)
- [ ] `GET validation`
- [ ] `POST /receivables` idempotente (UNIQUE `payment_id`)
- [ ] Publisher `PagamentoConfirmado` (Kafka)
- [ ] Testes unit + integração (Kafka)

### Coordinator ([[04 - PiggiesPaymentCoordinator]])
- [ ] `POST /payments` 202 + `GET /payments/{id}`
- [x] Clients HTTP (bloqueantes, fora da thread de borda) com timeout e retry
- [x] Orquestrador com reserva + validação em paralelo
- [x] Compensação (release) e timeout
- [x] Chamada de crédito ao Merchant com retry idempotente
- [ ] Testes unit + integração

### Fechamento
- [ ] Fluxo ponta a ponta demonstrável (script `curl` no README)
- [ ] Todos os testes passando (`mvnw test`)
- [ ] Prompts/pesquisas de IA commitados
- [ ] Todos conseguem explicar cada trecho do código 🎯

## Regras de equipe
- Contrato é a fonte da verdade: precisa mudar? Avise e altere o YAML primeiro.
- Commits pequenos e frequentes; `main` sempre compilando.
- Quem usou IA para gerar um trecho **lê e entende** antes de commitar.
- Bloqueado > 5 min? Peça ajuda — vencem juntos.
