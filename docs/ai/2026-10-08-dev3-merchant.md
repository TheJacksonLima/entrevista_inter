# 2026-10-08 — Dev 3: PiggiesMerchantService

**Ferramenta:** Claude Code (Sonnet 5.5)

**Prompt (resumo):** implementar o módulo `merchant` (Micronaut + PostgreSQL + Kafka) contract-first:
`GET /merchants/{id}/status`, `POST /receivables` idempotente por `paymentId`, `GET /merchants/{id}/receivables`,
publicação de `PagamentoConfirmado` (chave = paymentId) após o commit, seed X/Y, testes unitários e de integração.

**O que a IA gerou** (`merchant/`): entidades `Merchant`/`Receivable`; porta `ReceivableStore` (+ `JpaReceivableStore`,
INSERT ... ON CONFLICT (payment_id) DO NOTHING); `ReceivableService` (não transacional: cada operação do store faz
commit, então a publicação ocorre depois do commit); porta `PagamentoConfirmadoPublisher` (+ `@KafkaClient` com
acks=all e idempotência); controllers; handler de erros em `application/problem+json`; seed idempotente na subida;
testes `ReceivableServiceTest`, `PagamentoConfirmadoJsonTest` e `MerchantApiIntegrationTest`. `Placeholder.java` removido.

**Decisões**
- Republicação (`event_published=false` num retry): reutiliza o MESMO `eventId` e `occurredAt` (= `created_at`),
  guardados no recebível (coluna extra `event_id`), para os consumidores deduplicarem.
- Falha ao publicar: recebível fica gravado, resposta 503 (problem+json); o retry com o mesmo paymentId republica.
- Corrida com o mesmo paymentId: o perdedor recebe 200 com o recebível do vencedor e não publica (quem gravou publica).
- Mesmo paymentId com merchant/valor diferentes: 409 (não está no contrato; ver sugestões).

**Verificado por ferramenta:** compilação (main + test) e 11 testes sem Docker com JDK 21 (9 do serviço,
2 de JSON do evento validado com `ContractSchemas.assertValid`).
**Não verificado:** testes que exigem Docker (`MerchantApiIntegrationTest`, `MerchantApplicationTest`) — Postgres,
Kafka, mapeamento Hibernate/DDL, SQL nativo e o `@KafkaClient` real nunca foram exercitados.
