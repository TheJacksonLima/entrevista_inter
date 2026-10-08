---
tags: [spp, requisitos]
---

# 01 — Visão Geral e Requisitos

Voltar: [[00 - Índice]]

## O problema
O cliente escaneia um **QR Code** que contém `merchantId` e `amount` (inteiro de Piggies). O sistema precisa:

1. Registrar a **intenção de pagamento**;
2. **Reservar** o saldo do cliente;
3. **Validar** que o merchant está ativo;
4. Quando as 3 coisas deram certo → **confirmar**: debitar o cliente, creditar o merchant, publicar evento.

As etapas 1–3 acontecem **em paralelo**.

## Exemplo canônico
Cliente A paga 100 Piggies ao Merchant X:
`intenção registrada` + `reserva de 100 em A` + `X ativo?` → ✅ → `A: −100`, `X: +100`, evento publicado.

## Simplificações oficiais (não implementar!)
- Valor **sempre inteiro** de Piggies (use `long`, nunca `double`/`BigDecimal` sem necessidade).
- **Sem cancelamento após confirmação** (estorno fora do escopo).

> [!note] Mas cancelamento ANTES da confirmação existe
> Se a reserva funcionou e o merchant é inválido (ou vice-versa), é preciso **liberar a reserva**. Isso é compensação, não "cancelamento pós-confirmação".

## Requisitos funcionais
| ID | Requisito | Serviço |
|----|-----------|---------|
| RF1 | Receber pedido de pagamento (cliente, merchant, valor) e responder imediatamente | Coordinator |
| RF2 | Registrar intenção de pagamento com id único | Coordinator |
| RF3 | Reservar saldo (falha se saldo insuficiente) | Reserve |
| RF4 | Validar merchant ativo | Merchant |
| RF5 | Orquestrar reserva + validação em paralelo (após registrar a intenção) e decidir sucesso/falha | Coordinator |
| RF6 | Confirmar o débito (reserva → debitado) | Reserve |
| RF7 | Registrar o recebível/crédito do merchant (idempotente) | Merchant |
| RF8 | Publicar evento de pagamento no Kafka (com schema) | Merchant |
| RF9 | Consultar status do pagamento | Coordinator |
| RF10 | Liberar reserva se o pagamento falhar | Reserve |

## Requisitos não funcionais
- **Assíncrono na borda** (202 + polling).
- **Idempotência**: reprocessar o mesmo `paymentId` (retry HTTP, redelivery Kafka) não pode debitar/creditar duas vezes.
- **Consistência de saldo**: nunca ficar negativo, mesmo com concorrência.
- **Contract-first** e **testado**.
- Stack: **Java 25** (já configurado no `pom.xml`/`mise.toml`, mantido como está), **Micronaut 4.10.6**, **PostgreSQL**, **Kafka**, Maven.

## O que o scaffold já traz
O repo clonado (`entrevista_inter`) é um projeto Micronaut único com: Hibernate JPA, Micronaut Data, Kafka, Postgres, Validation, Problem JSON, Lombok e uma `AbstractIntegrationTest` com **Testcontainers** (Postgres + Kafka). Veja [[09 - Decisões e Riscos]] sobre ajustes necessários (módulos, placeholders).
