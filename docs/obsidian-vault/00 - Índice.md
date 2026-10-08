---
tags: [spp, piggies, indice]
aliases: [Home, SPP]
---

# SPP — Sistema de Pagamento com Porquinhos 🐷

> [!abstract] Em uma frase
> Três microserviços Java (versão já configurada no projeto: 25) + Micronaut que, juntos, fazem um cliente pagar um merchant em **Piggies** (stablecoin do Inter): reservar saldo, validar merchant, confirmar o débito e creditar o recebível, publicando o evento no Kafka.

## Mapa das notas

| # | Nota | Para que serve |
|---|------|----------------|
| 01 | [[01 - Visão Geral e Requisitos]] | O que o desafio pede, regras e critérios implícitos |
| 02 | [[02 - Arquitetura e Fluxo]] | Diagrama, sequência, máquina de estados, onde cada dado mora |
| 03 | [[03 - Contratos (Contract-First)]] | OpenAPI por serviço + JSON Schema do evento Kafka |
| 04 | [[04 - PiggiesPaymentCoordinator]] | Serviço de borda, assíncrono, orquestrador |
| 05 | [[05 - PiggiesReserveService]] | Reserva e débito do cliente (PostgreSQL) |
| 06 | [[06 - PiggiesMerchantService]] | Validação do merchant, crédito, publicação no Kafka |
| 07 | [[07 - Estratégia de Testes]] | Unitários + integração com Testcontainers |
| 08 | [[08 - Cronograma 1h30]] | Plano de entregas, divisão em equipe, checklist |
| 09 | [[09 - Decisões e Riscos]] | ADRs curtos, pontos em aberto, perguntas que a banca pode fazer |
| 10 | [[10 - Divisão da Equipe (4 devs)]] | Quem faz o quê, interfaces combinadas, checkpoints |

## Como usar este vault
- Abra a pasta `docs/obsidian-vault` como vault no Obsidian (*Open folder as vault*).
- Os diagramas usam **Mermaid** (renderiza nativamente no Obsidian).
- Checklists (`- [ ]`) em [[08 - Cronograma 1h30]] são o painel de acompanhamento da equipe.
- Prompts/pesquisas de IA usados no projeto devem ir em `docs/ai/` (exigência do README do desafio).

## Regras do README que guiam tudo
1. **Contract-first**: contratos (OpenAPI/JSON Schema) ANTES do código, um por serviço, inclusive eventos Kafka.
2. **Serviço de borda assíncrono**: o cliente não fica olhando ampulheta → `202 Accepted` + consulta de status.
3. **Testes unitários e de integração** são obrigatórios.
4. **Entrega parcial funcionando > muito código que não faz nada.**
5. **Tudo que a IA gerar deve ser entendido por quem commitou** — "a IA que fez" elimina.
6. Tempo total: **1h30**.
