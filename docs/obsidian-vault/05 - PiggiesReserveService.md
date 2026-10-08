---
tags: [spp, reserve, postgres]
---

# 05 — PiggiesReserveService

Voltar: [[00 - Índice]] · Fluxo: [[02 - Arquitetura e Fluxo]]

## Responsabilidade
Dono do **saldo em Piggies** dos clientes. Reserva, confirma (debita de fato) ou libera. Persistência em **PostgreSQL**.

## Conceito-chave: saldo disponível vs. reservado
Reservar não tira o dinheiro, **separa**. Assim o cliente não gasta duas vezes o mesmo saldo enquanto o pagamento está em curso.

```
available + reserved = saldo total
reservar:  available -= v ; reserved += v
confirmar: reserved  -= v            (debitado de verdade)
liberar:   reserved  -= v ; available += v
```

## Modelo de dados
**`account`**
| Coluna | Tipo | Nota |
|--------|------|------|
| `client_id` | varchar PK | |
| `available` | bigint | `CHECK (available >= 0)` |
| `reserved` | bigint | `CHECK (reserved >= 0)` |
| `version` | bigint | opcional (otimista) |

**`reservation`**
| Coluna | Tipo | Nota |
|--------|------|------|
| `payment_id` | uuid PK/UNIQUE | chave de idempotência |
| `client_id` | varchar FK | |
| `amount` | bigint | |
| `status` | varchar | `PENDING`, `CONFIRMED`, `RELEASED` |
| `created_at` / `updated_at` | timestamptz | |

> [!info] Como foi implementado
> A reserva faz primeiro `INSERT reservation … ON CONFLICT (payment_id) DO NOTHING` e só então o `UPDATE account … WHERE available >= :v`; se faltar saldo, o rollback desfaz o `INSERT`. Detalhes em [[12 - Implementação Devs 1, 2 e 3]].

## Operações (cada uma em UMA transação)
### Reservar
1. Se existe reservation com esse `payment_id` → devolve a existente (idempotente).
2. `UPDATE account SET available = available - :v, reserved = reserved + :v WHERE client_id = :c AND available >= :v`
3. 0 linhas afetadas → **saldo insuficiente** (409). Senão `INSERT reservation (PENDING)`.

> [!success] Por que `UPDATE ... WHERE available >= :v`?
> É **atômico no banco**: duas reservas concorrentes nunca deixam o saldo negativo, sem precisar de lock explícito. É a resposta curta para "como evitou race condition?".

### Confirmar
- Só de `PENDING`: `reserved -= v`, reservation → `CONFIRMED`. Já `CONFIRMED` → 200 (idempotente). `RELEASED` → 409.

### Liberar
- Só de `PENDING`: devolve para `available`, reservation → `RELEASED`. Já `RELEASED` → 200. `CONFIRMED` → 409 (sem cancelamento pós-confirmação!).

## Dados de teste/seed
Contas `A` (1000), `B` (50), `POOR` (0) via migration/seed de teste. Hibernate `update` do scaffold serve para a prova; **Flyway** é melhoria se sobrar tempo.

## Testes
- Unit: regras de transição de estado e idempotência.
- Integração (Postgres real): **concorrência** — 20 threads reservando 10 cada em conta de 100 → exatamente 10 sucessos, saldo final consistente. Ver [[07 - Estratégia de Testes]].

## Stretch
- Expiração: job que libera reservas `PENDING` com mais de N minutos.
