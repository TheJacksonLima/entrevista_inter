---
tags: [spp, arquitetura]
---

# 02 — Arquitetura e Fluxo

Voltar: [[00 - Índice]] · Próximo: [[03 - Contratos (Contract-First)]]

> [!info] Fonte da verdade visual
> Os diagramas oficiais da equipe estão em `docs/diagramas.md` (caso de uso + sequência). Esta nota os explica e acrescenta falhas, estados e dados.

## Visão de componentes

```mermaid
flowchart LR
    App([App do cliente]) -- "POST /payments (202)" --> C

    subgraph SPP
        C[PiggiesPaymentCoordinator<br/>:8080]
        R[PiggiesReserveService<br/>:8081]
        M[PiggiesMerchantService<br/>:8082]
    end

    C -- "HTTP: reservar / confirmar / liberar" --> R
    C -- "HTTP: validar merchant / creditar" --> M
    M -. "Kafka: PagamentoConfirmado" .-> K[(Kafka)]
    K -.-> Ext([Sistemas externos])

    C --- DBC[(Postgres<br/>coordinator)]
    R --- DBR[(Postgres<br/>reserve)]
    M --- DBM[(Postgres<br/>merchant)]
```

> [!tip] Um Postgres, três bancos/schemas
> Cada serviço é dono dos seus dados e nunca lê tabela de outro. Em dev/teste pode ser **uma instância** com bancos lógicos separados.

## Sequência do caminho feliz

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant C as Coordinator
    participant R as ReserveService
    participant M as MerchantService
    participant K as Kafka
    actor EX as Sistema Externo

    Cli->>C: POST /payments {clientId, merchantId, amount}
    C->>C: registra intenção (PENDING)
    C-->>Cli: 202 Accepted {paymentId}
    par Reserva de saldo (HTTP síncrono)
        C->>+R: POST /reserves {paymentId, clientId, amount}
        R-->>-C: 201 reserva PENDING
    and Validação do merchant
        C->>+M: GET /merchants/{merchantId}/status
        M-->>-C: 200 ATIVO
    end
    alt saldo insuficiente ou merchant inativo
        C->>C: status REJECTED
    else reserva + merchant OK
        Note over C,R: confirmação automática, sem ação do cliente
        C->>+R: POST /reserves/{id}/confirm
        R-->>-C: 200 CONFIRMED (débito efetuado)
        C->>+M: POST /receivables {paymentId, merchantId, amount}
        M->>K: publica PagamentoConfirmado
        M-->>-C: 201 crédito registrado
        C->>C: status CONFIRMED
        K-->>EX: consome evento
    end
    Cli->>C: GET /payments/{paymentId}
    C-->>Cli: CONFIRMED | REJECTED
```

### Leitura passo a passo
1. **Intenção síncrona**: o Coordinator grava `PENDING` *antes* de responder `202`. Assim o `GET /payments/{id}` nunca dá 404 por corrida.
   - **Borda assíncrona, interior síncrono**: o cliente recebe `202` na hora; entre os serviços as chamadas HTTP aguardam a resposta (como no `docs/diagramas.md`).
2. **Paralelismo**: reserva e validação do merchant rodam juntas (`CompletableFuture.allOf` ou virtual threads).
3. **Decisão**: só com as duas OK o Coordinator confirma. Confirmação = debitar no Reserve → creditar no Merchant.
4. **Evento**: o *Merchant* publica `PagamentoConfirmado` depois de gravar o recebível, então o evento só existe se o crédito existe.
5. **Status final**: `CONFIRMED` só depois de "creditado"; recusas viram `REJECTED`.
6. **Confirmação automática**: nenhuma ação do cliente depois do QR Code.

## Caminhos de falha
| Falha | Reação do Coordinator |
|-------|----------------------|
| Saldo insuficiente | `REJECTED (INSUFFICIENT_FUNDS)`; nada a liberar |
| Merchant inexistente/inativo | `REJECTED (MERCHANT_INVALID)`; **libera a reserva** |
| Reserve ou Merchant indisponível / timeout | `REJECTED (UNAVAILABLE/TIMEOUT)`; libera reserva (best effort) |
| Falha ao confirmar débito | Reserva continua `PENDING`; retry; expiração libera |
| Falha ao creditar **após** débito confirmado | Retry idempotente do crédito (não há estorno pós-débito) — ver [[09 - Decisões e Riscos]] |
| Crédito repetido (retry) | Merchant responde 200 com o mesmo recebível (UNIQUE `payment_id`) |

## Máquina de estados do pagamento (Coordinator)

> O nome do estado de recusa segue o diagrama oficial: **`REJECTED`** (não `FAILED`).

```mermaid
stateDiagram-v2
    [*] --> PENDING: intenção registrada
    PENDING --> CONFIRMED: reserva OK + merchant OK + débito + crédito
    PENDING --> REJECTED: reserva ou merchant falhou
    CONFIRMED --> [*]
    REJECTED --> [*]
```

Estado da **reserva** (Reserve): `PENDING → CONFIRMED` ou `PENDING → RELEASED`.

## Onde cada dado mora
| Dado | Serviço | Tabela |
|------|---------|--------|
| Intenção/status do pagamento | Coordinator | `payment` |
| Saldo e reservas | Reserve | `account`, `reservation` |
| Merchants e recebíveis | Merchant | `merchant`, `receivable` |

Detalhes em [[04 - PiggiesPaymentCoordinator]], [[05 - PiggiesReserveService]] e [[06 - PiggiesMerchantService]].
