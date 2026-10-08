# Diagramas — SPP (Sistema de Pagamento com Porquinhos)

> O cliente recebe `202 Accepted` na hora (borda assíncrona). Internamente, o Coordinator
> chama o ReserveService de forma **síncrona** (HTTP, aguardando a resposta) e, quando
> reserva + merchant estão OK, **confirma o débito automaticamente**, sem nenhuma ação do cliente.

## Caso de uso

```mermaid
flowchart LR
    Cliente(["👤 Cliente"])
    Merchant(["🏪 Merchant"])
    Externo(["📡 Sistema Externo<br/>(consumidor Kafka)"])

    subgraph SPP["SPP — Sistema de Pagamento com Porquinhos"]
        direction TB

        subgraph COORD["PiggiesPaymentCoordinator"]
            UC1(["1. Iniciar Pagamento<br/>QR Code: merchant + valor<br/>→ intenção registrada"])
            UC4(["4. Confirmar Pagamento<br/>automático · status: CONFIRMED"])
        end

        subgraph RES["PiggiesReserveService · PostgreSQL"]
            UC2(["2. Reservar Saldo<br/>status: PENDING"])
            DEB(["Confirmar débito<br/>status: CONFIRMED"])
        end

        subgraph MER["PiggiesMerchantService · PostgreSQL + Kafka"]
            UC3(["3. Validar Merchant<br/>merchant ativo?"])
            CRE(["Creditar merchant"])
            UC5(["5. Publicar Evento<br/>PagamentoConfirmado"])
        end
    end

    Cliente -->|"POST /payments<br/>202 Accepted"| UC1
    UC1 ==>|"HTTP síncrono<br/>(em paralelo)"| UC2
    UC1 -.->|em paralelo| UC3
    UC2 -->|reserva OK| UC4
    UC3 -->|merchant OK| UC4
    UC4 ==>|"automático<br/>HTTP síncrono"| DEB
    DEB --> CRE
    CRE --> UC5
    CRE --> Merchant
    UC5 -->|tópico Kafka| Externo
```

## Sequência

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant PC as PaymentCoordinator
    participant RS as ReserveService
    participant MS as MerchantService
    participant K as Kafka
    actor EX as Sistema Externo

    C->>PC: POST /payments {clientId, merchantId, amount}
    PC->>PC: registra intenção (PENDING)
    PC-->>C: 202 Accepted {paymentId}

    par Reserva de saldo (síncrona)
        PC->>+RS: POST /reserves {paymentId, clientId, amount}
        RS-->>-PC: 201 reserva PENDING
    and Validação do merchant
        PC->>+MS: GET /merchants/{merchantId}/status
        MS-->>-PC: 200 ATIVO
    end

    alt saldo insuficiente ou merchant inativo
        PC->>PC: status REJECTED
    else reserva + merchant OK
        Note over PC,RS: confirmação automática, sem ação do cliente
        PC->>+RS: POST /reserves/{id}/confirm (síncrono)
        RS-->>-PC: 200 CONFIRMED (débito efetuado)
        PC->>+MS: POST /receivables {paymentId, merchantId, amount}
        MS->>K: publica PagamentoConfirmado
        MS-->>-PC: 201 crédito registrado
        PC->>PC: status CONFIRMED
        K-->>EX: consome evento
    end

    C->>PC: GET /payments/{paymentId}
    PC-->>C: CONFIRMED | REJECTED
```
