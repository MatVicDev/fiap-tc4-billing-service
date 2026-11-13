# language: pt
Funcionalidade: Orçamento e pagamento via Mercado Pago como participante da Saga
  Como cliente da oficina
  Quero aprovar o orçamento e pagar pelo Mercado Pago
  E, se a OS falhar depois do pagamento, receber o estorno automaticamente

  Contexto:
    Dado que o orquestrador solicitou um orçamento com itens no total de "291.00"

  Cenário: Cliente aprova e paga o orçamento
    Quando o cliente aprova o orçamento
    Então deve ser publicado "billing.orcamento-aprovado" com link de pagamento
    Quando o Mercado Pago notifica o pagamento como "aprovado"
    Então o orçamento deve estar "PAGO"
    E deve ser publicado "billing.pagamento-confirmado"

  Cenário: Pagamento recusado pelo Mercado Pago
    Quando o cliente aprova o orçamento
    E o Mercado Pago notifica o pagamento como "recusado"
    Então deve ser publicado "billing.pagamento-recusado"
    E o pagamento deve estar "RECUSADO"

  Cenário: Estorno como compensação após falha na execução
    Quando o cliente aprova o orçamento
    E o Mercado Pago notifica o pagamento como "aprovado"
    E o orquestrador solicita o estorno do pagamento
    Então o pagamento deve estar "ESTORNADO"
    E o orçamento deve estar "ESTORNADO"
    E deve ser publicado "billing.pagamento-estornado"

  Cenário: Cliente rejeita e o orquestrador cancela o orçamento
    Quando o cliente rejeita o orçamento
    Então deve ser publicado "billing.orcamento-rejeitado"
    Quando o orquestrador solicita o cancelamento do orçamento
    Então deve ser publicado "billing.orcamento-cancelado"
