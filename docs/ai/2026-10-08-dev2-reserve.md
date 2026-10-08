# 2026-10-08 — Dev 2: módulo `reserve` (PiggiesReserveService)

## Prompt (resumido)
Implementar o PiggiesReserveService (Micronaut 4.10.6, PostgreSQL, porta 8081) conforme `contracts/reserve.openapi.yaml`:
contas `account`/`reservation`, seed A=1000/B=50/POOR=0, endpoints reserve/confirm/release/get, idempotência por
paymentId, atomicidade sob concorrência, erros `application/problem+json`, testes unitários + integração
(incl. 20 threads x 10 em conta de 100). Dono exclusivo de `reserve/`.

## O que foi gerado
- Domínio: `ReserveStatus`, `Reservation`, `ReserveRules` (decisão pura APPLY/NOOP/CONFLICT), `ReserveException`
  (NotFound / InsufficientBalance / InvalidState).
- Porta `ReserveStore` + `JpaReserveStore` (SQL nativo atômico via `EntityManager`) + `ReserveService`
  (`@Transactional`, uma transação por caso de uso).
- Entidades `AccountEntity` / `ReservationEntity` (só geram o schema; CHECK >= 0 via `columnDefinition`).
- `AccountSeeder` (StartupEvent, `INSERT ... ON CONFLICT DO NOTHING`), `ReserveController`, DTOs `@Serdeable`,
  `ReserveExceptionHandler` (problem+json). `Placeholder.java` removido.
- Concorrência: `INSERT reservation ... ON CONFLICT (payment_id) DO NOTHING` primeiro (reivindica o paymentId; a
  segunda transação com o mesmo id bloqueia até a primeira terminar e então devolve a existente com 200), depois
  `UPDATE account ... WHERE available >= :v` (0 linhas => exceção => rollback do INSERT => 409).
  Confirm/release: `UPDATE reservation SET status=... WHERE status='PENDING'` + ajuste de saldo na mesma transação.
- Testes: `ReserveRulesTest`, `ReserveServiceTest` (+ fake `InMemoryReserveStore`) — unitários;
  `ReserveControllerIntegrationTest` (Postgres/Testcontainers: fluxo feliz, idempotência, 409, 400, 404, conflitos,
  concorrência 20x10 e mesmo paymentId em paralelo).

## O que foi verificado
- Compilação main + test (JDK 21 override) e execução real de `ReserveRulesTest` + `ReserveServiceTest`: 12 testes, 0 falhas.
- NÃO executado (sem Docker): `ReserveControllerIntegrationTest` e `ReserveApplicationTest` — apenas compilados.
  SQL nativo, wiring do `EntityManager`/`@Transactional`, validação 400 e content-type problem+json não foram
  exercitados contra um banco real.
