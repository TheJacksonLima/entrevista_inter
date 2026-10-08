# Contratos (contract-first)

Fonte da verdade das integrações. **Mudou o contrato? Atualize o arquivo no mesmo commit do código**
(e avise a equipe antes de quebrar um consumidor).

| Arquivo | Dono | Consumidor |
|---------|------|-----------|
| `coordinator.openapi.yaml` | PiggiesPaymentCoordinator | App |
| `reserve.openapi.yaml` | PiggiesReserveService | Coordinator |
| `merchant.openapi.yaml` | PiggiesMerchantService | Coordinator |
| `events/pagamento-confirmado.v1.schema.json` | PiggiesMerchantService (produtor) | Sistemas externos |

Teste de contrato dos eventos: `co.inter.piggies.testing.ContractSchemas.assertValid(...)`
(módulo `testing-support`).
