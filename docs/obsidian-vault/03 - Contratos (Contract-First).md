---
tags: [spp, contratos, openapi, kafka]
---

# 03 — Contratos (Contract-First)

Voltar: [[00 - Índice]]

> [!warning] Primeira entrega do projeto
> Nada de código de negócio antes dos contratos commitados. É regra explícita do README e vira o "acordo" que permite a equipe trabalhar em paralelo.

## Estrutura sugerida
```
contracts/
├── coordinator.openapi.yaml
├── reserve.openapi.yaml
├── merchant.openapi.yaml
└── events/
    └── pagamento-confirmado.v1.schema.json
```

## Coordinator — `coordinator.openapi.yaml`
| Método | Rota | Resposta | Notas |
|--------|------|----------|-------|
| POST | `/payments` | `202` `{paymentId, status:"PENDING"}` + header `Location` | Header opcional `Idempotency-Key` |
| GET | `/payments/{paymentId}` | `200` `{paymentId,status,reason?,amount,...}` / `404` | Polling do App |

Body do POST:
```json
{ "clientId": "A", "merchantId": "X", "amount": 100 }
```
Validações: `amount` inteiro `>= 1`; ids não vazios. Erros em **Problem JSON** (`application/problem+json`, já no pom).

## Reserve — `reserve.openapi.yaml`
| Método | Rota | Resposta |
|--------|------|----------|
| POST | `/reserves` | `201` PENDING · `409` saldo insuficiente · `200` se `paymentId` já reservado (idempotente) |
| POST | `/reserves/{paymentId}/confirm` | `200` CONFIRMED (idempotente) · `404` · `409` se já RELEASED |
| POST | `/reserves/{paymentId}/release` | `200` RELEASED (idempotente) · `409` se já CONFIRMED |
| GET | `/reserves/{paymentId}` | `200` / `404` |

## Merchant — `merchant.openapi.yaml`
| Método | Rota | Resposta |
|--------|------|----------|
| GET | `/merchants/{merchantId}/status` | `200` `{merchantId, active:true/false}` · `404` desconhecido |
| POST | `/receivables` | `201` crédito registrado · `200` se `paymentId` já creditado (idempotente) · `409` merchant inativo · `404` |
| GET | `/merchants/{merchantId}/receivables` | `200` lista (útil p/ testes e demo) |

Body do crédito: `{ "paymentId": "<uuid>", "merchantId": "X", "amount": 100 }`.

## Evento Kafka (JSON Schema)
| Tópico | Produtor | Consumidor | Chave |
|--------|----------|-----------|-------|
| `piggies.pagamento-confirmado.v1` | Merchant | Sistemas externos | `paymentId` |

`pagamento-confirmado.v1.schema.json`:
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "PagamentoConfirmado",
  "type": "object",
  "required": ["eventId","paymentId","merchantId","amount","occurredAt"],
  "properties": {
    "eventId":    { "type": "string", "format": "uuid" },
    "paymentId":  { "type": "string", "format": "uuid" },
    "merchantId": { "type": "string" },
    "amount":     { "type": "integer", "minimum": 1 },
    "occurredAt": { "type": "string", "format": "date-time" }
  },
  "additionalProperties": false
}
```

> [!tip] Chave = paymentId
> Garante ordem por pagamento na partição e dá aos consumidores externos uma chave natural de idempotência.

## Como "provar" que o contrato é respeitado
- Teste de contrato: serializar o evento e **validar contra o JSON Schema** (ex.: `networknt json-schema-validator`, só em `test`).
- OpenAPI: no mínimo, testes de integração cobrindo cada rota/status descrito.

## Cuidados
- `additionalProperties: false` + versão no nome do tópico/arquivo (`.v1`) para evoluir sem quebrar.
- Mudou o contrato? Atualize o YAML/Schema **no mesmo commit** do código.
