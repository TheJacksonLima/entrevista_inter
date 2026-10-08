---
tags: [spp, adr, riscos]
---

# 09 — Decisões e Riscos

Voltar: [[00 - Índice]]

## Ajustes necessários no scaffold
> [!warning] Divergências encontradas ao ler o projeto clonado
> - `pom.xml` e `mise.toml` estão em **Java 25**. Decisão: **manter o Java já configurado no projeto**, sem alterar `jdk.version`/`release.version`.
> - O projeto é **um único módulo** (`test_jackson`) mas o desafio pede **3 serviços**.
> - Há placeholders (`Piggy`, `Test_jacksonTest`, `artifactId test_jackson`) a remover/renomear.
> - `application.yml` aponta para `postgres` local com senha vazia e `hbm2ddl=update` — ok para dev, ajustar por serviço (porta, banco).

## ADR-1 — Estrutura: multi-módulo Maven ✅
**Decisão:** `pom` pai + módulos `coordinator`, `reserve`, `merchant`. Cada módulo tem seu `Application` e `application.yml` (portas 8080/8081/8082).
**Alternativas:** (a) um app só com 3 "serviços" lógicos — mais rápido, mas foge de microserviços e de contrato por serviço; (b) 3 repositórios — inviável em 1h30.
**Custo:** ~10 min de setup; permite trabalho paralelo sem conflito.

## ADR-2 — Comunicação: HTTP entre serviços, Kafka só para saída ✅
Reserva, validação, débito e crédito precisam de resposta imediata para o orquestrador decidir → HTTP. Kafka serve para **publicar o fato** para sistemas externos.

## ADR-3 — Um único evento, publicado pelo Merchant ✅ (segue `docs/diagramas.md`)
O Merchant publica `PagamentoConfirmado` ao registrar o crédito. Vantagem: o evento só existe se o recebível existe, e há um único contrato Kafka para manter.
**Risco:** o enunciado diz que o Merchant também *consome* eventos. Se a banca esperar isso, evoluir para: Coordinator publica evento de débito confirmado → Merchant consome e credita (idempotente) → publica `PagamentoConfirmado`. **Vale perguntar na entrevista.**

## ADR-4 — Assíncrono = 202 + polling ✅
Simples, sem infra extra. Webhook/SSE/WebSocket ficam como evolução.

## ADR-5 — Atomicidade do saldo via `UPDATE ... WHERE available >= v` ✅
Sem lock pessimista nem retry de versão; correto sob concorrência. Ver [[05 - PiggiesReserveService]].

## ADR-6 — Sem Saga framework / 2PC
Compensação explícita (`release`) no orquestrador. Suficiente para o escopo e explicável em 30 s.

## ADR-7 — Intenção registrada de forma síncrona antes do 202 ✅
Segue o diagrama da equipe. O `GET /payments/{id}` logo após o 202 sempre encontra o pagamento (`PENDING`). Trade-off: o enunciado fala em registrar a intenção "em paralelo"; aqui ela vem imediatamente antes do paralelismo de reserva + validação, o que é barato (um insert) e elimina uma corrida.

## ADR-8 — Status da reserva: `PENDING → CONFIRMED | RELEASED` ✅
Nome `PENDING` alinhado ao diagrama.

## ADR-9 — Contratos reais do último commit (`3eccfca`) ✅
Rotas adotadas, vindas de `docs/diagramas.md`: `POST /reserves`, `POST /reserves/{id}/confirm`, `GET /merchants/{merchantId}/status`, `POST /receivables {paymentId, merchantId, amount}`. Status do pagamento: `PENDING`, `CONFIRMED`, **`REJECTED`**. O Merchant não recebe `clientId`, então o evento `PagamentoConfirmado` não o carrega.

## ⚠️ Lacuna no diagrama: reserva órfã
No ramo `REJECTED` o diagrama só marca o status. Se a reserva deu certo e o merchant está inativo, o saldo fica preso em `PENDING`. Mantive o `POST /reserves/{id}/release` como compensação (nota [[05 - PiggiesReserveService]]) — **combinar com o time** se entra no escopo ou vira expiração (stretch).

## Riscos
| Risco | Prob. | Impacto | Mitigação |
|-------|-------|---------|-----------|
| Estourar o tempo com setup multi-módulo | M | A | Timebox de 10 min; fallback: módulo único com 3 pacotes e 3 profiles |
| Docker indisponível → testes de integração não rodam | M | A | Verificar Docker no minuto 0; unitários independentes |
| Débito confirmado mas crédito falha (Merchant caiu) | M | A | Retry idempotente do crédito; sem estorno por enunciado; job de recuperação (stretch) |
| Recebível gravado mas evento não publicado | M | M | Flag `event_published` + republicar no retry; Outbox como stretch |
| Crédito/evento duplicado | A | A | UNIQUE `payment_id` + endpoint idempotente |
| Reserva órfã (Coordinator caiu no meio) | M | M | Timeout + job de expiração (stretch) |
| Código de IA não compreendido | M | **Eliminatório** | Revisar em dupla; registrar prompts em `docs/ai/` |
| Divergência entre contrato e implementação | M | M | Contrato primeiro; testes de contrato |

## Limitações aceitas (diga com clareza na apresentação)
- Sem autenticação/autorização.
- Sem Outbox/DLQ (se não sobrar tempo) → consistência eventual com janela de falha documentada.
- Um único Postgres compartilhado fisicamente em dev (bancos lógicos separados).
- Sem estorno pós-confirmação (por enunciado).

## Perguntas prováveis da banca (prepare respostas)
1. Como garante que o saldo não fica negativo com 2 pagamentos simultâneos? → ADR-5.
2. E se o Merchant cair depois de o cliente ser debitado? → retry idempotente do crédito; sem estorno por enunciado.
3. O crédito foi chamado duas vezes: duplica? → UNIQUE `payment_id`.
4. Por que 202 e não 200? → UX: sem ampulheta, processamento assíncrono.
5. Como evoluiria o contrato do evento sem quebrar consumidores? → versionamento `.v2`, campos opcionais, `additionalProperties`.
6. O enunciado fala que o Merchant consome Kafka. Onde está isso? → ADR-3 e caminho de evolução.
7. Onde estaria o gargalo em escala? → Reserve (linha quente de conta); particionar por `clientId`.
