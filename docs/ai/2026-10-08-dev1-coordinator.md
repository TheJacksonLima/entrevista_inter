# Dev 1 - Coordinator (borda e persistência)

## Prompt (resumo)
Implementar no módulo `coordinator`: entidade JPA `Payment`, `POST /payments` (202, Idempotency-Key, PENDING commitado antes do orquestrador), `GET /payments/{id}`, `PaymentStatusUpdater` real, remover `Placeholder`, testes unitários e de integração.

## Gerado
- `domain/Payment`, `domain/PaymentStatus`
- `service/PaymentRepository` (Micronaut Data, `GenericRepository`), `PaymentService` (registro + idempotência, trata corrida pela UNIQUE), `RepositoryPaymentStatusUpdater` (só transiciona de PENDING; nunca regride)
- `web/PaymentController` + DTOs (`PaymentRequest`, `PaymentAccepted`, `PaymentStatusResponse`, `ProblemBody`)
- Testes: `PaymentServiceTest`, `RepositoryPaymentStatusUpdaterTest` (unitários, fake em memória), `PaymentControllerTest` (integração, Postgres via Testcontainers, orquestrador substituído por fake)
- Removido `Placeholder.java`

## Verificado
- Compilação main + test com JDK 21; 11 testes unitários novos passaram (6 + 5).
- `PaymentControllerTest` apenas compila; não foi executado (sem Docker).
